package com.marketplace.search.indexing.application.services;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.marketplace.search.indexing.application.handlers.payloads.ProductMetricsPayload;
import com.marketplace.search.indexing.domain.repositories.CacheRepository;

/**
 * Serviço para gerenciar cache de métricas de produtos
 */
@Service
public class ProductMetricsCacheService {

  private static final Logger logger = LoggerFactory.getLogger(ProductMetricsCacheService.class);
  private static final String METRICS_CACHE_PREFIX = "metrics:product:";
  private static final Duration DEFAULT_TTL = Duration.ofHours(1); // TTL menor pois metrics mudam mais frequentemente

  private final CacheRepository cacheRepository;

  public ProductMetricsCacheService(CacheRepository cacheRepository) {
    this.cacheRepository = cacheRepository;
  }

  public void cacheMetrics(ProductMetricsPayload metrics) {
    if (metrics == null || metrics.getProductId() == null) {
      logger.warn("Tentativa de cachear metrics nulas ou sem productId");
      return;
    }
    String key = METRICS_CACHE_PREFIX + metrics.getProductId();
    cacheRepository.put(key, metrics, DEFAULT_TTL);
    logger.debug("Product metrics cached: {}", metrics.getProductId());
  }

  public ProductMetricsPayload getMetrics(String productId) {
    if (productId == null) {
      return null;
    }
    String key = METRICS_CACHE_PREFIX + productId;
    return cacheRepository.get(key, ProductMetricsPayload.class).orElse(null);
  }

  public void evictMetrics(String productId) {
    if (productId != null) {
      String key = METRICS_CACHE_PREFIX + productId;
      cacheRepository.evict(key);
      logger.debug("Product metrics evicted from cache: {}", productId);
    }
  }
}

