package com.marketplace.search.interfaces.rest.queries.mappers;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.marketplace.search.application.queries.BrandData;
import com.marketplace.search.application.queries.CategoryData;
import com.marketplace.search.application.queries.ProductData;
import com.marketplace.search.application.queries.SearchFilterData;
import com.marketplace.search.application.queries.SearchMetricsData;
import com.marketplace.search.application.queries.SearchRequestQuery;
import com.marketplace.search.application.queries.SearchResultQuery;
import com.marketplace.search.application.queries.SellerData;
import com.marketplace.search.application.queries.SellerReputationData;
import com.marketplace.search.application.queries.UserContextData;
import com.marketplace.search.application.queries.UserLocationData;
import com.marketplace.search.interfaces.rest.dtos.BrandDTO;
import com.marketplace.search.interfaces.rest.dtos.CategoryDTO;
import com.marketplace.search.interfaces.rest.dtos.ProductDTO;
import com.marketplace.search.interfaces.rest.dtos.SearchFilterDTO;
import com.marketplace.search.interfaces.rest.dtos.SearchMetricsDTO;
import com.marketplace.search.interfaces.rest.dtos.SearchRequestDTO;
import com.marketplace.search.interfaces.rest.dtos.SearchResultDTO;
import com.marketplace.search.interfaces.rest.dtos.SellerDTO;
import com.marketplace.search.interfaces.rest.dtos.SellerReputationDTO;
import com.marketplace.search.interfaces.rest.dtos.UserContextDTO;
import com.marketplace.search.interfaces.rest.dtos.UserLocationDTO;


@Component("SearchMapperRest")
public class SearchMapper {

  public SearchRequestQuery toqQuery(SearchRequestDTO searchRequest) {
    List<SearchFilterData> filters = searchRequest.filters() != null ? searchRequest.filters().stream()
        .map(this::mapFilter)
        .collect(Collectors.toList()) : List.of();

    return SearchRequestQuery.builder()
        .query(searchRequest.query())
        .categoryId(searchRequest.categoryId())
        .filters(filters)
        .sort(searchRequest.sort())
        .offset(searchRequest.offset())
        .limit(searchRequest.limit())
        .userContext(mapUserContext(searchRequest.userContext()))
        .build();
  }

  public SearchResultDTO toDto(SearchResultQuery query) {
    return SearchResultDTO.builder()
        .products(query.products().stream()
            .map(this::mapProduct)
            .collect(Collectors.toList()))
        .totalCount(query.totalCount())
        .pageSize(query.pageSize())
        .pageNumber(query.pageNumber())
        .totalPages(query.totalPages())
        .hasNextPage(query.hasNextPage())
        .hasPreviousPage(query.hasPreviousPage())
        .executionTimeMs(query.executionTimeMs())
        .metrics(mapMetricsToDTO(query.metrics()))
        .build();
  }

  private ProductDTO mapProduct(ProductData product) {
    return ProductDTO.builder()
        .id(product.id())
        .title(product.title())
        .description(product.description())
        .price(product.price())
        .currency(product.currency())
        .category(mapCategoryToDTO(product.category()))
        .brand(mapBrandToDTO(product.brand()))
        .seller(mapSellerToDTO(product.seller()))
        .images(product.images())
        .attributes(product.attributes())
        // Mapear outros campos conforme necessário
        .build();
  }

  private CategoryDTO mapCategoryToDTO(CategoryData category) {
    return new CategoryDTO(category.id(), category.name(), category.parentId(), category.path());
  }

  private BrandDTO mapBrandToDTO(BrandData brand) {
    return new BrandDTO(brand.id(), brand.name(), brand.description());
  }

  private SellerDTO mapSellerToDTO(SellerData seller) {
    return SellerDTO.builder()
        .id(seller.id())
        .name(seller.name())
        .type(seller.type())
        .reputation(mapSellerReputationToDTO(seller.reputation()))
        .status(seller.status())
        .memberSince(seller.memberSince())
        .build();
  }

  private SellerReputationDTO mapSellerReputationToDTO(SellerReputationData dto) {
    return new SellerReputationDTO(
        dto.score() != null ? dto.score() : 5.0,
        dto.totalReviews() != null ? dto.totalReviews() : 0,
        dto.positiveReviews() != null ? dto.positiveReviews() : 0,
        dto.neutralReviews() != null ? dto.neutralReviews() : 0,
        dto.negativeReviews() != null ? dto.negativeReviews() : 0,
        dto.cancellationRate() != null ? dto.cancellationRate() : 0.0,
        dto.deliveryPerformance() != null ? dto.deliveryPerformance() : 1.0);
  }

    private SearchMetricsDTO mapMetricsToDTO(SearchMetricsData metrics) {
    return SearchMetricsDTO.builder()
        .queriesPerSecond(metrics.queriesPerSecond())
        .averageScore(metrics.averageScore())
        .indexedDocuments(metrics.indexedDocuments())
        .indexSize(metrics.indexSize())
        .usedCache(metrics.usedCache())
        .shardInfo(metrics.shardInfo())
        .build();
  }

  private SearchFilterData mapFilter(SearchFilterDTO dto) {
    return new SearchFilterData(dto.name(), dto.type(), dto.values());
  }

  private UserContextData mapUserContext(UserContextDTO dto) {
    if (dto == null)
      return null;

    UserLocationData location = dto.location() != null ? mapUserLocation(dto.location())
        : UserLocationData.of("BR", "SP", "São Paulo");

    return UserContextData.builder()
        .userId(dto.userId())
        .location(location)
        .preferredCategories(dto.preferredCategories())
        .purchaseHistory(dto.purchaseHistory())
        .searchHistory(dto.searchHistory())
        .viewHistory(dto.viewHistory())
        .build();
  }

  private UserLocationData mapUserLocation(UserLocationDTO dto) {
    return new UserLocationData(
        dto.country() != null ? dto.country() : "BR",
        dto.state() != null ? dto.state() : "SP",
        dto.city() != null ? dto.city() : "São Paulo",
        dto.zipCode(),
        dto.latitude(),
        dto.longitude());
  }

}
