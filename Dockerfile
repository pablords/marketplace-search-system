# Multi-stage build for Java application
FROM eclipse-temurin:17-jdk-alpine AS builder

# Set working directory
WORKDIR /app

# Copy Maven wrapper and pom files
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
COPY domain/pom.xml domain/
COPY application/pom.xml application/
COPY infrastructure/pom.xml infrastructure/
COPY interfaces/pom.xml interfaces/
COPY bootstrap/pom.xml bootstrap/

# Download dependencies
RUN ./mvnw dependency:go-offline -B

# Copy source code
COPY domain/src domain/src
COPY application/src application/src
COPY infrastructure/src infrastructure/src
COPY interfaces/src interfaces/src
COPY bootstrap/src bootstrap/src

# Build application
RUN ./mvnw clean package -DskipTests

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
COPY --from=builder /app/bootstrap/target/search-system-bootstrap-*.jar app.jar

# Create logs directory
RUN mkdir -p /var/log/marketplace-search && \
    chown -R marketplace:marketplace /var/log/marketplace-search /app

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