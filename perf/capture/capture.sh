#!/usr/bin/env bash
# Capture beautiful, shareable PNGs of every Grafana dashboard (dark, kiosk) into a run dir.
# Reusable, host-dep-free: headless Chromium via the zenika/alpine-chrome docker image.
#
#   perf/capture/capture.sh [out-dir]     # default: perf/results/<latest>
#
# Requires Grafana anonymous viewer (perf/run.sh enables it once):
#   kubectl -n monitoring set env deploy/kps-grafana GF_AUTH_ANONYMOUS_ENABLED=true GF_AUTH_ANONYMOUS_ORG_ROLE=Viewer
set -euo pipefail
CALLER_PWD="$PWD"
cd "$(dirname "$0")/.."; PERF_DIR="$PWD"
MON_NS="${MON_NS:-monitoring}"
OUT_ARG="${1:-results/$(ls -1 results | grep -E '^[0-9]' | tail -1)}"
# Resolve OUT_ARG to an absolute path — accept absolute, caller-relative, or perf-relative.
case "$OUT_ARG" in
  /*) OUT="$OUT_ARG";;
  *)  if [ -d "$CALLER_PWD/$OUT_ARG" ]; then OUT="$CALLER_PWD/$OUT_ARG"; else OUT="$PERF_DIR/$OUT_ARG"; fi;;
esac
mkdir -p "$OUT/screenshots"
OUT="$(cd "$OUT" && pwd)"            # absolute — survives docker -v mounts regardless of caller cwd
SHOTS="$OUT/screenshots"
FROM="${FROM:-now-30m}"; TO="${TO:-now}"

echo ">> port-forward Grafana…"
kubectl -n "$MON_NS" port-forward svc/kps-grafana 3000:80 >/dev/null 2>&1 &
PF=$!; trap 'kill $PF 2>/dev/null || true' EXIT
sleep 4

# By default capture only the README-worthy dashboards (fast, relevant). ALL=1 grabs every dashboard.
# Heavy default k8s dashboards can hang headless Chrome, so every shot is wrapped in `timeout`.
FILTER="${FILTER:-jvm|springboot|spring-boot|node-exporter---nodes|prometheus-.-overview}"
echo ">> list dashboards (ALL=${ALL:-0})…"
mapfile -t ROWS < <(curl -s "http://localhost:3000/api/search?type=dash-db" \
  | python3 -c 'import sys,json;[print(d["uid"]+"|"+d["title"].lower().replace(" ","-").replace("/","-")) for d in json.load(sys.stdin)]')

for row in "${ROWS[@]}"; do
  uid="${row%%|*}"; slug="${row#*|}"
  if [ "${ALL:-0}" != 1 ] && ! echo "$slug" | grep -qE "$FILTER"; then continue; fi
  url="http://localhost:3000/d/${uid}?kiosk&theme=dark&from=${FROM}&to=${TO}&refresh=off"
  echo "   shooting ${slug}.png"
  # --headless=new + virtual-time-budget lets the Grafana SPA fully render; timeout guards a hang.
  timeout 80 docker run --rm --network host -v "$SHOTS:/out" zenika/alpine-chrome \
    --no-sandbox --headless=new --disable-gpu --hide-scrollbars --virtual-time-budget=12000 \
    --window-size=1920,1080 --screenshot="/out/${slug}.png" "$url" >/dev/null 2>&1 \
    && echo "     ok ($(du -k "$SHOTS/${slug}.png" 2>/dev/null | cut -f1) KB)" \
    || { echo "     (timed out / failed: $slug)"; docker kill "$(docker ps -q --filter ancestor=zenika/alpine-chrome | head -1)" 2>/dev/null || true; }
done

# Flamegraphs already collected as HTML in the run dir → render to PNG too.
for fg in "$OUT"/*.html; do
  [ -e "$fg" ] || continue
  name=$(basename "$fg" .html)
  docker run --rm -v "$OUT:/w" zenika/alpine-chrome \
    --no-sandbox --headless=new --virtual-time-budget=6000 --window-size=1600,1000 \
    --screenshot="/w/screenshots/${name}.png" "file:///w/$(basename "$fg")" >/dev/null 2>&1 || true
done

echo ">> screenshots in $OUT/screenshots/"
ls -1 "$OUT/screenshots/" 2>/dev/null || true
