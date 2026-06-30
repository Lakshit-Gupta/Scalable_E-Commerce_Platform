#!/usr/bin/env sh
# Run a k6 scenario against the running stack via the grafana/k6 Docker image (no local k6 needed).
# Usage: k6/run.sh [smoke|load] [BASE_URL]
#   k6/run.sh                       # smoke against http://localhost:8080
#   k6/run.sh load                  # load  against http://localhost:8080
#   k6/run.sh smoke http://gw:8080  # custom target
set -eu

SCRIPT="${1:-smoke}"
BASE_URL="${2:-http://localhost:8080}"
DIR="$(cd "$(dirname "$0")" && pwd)"

exec docker run --rm -i --network host \
  -e BASE_URL="$BASE_URL" \
  -v "$DIR:/scripts" \
  grafana/k6 run "/scripts/${SCRIPT}.js"
