package com.marketplace.search.application.mappers;

import com.marketplace.search.application.dto.*;
import com.marketplace.search.domain.valueobjects.*;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Mapper para conversão entre DTOs de busca e Value Objects do domínio
 */
@Component
public class SearchMapper {

    public SearchQuery toDomain(SearchRequestDTO dto) {
        Category category = dto.getCategoryId() != null ? 
            new Category(dto.getCategoryId(), "Unknown", null, "unknown") : null;
            
        List<SearchFilter> filters = dto.getFilters() != null ?
            dto.getFilters().stream()
                .map(this::mapFilter)
                .collect(Collectors.toList()) :
            List.of();
            
        SearchSort sort = mapSort(dto.getSort());
        
        return new SearchQuery(
            dto.getQuery(),
            category,
            filters,
            sort,
            dto.getOffset(),
            dto.getLimit()
        );
    }

    public UserContext mapUserContext(UserContextDTO dto) {
        if (dto == null) return null;
        
        UserLocation location = dto.getLocation() != null ?
            mapUserLocation(dto.getLocation()) :
            UserLocation.of("BR", "SP", "São Paulo");
            
        return new UserContext(
            dto.getUserId(),
            location,
            dto.getPreferredCategories() != null ? dto.getPreferredCategories() : Set.of(),
            dto.getPurchaseHistory() != null ? dto.getPurchaseHistory() : Set.of(),
            dto.getSearchHistory() != null ? dto.getSearchHistory() : Set.of(),
            dto.getViewHistory() != null ? dto.getViewHistory() : Set.of(),
            null // UserProfile seria mapeado separadamente
        );
    }

    public SearchResultDTO toDTO(SearchResult result) {
        List<ProductDTO> productDTOs = result.getProducts().stream()
            .map(product -> {
                ProductMapper productMapper = new ProductMapper();
                return productMapper.toDTO(product);
            })
            .collect(Collectors.toList());
            
        SearchResultDTO dto = new SearchResultDTO();
        dto.setProducts(productDTOs);
        dto.setTotalCount(result.getTotalCount());
        dto.setPageSize(result.getPageSize());
        dto.setPageNumber(result.getPageNumber());
        dto.setTotalPages(result.getTotalPages());
        dto.setHasNextPage(result.hasNextPage());
        dto.setHasPreviousPage(result.hasPreviousPage());
        dto.setExecutionTimeMs(result.getExecutionTime().toMillis());
        
        if (result.getMetrics() != null) {
            dto.setMetrics(mapMetricsToDTO(result.getMetrics()));
        }
        
        return dto;
    }

    private SearchFilter mapFilter(SearchFilterDTO dto) {
        FilterType type = mapFilterType(dto.getType());
        return new SearchFilter(dto.getName(), type, dto.getValues());
    }

    private FilterType mapFilterType(String type) {
        if (type == null) return FilterType.TERM;
        
        try {
            return FilterType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            return FilterType.TERM;
        }
    }

    private SearchSort mapSort(String sort) {
        if (sort == null) return SearchSort.RELEVANCE;
        
        try {
            return SearchSort.valueOf(sort.toUpperCase());
        } catch (IllegalArgumentException e) {
            return SearchSort.RELEVANCE;
        }
    }

    private UserLocation mapUserLocation(UserLocationDTO dto) {
        return new UserLocation(
            dto.getCountry() != null ? dto.getCountry() : "BR",
            dto.getState() != null ? dto.getState() : "SP",
            dto.getCity() != null ? dto.getCity() : "São Paulo",
            dto.getZipCode(),
            dto.getLatitude(),
            dto.getLongitude()
        );
    }

    private SearchMetricsDTO mapMetricsToDTO(SearchMetrics metrics) {
        SearchMetricsDTO dto = new SearchMetricsDTO();
        dto.setQueriesPerSecond(metrics.getQueriesPerSecond());
        dto.setAverageScore(metrics.getAverageScore());
        dto.setIndexedDocuments(metrics.getIndexedDocuments());
        dto.setIndexSize(metrics.getIndexSize());
        dto.setUsedCache(metrics.isUsedCache());
        dto.setShardInfo(metrics.getShardInfo());
        return dto;
    }
}