# Multi-stage build for Java application
FROM maven:3.9-eclipse-temurin-17-alpine AS builder

# Set working directory
WORKDIR /app

# Copy pom files for dependency resolution
COPY pom.xml .
COPY api-gateway/pom.xml api-gateway/
COPY api-gateway/bootstrap/pom.xml api-gateway/bootstrap/
COPY api-gateway/interfaces/pom.xml api-gateway/interfaces/

# Download dependencies (cached layer)
RUN mvn dependency:go-offline -B

# Copy source code
COPY api-gateway/bootstrap/src api-gateway/bootstrap/src
COPY api-gateway/interfaces/src api-gateway/interfaces/src

# Build application
RUN mvn clean package -DskipTests -pl api-gateway/bootstrap -am

# Runtime stage
FROM eclipse-temurin:17-jre-alpine

# Set working directory
WORKDIR /app

# Create non-root user
RUN addgroup -g 1001 -S marketplace && \
    adduser -S marketplace -u 1001

# Install curl for health checks
RUN apk add --no-cache curl

# Copy built JAR from builder stage
COPY --from=builder /app/api-gateway/bootstrap/target/bootstrap-*.jar app.jar

# Create logs directory
RUN mkdir -p /var/log/api-gateway && \
    chown -R marketplace:marketplace /var/log/api-gateway /app

# Switch to non-root user
USER marketplace

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD curl -f http://localhost:8080/api/v1/actuator/health || exit 1

# JVM optimization flags
ENV JAVA_OPTS="-XX:+UseContainerSupport \
               -XX:MaxRAMPercentage=75.0 \
               -XX:+UseG1GC \
               -XX:+UseStringDeduplication \
               -XX:+OptimizeStringConcat \
               -Djava.security.egd=file:/dev/./urandom \
               -Djava.awt.headless=true"

# Start application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
