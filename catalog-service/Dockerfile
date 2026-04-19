# Multi-stage build para Catalog Service (Java/Spring Boot)

# Stage 1: Build
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /build

# Copiar pom.xml raiz e do serviço
COPY pom.xml .
COPY catalog-service/pom.xml catalog-service/
COPY catalog-service/*/pom.xml catalog-service/

# Download dependencies (cache layer)
RUN mvn dependency:go-offline -f catalog-service/pom.xml -N || true

# Copiar código fonte
COPY catalog-service/ catalog-service/

# Build da aplicação
RUN mvn clean package -f catalog-service/pom.xml -DskipTests

# Stage 2: Runtime
# Usar imagem sem -alpine para melhor suporte multi-arch (ARM64/AMD64)
FROM eclipse-temurin:17-jre

RUN apt-get update && apt-get install -y --no-install-recommends \
    wget \
    && rm -rf /var/lib/apt/lists/* \
    && if ! getent group appuser >/dev/null 2>&1; then \
        if getent group 1000 >/dev/null 2>&1; then \
            groupadd -r appuser || true; \
        else \
            groupadd -r -g 1000 appuser || true; \
        fi; \
    fi \
    && if ! getent passwd appuser >/dev/null 2>&1; then \
        if getent passwd 1000 >/dev/null 2>&1; then \
            useradd -r -g appuser appuser || true; \
        else \
            useradd -r -u 1000 -g appuser appuser || true; \
        fi; \
    fi

WORKDIR /app

# Copiar JAR executável do stage de build (excluir *-original.jar)
COPY --from=builder /build/catalog-service/bootstrap/target/bootstrap-*.jar app.jar

# Download OpenTelemetry Java Agent
RUN wget -O opentelemetry-javaagent.jar https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/latest/download/opentelemetry-javaagent.jar

RUN chown -R appuser:appuser /app

USER appuser

EXPOSE 8081

ENV SERVER_PORT=8081
ENV ENVIRONMENT=production

HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8081/api/v1/actuator/health || exit 1

ENTRYPOINT ["java", "-javaagent:/app/opentelemetry-javaagent.jar", "-jar", "app.jar"]

