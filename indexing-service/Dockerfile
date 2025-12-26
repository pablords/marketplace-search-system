# Multi-stage build para Indexing Service (Java/Spring Boot)

# Stage 1: Build
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /build

# Copiar pom.xml raiz e do serviço
COPY pom.xml .
COPY indexing-service/pom.xml indexing-service/
COPY indexing-service/*/pom.xml indexing-service/

# Download dependencies (cache layer)
RUN mvn dependency:go-offline -f indexing-service/pom.xml -N || true

# Copiar código fonte
COPY indexing-service/ indexing-service/

# Build da aplicação
RUN mvn clean package -f indexing-service/pom.xml -DskipTests

# Stage 2: Runtime
FROM eclipse-temurin:17-jre-alpine

RUN apk --no-cache add wget && \
    addgroup -g 1000 appuser && \
    adduser -D -u 1000 -G appuser appuser

WORKDIR /app

# Copiar JAR do stage de build
COPY --from=builder /build/indexing-service/bootstrap/target/*.jar app.jar

RUN chown -R appuser:appuser /app

USER appuser

EXPOSE 8082

ENV SERVER_PORT=8082
ENV ENVIRONMENT=production

HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8082/api/v1/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]

