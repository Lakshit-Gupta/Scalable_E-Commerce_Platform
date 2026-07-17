# Performance Testing & Observability Bench

Reproducible load-testing + observability harness for the platform on Kubernetes (kind).
Seed at any scale, drive realistic user journeys with k6, watch everything in Grafana, and
capture shareable dashboard PNGs — all from `perf/run.sh`.

> **You do not need to run every scale.** Prove the pipeline once at `demo` scale (below); the
> exact same commands run at `stage1`…`stage5` by changing one word. Bigger stages just need more
> RAM/disk/time — see the table.

## What's here

| Path | Purpose |
|---|---|
| `run.sh` | orchestrator — `seed` / `test` / `capture` / `report` / `all` / `grafana` / `trim` |
| `stages/*.env` | row counts per scale (`demo`, `stage1`…`stage5`) |
| `seeder/` | SQL `generate_series` seeder (users/products/orders/payments) + Keycloak login pool |
| `k6/journeys.js` | weighted user journeys (browse / shop / buy / account) + 7 scenario presets |
| `observability/kube-prom-values.yaml` | lean kube-prometheus-stack values (scrapes the Spring services) |
| `capture/capture.sh` | headless-Chrome screenshots of every Grafana dashboard (dark, kiosk) |
| `capture/profile.sh` | on-demand JFR dump / async-profiler flamegraph of a service pod |
| `report/report.py` | renders `report.md` per run + a diff vs the previous run |
| `results/` | per-run artifacts (gitignored) |

## One-time setup (already done on this host)

```bash
# lean observability tier into the running kind cluster
helm upgrade --install kps prometheus-community/kube-prometheus-stack \
  -n monitoring --create-namespace -f perf/observability/kube-prom-values.yaml --wait

# let headless capture read dashboards without logging in, + k6 remote-write receiver
kubectl -n monitoring set env deploy/kps-grafana GF_AUTH_ANONYMOUS_ENABLED=true GF_AUTH_ANONYMOUS_ORG_ROLE=Viewer
kubectl -n monitoring patch prometheus kps-prometheus --type merge -p '{"spec":{"enableRemoteWriteReceiver":true}}'
```

Prereqs: kind cluster `ecommerce` up with the app (`kubectl apply -k k8s/base` + operators), Docker
(for the `grafana/k6` and `zenika/alpine-chrome` images), `kubectl`, `helm`, `python3`.

## Prove it (demo scale)

```bash
perf/run.sh seed demo               # ~1 min: 50k users, 2k products, 200k orders + Keycloak pool
perf/run.sh test smoke demo         # 20 VUs / 5 min through the gateway
perf/run.sh capture                 # screenshot every Grafana dashboard into the run dir
perf/run.sh report                  # render report.md (headline + slow SQL + screenshots)
# or all at once:
perf/run.sh all smoke demo
```

Watch it live: `perf/run.sh grafana` → http://localhost:3000 (admin/admin).

## Run any scale — change one word

Seed the scale you want, then run any scenario against it:

```bash
perf/run.sh seed  stage1             # 100k users / 20k products / 1M orders
perf/run.sh test  baseline stage1    # 100 VUs / 30 min
perf/run.sh test  stress   stage1    # ramp 100→10k VUs, auto-aborts at the breaking point
```

### Scales (`stages/*.env`)

| Stage | users | products | orders | fits the 32 GB reference host? |
|---|---|---|---|---|
| `demo`  | 50k | 2k | 200k | yes — seconds to seed |
| `stage1` | 100k | 20k | 1M | yes |
| `stage2` | 1M | 100k | 10M | yes (minutes to seed, ~10 GB) |
| `stage3` | 10M | 1M | 100M | tight — hours to seed, ~100 GB disk |
| `stage4` | 50M | 5M | 500M | **needs bigger hardware** (config only) |
| `stage5` | 100M | 10M | 1B | **needs bigger hardware** (config only) |

### Scenarios (`k6/journeys.js`)

| Scenario | Load | Use |
|---|---|---|
| `smoke` | 20 VUs / 5 min | correctness, no immediate failures |
| `baseline` | 100 VUs / 30 min | normal latency/throughput baseline |
| `medium` | 500 VUs / 45 min | moderate load |
| `heavy` | 2000 VUs / 60 min | expected production traffic |
| `stress` | ramp 100→10k VUs | find max sustainable load (auto-abort) |
| `spike` | 100→5000→100 VUs | validate HPA/KEDA scale-out + recovery |
| `soak` | `SOAK_VUS` for `SOAK_DURATION` | leak/stability. **Default 60 min** — set `SOAK_DURATION=12h` on capable hardware |

```bash
SOAK_VUS=500 SOAK_DURATION=60m perf/run.sh test soak stage1     # endurance (default 1h here)
```

## Screenshots for the README

`capture/capture.sh` renders every Grafana dashboard to a dark-theme PNG (headless Chromium, no
host deps beyond Docker). Point it at any run dir; it reads the run's time window:

```bash
FROM=now-30m TO=now perf/run.sh capture perf/results/<run>
```

Profiling a hot service (needs a JDK image with `libstdc++` and the JFR flag from the perf overlay):

```bash
perf/capture/profile.sh flame product-service cpu 30      # async-profiler flamegraph → HTML+PNG
perf/capture/profile.sh jfr   order-service               # JFR dump → open in JMC
```

## Cleanup (post-run)

```bash
perf/run.sh trim        # shrink seeded rows back to ~20 per table, VACUUM, reindex
```

Per-run artifacts live in `results/` (gitignored). Curated hero screenshots for docs are copied to
`docs/perf/`.

## Notes / known limits on the reference host (20 cores / 32 GB)

- The load generator (k6) shares the host with the cluster → numbers are **relative** (great for
  finding bottlenecks and comparing runs, conservative as absolute capacity).
- `stage4`/`stage5` are config-only here — seeding 500M–1B orders needs more disk/RAM/time.
- The k6 login pool authenticates **directly against Keycloak** (the gateway validates the JWT). The
  user-service `/auth/register` path is bypassed because its Keycloak-admin credentials aren't wired
  in this cluster.
- Live profiling needs a JDK base image with `libstdc++` (slim JRE images can't load async-profiler)
  and the `-XX:StartFlightRecording` flag from the perf overlay.
