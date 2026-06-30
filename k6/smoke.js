// k6 smoke test (v0.0.6): 1 VU, 1 iteration. Fast "is the edge alive + serving" check.
// Run: k6/run.sh smoke   (or: docker run --rm -i --network host -e BASE_URL=... grafana/k6 run k6/smoke.js)
import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export const options = {
  vus: 1,
  iterations: 1,
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<800'],
  },
};

export default function () {
  const health = http.get(`${BASE_URL}/actuator/health`);
  check(health, { 'gateway health is 200': (r) => r.status === 200 });

  const search = http.get(`${BASE_URL}/api/products/search?q=a`);
  check(search, { 'product search is 2xx': (r) => r.status >= 200 && r.status < 300 });

  sleep(1);
}
