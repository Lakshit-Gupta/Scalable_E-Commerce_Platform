// k6 load test (v0.0.6): ramping VUs over the core storefront flow through the gateway:
//   browse + search (public) -> register (auth) -> cart + place order (authed, order -> payment gRPC).
// Run: k6/run.sh load   (override target with BASE_URL env).
import http from 'k6/http';
import { check, group, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const JSON_HEADERS = { 'Content-Type': 'application/json' };

export const options = {
  scenarios: {
    storefront: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 20 }, // ramp up
        { duration: '1m', target: 20 },  // steady
        { duration: '30s', target: 0 },  // ramp down
      ],
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.05'],
    http_req_duration: ['p(95)<1000'],
  },
};

// Register a throwaway user and return its access token (or null on failure).
function register() {
  const email = `load_${__VU}_${Date.now()}@example.com`;
  const res = http.post(
    `${BASE_URL}/api/auth/register`,
    JSON.stringify({ email, password: 'password123' }),
    { headers: JSON_HEADERS },
  );
  const ok = check(res, { 'register is 2xx': (r) => r.status >= 200 && r.status < 300 });
  if (!ok) return null;
  try {
    return res.json('accessToken');
  } catch (_) {
    return null;
  }
}

export default function () {
  group('browse + search', () => {
    const search = http.get(`${BASE_URL}/api/products/search?q=headphones`);
    check(search, { 'search 2xx': (r) => r.status >= 200 && r.status < 300 });
    const browse = http.get(`${BASE_URL}/api/products/search?q=a`);
    check(browse, { 'browse 2xx': (r) => r.status >= 200 && r.status < 300 });
  });

  const token = register();
  if (token) {
    const authed = { headers: { ...JSON_HEADERS, Authorization: `Bearer ${token}` } };
    group('cart + order', () => {
      http.get(`${BASE_URL}/api/cart`, authed);
      const order = http.post(`${BASE_URL}/api/orders`, JSON.stringify({ totalAmount: 99.9 }), authed);
      check(order, { 'order 2xx': (r) => r.status >= 200 && r.status < 300 });
    });
  }

  sleep(1);
}
