# syntax=docker/dockerfile:1
# Reactor build for a service that depends on the shared 'common' library.
# Build context = repo ROOT (see docker-compose build.context), so the Maven reactor can
# build :common before this service via `-pl <module> -am` (also-make dependencies).
# .dockerignore keeps the context lean; the BuildKit cache mount shares the Maven repo.
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app

# The aggregator pom references every module, so the whole tree must be present to build.
COPY . .
RUN --mount=type=cache,target=/root/.m2 \
    mvn -q -e -pl services/order-service -am -DskipTests package

# ---- Runtime image ----
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Non-root user (api-security: run as least privilege)
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

COPY --from=builder /app/services/order-service/target/*.jar app.jar

# Container-aware JVM. Virtual threads are enabled via application.yml, not flags.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
