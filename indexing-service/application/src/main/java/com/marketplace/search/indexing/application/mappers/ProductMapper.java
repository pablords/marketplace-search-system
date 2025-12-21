package com.marketplace.search.indexing.application.mappers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.marketplace.search.indexing.application.commands.ProductCommand;
import com.marketplace.search.indexing.application.dtos.BrandDTO;
import com.marketplace.search.indexing.application.dtos.CategoryDTO;
import com.marketplace.search.indexing.application.dtos.ProductDTO;
import com.marketplace.search.indexing.application.dtos.SellerDTO;
import com.marketplace.search.indexing.application.dtos.SellerReputationDTO;
import com.marketplace.search.indexing.application.handlers.payloads.ProductPayload;
import com.marketplace.search.indexing.domain.entities.Category;
import com.marketplace.search.indexing.domain.entities.Product;
import com.marketplace.search.indexing.domain.entities.Seller;
import com.marketplace.search.indexing.domain.valueobjects.Brand;
import com.marketplace.search.indexing.domain.valueobjects.ProductId;
import com.marketplace.search.indexing.domain.valueobjects.ProductInfo;
import com.marketplace.search.indexing.domain.valueobjects.ProductMetrics;
import com.marketplace.search.indexing.domain.valueobjects.ProductStatus;
import com.marketplace.search.indexing.domain.valueobjects.SellerReputation;
import com.marketplace.search.indexing.domain.valueobjects.SellerStatus;
import com.marketplace.search.indexing.domain.valueobjects.SellerType;
/**
 * Mapper para conversão entre Product e ProductDTO
 */
@Component("ProductMapperApplication")
public class ProductMapper {

  public ProductCommand mapProductPayloadToDTO(ProductPayload data) {
    // Category
    CategoryDTO category = new CategoryDTO(
        data.getCategoryId(),
        data.getCategoryName(),
        null,
        data.getCategoryPath());

    // Brand
    BrandDTO brand = new BrandDTO(
        data.getBrandId(),
        data.getBrandName(),
        data.getDescription());

    // Seller
    // Seller reputation (if available in payload)
    Double sellerScore = null;
    try {
      if (data.getSellerScore() != null && !data.getSellerScore().isBlank())
        sellerScore = Double.valueOf(data.getSellerScore());
    } catch (NumberFormatException ignored) {
    }

    Double cancellationRate = null;
    try {
      if (data.getSellerCancellationRate() != null && !data.getSellerCancellationRate().isBlank())
        cancellationRate = Double.valueOf(data.getSellerCancellationRate());
    } catch (NumberFormatException ignored) {
    }

    Double deliveryPerformance = null;
    try {
      if (data.getSellerDeliveryPerformance() != null && !data.getSellerDeliveryPerformance().isBlank())
        deliveryPerformance = Double.valueOf(data.getSellerDeliveryPerformance());
    } catch (NumberFormatException ignored) {
    }

    com.marketplace.search.indexing.application.dtos.SellerReputationDTO reputation = null;
    if (sellerScore != null || data.getSellerTotalReviews() != null || data.getSellerPositiveReviews() != null
        || data.getSellerNeutralReviews() != null || data.getSellerNegativeReviews() != null
        || cancellationRate != null || deliveryPerformance != null) {
      reputation = com.marketplace.search.indexing.application.dtos.SellerReputationDTO.builder()
          .score(sellerScore)
          .totalReviews(data.getSellerTotalReviews())
          .positiveReviews(data.getSellerPositiveReviews())
          .neutralReviews(data.getSellerNeutralReviews())
          .negativeReviews(data.getSellerNegativeReviews())
          .cancellationRate(cancellationRate)
          .deliveryPerformance(deliveryPerformance)
          .build();
    }

    SellerDTO seller = SellerDTO.builder()
        .id(data.getSellerId())
        .name(data.getSellerName())
        .reputation(reputation)
        .type(data.getSellerType())
        .status(data.getSellerStatus())
        .build();

    // Product usando builder
    return ProductCommand.builder()
        .id(data.getId())
        .title(data.getTitle())
        .description(data.getDescription())
        .price(data.getPrice() != null ? new BigDecimal(data.getPrice()) : null)
        .currency(data.getCurrency())
        .category(category)
        .brand(brand)
        .seller(seller)
        .stockQuantity(data.getAvailableQuantity())
        .condition(data.getCondition())
        .isActive("ACTIVE".equals(data.getStatus()))
        .totalSold(data.getTotalSold())
        .reviewCount(data.getReviewCount())
        .averageRating(data.getAverageRating())
        .ctr(data.getCtr())
        .build();
  }

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

    // Mapear métricas enriquecidas do payload
    // totalViews não está disponível no ProductPayload, manter 0
    int totalViews = 0;
    
    // totalSales vem de totalSold no payload
    int totalSales = dto.totalSold() != null ? dto.totalSold() : 0;
    
    // totalReviews vem de reviewCount no payload
    int totalReviews = dto.reviewCount() != null ? dto.reviewCount() : 0;
    
    // averageRating vem do payload (string, precisa converter)
    double averageRating = 0.0;
    try {
      if (dto.averageRating() != null && !dto.averageRating().isBlank()) {
        averageRating = Double.parseDouble(dto.averageRating());
      }
    } catch (NumberFormatException e) {
      // Manter 0.0 se não conseguir converter
    }
    
    // stockQuantity vem do payload
    int stockQuantity = dto.stockQuantity() != null ? dto.stockQuantity() : 0;
    
    // conversionRate vem de ctr no payload (string, precisa converter)
    double conversionRate = 0.0;
    try {
      if (dto.ctr() != null && !dto.ctr().isBlank()) {
        conversionRate = Double.parseDouble(dto.ctr());
      }
    } catch (NumberFormatException e) {
      // Manter 0.0 se não conseguir converter
    }
    
    // lastSale e lastView não estão disponíveis no ProductPayload atual
    // Seriam obtidos de ProductMetricsPayload, mas não estão sendo mapeados ainda
    Instant lastSale = null;
    Instant lastView = null;

    ProductMetrics metrics = new ProductMetrics(
        totalViews,
        totalSales,
        totalReviews,
        averageRating,
        stockQuantity,
        conversionRate,
        lastSale,
        lastView
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