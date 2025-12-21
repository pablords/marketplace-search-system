package com.marketplace.search.indexing.application.services;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.marketplace.search.indexing.application.handlers.payloads.BrandPayload;
import com.marketplace.search.indexing.application.handlers.payloads.CategoryPayload;
import com.marketplace.search.indexing.application.handlers.payloads.SellerPayload;
import com.marketplace.search.indexing.domain.repositories.CacheRepository;

/**
 * Serviço para gerenciar cache de dimensões (brands, categories, sellers)
 */
@Service
public class DimensionCacheService {

  private static final Logger logger = LoggerFactory.getLogger(DimensionCacheService.class);
  private static final String BRAND_CACHE_PREFIX = "dimension:brand:";
  private static final String CATEGORY_CACHE_PREFIX = "dimension:category:";
  private static final String SELLER_CACHE_PREFIX = "dimension:seller:";
  private static final Duration DEFAULT_TTL = Duration.ofHours(24);

  private final CacheRepository cacheRepository;

  public DimensionCacheService(CacheRepository cacheRepository) {
    this.cacheRepository = cacheRepository;
  }

  // Brand operations
  public void cacheBrand(BrandPayload brand) {
    if (brand == null || brand.getId() == null) {
      logger.warn("Tentativa de cachear brand nulo ou sem ID");
      return;
    }
    String key = BRAND_CACHE_PREFIX + brand.getId();
    cacheRepository.put(key, brand, DEFAULT_TTL);
    logger.debug("Brand cached: {}", brand.getId());
  }

  public BrandPayload getBrand(String brandId) {
    if (brandId == null) {
      return null;
    }
    String key = BRAND_CACHE_PREFIX + brandId;
    return cacheRepository.get(key, BrandPayload.class).orElse(null);
  }

  public void evictBrand(String brandId) {
    if (brandId != null) {
      String key = BRAND_CACHE_PREFIX + brandId;
      cacheRepository.evict(key);
      logger.debug("Brand evicted from cache: {}", brandId);
    }
  }

  // Category operations
  public void cacheCategory(CategoryPayload category) {
    if (category == null || category.getId() == null) {
      logger.warn("Tentativa de cachear category nula ou sem ID");
      return;
    }
    String key = CATEGORY_CACHE_PREFIX + category.getId();
    cacheRepository.put(key, category, DEFAULT_TTL);
    logger.debug("Category cached: {}", category.getId());
  }

  public CategoryPayload getCategory(String categoryId) {
    if (categoryId == null) {
      return null;
    }
    String key = CATEGORY_CACHE_PREFIX + categoryId;
    return cacheRepository.get(key, CategoryPayload.class).orElse(null);
  }

  public void evictCategory(String categoryId) {
    if (categoryId != null) {
      String key = CATEGORY_CACHE_PREFIX + categoryId;
      cacheRepository.evict(key);
      logger.debug("Category evicted from cache: {}", categoryId);
    }
  }

  // Seller operations
  public void cacheSeller(SellerPayload seller) {
    if (seller == null || seller.getId() == null) {
      logger.warn("Tentativa de cachear seller nulo ou sem ID");
      return;
    }
    String key = SELLER_CACHE_PREFIX + seller.getId();
    cacheRepository.put(key, seller, DEFAULT_TTL);
    logger.debug("Seller cached: {}", seller.getId());
  }

  public SellerPayload getSeller(String sellerId) {
    if (sellerId == null) {
      return null;
    }
    String key = SELLER_CACHE_PREFIX + sellerId;
    return cacheRepository.get(key, SellerPayload.class).orElse(null);
  }

  public void evictSeller(String sellerId) {
    if (sellerId != null) {
      String key = SELLER_CACHE_PREFIX + sellerId;
      cacheRepository.evict(key);
      logger.debug("Seller evicted from cache: {}", sellerId);
    }
  }

  // Cache hit rate tracking
  public void incrementCacheHit(String dimensionType) {
    String key = "cache:stats:hit:" + dimensionType;
    cacheRepository.increment(key);
  }

  public void incrementCacheMiss(String dimensionType) {
    String key = "cache:stats:miss:" + dimensionType;
    cacheRepository.increment(key);
  }
}

