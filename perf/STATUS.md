# Perf bench — build status & resume point

_Last session ended early: host hit an OOM flood during a load run. Load testing stopped; the
monitoring stack we installed was uninstalled to free RAM. Everything below is on disk (not yet
committed to git)._

## DONE (works, verified)

- **Harness fully built** under `perf/` (see file map below).
- **Observability** installed + verified (kube-prometheus-stack scraped all 9 Spring services).
  → **uninstalled** at session end to free RAM. Reinstall = one command (see "Resume").
- **Seeding works** — `perf/run.sh seed demo` loaded 50k users / 2k products / ~200k orders +
  payments, a 200-user Keycloak login pool, and reindexed products into Elasticsearch.
- **Auth solved** — the user-service `/auth/register` + `/auth/login` path is broken in this
  cluster (its Keycloak-admin creds aren't wired). Workaround baked into the harness: the login
  pool is created directly in Keycloak (`partialImport`), and k6 grabs tokens straight from
  Keycloak's token endpoint — the gateway validates the JWT. Fully working.
- **Smoke test green** — 14,065 requests, ~46 req/s, P95 47 ms, all journey checks 100%.
- **Screenshots captured** → `docs/perf/screenshots/` (committed location):
  `spring-boot-apm.png`, `jvm-micrometer.png`, `node-exporter-nodes.png`.
- **Report** — `perf/results/<run>/report.md` (headline metrics, slow SQL, pod usage, run-to-run diff).
- **README** — Performance section added with the three dashboards embedded.

## REMAINING (pick up here)

1. **Seed trim was interrupted by the OOM.** `perf/run.sh trim` had started deleting when the host
   OOM'd; final row counts are unconfirmed. The seeded rows still live in the cluster's Postgres.
   Re-run `perf/run.sh trim` when the cluster is healthy and RAM is free (it's idempotent).
2. **Version bump not done** — bump `VERSION` + module poms to `v0.1.15` (per the repo convention).
3. **CLAUDE.md roadmap entry** not added for the perf bench.
4. **Nothing committed** — `git add perf/ docs/perf/ README.md .gitignore` when ready.
5. Optional: k8s per-pod + node-exporter-full dashboards time out in headless Chrome (too many
   panels). They render fine live in Grafana; capture them manually if wanted.

## Resume commands

```bash
# 1. reinstall the (lean) observability stack
helm upgrade --install kps prometheus-community/kube-prometheus-stack \
  -n monitoring --create-namespace -f perf/observability/kube-prom-values.yaml --wait
kubectl -n monitoring set env deploy/kps-grafana GF_AUTH_ANONYMOUS_ENABLED=true GF_AUTH_ANONYMOUS_ORG_ROLE=Viewer
kubectl -n monitoring patch prometheus kps-prometheus --type merge -p '{"spec":{"enableRemoteWriteReceiver":true}}'

# 2. seed → test → capture → report (demo scale). Change 'demo' to stage1..stage5 for bigger runs.
perf/run.sh all smoke demo
perf/run.sh grafana        # → http://localhost:3000 (admin/admin)

# 3. clean up after a run
perf/run.sh trim
```

## If the host is short on RAM again

The 20 GB baseline is the pre-existing `ecommerce` kind cluster (kafka + ES + keycloak +
Postgres HA + all services) + desktop apps — **not** this bench. To reclaim it:

```bash
kubectl -n ecommerce scale deploy --all --replicas=0    # pause the app tier (reversible)
# or, to stop the whole cluster:
kind delete cluster --name ecommerce                    # DESTRUCTIVE — wipes the cluster
```

Do **not** run k6 `stress`/`heavy`/`soak` scenarios on this 32 GB host — they were the OOM trigger.
`smoke`/`baseline` are safe.

## File map

| Path | What |
|---|---|
| `perf/run.sh` | orchestrator — `seed`/`test`/`capture`/`report`/`all`/`grafana`/`trim` |
| `perf/README.md` | full ops guide + per-scale command table |
| `perf/stages/*.env` | row counts per scale (demo, stage1…stage5) |
| `perf/seeder/seed.sh` + `sql/` | SQL seeder + Keycloak login pool |
| `perf/k6/journeys.js` | weighted user journeys + 7 scenario presets |
| `perf/observability/kube-prom-values.yaml` | lean Prometheus/Grafana values |
| `perf/capture/capture.sh` | Grafana dashboard → PNG (dark, kiosk) |
| `perf/capture/profile.sh` | JFR dump / async-profiler flamegraph |
| `perf/report/report.py` | render report.md + run-to-run diff |
| `docs/perf/screenshots/` | committed hero screenshots for the README |
| `perf/results/` | per-run artifacts (gitignored) |
