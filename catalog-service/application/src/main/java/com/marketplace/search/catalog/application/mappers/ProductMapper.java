package com.marketplace.search.catalog.application.mappers;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.marketplace.search.catalog.application.commands.ProductCommand;
import com.marketplace.search.catalog.application.payloads.BrandPaylod;
import com.marketplace.search.catalog.application.payloads.CategoryPayload;
import com.marketplace.search.catalog.application.payloads.ProductMetricsPayload;
import com.marketplace.search.catalog.application.payloads.ProductPayload;
import com.marketplace.search.catalog.application.payloads.SellerPayload;
import com.marketplace.search.catalog.application.payloads.SellerReputationPaylod;
import com.marketplace.search.catalog.domain.entities.Category;
import com.marketplace.search.catalog.domain.entities.Product;
import com.marketplace.search.catalog.domain.entities.Seller;
import com.marketplace.search.catalog.domain.valueobjects.Brand;
import com.marketplace.search.catalog.domain.valueobjects.ProductId;
import com.marketplace.search.catalog.domain.valueobjects.ProductInfo;
import com.marketplace.search.catalog.domain.valueobjects.ProductMetrics;
import com.marketplace.search.catalog.domain.valueobjects.ProductStatus;
import com.marketplace.search.catalog.domain.valueobjects.SellerReputation;
import com.marketplace.search.catalog.domain.valueobjects.SellerStatus;
import com.marketplace.search.catalog.domain.valueobjects.SellerType;

/**
 * Mapper para conversão entre Product e ProductDTO
 */
@Component("ProductMapperApplication")
public class ProductMapper {

  private static final Logger logger = LoggerFactory.getLogger(ProductMapper.class);

  public Product toDomain(ProductCommand dto) {
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

    ProductStatus status = ProductStatus.active(
        dto.stockQuantity() != null && dto.stockQuantity() > 0);

    Instant now = Instant.now();

    // Usa stockQuantity do comando quando productMetrics for null
    int stockQuantity = dto.stockQuantity() != null ? dto.stockQuantity() : 0;

    return Product.builder()
        .id(id)
        .info(info)
        .seller(seller)
        .metrics(mapProductMetrics(dto.productMetrics(), stockQuantity))
        .status(status)
        .createdAt(now)
        .updatedAt(now)
        .build();
  }

  public ProductPayload toDTO(Product product) {
    return ProductPayload.builder()
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

  private Category mapCategory(CategoryPayload dto) {
    return new Category(dto.id(), dto.name(), dto.parentId(), dto.path());
  }

  private CategoryPayload mapCategoryToDTO(Category category) {
    return new CategoryPayload(
        category.getId(),
        category.getName(),
        category.getParentId(),
        category.getPath());
  }

  private Brand mapBrand(BrandPaylod dto) {
    return new Brand(dto.id(), dto.name(), dto.description());
  }

  private BrandPaylod mapBrandToDTO(Brand brand) {
    return new BrandPaylod(brand.id(), brand.name(), brand.description());
  }

  private Seller mapSeller(SellerPayload dto) {
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

  private SellerPayload mapSellerToDTO(Seller seller) {
    return SellerPayload.builder()
        .id(seller.getId())
        .name(seller.getName())
        .type(seller.getType().name())
        .status(seller.getStatus().name())
        .reputation(mapSellerReputationToDTO(seller.getReputation()))
        .memberSince(seller.getMemberSince() != null ? seller.getMemberSince().toString() : null)
        .build();
  }

  private SellerReputation mapSellerReputation(SellerReputationPaylod dto) {
    return new SellerReputation(
        dto.score() != null ? dto.score() : 5.0,
        dto.totalReviews() != null ? dto.totalReviews() : 0,
        dto.positiveReviews() != null ? dto.positiveReviews() : 0,
        dto.neutralReviews() != null ? dto.neutralReviews() : 0,
        dto.negativeReviews() != null ? dto.negativeReviews() : 0,
        dto.cancellationRate() != null ? dto.cancellationRate() : 0.0,
        dto.deliveryPerformance() != null ? dto.deliveryPerformance() : 1.0);
  }

  private SellerReputationPaylod mapSellerReputationToDTO(SellerReputation reputation) {
    return new SellerReputationPaylod(
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

  private ProductMetrics mapProductMetrics(ProductMetricsPayload dto, int defaultStockQuantity) {
    if (dto == null) {
      logger.info("ProductMetrics is null, creating default ProductMetrics");
      // Cria ProductMetrics padrão com valores iniciais quando não fornecido
      return ProductMetrics.builder()
          .totalViews(0)
          .totalSales(0)
          .totalReviews(0)
          .averageRating(0.0)
          .stockQuantity(defaultStockQuantity)
          .conversionRate(0.0)
          .lastSale(null)
          .lastView(null)
          .popularity(0)
          .quality(0.0)
          .ctr(0.0)
          .build();
    }

    return ProductMetrics.builder()
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