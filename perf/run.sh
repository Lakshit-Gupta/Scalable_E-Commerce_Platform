#!/usr/bin/env bash
# Perf bench orchestrator. One command surface for the whole workflow.
#
#   perf/run.sh seed    [demo|stage1..stage5]     # generate + load data (default demo)
#   perf/run.sh test    <scenario> [stage]        # run a k6 scenario, collect artifacts
#   perf/run.sh capture [run-dir]                 # screenshot Grafana + flamegraph (README assets)
#   perf/run.sh report  [run-dir]                 # render report.md from collected artifacts
#   perf/run.sh all     <scenario> [stage]        # seed? + test + capture + report
#   perf/run.sh grafana                           # port-forward Grafana to localhost:3000 (admin/admin)
#   perf/run.sh trim                              # shrink seed back to a few rows (post-run cleanup)
#
# scenarios: smoke baseline medium heavy stress spike soak   (see perf/k6/journeys.js)
# stages:    demo stage1 stage2 stage3 stage4 stage5         (see perf/stages/*.env)
#
# Prereqs (already satisfied on the reference host): kind cluster `ecommerce` up with the app +
# observability tier (perf/observability/kube-prom-values.yaml installed via kube-prometheus-stack).
set -euo pipefail
cd "$(dirname "$0")"
NS="${NS:-ecommerce}"
MON_NS="${MON_NS:-monitoring}"
GW="${GW:-http://localhost:8090}"
HOST_HDR="${HOST_HDR:-api.example.local}"
RESULTS="results"

grafana_pf() {  # background port-forward Grafana; echoes the pid
  kubectl -n "$MON_NS" port-forward svc/kps-grafana 3000:80 >/dev/null 2>&1 &
  echo $!
}
prom_pf() {
  kubectl -n "$MON_NS" port-forward svc/kps-prometheus 9090:9090 >/dev/null 2>&1 &
  echo $!
}

cmd_seed()   { seeder/seed.sh "${1:-demo}"; }

cmd_test() {
  local scenario="${1:?scenario required}" stage="${2:-demo}"
  local ts run; ts=$(date +%Y%m%d-%H%M%S); run="$RESULTS/${ts}-${scenario}-${stage}"
  mkdir -p "$run"
  echo ">> [$scenario/$stage] k6 → $run"
  local pid; pid=$(prom_pf); sleep 3
  # Keycloak reachable at localhost:8081 so k6 can grab tokens (gateway validates them).
  kubectl -n "$NS" port-forward svc/keycloak 8081:8080 >/dev/null 2>&1 & local kcpid=$!; sleep 3
  # k6 via docker image (repo convention). Remote-write to Prometheus so k6 metrics land in Grafana.
  docker run --rm -i --network host --user "$(id -u):$(id -g)" \
    -e SCENARIO="$scenario" -e BASE_URL="$GW" -e HOST_HEADER="$HOST_HDR" \
    -e KC_TOKEN_URL="http://localhost:8081/realms/ecommerce/protocol/openid-connect/token" \
    -e K6_PROMETHEUS_RW_SERVER_URL="http://localhost:9090/api/v1/write" \
    -e LOGIN_POOL="$(grep -oP 'KEYCLOAK_LOGIN_USERS=\K.*' stages/${stage}.env)" \
    -v "$PWD/k6:/scripts:ro" -v "$PWD/$run:/out" \
    grafana/k6 run -o experimental-prometheus-rw --summary-export="/out/summary.json" \
    "/scripts/journeys.js" 2>&1 | tee "$run/k6.log" || true
  kill "$pid" "$kcpid" 2>/dev/null || true
  # server-side snapshots
  kubectl top pods -n "$NS" > "$run/pods-top.txt" 2>/dev/null || true
  kubectl get events -n "$NS" --sort-by=.lastTimestamp | tail -40 > "$run/events.txt" 2>/dev/null || true
  kubectl exec -n "$NS" pg-1 -c postgres -- psql -U postgres -d orders_db -c \
    "SELECT query, calls, round(total_exec_time::numeric,1) total_ms, round(mean_exec_time::numeric,2) mean_ms \
     FROM pg_stat_statements ORDER BY total_exec_time DESC LIMIT 20" > "$run/slow-queries.txt" 2>/dev/null || \
    echo "pg_stat_statements not enabled" > "$run/slow-queries.txt"
  echo "$run" > "$RESULTS/.last"
  echo ">> artifacts in $run"
}

cmd_capture() { capture/capture.sh "${1:-$(cat $RESULTS/.last 2>/dev/null)}"; }
cmd_report()  { python3 report/report.py "${1:-$(cat $RESULTS/.last 2>/dev/null)}"; }

cmd_all() {
  local scenario="${1:?scenario required}" stage="${2:-demo}"
  cmd_test "$scenario" "$stage"
  cmd_capture; cmd_report
}

cmd_grafana() {
  echo "Grafana → http://localhost:3000  (admin/admin). Ctrl-C to stop."
  kubectl -n "$MON_NS" port-forward svc/kps-grafana 3000:80
}

cmd_trim() {
  echo ">> Trimming seed to a few rows per table (keeps the schema + a demo-able catalog)…"
  # orders_items is the JPA @OneToMany join table (FK → orders); clear it before trimming orders.
  kubectl exec -n "$NS" pg-1 -c postgres -- psql -U postgres -d orders_db   -qc "DELETE FROM orders_items; DELETE FROM orders WHERE ctid NOT IN (SELECT ctid FROM orders LIMIT 20); DELETE FROM order_items WHERE ctid NOT IN (SELECT ctid FROM order_items LIMIT 20);"
  kubectl exec -n "$NS" pg-1 -c postgres -- psql -U postgres -d payments_db -qc "DELETE FROM payments WHERE ctid NOT IN (SELECT ctid FROM payments LIMIT 20);"
  kubectl exec -n "$NS" pg-1 -c postgres -- psql -U postgres -d users_db    -qc "DELETE FROM users WHERE email LIKE 'seeduser%' AND ctid NOT IN (SELECT ctid FROM users LIMIT 20);"
  kubectl exec -n "$NS" pg-1 -c postgres -- psql -U postgres -d products_db -qc "DELETE FROM products WHERE ctid NOT IN (SELECT ctid FROM products LIMIT 20);"
  for db in users_db products_db orders_db payments_db; do
    kubectl exec -n "$NS" pg-1 -c postgres -- psql -U postgres -d "$db" -qc "VACUUM ANALYZE;" >/dev/null
  done
  kubectl exec -n "$NS" deploy/product-service -- wget -q -O- --post-data='' http://localhost:8080/products/reindex >/dev/null 2>&1 || true
  echo ">> Trim done."
}

case "${1:-}" in
  seed)    shift; cmd_seed "$@";;
  test)    shift; cmd_test "$@";;
  capture) shift; cmd_capture "$@";;
  report)  shift; cmd_report "$@";;
  all)     shift; cmd_all "$@";;
  grafana) cmd_grafana;;
  trim)    cmd_trim;;
  *) grep -E '^#( |$)' "$0" | sed 's/^# \{0,1\}//'; exit 1;;
esac
