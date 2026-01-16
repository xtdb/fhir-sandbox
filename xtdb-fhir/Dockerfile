# ============================================
# Stage 1: Build
# ============================================
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /app

# Copy dependency definitions first (layer caching)
COPY pom.xml .

# Download dependencies (cached unless pom.xml changes)
RUN mvn dependency:go-offline -B

# Copy source code
COPY src ./src

# Build the application
RUN mvn clean package -DskipTests -B

# ============================================
# Stage 2: Runtime
# ============================================
FROM eclipse-temurin:21-jre-alpine

# Security: Run as non-root user
RUN addgroup -g 1000 appgroup && \
    adduser -u 1000 -G appgroup -D appuser

WORKDIR /app

# Copy JAR from builder
COPY --from=builder /app/target/xtdb-fhir-1.0-SNAPSHOT.jar app.jar

# Copy sample FHIR data
COPY data/fhir /app/data/fhir

# Set ownership
RUN chown -R appuser:appgroup /app

USER appuser

# Default port for health checks (Spring Boot Actuator)
EXPOSE 8080

# JVM options for containers
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar $0 $@"]
