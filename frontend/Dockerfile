# syntax=docker/dockerfile:1
# SvelteKit storefront (v0.0.16). Build with Bun (fast installs), run the adapter-node server.
FROM oven/bun:1-alpine AS build
WORKDIR /app
COPY package.json bun.lockb* package-lock.json* ./
RUN bun install
COPY . .
RUN bun run build

# ---- Runtime ----
FROM node:20-alpine
WORKDIR /app
ENV NODE_ENV=production
ENV PORT=3000

RUN addgroup -S app && adduser -S app -G app
COPY --from=build /app/build ./build
COPY --from=build /app/node_modules ./node_modules
COPY --from=build /app/package.json ./package.json
USER app

EXPOSE 3000
CMD ["node", "build/index.js"]
