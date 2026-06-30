# Scalable E-Commerce Platform

Microservices e-commerce platform on the Spring Cloud stack (Java 21, Spring Boot 3.3),
containerized with Docker Compose.

**Current version: `v0.1.0`** — **Auth-gated cart + checkout**: the storefront logs in via Keycloak
(server-to-server password grant → httpOnly cookie), and authed `/cart` + "Add to cart" → checkout
places an order through the gateway (order → payment over gRPC → Kafka outbox). Prior: v0.0.20 product
detail page; v0.0.19 "more like this"; v0.0.18 Chatwoot; v0.0.17 PostHog; v0.0.16 SvelteKit storefront.
See [Versioning](#versioning). Authoritative project reference: [`CLAUDE.md`](CLAUDE.md).

## Architecture

Communication model: **REST at the edge · gRPC service-to-service · RabbitMQ for async events.**

```
client ── REST ──► api-gateway (8080)   edge: JWT(RS256) validate, rate-limit (Redis),
                       │ lb:// via Eureka      correlation id, security headers, RFC-7807
   ┌──────────┬────────┼─────────┬──────────┬───────────────┐
 user      product    cart     order      payment      notification
 (auth/    (catalog+  (Redis)  (gRPC +    (gRPC server  (RabbitMQ
  JWT)      ES+cache)          RabbitMQ)   + idempotent) consumer)
   │          │                  │ gRPC sync ▲             ▲
   ▼          ▼                  └─ RabbitMQ async events ──┘
 users_db  products_db        orders_db   payments_db   (no DB)

infra: config-server (8888) · eureka-server (8761) · postgres · redis · rabbitmq · elasticsearch
observability: prometheus (9090) · grafana (3001)
```

- **Edge:** Spring Cloud Gateway (reactive) — validates the RS256 JWT once, injects
  `X-User-Id`/`X-User-Role`; rate-limits via Redis; mints/echoes `X-Correlation-Id`; sets security
  headers (HSTS, `X-Content-Type-Options`, `X-Frame-Options`, `Referrer-Policy`, strips `Server`);
  renders gateway errors as RFC-7807 `application/problem+json`.
- **Discovery/config:** Netflix Eureka + Spring Cloud Config (native/local).
- **Sync internal calls:** **gRPC** (net.devh `grpc-spring-boot-starter`) — order → payment,
  resolved via Eureka (`discovery:///PAYMENT-SERVICE`), wrapped by a Spring Cloud
  `CircuitBreakerFactory` (resilience4j) that returns 503 when payment is down.
- **Async events:** RabbitMQ (topic exchange + DLQ) — `order.placed` → notification.
- **Data:** Postgres per service · Redis (cart + product cache) · Elasticsearch (product search).
- **Auth:** RS256 JWT (private key in user-service, public key in gateway). Keys generated locally
  into each module's `src/main/resources/keys/` (gitignored).
- **Shared library (`common/`):** cross-cutting concerns every servlet service inherits by depending
  on it (Spring Boot auto-configuration, zero config):
  - **RFC-7807 errors** — failures render as `application/problem+json` via a shared
    `@RestControllerAdvice`. Domain exceptions map to statuses (`ResourceNotFoundException` → 404,
    `ConflictException` → 409, `UnauthorizedException` → 401, `BadRequestException` → 400,
    `ServiceUnavailableException` → 503); catch-all returns a safe 500.
  - **Bean Validation** — `@Valid` failures return 400 with a per-field `errors` array.
  - **Correlation id** — servlet `CorrelationIdFilter` + gRPC client/server interceptors propagate
    `X-Correlation-Id` across REST, gRPC, and into the MDC (logged as `[%X{correlationId}]`).
- **Shared contracts (`proto/`):** gRPC/protobuf definitions (`payment.proto`) compiled to Java
  stubs; consumed by payment (server) and order (client).

## Prerequisites

- Docker + Docker Compose (BuildKit enabled — default on modern Docker).
- No local JDK/Maven needed; builds run inside the `maven:3.9-eclipse-temurin-21` image.

## Run

```bash
docker compose up --build
```

Startup is health-gated: datastores → config-server → eureka → services → gateway → observability.

| Component | URL |
|-----------|-----|
| Storefront (SvelteKit) | http://localhost:3000 |
| API Gateway | http://localhost:8080 |
| Swagger UI (aggregated, all services) | http://localhost:8080/swagger-ui.html |
| Keycloak (admin / OIDC) | http://localhost:8081 (admin / see `.env`) |
| Eureka dashboard | http://localhost:8761 |
| RabbitMQ management | http://localhost:15672 (ecommerce / see `.env`) |
| Prometheus | http://localhost:9090 |
| Grafana | http://localhost:3001 (admin / see `.env`) |
| Jaeger (traces) | http://localhost:16686 |
| MinIO console (object storage) | http://localhost:9001 (minioadmin / see `.env`) |
| Elasticsearch | http://localhost:9200 |

### Smoke test (through the gateway)

```bash
# get a Keycloak access token (realm 'ecommerce', seeded user testuser/password)
TOKEN=$(curl -s -X POST \
  'http://localhost:8081/realms/ecommerce/protocol/openid-connect/token' \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -d 'grant_type=password&client_id=ecommerce-app&username=testuser&password=password' \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['access_token'])")

curl -s localhost:8080/api/users/me -H "Authorization: Bearer $TOKEN"

# create + search products
curl -s -X POST localhost:8080/api/products \
  -H 'Content-Type: application/json' \
  -d '{"name":"Wireless Headphones","description":"BT","category":"audio","brand":"Acme","price":99.9,"stockQuantity":10}'
curl -s "localhost:8080/api/products/search?q=headphones"

# place order (authed) -> order calls payment over gRPC, emits order.placed
curl -s -X POST localhost:8080/api/orders \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"totalAmount":99.9}'
```

## Verify the build (no boot)

```bash
docker run --rm -v "$PWD":/app -v ecommerce-m2:/root/.m2 \
  -w /app maven:3.9-eclipse-temurin-21 mvn -q -DskipTests package
```

## Security scan (supply chain)

OWASP Dependency-Check runs behind the `security` Maven profile (off by default — normal builds are
unaffected) and in CI:

```bash
mvn -Psecurity org.owasp:dependency-check-maven:aggregate -DnvdApiKey=$NVD_API_KEY
```

Report → `target/dependency-check-report.html`. Accepted findings / false positives go in
`owasp-suppressions.xml`. CI (`.github/workflows/ci.yml`) builds the reactor and runs this scan on
every push/PR (add the `NVD_API_KEY` repo secret for fast NVD sync).

## Load testing (k6)

With the stack up, drive load through the gateway (no local k6 — uses the `grafana/k6` image):

```bash
k6/run.sh smoke    # 1 VU sanity (health + product search)
k6/run.sh load     # ramping VUs: browse + search -> register -> cart + order
```

Scenarios live in `k6/`; thresholds fail the run on SLO breach. See [`k6/README.md`](k6/README.md).

## Versioning

`v0.MINOR.PATCH`. PATCH is the feature counter (1 → 20); at `.20` the MINOR rolls and PATCH resets:

```
v0.0.1 … v0.0.20  →  v0.1.0 … v0.1.20  →  v0.2.0 …      (v1.0.0 = final release)
```

Current: **v0.1.0** (`VERSION` file). Choose the payment provider with `PAYMENT_PROVIDER`
(`stripe` default | `razorpay` | `stub`); set the provider's keys (`STRIPE_SECRET_KEY`/`STRIPE_WEBHOOK_SECRET`
or `RAZORPAY_KEY_ID`/`RAZORPAY_KEY_SECRET`) to activate it, else it falls back to the dev stub.

## Notes

- Search lives **inside product-service** (Spring Data Elasticsearch), not a separate search-service.
- Config Server runs in **native mode** (classpath `config-repo/`) — no external Git repo needed.
- `ddl-auto: update` for dev; production should use Flyway migrations.
- Deferred to later versions: transactional outbox / CDC, MinIO media + CDN, OTel tracing,
  centralized logging (ELK), CI/CD, storefront frontend.
