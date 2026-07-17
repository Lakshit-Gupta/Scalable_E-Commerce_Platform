#!/usr/bin/env bash
# Seed the running kind stack for a load test.
#   perf/seeder/seed.sh [stage]      # stage = demo|stage1..stage5  (default demo)
#
# Realism tables (users, products) + volume tables (orders, order_items, payments) are generated
# server-side with generate_series (no row-by-row inserts, no temp CSVs). A small pool of REAL
# Keycloak users is registered through the gateway so k6 can authenticate. Products are reindexed
# into Elasticsearch. Emits perf/k6/sample.json (real search terms + product ids) for the load test.
set -euo pipefail
cd "$(dirname "$0")"

STAGE="${1:-demo}"
NS="${NS:-ecommerce}"
PG_POD="${PG_POD:-pg-1}"
PG_CT="${PG_CT:-postgres}"
GW="${GW:-http://localhost:8090}"
HOST_HDR="${HOST_HDR:-api.example.local}"

# shellcheck disable=SC1090
source "../stages/${STAGE}.env"
echo ">> Stage ${STAGE}: USERS=$USERS PRODUCTS=$PRODUCTS ORDERS=$ORDERS items/order=$AVG_ITEMS_PER_ORDER pool=$KEYCLOAK_LOGIN_USERS"

psql_db() {  # psql_db <db> <sqlfile> [-v k=v ...]
  local db="$1" file="$2"; shift 2
  kubectl exec -i -n "$NS" "$PG_POD" -c "$PG_CT" -- psql -U postgres -d "$db" -q "$@" -f - < "$file"
}

echo ">> [1/5] users ($USERS)…"
psql_db users_db    sql/users.sql    -v users="$USERS"
echo ">> [2/5] products ($PRODUCTS)…"
psql_db products_db sql/products.sql -v products="$PRODUCTS"
echo ">> [3/5] orders + items ($ORDERS)…"
psql_db orders_db   sql/orders.sql   -v orders="$ORDERS" -v users="$USERS" -v products="$PRODUCTS" -v avg_items="$AVG_ITEMS_PER_ORDER"
echo ">> [4/5] payments ($ORDERS)…"
psql_db payments_db sql/payments.sql -v orders="$ORDERS" -v users="$USERS"

echo ">> ANALYZE…"
for db in users_db products_db orders_db payments_db; do
  kubectl exec -n "$NS" "$PG_POD" -c "$PG_CT" -- psql -U postgres -d "$db" -qc "ANALYZE;" >/dev/null
done

echo ">> [5/5] Keycloak login pool ($KEYCLOAK_LOGIN_USERS real users) via admin partialImport…"
# The realm requires a complete profile (firstName/lastName) or the password grant fails with
# "Account is not fully set up". partialImport OVERWRITE upserts fully-set-up, verified users in one shot.
# (The user-service /auth/register path is unused here — it can't reach the Keycloak admin API in this env.)
KC_LOCAL="${KC_LOCAL:-http://localhost:8081}"
KC_PF=""
if ! curl -sf -o /dev/null "$KC_LOCAL/realms/ecommerce" 2>/dev/null; then
  kubectl -n "$NS" port-forward svc/keycloak 8081:8080 >/dev/null 2>&1 & KC_PF=$!; sleep 4
fi
ADMIN_TOKEN=$(curl -s -X POST "$KC_LOCAL/realms/master/protocol/openid-connect/token" \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -d 'grant_type=password&client_id=admin-cli&username=admin&password=admin' \
  | python3 -c 'import sys,json;print(json.load(sys.stdin)["access_token"])')
python3 - "$KEYCLOAK_LOGIN_USERS" > /tmp/kc-pool.json <<'PY'
import json,sys
n=int(sys.argv[1]); users=[]
for i in range(1,n+1):
    u=f"perfuser_{i:05d}@perf.test"
    users.append({"username":u,"email":u,"enabled":True,"emailVerified":True,
                  "firstName":"Perf","lastName":"User","requiredActions":[],
                  "credentials":[{"type":"password","value":"Perf!2345","temporary":False}]})
json.dump({"ifResourceExists":"OVERWRITE","users":users},sys.stdout)
PY
curl -s -o /dev/null -w "   partialImport HTTP %{http_code}\n" -X POST \
  "$KC_LOCAL/admin/realms/ecommerce/partialImport" \
  -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' \
  --data-binary @/tmp/kc-pool.json
rm -f /tmp/kc-pool.json
[ -n "$KC_PF" ] && kill "$KC_PF" 2>/dev/null || true
echo "   pool ready."

echo ">> Reindex products into Elasticsearch…"
kubectl exec -n "$NS" deploy/product-service -- wget -q -O- --post-data='' http://localhost:8080/products/reindex || \
  echo "   (reindex call returned non-zero; search may lag until product-service reindexes)"

echo ">> Emit perf/k6/sample.json (search terms + product ids)…"
kubectl exec -n "$NS" "$PG_POD" -c "$PG_CT" -- psql -U postgres -d products_db -tAc \
  "SELECT json_build_object(
     'terms', (SELECT array_agg(DISTINCT split_part(name,' ',1)) FROM (SELECT name FROM products LIMIT 300) s),
     'productIds', (SELECT array_agg(id) FROM (SELECT id FROM products ORDER BY random() LIMIT 50) t)
   )" > ../k6/sample.json
echo "   $(wc -c < ../k6/sample.json) bytes."

echo ">> Seed complete. Row counts:"
for pair in "users_db users" "products_db products" "orders_db orders" "orders_db order_items" "payments_db payments"; do
  db=${pair% *}; tbl=${pair#* }
  n=$(kubectl exec -n "$NS" "$PG_POD" -c "$PG_CT" -- psql -U postgres -d "$db" -tAc "SELECT count(*) FROM $tbl")
  printf '   %-24s %s\n' "$db.$tbl" "$n"
done
