// Realistic multi-journey load test driving the gateway (REST edge).
// Journeys: browser (search/detail/similar), shopper (login+cart), buyer (checkout), account (profile).
// One script, many scenarios — pick with SCENARIO env. Metrics remote-written to Prometheus so the
// k6 client-side view sits beside server metrics in Grafana.
//
//   k6 run -e SCENARIO=smoke   perf/k6/journeys.js
//   k6 run -e SCENARIO=stress  perf/k6/journeys.js
//
// BASE_URL      gateway base (default http://localhost:8090)
// HOST_HEADER   ingress host (default api.example.local)  — kind ingress is host-routed
// SOAK_VUS / SOAK_DURATION   soak knobs (default 1000 / 60m — capped for the reference host)
import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { SharedArray } from 'k6/data';
import { Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8090';
const HOST = __ENV.HOST_HEADER || 'api.example.local';
const SCENARIO = __ENV.SCENARIO || 'smoke';
const JSON_HEADERS = { 'Content-Type': 'application/json', Host: HOST };
// k6 authenticates directly against Keycloak (gateway = OAuth2 resource server, validates the JWT).
// The user-service /auth/login path is bypassed (its Keycloak admin config is broken in this env).
const KC_TOKEN_URL = __ENV.KC_TOKEN_URL || 'http://localhost:8081/realms/ecommerce/protocol/openid-connect/token';
const KC_CLIENT = __ENV.KC_CLIENT || 'ecommerce-app';
const LOGIN_POOL = Number(__ENV.LOGIN_POOL || 200);

// Seeded sample of real search terms + product ids (emitted by the seeder). Falls back to generic terms.
const sample = new SharedArray('sample', () => {
  try {
    return [JSON.parse(open('./sample.json'))];
  } catch (_) {
    return [{ terms: ['shirt', 'phone', 'shoes', 'bag', 'watch'], productIds: [] }];
  }
})[0];

const journeyDuration = new Trend('journey_duration', true);

// ---- scenario presets ------------------------------------------------------
const THRESH = {
  http_req_failed: ['rate<0.05'],
  http_req_duration: ['p(95)<1500'],
};
function scenarioOptions(name) {
  switch (name) {
    case 'smoke':
      return { executor: 'constant-vus', vus: 20, duration: '5m' };
    case 'baseline':
      return { executor: 'constant-vus', vus: 100, duration: '30m' };
    case 'medium':
      return { executor: 'constant-vus', vus: 500, duration: '45m' };
    case 'heavy':
      return { executor: 'constant-vus', vus: 2000, duration: '60m' };
    case 'stress':
      return {
        executor: 'ramping-arrival-rate',
        startRate: 50, timeUnit: '1s', preAllocatedVUs: 500, maxVUs: 12000,
        stages: [
          { target: 100, duration: '3m' }, { target: 500, duration: '5m' },
          { target: 1000, duration: '5m' }, { target: 2000, duration: '5m' },
          { target: 5000, duration: '5m' }, { target: 10000, duration: '5m' },
        ],
      };
    case 'spike':
      return {
        executor: 'ramping-vus', startVUs: 100,
        stages: [
          { target: 100, duration: '30s' }, { target: 5000, duration: '10s' },
          { target: 5000, duration: '5m' }, { target: 100, duration: '10s' },
          { target: 100, duration: '3m' },
        ],
      };
    case 'soak':
      return {
        executor: 'constant-vus',
        vus: Number(__ENV.SOAK_VUS || 1000),
        duration: __ENV.SOAK_DURATION || '60m',
      };
    default:
      return { executor: 'constant-vus', vus: 20, duration: '2m' };
  }
}
export const options = {
  scenarios: { main: scenarioOptions(SCENARIO) },
  // stress auto-aborts at the breaking point so the run names the max sustainable load.
  thresholds: SCENARIO === 'stress'
    ? { http_req_failed: [{ threshold: 'rate<0.10', abortOnFail: true, delayAbortEval: '30s' }],
        http_req_duration: [{ threshold: 'p(95)<3000', abortOnFail: true, delayAbortEval: '30s' }] }
    : THRESH,
};

// ---- helpers ---------------------------------------------------------------
function pick(arr) { return arr[Math.floor(Math.random() * arr.length)]; }

function login() {
  // Keycloak login-pool user (seeded). Direct password grant → gateway validates the JWT.
  const n = 1 + Math.floor(Math.random() * LOGIN_POOL);
  const id = String(n).padStart(5, '0');
  const res = http.post(
    KC_TOKEN_URL,
    `grant_type=password&client_id=${KC_CLIENT}&username=perfuser_${id}@perf.test&password=Perf!2345`,
    { headers: { 'Content-Type': 'application/x-www-form-urlencoded' }, tags: { journey: 'login' } },
  );
  if (res.status === 200) {
    try { return res.json('access_token'); } catch (_) { return null; }
  }
  return null;
}

function browse() {
  group('browser', () => {
    const t0 = Date.now();
    check(http.get(`${BASE_URL}/api/recommendations/trending`, { headers: { Host: HOST }, tags: { journey: 'browse' } }),
      { 'trending ok': (r) => r.status === 200 });
    const term = pick(sample.terms);
    const s = http.get(`${BASE_URL}/api/products/search?q=${encodeURIComponent(term)}`,
      { headers: { Host: HOST }, tags: { journey: 'browse' } });
    check(s, { 'search 2xx': (r) => r.status >= 200 && r.status < 300 });
    if (sample.productIds.length) {
      const pid = pick(sample.productIds);
      check(http.get(`${BASE_URL}/api/products/${pid}`, { headers: { Host: HOST }, tags: { journey: 'browse' } }),
        { 'detail 2xx': (r) => r.status >= 200 && r.status < 300 });
      http.get(`${BASE_URL}/api/products/${pid}/similar`, { headers: { Host: HOST }, tags: { journey: 'browse' } });
    }
    journeyDuration.add(Date.now() - t0, { journey: 'browse' });
  });
}

function shop(token, checkout) {
  const authed = { headers: { ...JSON_HEADERS, Authorization: `Bearer ${token}` }, tags: { journey: checkout ? 'buy' : 'shop' } };
  group(checkout ? 'buyer' : 'shopper', () => {
    http.get(`${BASE_URL}/api/cart`, authed);
    if (sample.productIds.length) {
      const pid = pick(sample.productIds);
      http.post(`${BASE_URL}/api/cart/items`, JSON.stringify({ productId: pid, quantity: 1 }), authed);
    }
    if (checkout) {
      const key = `k6-${__VU}-${__ITER}-${Date.now()}`;
      const order = http.post(`${BASE_URL}/api/orders`,
        JSON.stringify({ totalAmount: 49.9 }),
        { headers: { ...authed.headers, 'Idempotency-Key': key }, tags: { journey: 'buy' } });
      check(order, { 'order 2xx': (r) => r.status >= 200 && r.status < 300 });
    }
  });
}

// ---- weighted journey mix --------------------------------------------------
export default function () {
  const r = Math.random();
  if (r < 0.5) {
    browse();                                   // 50% browsers
  } else if (r < 0.8) {
    const t = login(); if (t) shop(t, false); else browse();   // 30% shoppers
  } else if (r < 0.95) {
    const t = login(); if (t) shop(t, true); else browse();    // 15% buyers
  } else {
    const t = login();                          // 5% account
    if (t) http.put(`${BASE_URL}/api/users/me`,
      JSON.stringify({ phoneNumber: '+15550000000' }),
      { headers: { ...JSON_HEADERS, Authorization: `Bearer ${t}` }, tags: { journey: 'account' } });
  }
  sleep(Math.random() * 2 + 0.5);
}
