package com.marketplace.search.application.mappers;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.marketplace.search.application.dto.BrandDTO;
import com.marketplace.search.application.dto.CategoryDTO;
import com.marketplace.search.application.dto.ProductDTO;
import com.marketplace.search.application.dto.SellerDTO;
import com.marketplace.search.application.dto.SellerReputationDTO;
import com.marketplace.search.domain.entities.Product;
import com.marketplace.search.domain.valueobjects.Brand;
import com.marketplace.search.domain.valueobjects.Category;
import com.marketplace.search.domain.valueobjects.ProductId;
import com.marketplace.search.domain.valueobjects.ProductInfo;
import com.marketplace.search.domain.valueobjects.ProductMetrics;
import com.marketplace.search.domain.valueobjects.ProductStatus;
import com.marketplace.search.domain.valueobjects.Seller;
import com.marketplace.search.domain.valueobjects.SellerReputation;

/**
 * Mapper para conversão entre Product e ProductDTO
 */
@Component
public class ProductMapper {

  public Product toDomain(ProductDTO dto) {
  ProductId id = ProductId.from(dto.getId());

  ProductInfo info = new ProductInfo(
    dto.getTitle(),
    dto.getDescription() != null ? dto.getDescription() : "",
    dto.getPrice(),
    dto.getCurrency(),
    mapCategory(dto.getCategory()),
    mapBrand(dto.getBrand()),
    dto.getImages() != null ? dto.getImages() : List.of(),
    dto.getAttributes() != null ? dto.getAttributes() : Set.of(),
    dto.getTags() != null ? dto.getTags() : Set.of());

  Seller seller = mapSeller(dto.getSeller());

  ProductMetrics metrics = new ProductMetrics(
    0, // totalViews - seria obtido de outra fonte
    0, // totalSales
    0, // totalReviews
    0.0, // averageRating
    dto.getStockQuantity() != null ? dto.getStockQuantity() : 0,
    0.0, // conversionRate
    null, // lastSale
    null // lastView
  );

  ProductStatus status = ProductStatus.active(
    dto.getStockQuantity() != null && dto.getStockQuantity() > 0);

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
    ProductDTO dto = new ProductDTO();

    dto.setId(product.getId().getValue());
    dto.setTitle(product.getInfo().getTitle());
    dto.setDescription(product.getInfo().getDescription());
    dto.setPrice(product.getInfo().getPrice());
    dto.setCurrency(product.getInfo().getCurrency());

    dto.setCategory(mapCategoryToDTO(product.getInfo().getCategory()));
    dto.setBrand(mapBrandToDTO(product.getInfo().getBrand()));
    dto.setSeller(mapSellerToDTO(product.getSeller()));

    dto.setImages(product.getInfo().getImages());
    dto.setAttributes(product.getInfo().getAttributes());
    dto.setTags(product.getInfo().getTags());

    dto.setStockQuantity(product.getMetrics().getStockQuantity());
    dto.setIsActive(product.getStatus().isActive());

    return dto;
  }

  private Category mapCategory(CategoryDTO dto) {
    return new Category(dto.getId(), dto.getName(), dto.getParentId(), dto.getPath());
  }

  private CategoryDTO mapCategoryToDTO(Category category) {
    return new CategoryDTO(
        category.getId(),
        category.getName(),
        category.getParentId(),
        category.getPath());
  }

  private Brand mapBrand(BrandDTO dto) {
    return new Brand(dto.getId(), dto.getName(), dto.getDescription());
  }

  private BrandDTO mapBrandToDTO(Brand brand) {
    return new BrandDTO(brand.getId(), brand.getName(), brand.getDescription());
  }

  private Seller mapSeller(SellerDTO dto) {
    SellerReputation reputation = dto.getReputation() != null ? mapSellerReputation(dto.getReputation())
        : new SellerReputation(5.0, 0, 0, 0, 0, 0.0, 1.0);

    return new Seller(
        dto.getId(),
        dto.getName(),
        mapSellerType(dto.getType()),
        reputation,
        mapSellerStatus(dto.getStatus()),
        dto.getMemberSince() != null ? Instant.parse(dto.getMemberSince()) : null);
  }

  private SellerDTO mapSellerToDTO(Seller seller) {
    SellerDTO dto = new SellerDTO();
    dto.setId(seller.getId());
    dto.setName(seller.getName());
    dto.setType(seller.getType().name());
    dto.setStatus(seller.getStatus().name());
    dto.setReputation(mapSellerReputationToDTO(seller.getReputation()));
    if (seller.getMemberSince() != null) {
      dto.setMemberSince(seller.getMemberSince().toString());
    }
    return dto;
  }

  private SellerReputation mapSellerReputation(SellerReputationDTO dto) {
    return new SellerReputation(
        dto.getScore() != null ? dto.getScore() : 5.0,
        dto.getTotalReviews() != null ? dto.getTotalReviews() : 0,
        dto.getPositiveReviews() != null ? dto.getPositiveReviews() : 0,
        dto.getNeutralReviews() != null ? dto.getNeutralReviews() : 0,
        dto.getNegativeReviews() != null ? dto.getNegativeReviews() : 0,
        dto.getCancellationRate() != null ? dto.getCancellationRate() : 0.0,
        dto.getDeliveryPerformance() != null ? dto.getDeliveryPerformance() : 1.0);
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

  private com.marketplace.search.domain.valueobjects.SellerType mapSellerType(String type) {
    if (type == null)
      return com.marketplace.search.domain.valueobjects.SellerType.REGULAR;

    try {
      return com.marketplace.search.domain.valueobjects.SellerType.valueOf(type.toUpperCase());
    } catch (IllegalArgumentException e) {
      return com.marketplace.search.domain.valueobjects.SellerType.REGULAR;
    }
  }

  private com.marketplace.search.domain.valueobjects.SellerStatus mapSellerStatus(String status) {
    if (status == null)
      return com.marketplace.search.domain.valueobjects.SellerStatus.ACTIVE;

    try {
      return com.marketplace.search.domain.valueobjects.SellerStatus.valueOf(status.toUpperCase());
    } catch (IllegalArgumentException e) {
      return com.marketplace.search.domain.valueobjects.SellerStatus.ACTIVE;
    }
  }
}