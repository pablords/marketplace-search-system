package com.marketplace.search.catalog.bootstrap.config;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

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

  @EventListener(ApplicationReadyEvent.class)
  public void registerConnectorAsync() {
    CompletableFuture.runAsync(this::registerWithRetry);
  }

  private void registerWithRetry() {
    int maxAttempts = 30;
    int attempt = 0;
    long delayMs = 10000; // 10 segundos entre tentativas

    log.info("Iniciando processo assíncrono de registro do Debezium Connector '{}'", connectorName);

    while (attempt < maxAttempts) {
      attempt++;
      try {
        log.info("Tentativa {}/{} de registrar/atualizar o Debezium Connector...", attempt, maxAttempts);
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Verificar se o connector já existe
        boolean exists = false;
        try {
          ResponseEntity<String> getResponse = restTemplate.getForEntity(connectUrl + "/" + connectorName, String.class);
          if (getResponse.getStatusCode().is2xxSuccessful()) {
            exists = true;
          }
        } catch (Exception e) {
          // Se for 404, significa que não existe (o que é esperado). Caso contrário, loga e assume que não existe para tentar criar
          log.debug("Erro ao verificar existência do conector (pode não existir ainda): {}", e.getMessage());
        }

        if (exists) {
          log.info("Conector '{}' já existe. Atualizando configuração...", connectorName);
          
          Map<String, Object> configBody = new HashMap<>();
          configBody.putAll(properties.getConfig());
          HttpEntity<Map<String, Object>> configRequest = new HttpEntity<>(configBody, headers);
          
          ResponseEntity<String> putResponse = restTemplate.exchange(
              connectUrl + "/" + connectorName + "/config",
              HttpMethod.PUT,
              configRequest,
              String.class
          );
          log.info("Debezium Connector atualizado com sucesso: {}", putResponse.getBody());
          return; // Sucesso, encerra o loop
        } else {
          log.info("Conector '{}' não existe. Registrando...", connectorName);
          
          Map<String, Object> requestBody = new HashMap<>();
          requestBody.put("name", connectorName);
          requestBody.put("config", properties.getConfig());
          HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
          
          ResponseEntity<String> response = restTemplate.postForEntity(connectUrl, request, String.class);
          log.info("Debezium Connector registrado com sucesso: {}", response.getBody());
          return; // Sucesso, encerra o loop
        }
      } catch (Exception e) {
        log.warn("Tentativa {} falhou: {}. Nova tentativa em {}s...", attempt, e.getMessage(), delayMs / 1000);
        try {
          Thread.sleep(delayMs);
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
          log.error("Registro do Debezium Connector interrompido.");
          return;
        }
      }
    }
    log.error("Excedido o número máximo de tentativas ({}) para registrar o Debezium Connector.", maxAttempts);
  }
}
