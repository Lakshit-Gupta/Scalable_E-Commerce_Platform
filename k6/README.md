# k6 load testing (v0.0.6)

Performance scenarios that drive the platform **through the api-gateway** (`:8080`), so they exercise
the real edge path: REST → gateway → services → (order → payment over gRPC) → RabbitMQ.

| Script | What it does | When |
|--------|--------------|------|
| `smoke.js` | 1 VU, 1 iteration: gateway health + product list | fast sanity / CI gate |
| `load.js` | ramping VUs (0→20→0): browse + search → register → cart + order | load / perf gate |

## Run

Stack must be up first (`docker compose up --build`). No local k6 needed — uses the `grafana/k6` image:

```bash
k6/run.sh smoke              # smoke vs http://localhost:8080
k6/run.sh load               # load  vs http://localhost:8080
k6/run.sh smoke http://host:8080   # custom target
```

Or directly:

```bash
docker run --rm -i --network host -e BASE_URL=http://localhost:8080 \
  -v "$PWD/k6:/scripts" grafana/k6 run /scripts/smoke.js
```

## Thresholds

Both scripts fail (non-zero exit) if SLOs are breached — `smoke`: `http_req_failed<1%`,
`p(95)<800ms`; `load`: `http_req_failed<5%`, `p(95)<1000ms`. Tune per environment.

CI: `.github/workflows/load-test.yml` is **manual** (`workflow_dispatch`) — boots the stack and runs
the smoke test. Kept off push/PR so heavy runs never block normal CI.
