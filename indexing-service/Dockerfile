# Multi-stage build para Indexing Service (Java/Spring Boot)

# Stage 1: Build
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /build

# Copiar pom.xml raiz e do serviço
COPY pom.xml .
COPY indexing-service/pom.xml indexing-service/
COPY indexing-service/*/pom.xml indexing-service/

# Verificar conectividade antes de baixar dependências
RUN nslookup repo.maven.apache.org || echo "DNS check failed, will retry"

# Download dependencies (cache layer) com retry e timeout aumentado
RUN mvn dependency:go-offline -f indexing-service/pom.xml -N || \
    (echo "Retry 1..." && sleep 10 && mvn dependency:go-offline -f indexing-service/pom.xml -N) || \
    (echo "Retry 2..." && sleep 20 && mvn dependency:go-offline -f indexing-service/pom.xml -N) || \
    (echo "Retry 3..." && sleep 30 && mvn dependency:go-offline -f indexing-service/pom.xml -N) || true

# Copiar código fonte
COPY indexing-service/ indexing-service/

# Build da aplicação com retry e timeout aumentado
RUN mvn clean package -f indexing-service/pom.xml -DskipTests || \
    (echo "Build retry 1..." && sleep 10 && mvn clean package -f indexing-service/pom.xml -DskipTests) || \
    (echo "Build retry 2..." && sleep 20 && mvn clean package -f indexing-service/pom.xml -DskipTests) || \
    (echo "Build retry 3..." && sleep 30 && mvn clean package -f indexing-service/pom.xml -DskipTests)

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
COPY --from=builder /build/indexing-service/bootstrap/target/bootstrap-*.jar app.jar

# Download OpenTelemetry Java Agent
RUN wget -O opentelemetry-javaagent.jar https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/latest/download/opentelemetry-javaagent.jar

RUN chown -R appuser:appuser /app

USER appuser

EXPOSE 8082

ENV SERVER_PORT=8082
ENV ENVIRONMENT=production

HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8082/api/v1/actuator/health || exit 1

ENTRYPOINT ["java", "-javaagent:/app/opentelemetry-javaagent.jar", "-jar", "app.jar"]

