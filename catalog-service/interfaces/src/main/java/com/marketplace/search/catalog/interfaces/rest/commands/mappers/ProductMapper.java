package com.marketplace.search.catalog.interfaces.rest.commands.mappers;

import org.springframework.stereotype.Component;

import com.marketplace.search.catalog.application.commands.ProductCommand;
import com.marketplace.search.catalog.application.dtos.BrandDTO;
import com.marketplace.search.catalog.interfaces.rest.dtos.CategoryDTO;
import com.marketplace.search.catalog.interfaces.rest.dtos.ProductDTO;
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
        .brand(dto.brand() != null ? new BrandDTO(
            dto.brand().id(), dto.brand().name(), dto.brand().description()) : null)
        .seller(seller)
        .images(dto.images())
        .attributes(dto.attributes())
        .stockQuantity(dto.stockQuantity())
        .build();
  }

  private com.marketplace.search.catalog.application.dtos.CategoryDTO mapCategory(CategoryDTO dto) {
    return new com.marketplace.search.catalog.application.dtos.CategoryDTO(dto.id(), dto.name(), dto.parentId(), dto.path());
  }

  private com.marketplace.search.catalog.application.dtos.SellerDTO mapSeller(SellerDTO dto) {
    var reputation = dto.reputation() != null ? mapSellerReputation(dto.reputation())
        : new com.marketplace.search.catalog.application.dtos.SellerReputationDTO(5.0, 0, 0, 0, 0, 0.0, 1.0);

    return new com.marketplace.search.catalog.application.dtos.SellerDTO(
        dto.id(),
        dto.name(),
        dto.type(),
        reputation,
        dto.status(),
        dto.memberSince());
  }

  private com.marketplace.search.catalog.application.dtos.SellerReputationDTO mapSellerReputation(SellerReputationDTO dto) {
    return new com.marketplace.search.catalog.application.dtos.SellerReputationDTO(
        dto.score() != null ? dto.score() : 5.0,
        dto.totalReviews() != null ? dto.totalReviews() : 0,
        dto.positiveReviews() != null ? dto.positiveReviews() : 0,
        dto.neutralReviews() != null ? dto.neutralReviews() : 0,
        dto.negativeReviews() != null ? dto.negativeReviews() : 0,
        dto.cancellationRate() != null ? dto.cancellationRate() : 0.0,
        dto.deliveryPerformance() != null ? dto.deliveryPerformance() : 1.0);
  }

}
