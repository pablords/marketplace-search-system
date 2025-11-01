package com.marketplace.search.application.usecases;

import java.time.Duration;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.marketplace.search.application.config.SearchCacheProperties;
import com.marketplace.search.application.dto.SearchMetricsDTO;
import com.marketplace.search.application.dto.SearchRequestDTO;
import com.marketplace.search.application.dto.SearchResultDTO;
import com.marketplace.search.application.mappers.SearchMapper;
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
    public SearchResultDTO execute(SearchRequestDTO request) {
        logger.info("Executing search: query='{}', limit={}, offset={}", 
                   request.getQuery(), request.getLimit(), request.getOffset());
        
        try {
            // Mapear DTOs para objetos de domínio
            SearchQuery query = searchMapper.toDomain(request);
            UserContext userContext = searchMapper.mapUserContext(request.getUserContext());

            String cacheKey = buildCacheKey(query, userContext, "standard");
            SearchResultDTO cachedResult = getFromCache(cacheKey);
            if (cachedResult != null) {
                return cachedResult;
            }
            
            // Executar busca usando o serviço de domínio
            SearchResult result = searchDomainService.smartSearch(query, userContext);
            
            // Mapear resultado para DTO
            SearchResultDTO resultDTO = searchMapper.toDTO(result);

            storeInCache(cacheKey, resultDTO, result.hasResults());
            
            logger.info("Search completed: found {} products in {}ms", 
                       result.products().size(), result.executionTime().toMillis());
            
            return resultDTO;
            
        } catch (Exception e) {
            logger.error("Error executing search for query: {}", request.getQuery(), e);
            throw new SearchException("Failed to execute search", e);
        }
    }

    /**
     * Executa busca com fallback automático
     */
    public SearchResultDTO executeWithFallback(SearchRequestDTO request) {
        logger.info("Executing search with fallback: query='{}'", request.getQuery());
        
        try {
            SearchQuery query = searchMapper.toDomain(request);
            UserContext userContext = searchMapper.mapUserContext(request.getUserContext());

            String cacheKey = buildCacheKey(query, userContext, "fallback");
            SearchResultDTO cachedResult = getFromCache(cacheKey);
            if (cachedResult != null) {
                return cachedResult;
            }
            
            SearchResult result = searchDomainService.searchWithFallback(query, userContext);
            SearchResultDTO resultDTO = searchMapper.toDTO(result);

            storeInCache(cacheKey, resultDTO, result.hasResults());
            
            logger.info("Search with fallback completed: found {} products", 
                       result.products().size());
            
            return resultDTO;
            
        } catch (Exception e) {
            logger.error("Error executing search with fallback for query: {}", request.getQuery(), e);
            throw new SearchException("Failed to execute search with fallback", e);
        }
    }

    private SearchResultDTO getFromCache(String cacheKey) {
        if (!isCacheEnabled() || cacheKey == null) {
            return null;
        }

        try {
            Optional<SearchResultDTO> cached = cacheRepository.get(cacheKey, SearchResultDTO.class);
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

    private void storeInCache(String cacheKey, SearchResultDTO resultDTO, boolean hasResults) {
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

    private SearchResultDTO markAsCached(SearchResultDTO dto) {
        if (dto == null) {
            return null;
        }

        dto.setExecutionTimeMs(0);

        SearchMetricsDTO metrics = dto.getMetrics();
        if (metrics == null) {
            metrics = new SearchMetricsDTO();
            dto.setMetrics(metrics);
        }
        metrics.setUsedCache(true);

        return dto;
    }
}

/**
 * Exceção específica para erros de busca.
 */
class SearchException extends RuntimeException {
    public SearchException(String message, Throwable cause) {
        super(message, cause);
    }
}