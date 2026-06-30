# CLAUDE.md — Scalable E-Commerce Platform

Authoritative project reference. Original startup spec `v1_stack_deep_dive.md` **deleted — do not reference or recreate it.** This file + `README.md` + plan docs = source of truth.

## Architecture (the original, confirmed plan)
Communication model:
- **Edge (client → gateway): REST.**
- **Service ↔ service (sync internal): gRPC.** (Feign used briefly in v0.0.1–0.0.2; removed in v0.0.3.)
- **Async events: RabbitMQ.**

Components:
- **api-gateway** (Spring Cloud Gateway, reactive): **Keycloak OIDC access-token validation**
  (Spring Security OAuth2 resource server, realm JWKS) → `X-User-Id`/`X-User-Role` injection, Redis
  rate-limiting, correlation-id origination, security headers, RFC-7807 errors. Built v0.0.9 —
  hand-rolled RS256 validation removed from the gateway (request-header mutation now via
  `MutableRequestHeaders` since Security makes request headers read-only).
- **Services (Spring Boot 3 MVC + virtual threads):** user (auth/JWT), product (catalog + Elasticsearch
  + Redis cache + S3 media via presigned URLs), cart (Redis), order (gRPC→payment + RabbitMQ + Kafka outbox), payment (gRPC server + REST debug +
  idempotent charge via `PaymentGateway` → Stripe primary / stub fallback; signed Stripe webhooks),
  notification (RabbitMQ
  consumer → Resend email, log-fallback w/o key), recommendation (Kafka consumer → Redis aggregates
  → REST trending + frequently-bought-together).
- **Infra:** config-server (8888, native), eureka-server (8761), postgres (db-per-service),
  redis, rabbitmq, kafka (KRaft single-node, event streaming), elasticsearch, minio (S3-compatible
  object storage, :9000 API / :9001 console), keycloak (8081, OIDC IdP; realm import at
  `infrastructure/keycloak/realm-ecommerce.json`). Observability: prometheus (9090) +
  grafana (3001) + jaeger (16686, OTLP distributed tracing).
- **Frontend:** `frontend/` **Svelte 5 + SvelteKit** storefront (adapter-node SSR on :3000). Server
  load functions are the BFF — they call the api-gateway over REST server-to-server (`GATEWAY_URL`);
  the browser never hits the gateway directly. PostHog web analytics via CDN snippet (v0.0.17). Not a
  Maven module (Node build).
- **Shared modules:** `common/` (servlet cross-cutting, auto-configured) and `proto/` (gRPC contracts).

## Cross-cutting (`common/`, auto-configured via AutoConfiguration.imports)
- RFC-7807 `GlobalExceptionHandler` — uniform `application/problem+json`. Throw common exceptions
  (`ResourceNotFoundException` 404, `ConflictException` 409, `UnauthorizedException` 401,
  `BadRequestException` 400, `ServiceUnavailableException` 503); do NOT add per-controller `@ExceptionHandler`.
- Bean Validation → 400 with `errors[]`.
- Correlation id: servlet `CorrelationIdFilter` + gRPC client/server interceptors propagate
  `X-Correlation-Id` (MDC key `correlationId`, logged as `[%X{correlationId}]`). Gateway reactive,
  re-implements correlation/security/RFC-7807 itself (no dependency on `common`).

## gRPC
- Contracts in `proto/src/main/proto/*.proto` → generated stubs (pkg `com.ecommerce.grpc.*`).
- Library: **net.devh grpc-spring-boot-starter 3.1.0.RELEASE** (`@GrpcService`, `@GrpcClient`,
  global interceptors). Versions pinned: protobuf 3.25.5, grpc 1.65.1.
- payment = gRPC server on :9090 (registers `gRPC.port` in Eureka metadata) + keeps REST for debug.
- order = gRPC client, `grpc.client.payment.address=discovery:///PAYMENT-SERVICE`, plaintext,
  call wrapped by `CircuitBreakerFactory.create("payment")` (resilience4j) → 503 on failure.

## API docs (springdoc/Swagger) — built v0.0.4
- Each service: `springdoc-openapi-starter-webmvc-ui` 2.6.0 → `/v3/api-docs` + `/swagger-ui.html`.
- Gateway aggregates via `springdoc-openapi-starter-webflux-ui` 2.6.0: one UI at
  `:8080/swagger-ui.html` with a per-service dropdown. Public gateway routes
  `/aggregate/<svc>/v3/api-docs` (`RewritePath` → `/v3/api-docs`, no JWT) feed
  `springdoc.swagger-ui.urls`. Services covered: user, product, cart, order, payment.
- Pin 2.6.x (not in Spring Boot BOM); 2.6.x = Spring Boot 3.3.x.

## Build & run
- **No local Maven; local JDK is 17. Target is Java 21.** Build only inside container:
  ```bash
  docker run --rm -v "$PWD":/app -v ecommerce-m2:/root/.m2 \
    -w /app maven:3.9-eclipse-temurin-21 mvn -q -DskipTests package
  ```
- Run: `docker compose up --build` (health-gated). Stop: `docker compose down`.
- Supply-chain scan (OFF by default, no impact on normal build):
  `mvn -Psecurity org.owasp:dependency-check-maven:aggregate` (NVD key via `-DnvdApiKey=…`).
  Same scan runs in GitHub Actions CI (`.github/workflows/ci.yml`); accepted findings →
  `owasp-suppressions.xml`.
- Load test (stack up): `k6/run.sh smoke` (or `load`) — drives flows through the gateway via the
  `grafana/k6` Docker image (no local k6). Scenarios in `k6/`.
- Root `pom.xml` = aggregator over 12 modules (`common` + `proto` + 3 infra + 7 services); each
  module's `<parent>` is `spring-boot-starter-parent`.
- Service Docker builds use **context = repo root** + `mvn -pl services/<svc> -am package` (reactor,
  so `common`/`proto` build first). Infra modules use module-dir context (`Dockerfile.template`).
  BuildKit cache mount shares the Maven repo; `.dockerignore` keeps context lean.

## Stack pins
Spring Boot 3.3.5 · Spring Cloud 2023.0.3 · net.devh grpc 3.1.0.RELEASE (grpc 1.65 / protobuf 3.25) ·
Svelte 5 + SvelteKit 2 + adapter-node (storefront, Node 20) ·
jjwt 0.11.5 (user-service legacy only) · Keycloak 26.0 (OIDC; gateway = OAuth2 resource server) · stripe-java 33.1.0 + razorpay-java 1.4.9 (payment-service) · springdoc-openapi 2.6.0 · OWASP dependency-check 10.0.4 (`security` profile) · micrometer-tracing 1.3.x + OpenTelemetry 1.37 (Boot-managed) · Jaeger all-in-one 1.60 · Postgres 16 · Redis 7 · RabbitMQ 3.13 · Kafka 3.8 KRaft + spring-kafka (Boot-managed) · Elasticsearch 8.13 · MinIO (S3-compatible, local) + AWS SDK v2 2.28.16 · Traefik not used (gateway = SCG).

## Versioning
`v0.MINOR.PATCH`; PATCH = feature counter 1→20, rolls MINOR at 20; `v1.0.0` = final.
Current in `VERSION`. Bump VERSION + all module pom `<version>` + `common`/`proto` dep refs together.

## Conventions
- ddl-auto=update for dev (Flyway in production).
- Don't reintroduce Feign. Don't reference the deleted `v1_stack_deep_dive.md`.
- Deferred items now tracked in **Target stack & roadmap** (below).

## Target stack & roadmap (planned — not yet built)
Everything above describes what's **built (v0.1.0)**. Below is the agreed forward stack; introduce
per the version roadmap, each as its own v0.0.x/v0.1.x feature.

**Frontend** — Svelte 5 + SvelteKit storefront ✅ built v0.0.16: `frontend/` (adapter-node SSR, :3000);
server load = BFF over REST to the gateway; home (trending + catalog), `/search`, and product detail
✅ built v0.0.20 (`/product/[id]` — renders the product + its "more like this" similar products from
`/api/products/{id}/similar`, missing product → 404; catalog/search/trending cards link to it).
Auth-gated cart + checkout ✅ built v0.1.0: `/login` does a server-to-server Keycloak password-grant
(`src/lib/server/auth.js`) and stores the access token in an **httpOnly cookie**; `hooks.server.js`
loads it into `locals.user`; authed BFF calls attach it as `Bearer` (`gatewayFetch`) so the gateway
validates + injects `X-User-Id`. `/cart` (add/remove/checkout actions) and product "Add to cart"
flow → places an order via `/api/orders` (gRPC→payment→outbox→Kafka) then clears the cart. adapter-node
needs `ORIGIN` set (SvelteKit form-action CSRF). TODO: Cloudflare CDN, migrate user-service off
hand-rolled JWT, token refresh.
- **Svelte 5 + SvelteKit** storefront (SSR/BFF). Talks to api-gateway over REST. Served via Cloudflare CDN.

**Auth** (supersedes hand-rolled RS256 JWT) — Keycloak ✅ built v0.0.9: realm `ecommerce`, client
`ecommerce-app` (direct-access grant), gateway validates access tokens via JWKS
(`spring.security.oauth2.resourceserver.jwt.jwk-set-uri`). Token (dev): password grant at
`http://localhost:8081/realms/ecommerce/protocol/openid-connect/token`. TODO: migrate user-service
registration/login off hand-rolled JWT to Keycloak and key profile data by the Keycloak subject.
- **Keycloak** (OIDC/OAuth2) as the identity provider. Gateway validates access tokens via Keycloak
  JWKS; services are resource servers; user-service keeps profile data keyed by the Keycloak subject.

**Payments** (payment-service behind one `PaymentGateway` abstraction; provider adapters) — Stripe
✅ built v0.0.10: `StripePaymentGateway` (confirmed PaymentIntent, idempotency key = orderId) +
`StubPaymentGateway` fallback when `STRIPE_SECRET_KEY` unset; `provider`/`providerRef` persisted, no
card data. Selection in `PaymentGatewayConfig`. Signed webhooks ✅ built v0.0.14: public
`POST /api/payments/webhooks/stripe` (HMAC-verified via `STRIPE_WEBHOOK_SECRET`, idempotent dedupe in
`processed_webhook_events`) settles payment status async (`payment_intent.succeeded|payment_failed`).
Razorpay ✅ built v0.0.15: secondary adapter (`RazorpayPaymentGateway` — creates a Razorpay Order)
selected via `PAYMENT_PROVIDER=stripe|razorpay|stub` in `PaymentGatewayConfig` (stub fallback when
the chosen provider's keys are absent).
- **Stripe = primary/first-priority**, **Razorpay = secondary** (regional). Signed webhooks for async
  settlement, idempotency keys, never store card data (PCI — tokenize at provider).

**Messaging & event streaming** (distinct roles, both kept) — Kafka ✅ built v0.0.11.
- **RabbitMQ** — commands / work queues / DLQ (order→notification email).
- **Kafka** — event streaming & fan-out. order-service stages `order.placed` in a DB **outbox** table
  in the same tx as the order; `OutboxRelay` (@Scheduled polling publisher) publishes to topic
  `ecommerce.order-events`; notification-service consumes (audit/fan-out). At-least-once — consumers
  must be idempotent. TODO: Debezium CDC to replace the poller; more consumers (analytics/search/reco).

**Data & search**
- Postgres (db-per-service), Redis (cache/cart), Elasticsearch (search) — current.
- **pgvector / vector store** for recommendation embeddings (planned).

**Media / object storage / CDN** — object storage ✅ built v0.0.13: product-service uses the S3 API
(AWS SDK v2) against **MinIO** locally / **Cloudflare R2** in prod (swap via `storage.*` env). Presigned
URLs: `POST /api/products/{id}/media/presign-upload` (authed) → direct client PUT; `GET
/api/products/{id}/media` (public) → presigned GET. Bytes never transit the service. TODO: Cloudflare CDN.
- **Cloudflare R2** (S3-compatible) for product media & uploads; **MinIO** for local dev; served via
  **Cloudflare CDN** (also fronts the SvelteKit app + static assets).

**Notifications** — Resend ✅ built v0.0.8: notification-service `ResendEmailService` (Spring
`RestClient`, no extra dep) sends on `order.placed`; logs a fallback when `RESEND_API_KEY` is unset.
TODO: resolve recipient email from user-service (event carries only the user id). SMS later.
- **Resend** for transactional email (supersedes the SendGrid stub); notification-service consumes
  events → Resend. SMS later (e.g. Twilio).

**Analytics** — PostHog ✅ built v0.0.17: storefront loads the PostHog web snippet (CDN, client-side,
no npm dep) and captures `$pageview`s; notification-service captures `order_placed` from the Kafka
stream via REST (`PostHogAnalytics`). Both no-op without a key (`PUBLIC_POSTHOG_KEY` / `POSTHOG_API_KEY`).
- **PostHog** — product analytics, funnels, feature flags (frontend + backend events, also via Kafka).

**Observability** (extends current Prometheus + Grafana) — OpenTelemetry tracing ✅ built v0.0.7:
Micrometer Tracing → OTLP → **Jaeger** (`:16686`). Spans cover REST (gateway→service) and gRPC
(order→payment via `ObservationGrpcClient/ServerInterceptor`); config in shared `config-repo` +
gateway yml (`management.tracing.sampling`, `management.otlp.tracing.endpoint`). Kafka/Tempo/Loki
still planned.
- **OpenTelemetry** distributed tracing across REST / gRPC / Kafka (OTLP → collector → Grafana Tempo
  or Jaeger). Metrics: Prometheus + Grafana. Logs → Loki (optional). Replaces correlation-id-only
  tracing with full spans (correlation id becomes the trace id).

**Customer support & CMS** — Chatwoot ✅ built v0.0.18: storefront loads the official Chatwoot live-chat
SDK client-side (`frontend/src/lib/chatwoot.js` → `initChatwoot`, called from `+layout.svelte` onMount;
CDN, no npm dep, no SSR impact). No-op unless **both** `PUBLIC_CHATWOOT_BASE_URL` and
`PUBLIC_CHATWOOT_WEBSITE_TOKEN` are set (points at any hosted/self-hosted Chatwoot instance). TODO:
inbound webhooks, identify the logged-in user. SMS/CMS still planned.
- **Chatwoot** — live chat / helpdesk (widget embedded in SvelteKit, webhooks).
- **Payload CMS** — headless CMS for marketing/content & product enrichment.

**API docs** — ✅ built v0.0.4 (see "API docs (springdoc/Swagger)" above)
- **springdoc-openapi** (Swagger UI) per service; aggregated at the gateway.

**Recommendations** (phased) — Phase 1 ✅ built v0.0.12: `recommendation-service` consumes
`ecommerce.order-events` (own consumer group) → Redis ZSETs (`reco:popularity`, `reco:cobought:{id}`)
→ REST `GET /api/recommendations/trending` + `/frequently-bought-together/{productId}`. Orders now
carry optional `items`; the outbox event includes `productIds`. Content-based "more like this"
✅ built v0.0.19: product-service serves `GET /api/products/{id}/similar` via an Elasticsearch
More-Like-This query over the `products` index (`ProductSearchService.moreLikeThis`, fields
name/description/category/brand, `min_term_freq`/`min_doc_freq`=1 for small catalogs, seed excluded);
public route (lives in product-service since it owns the ES index — no new infra). TODO: Phase 2.
- Phase 1: a `recommendation-service` consuming Kafka order/behaviour events → popularity/trending,
  "frequently bought together" (co-purchase co-occurrence), and content-based "more like this"
  (Elasticsearch MLT). Aggregates cached in Redis/Postgres, served via REST/gRPC.
- Phase 2: personalized — collaborative filtering (ALS) or vector similarity (pgvector) over
  behavioural embeddings; optional realtime feature store.

**Security & supply chain** — OWASP Dependency-Check ✅ built v0.0.5 (Maven `security` profile,
report-only/non-blocking, runs in GitHub Actions CI); rest planned.
- **OWASP Dependency-Check** in CI (+ Trivy/Snyk for images, optional). Secrets via env/Docker
  secrets → Vault. Keep existing api-security practices (headers, RFC-7807, UUID ids, rate limiting).

**Deploy & CI/CD** — GitHub Actions CI ✅ started v0.0.5 (`.github/workflows/ci.yml`: reactor build
+ OWASP dep-check, report uploaded); image build/push, deploy, k8s still planned.
- **Docker** (current) → **Kubernetes** (Helm/Kustomize, HPA autoscaling; in-cluster discovery can
  replace Eureka on k8s). **GitHub Actions**: build, test, OWASP dep-check, image build/push, deploy.
- **k6** for load testing — ✅ built v0.0.6: `k6/smoke.js` + `k6/load.js` run via the `grafana/k6`
  image (`k6/run.sh`), drive flows through the gateway; manual `load-test.yml` workflow. (perf gates in CI.)