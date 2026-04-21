package com.marketplace.search.search.application.mappers;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.marketplace.search.search.application.queries.BrandData;
import com.marketplace.search.search.application.queries.CategoryData;
import com.marketplace.search.search.application.queries.ProductData;
import com.marketplace.search.search.application.queries.SellerData;
import com.marketplace.search.search.application.queries.SellerReputationData;
import com.marketplace.search.search.domain.entities.Category;
import com.marketplace.search.search.domain.entities.Product;
import com.marketplace.search.search.domain.entities.Seller;
import com.marketplace.search.search.domain.valueobjects.Brand;
import com.marketplace.search.search.domain.valueobjects.ProductId;
import com.marketplace.search.search.domain.valueobjects.ProductInfo;
import com.marketplace.search.search.domain.valueobjects.ProductMetrics;
import com.marketplace.search.search.domain.valueobjects.ProductStatus;
import com.marketplace.search.search.domain.valueobjects.SellerReputation;
import com.marketplace.search.search.domain.valueobjects.SellerStatus;
import com.marketplace.search.search.domain.valueobjects.SellerType;

/**
 * Mapper para conversão entre Product e ProductDTO
 */
@Component("ProductMapperApplication")
public class ProductMapper {

  public ProductData toDTO(Product product) {
    return ProductData.builder()
        .id(product.getId().getValue())
        .title(product.getInfo().getTitle())
        .description(product.getInfo().getDescription())
        .price(product.getInfo().getPrice())
        .currency(product.getInfo().getCurrency())
        .category(mapCategoryToDTO(product.getInfo().getCategory()))
        .brand(mapBrandToDTO(product.getInfo().getBrand()))
        .seller(mapSellerToDTO(product.getSeller()))
        .images(product.getInfo().getImages())
        .attributes(product.getInfo().getAttributes())
        .tags(product.getInfo().getTags())
        .stockQuantity(product.getMetrics().stockQuantity())
        .isActive(product.getStatus().isActive())
        .rankingDebug(mapRankingDebugToDTO(product.getRankingDebug()))
        .build();
  }

  private com.marketplace.search.search.application.queries.RankingDebugData mapRankingDebugToDTO(com.marketplace.search.search.domain.valueobjects.RankingDebug rankingDebug) {
    if (rankingDebug == null) {
      return null;
    }
    return new com.marketplace.search.search.application.queries.RankingDebugData(rankingDebug.finalScore(), rankingDebug.features());
  }

  private CategoryData mapCategoryToDTO(Category category) {
    return new CategoryData(
        category.getId(),
        category.getName(),
        category.getParentId(),
        category.getPath());
  }

  private BrandData mapBrandToDTO(Brand brand) {
    return new BrandData(brand.id(), brand.name(), brand.description());
  }

  private SellerData mapSellerToDTO(Seller seller) {
    return SellerData.builder()
        .id(seller.getId())
        .name(seller.getName())
        .type(seller.getType().name())
        .status(seller.getStatus().name())
        .reputation(mapSellerReputationToDTO(seller.getReputation()))
        .memberSince(seller.getMemberSince() != null ? seller.getMemberSince().toString() : null)
        .build();
  }

  private SellerReputationData mapSellerReputationToDTO(SellerReputation reputation) {
    return new SellerReputationData(
        reputation.getScore(),
        reputation.getTotalReviews(),
        reputation.getPositiveReviews(),
        reputation.getNeutralReviews(),
        reputation.getNegativeReviews(),
        reputation.getCancellationRate(),
        reputation.getDeliveryPerformance());
  }
}

