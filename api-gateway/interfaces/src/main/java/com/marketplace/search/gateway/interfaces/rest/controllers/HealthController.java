package com.marketplace.search.gateway.interfaces.rest.controllers;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/health")
@Tag(name = "Health", description = "Saúde do API Gateway")
public class HealthController {
  @Operation(summary = "Health check", description = "Verifica se o API Gateway está funcionando")
  @GetMapping
  public ResponseEntity<Map<String, Object>> health() {
    Map<String, Object> health = new HashMap<>();
    health.put("status", "UP");
    health.put("service", "api-gateway");
    health.put("timestamp", Instant.now());
    return ResponseEntity.ok(health);
  }
}

