# Multi-stage Dockerfile for building and running the reloader backend
# Build stage: use Maven to build the jar
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace

# Copy project sources (copying the backend directory only speeds up context)
COPY backend /workspace/backend
WORKDIR /workspace/backend

# Build the project (skip tests by default for faster image builds)
RUN mvn -B -DskipTests package

# Runtime stage: use a small JRE image
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Copy the built jar from the build stage. The artifact name may include version
# so use a wildcard; this requires a single jar in target/ after the build.
COPY --from=build /workspace/backend/target/*.jar /app/app.jar

# Expose the HTTP port used by the Spring Boot app
EXPOSE 8080

# Default environment variables useful for local dev
ENV RELOADER_USE_H2_EXTERNAL=false
ENV EXTERNAL_DB_ALLOW_WRITES=false

# Run as non-root where possible; fall back to root when not available
USER 0

ENTRYPOINT ["/usr/bin/java", "-jar", "/app/app.jar"]
