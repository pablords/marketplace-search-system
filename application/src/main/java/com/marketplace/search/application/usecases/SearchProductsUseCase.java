package com.marketplace.search.application.usecases;

import java.time.Duration;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.marketplace.search.application.config.SearchCacheProperties;
import com.marketplace.search.application.exceptions.SearchException;
import com.marketplace.search.application.mappers.SearchMapper;
import com.marketplace.search.application.queries.SearchMetricsData;
import com.marketplace.search.application.queries.SearchRequestQuery;
import com.marketplace.search.application.queries.SearchResultQuery;
import com.marketplace.search.domain.repositories.CacheRepository;
import com.marketplace.search.domain.services.SearchDomainService;
import com.marketplace.search.domain.valueobjects.SearchQuery;
import com.marketplace.search.domain.valueobjects.SearchResult;
import com.marketplace.search.domain.valueobjects.UserContext;


/**
 * Caso de uso para busca de produtos
 */
@Service
public class SearchProductsUseCase {

  private static final Logger logger = LoggerFactory.getLogger(SearchProductsUseCase.class);

  private final SearchDomainService searchDomainService;
  private final SearchMapper searchMapper;
  private final CacheRepository cacheRepository;
  private final SearchCacheProperties cacheProperties;

  public SearchProductsUseCase(SearchDomainService searchDomainService,
      SearchMapper searchMapper,
      CacheRepository cacheRepository,
      SearchCacheProperties cacheProperties) {
    this.searchDomainService = searchDomainService;
    this.searchMapper = searchMapper;
    this.cacheRepository = cacheRepository;
    this.cacheProperties = cacheProperties;
  }

  /**
   * Executa busca padrão de produtos
   */
  public SearchResultQuery execute(SearchRequestQuery request) {
    logger.info("Executing search: query='{}', limit={}, offset={}",
        request.query(), request.limit(), request.offset());

    try {
      // Mapear DTOs para objetos de domínio
      SearchQuery query = searchMapper.toDomain(request);
      UserContext userContext = searchMapper.mapUserContext(request.userContext());

      String cacheKey = buildCacheKey(query, userContext, "standard");
      SearchResultQuery cachedResult = getFromCache(cacheKey);
      if (cachedResult != null) {
        return cachedResult;
      }

      // Executar busca usando o serviço de domínio
      SearchResult result = searchDomainService.smartSearch(query, userContext);

      // Mapear resultado para DTO
      SearchResultQuery resultDTO = searchMapper.toDTO(result);

      storeInCache(cacheKey, resultDTO, result.hasResults());

      logger.info("Search completed: found {} products in {}ms",
          result.products().size(), result.executionTime().toMillis());

      return resultDTO;

    } catch (Exception e) {
      logger.error("Error executing search for query: {}", request.query(), e);
      throw new SearchException("Failed to execute search", e);
    }
  }

  /**
   * Executa busca de forma assíncrona
   */
  @Async("taskExecutor")
  public CompletableFuture<SearchResultQuery> executeAsync(SearchRequestQuery request) {
    try {
      SearchResultQuery result = execute(request);
      return CompletableFuture.completedFuture(result);
    } catch (Exception e) {
      return CompletableFuture.failedFuture(e);
    }
  }

  /**
   * Executa busca com fallback automático
   */
  public SearchResultQuery executeWithFallback(SearchRequestQuery request) {
    logger.info("Executing search with fallback: query='{}'", request.query());

    try {
      SearchQuery query = searchMapper.toDomain(request);
      UserContext userContext = searchMapper.mapUserContext(request.userContext());

      String cacheKey = buildCacheKey(query, userContext, "fallback");
      SearchResultQuery cachedResult = getFromCache(cacheKey);
      if (cachedResult != null) {
        return cachedResult;
      }

      SearchResult result = searchDomainService.searchWithFallback(query, userContext);
      SearchResultQuery resultDTO = searchMapper.toDTO(result);

      storeInCache(cacheKey, resultDTO, result.hasResults());

      logger.info("Search with fallback completed: found {} products",
          result.products().size());

      return resultDTO;

    } catch (Exception e) {
      logger.error("Error executing search with fallback for query: {}", request.query(), e);
      throw new SearchException("Failed to execute search with fallback", e);
    }
  }

  private SearchResultQuery getFromCache(String cacheKey) {
    if (!isCacheEnabled() || cacheKey == null) {
      return null;
    }

    try {
      Optional<SearchResultQuery> cached = cacheRepository.get(cacheKey, SearchResultQuery.class);
      if (cached.isPresent()) {
        logger.debug("Cache hit for key {}", cacheKey);
        return markAsCached(cached.get());
      }
      logger.debug("Cache miss for key {}", cacheKey);
    } catch (Exception ex) {
      logger.warn("Failed to retrieve cache entry for key {}: {}", cacheKey, ex.getMessage());
    }
    return null;
  }

  private void storeInCache(String cacheKey, SearchResultQuery resultDTO, boolean hasResults) {
    if (!isCacheEnabled() || cacheKey == null || resultDTO == null) {
      return;
    }

    if (!hasResults) {
      logger.debug("Skipping cache store for key {} because result has no products", cacheKey);
      return;
    }

    Duration ttl = cacheProperties.getSearchResultsTtl();
    if (ttl.isZero() || ttl.isNegative()) {
      logger.debug("Skipping cache store for key {} due to invalid TTL {}", cacheKey, ttl);
      return;
    }

    try {
      cacheRepository.put(cacheKey, resultDTO, ttl);
      logger.debug("Stored search result in cache with key {} for TTL {}", cacheKey, ttl);
    } catch (Exception ex) {
      logger.warn("Failed to store cache entry for key {}: {}", cacheKey, ex.getMessage());
    }
  }

  private String buildCacheKey(SearchQuery query, UserContext userContext, String mode) {
    if (!isCacheEnabled()) {
      return null;
    }

    StringBuilder builder = new StringBuilder(cacheProperties.getKeyPrefix());
    builder.append(":mode=").append(mode);
    builder.append(":q=").append(query.terms());
    builder.append(":o=").append(query.offset());
    builder.append(":l=").append(query.limit());
    builder.append(":s=").append(query.sort().name());

    if (query.hasCategoryFilter() && query.category() != null) {
      builder.append(":c=").append(query.category().getId());
    }

    if (!query.filters().isEmpty()) {
      String filtersKey = query.filters().stream()
          .sorted(Comparator.comparing(f -> f.name().trim()))
          .map(filter -> filter.name() + "=" + String.join(",", filter.values()))
          .collect(Collectors.joining("|"));
      builder.append(":f=").append(Integer.toHexString(filtersKey.hashCode()));
    }

    if (userContext != null) {
      if (!userContext.isAnonymous() && userContext.userId() != null) {
        builder.append(":u=").append(userContext.userId());
      } else if (userContext.location() != null) {
        builder.append(":loc=")
            .append(userContext.location().country())
            .append("-")
            .append(userContext.location().state());
      } else {
        builder.append(":u=anon");
      }
    } else {
      builder.append(":u=anon");
    }

    return builder.toString().toLowerCase();
  }

  private boolean isCacheEnabled() {
    return cacheProperties != null && cacheProperties.isEnabled() && cacheProperties.hasValidSearchTtl();
  }

  private SearchResultQuery markAsCached(SearchResultQuery dto) {
    logger.debug("isCacheEnabled {}", isCacheEnabled());
    if (dto == null) {
      return null;
    }

    // Records são imutáveis, precisamos criar novas instâncias
    SearchMetricsData metrics = dto.metrics();
    if (metrics == null) {
      metrics = SearchMetricsData.builder()
          .usedCache(isCacheEnabled())
          .build();
    } else {
      metrics = SearchMetricsData.builder()
          .queriesPerSecond(metrics.queriesPerSecond())
          .averageScore(metrics.averageScore())
          .indexedDocuments(metrics.indexedDocuments())
          .indexSize(metrics.indexSize())
          .usedCache(isCacheEnabled())
          .shardInfo(metrics.shardInfo())
          .build();
    }

    // Criar novo DTO com metrics atualizado e executionTime zerado
    return SearchResultQuery.builder()
        .products(dto.products())
        .totalCount(dto.totalCount())
        .pageSize(dto.pageSize())
        .pageNumber(dto.pageNumber())
        .totalPages(dto.totalPages())
        .hasNextPage(dto.hasNextPage())
        .hasPreviousPage(dto.hasPreviousPage())
        .executionTimeMs(0)
        .metrics(metrics)
        .build();
  }
}