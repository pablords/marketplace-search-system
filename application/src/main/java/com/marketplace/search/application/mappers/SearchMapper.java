package com.marketplace.search.application.mappers;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.marketplace.search.application.dto.ProductDTO;
import com.marketplace.search.application.dto.SearchFilterDTO;
import com.marketplace.search.application.dto.SearchMetricsDTO;
import com.marketplace.search.application.dto.SearchRequestDTO;
import com.marketplace.search.application.dto.SearchResultDTO;
import com.marketplace.search.application.dto.UserContextDTO;
import com.marketplace.search.application.dto.UserLocationDTO;
import com.marketplace.search.domain.entities.Category;
import com.marketplace.search.domain.valueobjects.FilterType;
import com.marketplace.search.domain.valueobjects.SearchFilter;
import com.marketplace.search.domain.valueobjects.SearchMetrics;
import com.marketplace.search.domain.valueobjects.SearchQuery;
import com.marketplace.search.domain.valueobjects.SearchResult;
import com.marketplace.search.domain.valueobjects.SearchSort;
import com.marketplace.search.domain.valueobjects.UserContext;
import com.marketplace.search.domain.valueobjects.UserLocation;

/**
 * Mapper para conversão entre DTOs de busca e Value Objects do domínio
 */
@Component
public class SearchMapper {

  public SearchQuery toDomain(SearchRequestDTO dto) {
    Category category = dto.categoryId() != null ? new Category(dto.categoryId(), "Unknown", null, "unknown") : null;

    List<SearchFilter> filters = dto.filters() != null ? dto.filters().stream()
        .map(this::mapFilter)
        .collect(Collectors.toList()) : List.of();

    SearchSort sort = mapSort(dto.sort());

    return new SearchQuery(
        dto.query(),
        category,
        filters,
        sort,
        dto.offset(),
        dto.limit());
  }

  public UserContext mapUserContext(UserContextDTO dto) {
    if (dto == null)
      return null;

    UserLocation location = dto.location() != null ? mapUserLocation(dto.location())
        : UserLocation.of("BR", "SP", "São Paulo");

    return new UserContext(
        dto.userId(),
        location,
        dto.preferredCategories() != null ? dto.preferredCategories() : Set.of(),
        dto.purchaseHistory() != null ? dto.purchaseHistory() : Set.of(),
        dto.searchHistory() != null ? dto.searchHistory() : Set.of(),
        dto.viewHistory() != null ? dto.viewHistory() : Set.of(),
        null // UserProfile seria mapeado separadamente
    );
  }

  public SearchResultDTO toDTO(SearchResult result) {
    List<ProductDTO> productDTOs = result.products().stream()
        .map(product -> {
          ProductMapper productMapper = new ProductMapper();
          return productMapper.toDTO(product);
        })
        .collect(Collectors.toList());

    return SearchResultDTO.builder()
        .products(productDTOs)
        .totalCount(result.totalCount())
        .pageSize(result.pageSize())
        .pageNumber(result.pageNumber())
        .totalPages(result.getTotalPages())
        .hasNextPage(result.hasNextPage())
        .hasPreviousPage(result.hasPreviousPage())
        .executionTimeMs(result.executionTime().toMillis())
        .metrics(mapMetricsToDTO(result.metrics()))
        .build();

  }

  private SearchFilter mapFilter(SearchFilterDTO dto) {
    FilterType type = mapFilterType(dto.type());
    return new SearchFilter(dto.name(), type, dto.values());
  }

  private FilterType mapFilterType(String type) {
    if (type == null)
      return FilterType.TERM;

    try {
      return FilterType.valueOf(type.toUpperCase());
    } catch (IllegalArgumentException e) {
      return FilterType.TERM;
    }
  }

  private SearchSort mapSort(String sort) {
    if (sort == null)
      return SearchSort.RELEVANCE;

    try {
      return SearchSort.valueOf(sort.toUpperCase());
    } catch (IllegalArgumentException e) {
      return SearchSort.RELEVANCE;
    }
  }

  private UserLocation mapUserLocation(UserLocationDTO dto) {
    return new UserLocation(
        dto.country() != null ? dto.country() : "BR",
        dto.state() != null ? dto.state() : "SP",
        dto.city() != null ? dto.city() : "São Paulo",
        dto.zipCode(),
        dto.latitude(),
        dto.longitude());
  }

  private SearchMetricsDTO mapMetricsToDTO(SearchMetrics metrics) {
    return SearchMetricsDTO.builder()
        .queriesPerSecond(metrics.getQueriesPerSecond())
        .averageScore(metrics.getAverageScore())
        .indexedDocuments(metrics.getIndexedDocuments())
        .indexSize(metrics.getIndexSize())
        .usedCache(metrics.isUsedCache())
        .shardInfo(metrics.getShardInfo())
        .build();
  }
}