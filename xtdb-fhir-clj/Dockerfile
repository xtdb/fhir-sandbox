# Build stage
FROM clojure:temurin-21-tools-deps-alpine AS builder

WORKDIR /build

COPY deps.edn build.clj ./
COPY src ./src

RUN clojure -T:build uber

# Runtime stage
FROM eclipse-temurin:21-jre-alpine

RUN addgroup -g 1000 appgroup && \
    adduser -u 1000 -G appgroup -D appuser

WORKDIR /app

COPY --from=builder /build/target/xtdb-fhir-clj.jar xtdb-fhir-clj.jar

RUN chown -R appuser:appgroup /app

USER appuser

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "xtdb-fhir-clj.jar"]