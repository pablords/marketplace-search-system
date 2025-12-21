package com.marketplace.search.indexing.application.services;

import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.marketplace.search.indexing.application.handlers.payloads.BrandPayload;
import com.marketplace.search.indexing.application.handlers.payloads.CategoryPayload;
import com.marketplace.search.indexing.application.handlers.payloads.ProductMetricsPayload;
import com.marketplace.search.indexing.application.handlers.payloads.ProductPayload;
import com.marketplace.search.indexing.application.handlers.payloads.SellerPayload;

/**
 * Serviço para enriquecer eventos de produtos com dados de dimensões e métricas
 */
@Service
public class ProductEnrichmentService {

  private static final Logger logger = LoggerFactory.getLogger(ProductEnrichmentService.class);

  private final DimensionCacheService dimensionCacheService;
  private final ProductMetricsCacheService metricsCacheService;
  private final EnrichmentMetrics enrichmentMetrics;

  public ProductEnrichmentService(
      DimensionCacheService dimensionCacheService,
      ProductMetricsCacheService metricsCacheService,
      EnrichmentMetrics enrichmentMetrics) {
    this.dimensionCacheService = dimensionCacheService;
    this.metricsCacheService = metricsCacheService;
    this.enrichmentMetrics = enrichmentMetrics;
  }

  /**
   * Enriquece um ProductPayload com dados de dimensões e métricas do cache
   * 
   * @param product ProductPayload a ser enriquecido
   * @return ProductPayload enriquecido (mesma instância modificada)
   */
  public ProductPayload enrich(ProductPayload product) {
    if (product == null) {
      logger.warn("Tentativa de enriquecer produto nulo");
      enrichmentMetrics.recordEnrichment(false, false, Duration.ZERO);
      return null;
    }

    Instant startTime = Instant.now();
    logger.debug("Enriquecendo produto: {}", product.getId());

    boolean brandEnriched = false;
    boolean categoryEnriched = false;
    boolean sellerEnriched = false;
    boolean metricsEnriched = false;

    // Enriquecer com Brand
    if (product.getBrandId() != null) {
      BrandPayload brand = dimensionCacheService.getBrand(product.getBrandId());
      if (brand != null) {
        product.setBrandName(brand.getName());
        product.setBrandDescription(brand.getDescription());
        dimensionCacheService.incrementCacheHit("brand");
        brandEnriched = true;
        logger.debug("Brand enriquecido para produto {}: {}", product.getId(), brand.getName());
      } else {
        dimensionCacheService.incrementCacheMiss("brand");
        logger.warn("Brand não encontrado no cache para produto {}: brandId={}", product.getId(), product.getBrandId());
      }
    }

    // Enriquecer com Category
    if (product.getCategoryId() != null) {
      CategoryPayload category = dimensionCacheService.getCategory(product.getCategoryId());
      if (category != null) {
        product.setCategoryName(category.getName());
        product.setCategoryPath(category.getPath());
        dimensionCacheService.incrementCacheHit("category");
        categoryEnriched = true;
        logger.debug("Category enriquecida para produto {}: {}", product.getId(), category.getName());
      } else {
        dimensionCacheService.incrementCacheMiss("category");
        logger.warn("Category não encontrada no cache para produto {}: categoryId={}", product.getId(), product.getCategoryId());
      }
    }

    // Enriquecer com Seller
    if (product.getSellerId() != null) {
      SellerPayload seller = dimensionCacheService.getSeller(product.getSellerId());
      if (seller != null) {
        product.setSellerName(seller.getName());
        product.setSellerType(seller.getType());
        product.setSellerStatus(seller.getStatus());
        product.setSellerScore(seller.getScore());
        product.setSellerTotalReviews(seller.getTotalReviews());
        product.setSellerPositiveReviews(seller.getPositiveReviews());
        product.setSellerNeutralReviews(seller.getNeutralReviews());
        product.setSellerNegativeReviews(seller.getNegativeReviews());
        product.setSellerCancellationRate(seller.getCancellationRate());
        product.setSellerDeliveryPerformance(seller.getDeliveryPerformance());
        dimensionCacheService.incrementCacheHit("seller");
        sellerEnriched = true;
        logger.debug("Seller enriquecido para produto {}: {}", product.getId(), seller.getName());
      } else {
        dimensionCacheService.incrementCacheMiss("seller");
        logger.warn("Seller não encontrado no cache para produto {}: sellerId={}", product.getId(), product.getSellerId());
      }
    }

    // Enriquecer com ProductMetrics
    ProductMetricsPayload metrics = metricsCacheService.getMetrics(product.getId());
    if (metrics != null) {
      product.setTotalSold(metrics.getTotalSales());
      product.setReviewCount(metrics.getTotalReviews());
      product.setCtr(metrics.getCtr());
      product.setAverageRating(metrics.getAverageRating());
      // Note: stock_quantity pode vir de metrics ou de available_quantity do produto
      if (metrics.getStockQuantity() != null) {
        // Preferir stock_quantity de metrics se disponível
        product.setAvailableQuantity(metrics.getStockQuantity());
      }
      metricsEnriched = true;
      logger.debug("Metrics enriquecidas para produto {}", product.getId());
    } else {
      logger.debug("Metrics não encontradas no cache para produto {}", product.getId());
    }

    Duration duration = Duration.between(startTime, Instant.now());
    boolean complete = brandEnriched && categoryEnriched && sellerEnriched;
    boolean success = product.getId() != null; // Sucesso se pelo menos o produto tem ID

    enrichmentMetrics.recordEnrichment(success, complete, duration);

    if (!complete) {
      logger.warn("Enriquecimento incompleto para produto {} - Brand: {}, Category: {}, Seller: {}, Metrics: {}",
          product.getId(), brandEnriched, categoryEnriched, sellerEnriched, metricsEnriched);
    }

    return product;
  }
}

