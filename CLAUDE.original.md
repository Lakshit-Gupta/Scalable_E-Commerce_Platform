# CLAUDE.md — Scalable E-Commerce Platform

Authoritative project reference. The original startup spec `v1_stack_deep_dive.md` has been
**deleted — do not reference or recreate it.** This file + `README.md` + plan docs are the source
of truth.

## Architecture (the original, confirmed plan)
Communication model:
- **Edge (client → gateway): REST.**
- **Service ↔ service (sync internal): gRPC.** (Feign was used briefly in v0.0.1–0.0.2; removed in v0.0.3.)
- **Async events: RabbitMQ.**

Components:
- **api-gateway** (Spring Cloud Gateway, reactive): JWT(RS256) validation, `X-User-Id`/`X-User-Role`
  injection, Redis rate-limiting, correlation-id origination, security headers, RFC-7807 errors.
  (Planned: **Keycloak** OIDC supersedes the hand-rolled JWT — see Target stack & roadmap.)
- **Services (Spring Boot 3 MVC + virtual threads):** user (auth/JWT), product (catalog + Elasticsearch
  + Redis cache), cart (Redis), order (gRPC→payment + RabbitMQ), payment (gRPC server + REST debug +
  idempotent stub), notification (RabbitMQ consumer).
- **Infra:** config-server (8888, native), eureka-server (8761), postgres (db-per-service),
  redis, rabbitmq, elasticsearch. Observability: prometheus (9090) + grafana (3001).
- **Shared modules:** `common/` (servlet cross-cutting, auto-configured) and `proto/` (gRPC contracts).

## Cross-cutting (`common/`, auto-configured via AutoConfiguration.imports)
- RFC-7807 `GlobalExceptionHandler` — uniform `application/problem+json`. Throw the common exceptions
  (`ResourceNotFoundException` 404, `ConflictException` 409, `UnauthorizedException` 401,
  `BadRequestException` 400, `ServiceUnavailableException` 503); do NOT add per-controller `@ExceptionHandler`.
- Bean Validation → 400 with `errors[]`.
- Correlation id: servlet `CorrelationIdFilter` + gRPC client/server interceptors propagate
  `X-Correlation-Id` (MDC key `correlationId`, logged as `[%X{correlationId}]`). Gateway is reactive
  and re-implements correlation/security/RFC-7807 itself (does not depend on `common`).

## gRPC
- Contracts in `proto/src/main/proto/*.proto` → generated stubs (pkg `com.ecommerce.grpc.*`).
- Library: **net.devh grpc-spring-boot-starter 3.1.0.RELEASE** (`@GrpcService`, `@GrpcClient`,
  global interceptors). Versions pinned: protobuf 3.25.5, grpc 1.65.1.
- payment = gRPC server on :9090 (registers `gRPC.port` in Eureka metadata) + keeps REST for debug.
- order = gRPC client, `grpc.client.payment.address=discovery:///PAYMENT-SERVICE`, plaintext,
  call wrapped by `CircuitBreakerFactory.create("payment")` (resilience4j) → 503 on failure.

## Build & run
- **No local Maven; local JDK is 17. Target is Java 21.** Build only inside the container:
  ```bash
  docker run --rm -v "$PWD":/app -v ecommerce-m2:/root/.m2 \
    -w /app maven:3.9-eclipse-temurin-21 mvn -q -DskipTests package
  ```
- Run: `docker compose up --build` (health-gated). Stop: `docker compose down`.
- Root `pom.xml` = aggregator over 11 modules (`common` + `proto` + 3 infra + 6 services); each
  module's `<parent>` is `spring-boot-starter-parent`.
- Service Docker builds use **context = repo root** + `mvn -pl services/<svc> -am package` (reactor,
  so `common`/`proto` build first). Infra modules use module-dir context (`Dockerfile.template`).
  BuildKit cache mount shares the Maven repo; `.dockerignore` keeps context lean.

## Stack pins
Spring Boot 3.3.5 · Spring Cloud 2023.0.3 · net.devh grpc 3.1.0.RELEASE (grpc 1.65 / protobuf 3.25) ·
jjwt 0.11.5 · Postgres 16 · Redis 7 · RabbitMQ 3.13 · Elasticsearch 8.13 · Traefik not used (gateway = SCG).

## Versioning
`v0.MINOR.PATCH`; PATCH = feature counter 1→20, rolls MINOR at 20; `v1.0.0` = final.
Current in `VERSION`. Bump VERSION + all module pom `<version>` + `common`/`proto` dep refs together.

## Conventions
- ddl-auto=update for dev (Flyway in production).
- Don't reintroduce Feign. Don't reference the deleted `v1_stack_deep_dive.md`.
- Deferred items now tracked in **Target stack & roadmap** (below).

## Target stack & roadmap (planned — not yet built)
Everything above describes what's **built (v0.0.3)**. Below is the agreed forward stack; introduce
per the version roadmap, each as its own v0.0.x/v0.1.x feature.

**Frontend**
- **Svelte 5 + SvelteKit** storefront (SSR/BFF). Talks to api-gateway over REST. Served via Cloudflare CDN.

**Auth** (supersedes hand-rolled RS256 JWT)
- **Keycloak** (OIDC/OAuth2) as the identity provider. Gateway validates access tokens via Keycloak
  JWKS; services are resource servers; user-service keeps profile data keyed by the Keycloak subject.

**Payments** (payment-service behind one `PaymentGateway` abstraction; provider adapters)
- **Stripe = primary/first-priority**, **Razorpay = secondary** (regional). Signed webhooks for async
  settlement, idempotency keys, never store card data (PCI — tokenize at provider).

**Messaging & event streaming** (distinct roles, both kept)
- **RabbitMQ** — commands / work queues / DLQ (current).
- **Kafka** — event streaming & fan-out (domain events → analytics, search indexing, recommendations,
  audit log). Reliable publish via **transactional outbox → Kafka** (Debezium CDC).

**Data & search**
- Postgres (db-per-service), Redis (cache/cart), Elasticsearch (search) — current.
- **pgvector / vector store** for recommendation embeddings (planned).

**Media / object storage / CDN**
- **Cloudflare R2** (S3-compatible) for product media & uploads; **MinIO** for local dev; served via
  **Cloudflare CDN** (also fronts the SvelteKit app + static assets).

**Notifications**
- **Resend** for transactional email (supersedes the SendGrid stub); notification-service consumes
  events → Resend. SMS later (e.g. Twilio).

**Analytics**
- **PostHog** — product analytics, funnels, feature flags (frontend + backend events, also via Kafka).

**Observability** (extends current Prometheus + Grafana)
- **OpenTelemetry** distributed tracing across REST / gRPC / Kafka (OTLP → collector → Grafana Tempo
  or Jaeger). Metrics: Prometheus + Grafana. Logs → Loki (optional). Replaces correlation-id-only
  tracing with full spans (correlation id becomes the trace id).

**Customer support & CMS**
- **Chatwoot** — live chat / helpdesk (widget embedded in SvelteKit, webhooks).
- **Payload CMS** — headless CMS for marketing/content & product enrichment.

**API docs**
- **springdoc-openapi** (Swagger UI) per service; aggregated at the gateway.

**Recommendations** (phased — "good enough" first)
- Phase 1: a `recommendation-service` consuming Kafka order/behaviour events → popularity/trending,
  "frequently bought together" (co-purchase co-occurrence), and content-based "more like this"
  (Elasticsearch MLT). Aggregates cached in Redis/Postgres, served via REST/gRPC.
- Phase 2: personalized — collaborative filtering (ALS) or vector similarity (pgvector) over
  behavioural embeddings; optional realtime feature store.

**Security & supply chain**
- **OWASP Dependency-Check** in CI (+ Trivy/Snyk for images, optional). Secrets via env/Docker
  secrets → Vault. Keep existing api-security practices (headers, RFC-7807, UUID ids, rate limiting).

**Deploy & CI/CD**
- **Docker** (current) → **Kubernetes** (Helm/Kustomize, HPA autoscaling; in-cluster discovery can
  replace Eureka on k8s). **GitHub Actions**: build, test, OWASP dep-check, image build/push, deploy.
- **k6** for load testing (per-flow scenarios; perf gates in CI).
