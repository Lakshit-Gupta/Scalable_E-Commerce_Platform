# syntax=docker/dockerfile:1
# Payload CMS (v0.1.1). Build with Bun (fast installs); run on Node (Payload/Next's supported
# runtime — Bun's runtime trips over @lexical/react). On startup the container applies committed
# Payload migrations (production-correct schema management — the platform's Flyway analog) then
# starts the Next.js server. Mirrors the storefront's Bun-build / Node-run split.
FROM oven/bun:1-alpine AS build
WORKDIR /app
COPY package.json bun.lock* ./
RUN bun install
COPY . .
# Build-time-only envs (inline, not baked): config loads during `next build`; no DB pool / S3 call is
# made. STORAGE_* dummies keep the S3 storage plugin active so the admin importMap matches runtime.
RUN PAYLOAD_SECRET=build-time-only \
    DATABASE_URL=postgres://build:build@localhost:5432/build \
    STORAGE_ENDPOINT=http://localhost:9000 STORAGE_BUCKET=build \
    STORAGE_ACCESS_KEY=build STORAGE_SECRET_KEY=build \
    bun run build

# ---- Runtime ----
FROM node:20-alpine AS runtime
WORKDIR /app
ENV NODE_ENV=production
ENV PORT=3000
ENV HOSTNAME=0.0.0.0

# Full app (deps + build + src/migrations) so the Payload CLI can run migrations via node + tsx.
COPY --from=build /app ./

EXPOSE 3000
# Ensure the media bucket (best-effort), apply migrations (idempotent), then start Next.
# Real PAYLOAD_SECRET/DATABASE_URL/STORAGE_* come from the runtime environment.
CMD ["sh", "-c", "node scripts/ensure-bucket.mjs; node node_modules/.bin/payload migrate && node node_modules/.bin/next start -p 3000 -H 0.0.0.0"]
