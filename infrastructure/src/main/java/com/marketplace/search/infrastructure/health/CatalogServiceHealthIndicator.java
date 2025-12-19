package com.marketplace.search.infrastructure.health;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

/**
 * Health indicator para verificar a saúde do catalog-service.
 * Faz uma chamada HTTP ao endpoint de health do catalog-service
 * e reporta o status no Spring Actuator.
 */
@Component
public class CatalogServiceHealthIndicator implements HealthIndicator {

  private static final Logger logger = LoggerFactory.getLogger(CatalogServiceHealthIndicator.class);

  private final WebClient webClient;
  private final String healthCheckUrl;
  private final int timeout;

  public CatalogServiceHealthIndicator(
      @org.springframework.beans.factory.annotation.Qualifier("catalogServiceWebClient") WebClient webClient,
      @Value("${services.catalog.base-url:http://localhost:8081/api/v1}") String catalogServiceBaseUrl,
      @Value("${services.catalog.timeout:5000}") int timeout) {
    this.webClient = webClient;
    // O catalog-service expõe health em /actuator/health ou /health
    // Vamos tentar /actuator/health primeiro (padrão Spring Boot Actuator)
    this.healthCheckUrl = catalogServiceBaseUrl.replace("/api/v1", "") + "/actuator/health";
    this.timeout = timeout;
  }

  @Override
  public Health health() {
    try {
      return checkCatalogServiceHealth()
          .block(Duration.ofMillis(timeout));
    } catch (Exception e) {
      logger.error("Erro ao verificar saúde do catalog-service", e);
      return Health.down()
          .withDetail("error", e.getMessage())
          .withDetail("service", "catalog-service")
          .withDetail("url", healthCheckUrl)
          .build();
    }
  }

  /**
   * Verifica a saúde do catalog-service fazendo uma chamada HTTP ao endpoint de health.
   * 
   * @return Health com status UP se o serviço estiver disponível, DOWN caso contrário
   */
  private Mono<Health> checkCatalogServiceHealth() {
    logger.debug("Verificando saúde do catalog-service em: {}", healthCheckUrl);

    return webClient.get()
        .uri(healthCheckUrl)
        .retrieve()
        .bodyToMono(String.class)
        .map(response -> {
          logger.debug("Catalog-service está saudável. Resposta: {}", response);
          return Health.up()
              .withDetail("service", "catalog-service")
              .withDetail("url", healthCheckUrl)
              .withDetail("status", "UP")
              .build();
        })
        .retryWhen(Retry.backoff(1, Duration.ofMillis(100))
            .filter(throwable -> throwable instanceof WebClientResponseException
                && ((WebClientResponseException) throwable).getStatusCode().is5xxServerError())
            .doBeforeRetry(retrySignal -> logger.warn(
                "Tentando novamente verificar saúde do catalog-service após erro 5xx")))
        .onErrorResume(WebClientResponseException.class, ex -> {
          logger.warn("Catalog-service retornou erro HTTP: {} - {}", 
              ex.getStatusCode(), ex.getResponseBodyAsString());
          return Mono.just(Health.down()
              .withDetail("service", "catalog-service")
              .withDetail("url", healthCheckUrl)
              .withDetail("status", "DOWN")
              .withDetail("httpStatus", ex.getStatusCode().value())
              .withDetail("error", ex.getMessage())
              .build());
        })
        .onErrorResume(throwable -> {
          logger.error("Erro ao verificar saúde do catalog-service", throwable);
          return Mono.just(Health.down()
              .withDetail("service", "catalog-service")
              .withDetail("url", healthCheckUrl)
              .withDetail("status", "DOWN")
              .withDetail("error", throwable.getMessage())
              .withDetail("errorType", throwable.getClass().getSimpleName())
              .build());
        });
  }
}

