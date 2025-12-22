package com.marketplace.search.interfaces.rest.commands.mappers;

import org.springframework.stereotype.Component;

import com.marketplace.search.application.commands.ProductCommand;
import com.marketplace.search.application.queries.BrandData;
import com.marketplace.search.application.queries.CategoryData;
import com.marketplace.search.application.queries.SellerData;
import com.marketplace.search.application.queries.SellerReputationData;
import com.marketplace.search.interfaces.rest.dtos.CategoryDTO;
import com.marketplace.search.interfaces.rest.dtos.ProductDTO;
import com.marketplace.search.interfaces.rest.dtos.SellerDTO;
import com.marketplace.search.interfaces.rest.dtos.SellerReputationDTO;

@Component("ProductMapperRest")
public class ProductMapper {
  public ProductCommand toCommand(ProductDTO dto) {

    SellerData seller = mapSeller(dto.seller());

    return ProductCommand.builder()
        .id(dto.id())
        .title(dto.title())
        .description(dto.description())
        .price(dto.price())
        .currency(dto.currency())
        .category(mapCategory(dto.category()))
        .brand(dto.brand() != null ? new BrandData(
            dto.brand().id(), dto.brand().name(), dto.brand().description()) : null)
        .seller(seller)
        .images(dto.images())
        .attributes(dto.attributes())
        .build();
  }

  private CategoryData mapCategory(CategoryDTO dto) {
    return new CategoryData(dto.id(), dto.name(), dto.parentId(), dto.path());
  }

  private SellerData mapSeller(SellerDTO dto) {
    SellerReputationData reputation = dto.reputation() != null ? mapSellerReputation(dto.reputation())
        : new SellerReputationData(5.0, 0, 0, 0, 0, 0.0, 1.0);

    return new SellerData(
        dto.id(),
        dto.name(),
        dto.type(),
        reputation,
        dto.status(),
        dto.memberSince());
  }

  private SellerReputationData mapSellerReputation(SellerReputationDTO dto) {
    return new SellerReputationData(
        dto.score() != null ? dto.score() : 5.0,
        dto.totalReviews() != null ? dto.totalReviews() : 0,
        dto.positiveReviews() != null ? dto.positiveReviews() : 0,
        dto.neutralReviews() != null ? dto.neutralReviews() : 0,
        dto.negativeReviews() != null ? dto.negativeReviews() : 0,
        dto.cancellationRate() != null ? dto.cancellationRate() : 0.0,
        dto.deliveryPerformance() != null ? dto.deliveryPerformance() : 1.0);
  }

}
