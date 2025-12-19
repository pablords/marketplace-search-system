package com.marketplace.search.infrastructure.config;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;

/**
 * Configuração do WebClient para comunicação HTTP com outros serviços.
 * Inclui timeout, retry e circuit breaker para resiliência.
 */
@Configuration
public class WebClientConfig {

  private static final Logger logger = LoggerFactory.getLogger(WebClientConfig.class);

  @Value("${services.catalog.base-url:http://localhost:8081/api/v1}")
  private String catalogServiceBaseUrl;

  @Value("${services.catalog.timeout:5000}")
  private int catalogServiceTimeout;

  @Value("${services.catalog.retry.max-attempts:3}")
  private int maxRetryAttempts;

  @Value("${services.catalog.retry.min-backoff:500}")
  private long retryMinBackoff;

  @Value("${services.catalog.circuit-breaker.failure-rate-threshold:50}")
  private float circuitBreakerFailureRateThreshold;

  @Value("${services.catalog.circuit-breaker.wait-duration-in-open-state:10000}")
  private int circuitBreakerWaitDurationInOpenState;

  @Value("${services.catalog.circuit-breaker.sliding-window-size:10}")
  private int circuitBreakerSlidingWindowSize;

  @Value("${services.search.base-url:http://localhost:8083/api/v1}")
  private String searchServiceBaseUrl;

  @Value("${services.search.timeout:3000}")
  private int searchServiceTimeout;

  @Bean
  public CircuitBreakerRegistry circuitBreakerRegistry() {
    CircuitBreakerConfig config = CircuitBreakerConfig.custom()
        .failureRateThreshold(circuitBreakerFailureRateThreshold)
        .waitDurationInOpenState(Duration.ofMillis(circuitBreakerWaitDurationInOpenState))
        .slidingWindowSize(circuitBreakerSlidingWindowSize)
        .minimumNumberOfCalls(5)
        .permittedNumberOfCallsInHalfOpenState(3)
        .automaticTransitionFromOpenToHalfOpenEnabled(true)
        .recordExceptions(WebClientResponseException.class, Exception.class)
        .build();

    return CircuitBreakerRegistry.of(config);
  }

  @Bean
  public CircuitBreaker catalogServiceCircuitBreaker(CircuitBreakerRegistry registry) {
    return registry.circuitBreaker("catalogService");
  }

  @Bean
  public CircuitBreaker searchServiceCircuitBreaker(CircuitBreakerRegistry registry) {
    return registry.circuitBreaker("searchService");
  }

  @Bean
  public WebClient catalogServiceWebClient(CircuitBreaker circuitBreaker) {
    HttpClient httpClient = HttpClient.create()
        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, catalogServiceTimeout)
        .responseTimeout(Duration.ofMillis(catalogServiceTimeout))
        .doOnConnected(conn -> conn
            .addHandlerLast(new ReadTimeoutHandler(catalogServiceTimeout / 1000))
            .addHandlerLast(new WriteTimeoutHandler(catalogServiceTimeout / 1000)));

    return WebClient.builder()
        .baseUrl(catalogServiceBaseUrl)
        .clientConnector(new ReactorClientHttpConnector(httpClient))
        .filter(logRequest())
        .filter(circuitBreakerFilter(circuitBreaker))
        .filter(retryFilter())
        .filter(logResponse())
        .build();
  }

  @Bean
  public WebClient searchServiceWebClient(CircuitBreaker circuitBreaker) {
    HttpClient httpClient = HttpClient.create()
        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, searchServiceTimeout)
        .responseTimeout(Duration.ofMillis(searchServiceTimeout))
        .doOnConnected(conn -> conn
            .addHandlerLast(new ReadTimeoutHandler(searchServiceTimeout / 1000))
            .addHandlerLast(new WriteTimeoutHandler(searchServiceTimeout / 1000)));

    return WebClient.builder()
        .baseUrl(searchServiceBaseUrl)
        .clientConnector(new ReactorClientHttpConnector(httpClient))
        .filter(logRequest())
        .filter(circuitBreakerFilter(circuitBreaker))
        .filter(retryFilter())
        .filter(logResponse())
        .build();
  }

  /**
   * Filtro de retry que tenta novamente em caso de erros 5xx ou timeouts.
   */
  private ExchangeFilterFunction retryFilter() {
    return (request, next) -> next.exchange(request)
        .flatMap(response -> {
          if (response.statusCode().is5xxServerError()) {
            // Para erros 5xx, criar uma exceção sem ler o body (para permitir retry)
            return Mono.error(WebClientResponseException.create(
                response.statusCode().value(),
                response.statusCode().toString(),
                response.headers().asHttpHeaders(),
                new byte[0],
                null));
          }
          return Mono.just(response);
        })
        .retryWhen(Retry.backoff(maxRetryAttempts, Duration.ofMillis(retryMinBackoff))
            .filter(throwable -> {
              if (throwable instanceof WebClientResponseException) {
                WebClientResponseException ex = (WebClientResponseException) throwable;
                return ex.getStatusCode().is5xxServerError();
              }
              return throwable instanceof java.util.concurrent.TimeoutException
                  || throwable instanceof java.net.ConnectException
                  || throwable instanceof java.io.IOException;
            })
            .doBeforeRetry(retrySignal -> logger.warn(
                "Tentando novamente requisição após erro. Tentativa: {}/{}",
                retrySignal.totalRetries() + 1, maxRetryAttempts))
            .doAfterRetry(retrySignal -> logger.debug(
                "Retry executado. Tentativa: {}", retrySignal.totalRetries())));
  }

  /**
   * Filtro de circuit breaker que protege contra falhas em cascata.
   */
  private ExchangeFilterFunction circuitBreakerFilter(CircuitBreaker circuitBreaker) {
    return (request, next) -> next.exchange(request)
        .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
        .doOnSuccess(response -> {
          if (response.statusCode().is2xxSuccessful()) {
            logger.debug("Requisição bem-sucedida para: {}", request.url());
          }
        })
        .doOnError(throwable -> {
          logger.error("Erro na requisição para: {}. Estado do circuit breaker: {}",
              request.url(), circuitBreaker.getState(), throwable);
        });
  }

  /**
   * Filtro para logar requisições HTTP.
   */
  private ExchangeFilterFunction logRequest() {
    return ExchangeFilterFunction.ofRequestProcessor(request -> {
      if (logger.isDebugEnabled()) {
        logger.debug("Requisição: {} {}", request.method(), request.url());
      }
      return Mono.just(request);
    });
  }

  /**
   * Filtro para logar respostas HTTP.
   */
  private ExchangeFilterFunction logResponse() {
    return ExchangeFilterFunction.ofResponseProcessor(response -> {
      if (logger.isDebugEnabled()) {
        logger.debug("Resposta: {} {}", response.statusCode(), response.headers());
      }
      return Mono.just(response);
    });
  }
}

