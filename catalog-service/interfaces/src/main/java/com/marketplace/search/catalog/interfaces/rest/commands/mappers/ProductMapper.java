package com.marketplace.search.catalog.interfaces.rest.commands.mappers;

import org.springframework.stereotype.Component;

import com.marketplace.search.catalog.application.commands.ProductCommand;
import com.marketplace.search.catalog.application.payloads.BrandPaylod;
import com.marketplace.search.catalog.application.payloads.CategoryPayload;
import com.marketplace.search.catalog.application.payloads.ProductMetricsPayload;
import com.marketplace.search.catalog.application.payloads.SellerPayload;
import com.marketplace.search.catalog.application.payloads.SellerReputationPaylod;
import com.marketplace.search.catalog.interfaces.rest.dtos.BrandDTO;
import com.marketplace.search.catalog.interfaces.rest.dtos.CategoryDTO;
import com.marketplace.search.catalog.interfaces.rest.dtos.ProductDTO;
import com.marketplace.search.catalog.interfaces.rest.dtos.ProductMetricsDTO;
import com.marketplace.search.catalog.interfaces.rest.dtos.SellerDTO;
import com.marketplace.search.catalog.interfaces.rest.dtos.SellerReputationDTO;

@Component("ProductMapperRest")
public class ProductMapper {
  public ProductCommand toCommand(ProductDTO dto) {

    var seller = mapSeller(dto.seller());


    return ProductCommand.builder()
        .id(dto.id())
        .title(dto.title())
        .description(dto.description())
        .price(dto.price())
        .currency(dto.currency())
        .category(mapCategory(dto.category()))
        .brand(mapBrand(dto.brand()))
        .seller(seller)
        .images(dto.images())
        .attributes(dto.attributes())
        .stockQuantity(dto.stockQuantity())
        .productMetrics(mapProductMetrics(dto.productMetrics()))
        .build();
  }

  private CategoryPayload mapCategory(CategoryDTO dto) {
    return new CategoryPayload(dto.id(), dto.name(), dto.parentId(),
        dto.path());
  }

  private SellerPayload mapSeller(SellerDTO dto) {
    var reputation = dto.reputation() != null ? mapSellerReputation(dto.reputation())
        : new SellerReputationPaylod(5.0, 0, 0, 0, 0, 0.0, 1.0);

    return new SellerPayload(
        dto.id(),
        dto.name(),
        dto.type(),
        reputation,
        dto.status(),
        dto.memberSince());
  }

  private SellerReputationPaylod mapSellerReputation(
      SellerReputationDTO dto) {
    return new SellerReputationPaylod(
        dto.score() != null ? dto.score() : 5.0,
        dto.totalReviews() != null ? dto.totalReviews() : 0,
        dto.positiveReviews() != null ? dto.positiveReviews() : 0,
        dto.neutralReviews() != null ? dto.neutralReviews() : 0,
        dto.negativeReviews() != null ? dto.negativeReviews() : 0,
        dto.cancellationRate() != null ? dto.cancellationRate() : 0.0,
        dto.deliveryPerformance() != null ? dto.deliveryPerformance() : 1.0);
  }

  private BrandPaylod mapBrand(BrandDTO dto) {
    return new BrandPaylod(
        dto.id(), dto.name(), dto.description());
  }

  private ProductMetricsPayload mapProductMetrics(ProductMetricsDTO dto) {
    if (dto == null) {
      return null;
    }

    return ProductMetricsPayload.builder()
        .totalViews(dto.totalViews())
        .totalSales(dto.totalSales())
        .totalReviews(dto.totalReviews())
        .averageRating(dto.averageRating())
        .stockQuantity(dto.stockQuantity())
        .lastSale(dto.lastSale())
        .lastView(dto.lastView())
        .popularity(dto.popularity())
        .quality(dto.quality())
        .ctr(dto.ctr())
        .build();
  }

}
