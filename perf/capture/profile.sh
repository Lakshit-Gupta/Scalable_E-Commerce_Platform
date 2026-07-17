#!/usr/bin/env bash
# On-demand JVM profiling of a running service pod.
#   perf/capture/profile.sh jfr   <service> [out-dir]                 # dump the continuous JFR recording
#   perf/capture/profile.sh flame <service> [cpu|wall|alloc|lock] [secs] [out-dir]   # async-profiler flamegraph
#
# JFR: services run with -XX:StartFlightRecording (perf overlay) so a dump is always available;
#      analyze the .jfr in JMC. Flame: downloads async-profiler once into perf/.cache, runs asprof in-pod.
set -euo pipefail
cd "$(dirname "$0")/.."
NS="${NS:-ecommerce}"
MODE="${1:?jfr|flame}"; SVC="${2:?service}"
POD=$(kubectl get pod -n "$NS" -l "app=$SVC" -o jsonpath='{.items[0].metadata.name}')

if [ "$MODE" = jfr ]; then
  OUT="${3:-results/$(ls -1 results | grep -E '^[0-9]' | tail -1)}"; mkdir -p "$OUT"
  kubectl exec -n "$NS" "$POD" -- jcmd 1 JFR.dump filename=/tmp/rec.jfr >/dev/null
  kubectl cp -n "$NS" "$POD:/tmp/rec.jfr" "$OUT/${SVC}.jfr"
  echo ">> $OUT/${SVC}.jfr  (open in JMC)"
  exit 0
fi

EVENT="${3:-cpu}"; SECS="${4:-30}"; OUT="${5:-results/$(ls -1 results | grep -E '^[0-9]' | tail -1)}"; mkdir -p "$OUT"
AP_VER=3.0; AP="async-profiler-${AP_VER}-linux-x64"
if [ ! -d ".cache/$AP" ]; then
  echo ">> fetching async-profiler…"
  curl -sL "https://github.com/async-profiler/async-profiler/releases/download/v${AP_VER}/${AP}.tar.gz" \
    | tar xz -C .cache
fi
kubectl cp -n "$NS" ".cache/$AP/lib/libasyncProfiler.so" "$POD:/tmp/libap.so"
kubectl cp -n "$NS" ".cache/$AP/bin/asprof" "$POD:/tmp/asprof" 2>/dev/null || true
echo ">> profiling $SVC ($EVENT, ${SECS}s)…"
kubectl exec -n "$NS" "$POD" -- sh -c \
  "/tmp/asprof -e $EVENT -d $SECS -f /tmp/flame.html --libpath /tmp/libap.so 1 2>/dev/null || \
   java -agentpath:/tmp/libap.so=start,event=$EVENT,file=/tmp/flame.html,duration=$SECS -version 2>/dev/null || true"
kubectl cp -n "$NS" "$POD:/tmp/flame.html" "$OUT/${SVC}-${EVENT}-flame.html"
echo ">> $OUT/${SVC}-${EVENT}-flame.html  (open in a browser; capture.sh renders it to PNG)"
