package com.marketplace.search.infrastructure.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import reactor.netty.http.client.HttpClient;

/**
 * Configuração do WebClient para comunicação HTTP com outros serviços.
 */
@Configuration
public class WebClientConfig {

  @Value("${services.catalog.base-url:http://localhost:8081/api/v1}")
  private String catalogServiceBaseUrl;

  @Value("${services.catalog.timeout:5000}")
  private int catalogServiceTimeout;

  @Bean
  public WebClient catalogServiceWebClient() {
    HttpClient httpClient = HttpClient.create()
        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, catalogServiceTimeout)
        .responseTimeout(Duration.ofMillis(catalogServiceTimeout))
        .doOnConnected(conn -> conn
            .addHandlerLast(new ReadTimeoutHandler(catalogServiceTimeout / 1000))
            .addHandlerLast(new WriteTimeoutHandler(catalogServiceTimeout / 1000)));

    return WebClient.builder()
        .baseUrl(catalogServiceBaseUrl)
        .clientConnector(new ReactorClientHttpConnector(httpClient))
        .build();
  }
}

