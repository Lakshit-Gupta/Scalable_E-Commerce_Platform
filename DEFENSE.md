# Project Defense Playbook

A copy-paste guide to **prove every technology is actually working** during the defense.
Stack to demonstrate: **Java · Spring Boot 3 · Spring Cloud Gateway · Spring Security (OAuth2) ·
gRPC · Apache Kafka · RabbitMQ · PostgreSQL · Redis · Elasticsearch · Docker.**

> Dev passwords (from `.env`): DB `ecommerce_dev_pw`, Redis `redis_dev_pw`, RabbitMQ `rabbit_dev_pw`,
> Grafana `admin`. Keycloak demo user: **`testuser` / `password`** (realm `ecommerce`, client `ecommerce-app`).

---

## 0. The one-sentence pitch

> "It's a microservice e-commerce platform. The browser only ever talks REST to **one** Spring Cloud
> Gateway, which validates a **Keycloak OAuth2** token and injects the user identity. Services talk to
> each other with **gRPC** for synchronous calls and over **Kafka / RabbitMQ** for async events.
> Each service owns its own **PostgreSQL** database, uses **Redis** for caching/cart, and product search
> runs on **Elasticsearch**. Everything runs as **Docker** containers via one `docker compose up`."

---

## 1. Bring the stack up (do this before the panel arrives)

```bash
docker compose up --build -d        # health-gated; wait until all are healthy
docker compose ps                   # PROOF of Docker: ~20 containers, state "healthy"
```

On startup the product-service **auto-seeds 63 products** into Postgres **and** Elasticsearch
(see `DataSeeder.java`). Verify:

```bash
curl -s "http://localhost:8080/api/products?size=5" | head      # from Postgres
curl -s "http://localhost:9200/products/_count"                 # from Elasticsearch -> {"count":63,...}
```

Open the storefront: **http://localhost:3000** — you should see the hero, category chips and a
grid of product cards.

### Get an access token (needed for any write/authenticated call)

```bash
TOKEN=$(curl -s http://localhost:8081/realms/ecommerce/protocol/openid-connect/token \
  -d grant_type=password -d client_id=ecommerce-app \
  -d username=testuser -d password=password | jq -r .access_token)
echo "$TOKEN" | cut -c1-40        # should print the start of a JWT
```

---

## 2. The "golden path" demo — one action lights up almost everything

**Place an order** (through the storefront *or* curl). A single order exercises **Gateway → Security →
PostgreSQL → gRPC → Kafka → RabbitMQ → Redis** in one shot.

```bash
# via the gateway, with the bearer token
curl -s -X POST http://localhost:8080/api/orders \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"totalAmount":"109999","items":[{"productId":"<paste-an-id-from-step-1>","quantity":1,"price":"109999"}]}' | jq
```

Now show, side by side, what that one request triggered:

```bash
docker compose logs order-service        | grep -E "outbox->kafka"     # Kafka publish
docker compose logs notification-service | grep -E "\[kafka\]"         # Kafka consume
docker compose logs notification-service | grep -E "order notification"# RabbitMQ consume -> email
```

Then **refresh the storefront home page** — "Trending now" updates, because the recommendation-service
consumed the same Kafka event and wrote to a Redis sorted set. That's the whole architecture in 30s.

---

## 3. Prove each technology individually

| # | Tech | Where it lives (code) | Live proof command / URL | What you say |
|---|------|------------------------|--------------------------|--------------|
| 1 | **Java 21 + Spring Boot 3** | every `services/*` + `infrastructure/*` | `docker compose exec product-service java -version` → 21; `curl localhost:8080/actuator/health` | "Boot 3 MVC on Java 21 **virtual threads**; Actuator exposes health/metrics." |
| 2 | **Spring Cloud Gateway** | `infrastructure/api-gateway` | `curl -s localhost:8080/actuator/gateway/routes \| jq '.[].route_id'` | "Reactive edge; the **only** port (8080) clients can reach. Routes by path → `lb://SERVICE` via Eureka." |
| 3 | **Spring Security (OAuth2)** | gateway `SecurityConfig.java` | `curl -i localhost:8080/api/orders/x` → **401** problem+json; add `-H "Authorization: Bearer $TOKEN"` → **200/404** | "Gateway is an **OAuth2 resource server**; validates the Keycloak JWT against the realm **JWKS**, then injects `X-User-Id`." |
| 4 | **gRPC** | `proto/`, `order-service` (client), `payment-service` (`@GrpcService`) | place an order (step 2), then **Jaeger** http://localhost:16686 → trace shows a `grpc` span `order → payment`; or `grep -ri grpc proto/src` | "Internal sync calls are **gRPC** (binary, contract-first). Order calls Payment over gRPC, wrapped in a resilience4j **circuit breaker**." |
| 5 | **Apache Kafka** | `order-service/outbox/OutboxRelay.java` (producer), `notification`/`recommendation` `@KafkaListener` | `docker compose logs order-service \| grep outbox->kafka` and `... notification-service \| grep '\[kafka\]'` | "**Event streaming / fan-out.** Order stages an event in a DB **outbox** in the same tx, a relay publishes to topic `ecommerce.order-events`, multiple consumers fan out (analytics, recommendations)." |
| 6 | **RabbitMQ** | `order` `OrderEventPublisher` → `notification` `@RabbitListener` | **Management UI** http://localhost:15672 (`ecommerce`/`rabbit_dev_pw`) → Queues → `notification.queue`; place an order and watch the message counter | "**Commands / work queue with DLQ.** Order publishes `order.placed`; notification consumes it to send the confirmation email, manual ack + retry → dead-letter." |
| 7 | **PostgreSQL** | every service, **db-per-service** | `docker compose exec postgres psql -U ecommerce -d products_db -c "select category, count(*) from products group by category;"` ; `\l` shows `products_db`, `orders_db`, … | "Each service owns its schema — no shared DB. 63 products seeded here." |
| 8 | **Redis** | product cache, cart store, gateway rate-limiter, reco ZSETs | `docker compose exec redis redis-cli -a redis_dev_pw KEYS '*'` ; `... ZREVRANGE reco:popularity 0 -1 WITHSCORES` | "Three jobs: **cache** (product `@Cacheable`), **cart** store, gateway **rate-limit** tokens, and recommendation **sorted sets**." |
| 9 | **Elasticsearch** | `product-service` `ProductSearchService` | `curl -s "localhost:8080/api/products/search?q=headphones" \| jq '.[].name'` returns headphones first; `curl "localhost:8080/api/products/search?q=shoes&minPrice=5000"` | "Full-text **multi_match** (fuzzy, name boosted) + category/price **filters**, plus **More-Like-This** for `/{id}/similar`." |
| 10 | **Docker** | `docker-compose.yml`, `Dockerfile.template` | `docker compose ps` (all healthy) | "~20 containers — app services + Postgres/Redis/Rabbit/Kafka/ES/Keycloak + observability — one command, health-gated." |

---

## 4. Elasticsearch — show it's a *real* search, not a DB query

This is the most-asked. Run these to demonstrate **relevance ranking + filters**:

```bash
# fuzzy full-text: typo still matches (multi_match fuzziness=AUTO)
curl -s "http://localhost:8080/api/products/search?q=headphnes" | jq '.[].name'

# faceted filter: category + price range, executed in Elasticsearch (non-scoring filter clause)
curl -s "http://localhost:8080/api/products/search?category=Footwear&minPrice=5000" | jq '.[] | {name, price}'

# content-based "more like this" (ES MLT) — open any product page, scroll to "More like this"
curl -s "http://localhost:8080/api/products/<id>/similar" | jq '.[].name'

# the index itself
curl -s "http://localhost:9200/_cat/indices?v"
```

If ES ever looks empty (e.g. it booted slower than the seeder), rebuild the index from Postgres:

```bash
curl -s -X POST http://localhost:8080/api/products/reindex \
  -H "Authorization: Bearer $TOKEN"        # -> {"reindexed":63}
```

---

## 5. Likely panel questions → crisp answers

- **"Why both Kafka *and* RabbitMQ — isn't that redundant?"**
  Different jobs. **RabbitMQ = commands / work queues** (one consumer does the work, acks, retries,
  dead-letters — e.g. "send this email"). **Kafka = event stream** (an immutable log many independent
  consumers replay/fan-out from — analytics, recommendations, search indexing). Same event can do both.

- **"Why gRPC internally instead of REST everywhere?"**
  Service-to-service sync calls are high-volume and contract-first. gRPC gives a typed `.proto`
  contract, binary HTTP/2 (smaller/faster than JSON), and streaming. REST stays at the **edge** for
  browsers. (Feign was used early and removed in v0.0.3.)

- **"How does the transactional outbox avoid losing events?"**
  The order row and the outbox row are written in the **same DB transaction**. A scheduled relay polls
  unpublished rows and sends them to Kafka, marking them published. A crash mid-send just re-sends next
  run → **at-least-once**, so consumers are idempotent. No dual-write inconsistency.

- **"Where exactly is auth enforced?"**
  Only at the gateway (`SecurityConfig.java`). It's an OAuth2 **resource server** validating the
  Keycloak JWT signature/expiry via **JWKS**, then forwards a trusted `X-User-Id` header. Product
  **reads** and recommendation reads are public; everything else needs a bearer token (try the 401 demo).

- **"Is the search just `SELECT * LIKE`?"**
  No — it's an Elasticsearch `bool` query: a scoring `multi_match` (fuzzy, `name^3` boost) combined with
  non-scoring `term`/`range` **filter** clauses, served from the `products` index, separate from Postgres.

- **"What happens if payment-service is down?"**
  The order is saved `PENDING` first; the gRPC call is wrapped in a **resilience4j circuit breaker**;
  on failure the fallback throws `503` (RFC-7807) and the order stays `PENDING` for reconciliation.

---

## 6. Handy dashboards to leave open on screen

| URL | What it shows |
|-----|---------------|
| http://localhost:3000 | Storefront (the product) |
| http://localhost:8080/swagger-ui.html | Aggregated API docs, dropdown per service |
| http://localhost:8761 | Eureka — every service registered (proves discovery) |
| http://localhost:15672 | RabbitMQ management — queues + message rates |
| http://localhost:16686 | Jaeger — distributed traces incl. the gRPC span |
| http://localhost:3001 | Grafana — metrics (orders.placed, latency) |
| http://localhost:9090 | Prometheus — raw metrics |

**One-liner to recover if something looks wedged:** `docker compose restart <service>`
(or `docker compose logs -f <service>` to narrate what it's doing live).
