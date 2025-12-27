package com.marketplace.search.catalog.bootstrap.config;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

@Configuration
@Slf4j
public class DebeziumConnectorRegistry {

  @Value("${debezium.connector.name}")
  private String connectorName;
  @Value("${debezium.connector.url}")
  private String connectUrl;
  private final RestTemplate restTemplate;
  private final DebeziumConnectorProperties properties;

  public DebeziumConnectorRegistry(RestTemplateBuilder builder, DebeziumConnectorProperties properties) {
    this.restTemplate = builder
        .setConnectTimeout(Duration.ofSeconds(5))
        .setReadTimeout(Duration.ofSeconds(10))
        .build();
    this.properties = properties;
  }

  @PostConstruct
  public void registerConnector() {

    try {
      ResponseEntity<String> getResponse = restTemplate.getForEntity(connectUrl + "/" + connectorName, String.class);
      if (getResponse.getStatusCode().is2xxSuccessful()) {
        log.info("Conector '{}' já registrado no Kafka Connect.", connectorName);
        return;
      }
    } catch (Exception e) {
      log.info("Conector '{}' ainda não está registrado. Registrando agora...", connectorName);
    }

    log.info("Configuração enviada ao Kafka Connect: {}", properties.getConfig());

    Map<String, Object> requestBody = new HashMap<>();
    requestBody.put("name", connectorName);
    requestBody.put("config", properties.getConfig());

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);

    HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

    try {
      ResponseEntity<String> response = restTemplate.postForEntity(connectUrl, request, String.class);
      log.info("Debezium Connector registrado com sucesso: {}", response.getBody());
    } catch (Exception e) {
      log.error("Erro ao registrar o Debezium Connector: {}", e.getMessage(), e);
    }
  }
}
