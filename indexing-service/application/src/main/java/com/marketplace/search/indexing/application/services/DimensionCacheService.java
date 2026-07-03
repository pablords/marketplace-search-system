package com.marketplace.search.indexing.application.services;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.marketplace.search.indexing.application.handlers.payloads.BrandPayload;
import com.marketplace.search.indexing.application.handlers.payloads.CategoryPayload;
import com.marketplace.search.indexing.application.handlers.payloads.SellerPayload;
import com.marketplace.search.indexing.domain.repositories.CacheRepository;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;

/**
 * Serviço para gerenciar cache de dimensões (brands, categories, sellers)
 * Implementa cache L1 (em memória) e L2 (Redis) com métricas de monitoramento.
 */
@Service
public class DimensionCacheService {

  private static final Logger logger = LoggerFactory.getLogger(DimensionCacheService.class);
  private static final String BRAND_CACHE_PREFIX = "dimension:brand:";
  private static final String CATEGORY_CACHE_PREFIX = "dimension:category:";
  private static final String SELLER_CACHE_PREFIX = "dimension:seller:";
  private static final Duration DEFAULT_TTL = Duration.ofHours(24);

  private final CacheRepository cacheRepository;
  private final MeterRegistry meterRegistry;

  // L1 Caches em memória para alto rendimento
  private final Map<String, BrandPayload> brandL1Cache = new ConcurrentHashMap<>();
  private final Map<String, CategoryPayload> categoryL1Cache = new ConcurrentHashMap<>();
  private final Map<String, SellerPayload> sellerL1Cache = new ConcurrentHashMap<>();

  public DimensionCacheService(CacheRepository cacheRepository, MeterRegistry meterRegistry) {
    this.cacheRepository = cacheRepository;
    this.meterRegistry = meterRegistry;

    // Registra métricas de tamanho de cache L1 (Gauge) no Micrometer
    meterRegistry.gauge("l1_cache_size", List.of(Tag.of("cache", "brand")), brandL1Cache, Map::size);
    meterRegistry.gauge("l1_cache_size", List.of(Tag.of("cache", "category")), categoryL1Cache, Map::size);
    meterRegistry.gauge("l1_cache_size", List.of(Tag.of("cache", "seller")), sellerL1Cache, Map::size);
  }

  private void recordHit(String cacheName) {
    meterRegistry.counter("l1_cache_requests_total", "cache", cacheName, "result", "hit").increment();
  }

  private void recordMiss(String cacheName) {
    meterRegistry.counter("l1_cache_requests_total", "cache", cacheName, "result", "miss").increment();
  }

  private void recordEviction(String cacheName) {
    meterRegistry.counter("l1_cache_evictions_total", "cache", cacheName).increment();
  }

  // Brand operations
  public void cacheBrand(BrandPayload brand) {
    if (brand == null || brand.getId() == null) {
      logger.warn("Tentativa de cachear brand nulo ou sem ID");
      return;
    }
    String key = BRAND_CACHE_PREFIX + brand.getId();
    
    // Atualiza L1 e L2
    brandL1Cache.put(brand.getId(), brand);
    cacheRepository.put(key, brand, DEFAULT_TTL);
    logger.debug("Brand cached: {} (L1 and L2)", brand.getId());
  }

  public BrandPayload getBrand(String brandId) {
    if (brandId == null) {
      return null;
    }

    // Tenta obter do L1 (em memória)
    BrandPayload brand = brandL1Cache.get(brandId);
    if (brand != null) {
      recordHit("brand");
      return brand;
    }

    recordMiss("brand");
    String key = BRAND_CACHE_PREFIX + brandId;
    Optional<BrandPayload> l2Brand = cacheRepository.get(key, BrandPayload.class);
    
    if (l2Brand.isPresent()) {
      BrandPayload found = l2Brand.get();
      brandL1Cache.put(brandId, found); // Carrega no L1
      return found;
    }

    return null;
  }

  public void evictBrand(String brandId) {
    if (brandId != null) {
      brandL1Cache.remove(brandId);
      String key = BRAND_CACHE_PREFIX + brandId;
      cacheRepository.evict(key);
      recordEviction("brand");
      logger.debug("Brand evicted from cache: {} (L1 and L2)", brandId);
    }
  }

  // Category operations
  public void cacheCategory(CategoryPayload category) {
    if (category == null || category.getId() == null) {
      logger.warn("Tentativa de cachear category nula ou sem ID");
      return;
    }
    String key = CATEGORY_CACHE_PREFIX + category.getId();
    
    // Atualiza L1 e L2
    categoryL1Cache.put(category.getId(), category);
    cacheRepository.put(key, category, DEFAULT_TTL);
    logger.debug("Category cached: {} (L1 and L2)", category.getId());
  }

  public CategoryPayload getCategory(String categoryId) {
    if (categoryId == null) {
      return null;
    }

    // Tenta obter do L1
    CategoryPayload category = categoryL1Cache.get(categoryId);
    if (category != null) {
      recordHit("category");
      return category;
    }

    recordMiss("category");
    String key = CATEGORY_CACHE_PREFIX + categoryId;
    Optional<CategoryPayload> l2Category = cacheRepository.get(key, CategoryPayload.class);
    
    if (l2Category.isPresent()) {
      CategoryPayload found = l2Category.get();
      categoryL1Cache.put(categoryId, found); // Carrega no L1
      return found;
    }

    return null;
  }

  public void evictCategory(String categoryId) {
    if (categoryId != null) {
      categoryL1Cache.remove(categoryId);
      String key = CATEGORY_CACHE_PREFIX + categoryId;
      cacheRepository.evict(key);
      recordEviction("category");
      logger.debug("Category evicted from cache: {} (L1 and L2)", categoryId);
    }
  }

  // Seller operations
  public void cacheSeller(SellerPayload seller) {
    if (seller == null || seller.getId() == null) {
      logger.warn("Tentativa de cachear seller nulo ou sem ID");
      return;
    }
    String key = SELLER_CACHE_PREFIX + seller.getId();
    
    // Atualiza L1 e L2
    sellerL1Cache.put(seller.getId(), seller);
    cacheRepository.put(key, seller, DEFAULT_TTL);
    logger.debug("Seller cached: {} (L1 and L2)", seller.getId());
  }

  public SellerPayload getSeller(String sellerId) {
    if (sellerId == null) {
      return null;
    }

    // Tenta obter do L1
    SellerPayload seller = sellerL1Cache.get(sellerId);
    if (seller != null) {
      recordHit("seller");
      return seller;
    }

    recordMiss("seller");
    String key = SELLER_CACHE_PREFIX + sellerId;
    Optional<SellerPayload> l2Seller = cacheRepository.get(key, SellerPayload.class);
    
    if (l2Seller.isPresent()) {
      SellerPayload found = l2Seller.get();
      sellerL1Cache.put(sellerId, found); // Carrega no L1
      return found;
    }

    return null;
  }

  public void evictSeller(String sellerId) {
    if (sellerId != null) {
      sellerL1Cache.remove(sellerId);
      String key = SELLER_CACHE_PREFIX + sellerId;
      cacheRepository.evict(key);
      recordEviction("seller");
      logger.debug("Seller evicted from cache: {} (L1 and L2)", sellerId);
    }
  }

  // Cache hit rate tracking (L2 stats)
  public void incrementCacheHit(String dimensionType) {
    String key = "cache:stats:hit:" + dimensionType;
    cacheRepository.increment(key);
  }

  public void incrementCacheMiss(String dimensionType) {
    String key = "cache:stats:miss:" + dimensionType;
    cacheRepository.increment(key);
  }
}

