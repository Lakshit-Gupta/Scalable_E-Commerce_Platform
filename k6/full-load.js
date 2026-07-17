// Full-system load test (k8s): browse (public reads) + checkout (Keycloak-authed cart->order saga).
// setup() creates N Keycloak users via the admin API so per-user rate-limit buckets behave like real traffic.
// Run in-cluster: kubectl create cm k6-script --from-file=full-load.js; see k6/README.md.
// Env: BASE_URL (gateway), KC_URL (keycloak), KC_ADMIN/KC_ADMIN_PASS (dev: admin/admin).
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';

const GW = __ENV.BASE_URL || 'http://api-gateway:8080';
const KC = __ENV.KC_URL || 'http://keycloak:8080';
const USERS = 150;

const rateLimited = new Counter('rate_limited_429');
const orderPlaced = new Counter('orders_placed');
const orderFailed = new Counter('orders_failed');

export const options = {
  scenarios: {
    browse: {
      executor: 'ramping-vus', exec: 'browse', startVUs: 0,
      stages: [
        { duration: '1m', target: 150 },
        { duration: '1m', target: 300 },
        { duration: '2m', target: 300 },
        { duration: '30s', target: 0 },
      ],
    },
    checkout: {
      executor: 'ramping-vus', exec: 'checkout', startVUs: 0,
      stages: [
        { duration: '1m', target: 50 },
        { duration: '1m', target: 100 },
        { duration: '2m', target: 100 },
        { duration: '30s', target: 0 },
      ],
    },
  },
  summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

export function setup() {
  const admin = http.post(`${KC}/realms/master/protocol/openid-connect/token`, {
    grant_type: 'password', client_id: 'admin-cli',
    username: __ENV.KC_ADMIN || 'admin', password: __ENV.KC_ADMIN_PASS || 'admin',
  }).json('access_token');
  const h = { headers: { Authorization: `Bearer ${admin}`, 'Content-Type': 'application/json' } };
  for (let i = 0; i < USERS; i++) {
    // firstName/lastName required by the KC26 default user profile — without them the account
    // is "not fully set up" and password grants fail with invalid_grant.
    const body = {
      username: `load${i}`, enabled: true, email: `load${i}@example.com`, emailVerified: true,
      firstName: 'Load', lastName: `User${i}`, requiredActions: [],
      credentials: [{ type: 'password', value: 'password', temporary: false }],
    };
    const res = http.post(`${KC}/admin/realms/ecommerce/users`, JSON.stringify(body), h);
    if (res.status === 409) { // exists (maybe from an older broken run) — repair it
      const found = http.get(`${KC}/admin/realms/ecommerce/users?username=load${i}&exact=true`, h).json();
      if (found.length) http.put(`${KC}/admin/realms/ecommerce/users/${found[0].id}`, JSON.stringify(body), h);
    }
  }
  // PRODUCT_IDS="id:price,id:price" pins order targets (seeded high stock); else first 5 from catalog.
  // Catalog list may serve cached stock — Postgres is authoritative for reservations, so no stock filter.
  const products = __ENV.PRODUCT_IDS
    ? __ENV.PRODUCT_IDS.split(',').map((s) => { const [id, price] = s.split(':'); return { id, price: Number(price) }; })
    : http.get(`${GW}/api/products`).json().slice(0, 5).map((p) => ({ id: p.id, price: p.price }));
  if (products.length === 0) throw new Error('no products in catalog');
  return { products };
}

// per-VU token cache (Keycloak dev token lifespan ~300s)
let token = null;
let tokenAt = 0;
function getToken() {
  if (token && Date.now() - tokenAt < 240000) return token;
  if (!token && tokenAt && Date.now() - tokenAt < 10000) return null; // backoff: don't stampede KC after a failed login
  const res = http.post(`${KC}/realms/ecommerce/protocol/openid-connect/token`, {
    grant_type: 'password', client_id: 'ecommerce-app',
    username: `load${__VU % USERS}`, password: 'password',
  }, { tags: { name: 'kc-login' } });
  check(res, { 'login 200': (r) => r.status === 200 });
  token = res.status === 200 ? res.json('access_token') : null;
  tokenAt = Date.now();
  return token;
}

function track(res) {
  if (res.status === 429) rateLimited.add(1);
  return res;
}

export function browse(data) {
  const t = getToken();
  const auth = t ? { Authorization: `Bearer ${t}` } : {};
  const p = data.products[Math.floor(Math.random() * data.products.length)];
  const reqs = [
    ['products-list', `${GW}/api/products`],
    ['search', `${GW}/api/products/search?q=a`],
    ['product-detail', `${GW}/api/products/${p.id}`],
    ['similar', `${GW}/api/products/${p.id}/similar`],
    ['trending', `${GW}/api/recommendations/trending`],
  ];
  for (const [name, url] of reqs) {
    const res = track(http.get(url, { headers: auth, tags: { name } }));
    check(res, { [`${name} 2xx`]: (r) => r.status >= 200 && r.status < 300 });
  }
  sleep(0.3 + Math.random() * 0.7);
}

export function checkout(data) {
  const t = getToken();
  if (!t) { sleep(1); return; }
  const h = { headers: { Authorization: `Bearer ${t}`, 'Content-Type': 'application/json' } };
  const p = data.products[Math.floor(Math.random() * data.products.length)];
  const qty = 1 + Math.floor(Math.random() * 3);

  track(http.post(`${GW}/api/cart/items`, JSON.stringify({ productId: p.id, quantity: qty }),
    { ...h, tags: { name: 'cart-add' } }));
  track(http.get(`${GW}/api/cart`, { ...h, tags: { name: 'cart-get' } }));

  const order = track(http.post(`${GW}/api/orders`, JSON.stringify({
    totalAmount: p.price * qty,
    items: [{ productId: p.id, quantity: qty, price: p.price }],
  }), { ...h, tags: { name: 'order-place' } }));
  if (order.status === 201) orderPlaced.add(1); else orderFailed.add(1);
  check(order, { 'order 201': (r) => r.status === 201 });

  sleep(0.5 + Math.random());
}
