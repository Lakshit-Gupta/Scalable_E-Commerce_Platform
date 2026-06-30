# syntax=docker/dockerfile:1
# Canonical multi-stage build for every Spring Boot module.
# Builder = Maven + JDK 21 (no Maven wrapper needed). Runtime = slim JRE.
# Build context is the module dir, so each service builds independently.
# The BuildKit cache mount shares the Maven repo across all module image builds.
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app

COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -q -e -DskipTests dependency:go-offline || true

COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -q -e -DskipTests package

# ---- Runtime image ----
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Non-root user (api-security: run as least privilege)
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

COPY --from=builder /app/target/*.jar app.jar

# Container-aware JVM. Virtual threads are enabled via application.yml, not flags.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
