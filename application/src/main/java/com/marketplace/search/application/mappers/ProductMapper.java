package com.marketplace.search.application.mappers;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.marketplace.search.domain.entities.Category;
import com.marketplace.search.domain.entities.Product;
import com.marketplace.search.domain.entities.Seller;
import com.marketplace.search.domain.valueobjects.Brand;
import com.marketplace.search.domain.valueobjects.ProductId;
import com.marketplace.search.domain.valueobjects.ProductInfo;
import com.marketplace.search.domain.valueobjects.ProductMetrics;
import com.marketplace.search.domain.valueobjects.ProductStatus;
import com.marketplace.search.domain.valueobjects.SellerReputation;
import com.marketplace.search.domain.valueobjects.SellerStatus;
import com.marketplace.search.domain.valueobjects.SellerType;
import com.marketplace.search.interfaces.rest.dtos.BrandDTO;
import com.marketplace.search.interfaces.rest.dtos.CategoryDTO;
import com.marketplace.search.interfaces.rest.dtos.ProductDTO;
import com.marketplace.search.interfaces.rest.dtos.SellerDTO;
import com.marketplace.search.interfaces.rest.dtos.SellerReputationDTO;

/**
 * Mapper para conversão entre Product e ProductDTO
 */
@Component
public class ProductMapper {

  public Product toDomain(ProductDTO dto) {
    ProductId id = ProductId.from(dto.id());

    ProductInfo info = new ProductInfo(
        dto.title(),
        dto.description() != null ? dto.description() : "",
        dto.price(),
        dto.currency(),
        mapCategory(dto.category()),
        mapBrand(dto.brand()),
        dto.images() != null ? dto.images() : List.of(),
        dto.attributes() != null ? dto.attributes() : Set.of(),
        dto.tags() != null ? dto.tags() : Set.of());

    Seller seller = mapSeller(dto.seller());

    ProductMetrics metrics = new ProductMetrics(
        0, // totalViews - seria obtido de outra fonte
        0, // totalSales
        0, // totalReviews
        0.0, // averageRating
        dto.stockQuantity() != null ? dto.stockQuantity() : 0,
        0.0, // conversionRate
        null, // lastSale
        null // lastView
    );

    ProductStatus status = ProductStatus.active(
        dto.stockQuantity() != null && dto.stockQuantity() > 0);

    Instant now = Instant.now();

    return Product.builder()
        .id(id)
        .info(info)
        .seller(seller)
        .metrics(metrics)
        .status(status)
        .createdAt(now)
        .updatedAt(now)
        .build();
  }

  public ProductDTO toDTO(Product product) {
    return ProductDTO.builder()
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
        .build();
  }

  private Category mapCategory(CategoryDTO dto) {
    return new Category(dto.id(), dto.name(), dto.parentId(), dto.path());
  }

  private CategoryDTO mapCategoryToDTO(Category category) {
    return new CategoryDTO(
        category.getId(),
        category.getName(),
        category.getParentId(),
        category.getPath());
  }

  private Brand mapBrand(BrandDTO dto) {
    return new Brand(dto.id(), dto.name(), dto.description());
  }

  private BrandDTO mapBrandToDTO(Brand brand) {
    return new BrandDTO(brand.id(), brand.name(), brand.description());
  }

  private Seller mapSeller(SellerDTO dto) {
    SellerReputation reputation = dto.reputation() != null ? mapSellerReputation(dto.reputation())
        : new SellerReputation(5.0, 0, 0, 0, 0, 0.0, 1.0);

    return new Seller(
        dto.id(),
        dto.name(),
        mapSellerType(dto.type()),
        reputation,
        mapSellerStatus(dto.status()),
        dto.memberSince() != null ? Instant.parse(dto.memberSince()) : null);
  }

  private SellerDTO mapSellerToDTO(Seller seller) {
    return SellerDTO.builder()
        .id(seller.getId())
        .name(seller.getName())
        .type(seller.getType().name())
        .status(seller.getStatus().name())
        .reputation(mapSellerReputationToDTO(seller.getReputation()))
        .memberSince(seller.getMemberSince() != null ? seller.getMemberSince().toString() : null)
        .build();
  }

  private SellerReputation mapSellerReputation(SellerReputationDTO dto) {
    return new SellerReputation(
        dto.score() != null ? dto.score() : 5.0,
        dto.totalReviews() != null ? dto.totalReviews() : 0,
        dto.positiveReviews() != null ? dto.positiveReviews() : 0,
        dto.neutralReviews() != null ? dto.neutralReviews() : 0,
        dto.negativeReviews() != null ? dto.negativeReviews() : 0,
        dto.cancellationRate() != null ? dto.cancellationRate() : 0.0,
        dto.deliveryPerformance() != null ? dto.deliveryPerformance() : 1.0);
  }

  private SellerReputationDTO mapSellerReputationToDTO(SellerReputation reputation) {
    return new SellerReputationDTO(
        reputation.getScore(),
        reputation.getTotalReviews(),
        reputation.getPositiveReviews(),
        reputation.getNeutralReviews(),
        reputation.getNegativeReviews(),
        reputation.getCancellationRate(),
        reputation.getDeliveryPerformance());
  }

  private SellerType mapSellerType(String type) {
    if (type == null)
      return SellerType.REGULAR;

    try {
      return SellerType.valueOf(type.toUpperCase());
    } catch (IllegalArgumentException e) {
      return SellerType.REGULAR;
    }
  }

  private SellerStatus mapSellerStatus(String status) {
    if (status == null)
      return SellerStatus.ACTIVE;

    try {
      return SellerStatus.valueOf(status.toUpperCase());
    } catch (IllegalArgumentException e) {
      return SellerStatus.ACTIVE;
    }
  }
}