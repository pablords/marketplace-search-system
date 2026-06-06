FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY bootstrap/target/bootstrap-1.0.0-SNAPSHOT.jar app.jar
RUN wget -O opentelemetry-javaagent.jar https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/latest/download/opentelemetry-javaagent.jar
EXPOSE 8082
ENV SERVER_PORT=8082
ENV ENVIRONMENT=production
HEALTHCHECK --interval=30s --timeout=10s --start-period=10s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8082/actuator/health || exit 1
ENTRYPOINT ["java", "-Xshare:off", "-javaagent:/app/opentelemetry-javaagent.jar", "-jar", "app.jar"]
