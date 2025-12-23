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
import com.marketplace.search.indexing.application.handlers.payloads.BrandPayload;
import com.marketplace.search.indexing.application.handlers.payloads.CategoryPayload;
import com.marketplace.search.indexing.application.handlers.payloads.ProductMetricsPayload;
import com.marketplace.search.indexing.application.handlers.payloads.ProductPayload;
import com.marketplace.search.indexing.application.handlers.payloads.SellerPayload;
import com.marketplace.search.indexing.application.services.ProductMetricsCacheService;
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

  private final ProductMetricsCacheService metricsCacheService;

  public ProductMapper(ProductMetricsCacheService metricsCacheService) {
    this.metricsCacheService = metricsCacheService;
  }

  public ProductCommand mapProductPayloadToDTO(ProductPayload data) {
    // Category
    CategoryPayload category = new CategoryPayload();
    category.setId(data.getCategoryId());
    category.setName(data.getCategoryName());
    category.setPath(data.getCategoryPath());
    category.setParentId(null);
    category.setCreatedAt(null);

    // Brand
    BrandPayload brand = new BrandPayload();
    brand.setId(data.getBrandId());
    brand.setName(data.getBrandName());
    brand.setDescription(data.getBrandDescription());
    brand.setCreatedAt(null);

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

    

    SellerPayload seller = new SellerPayload();
    seller.setId(data.getSellerId());
    seller.setName(data.getSellerName());
    seller.setType(data.getSellerType());
    seller.setStatus(data.getSellerStatus());
    seller.setScore(data.getSellerScore());
    seller.setTotalReviews(data.getSellerTotalReviews());
    seller.setPositiveReviews(data.getSellerPositiveReviews());
    seller.setNeutralReviews(data.getSellerNeutralReviews());
    seller.setNegativeReviews(data.getSellerNegativeReviews());
    seller.setCancellationRate(data.getSellerCancellationRate());
    seller.setDeliveryPerformance(data.getSellerDeliveryPerformance());
    seller.setUpdatedAt(null);

    // ProductMetrics - tentar obter do cache primeiro, senão criar a partir dos dados enriquecidos do ProductPayload
    ProductMetricsPayload productMetrics = metricsCacheService.getMetrics(data.getId());
    
    // Se não encontrou no cache, criar a partir dos dados do ProductPayload enriquecido
    if (productMetrics == null) {
      productMetrics = new ProductMetricsPayload();
      productMetrics.setProductId(data.getId());
      productMetrics.setTotalSales(data.getTotalSold());
      productMetrics.setTotalReviews(data.getReviewCount());
      productMetrics.setCtr(data.getCtr());
      productMetrics.setAverageRating(data.getAverageRating());
      productMetrics.setStockQuantity(data.getAvailableQuantity());
      productMetrics.setLastSale(null);
      productMetrics.setLastView(null);
      productMetrics.setUpdatedAt(data.getUpdatedAt());
    } else {
      // Se encontrou no cache, garantir que os dados mais recentes do ProductPayload sejam usados
      // (caso o ProductPayload tenha dados mais atualizados que o cache)
      if (data.getTotalSold() != null) {
        productMetrics.setTotalSales(data.getTotalSold());
      }
      if (data.getReviewCount() != null) {
        productMetrics.setTotalReviews(data.getReviewCount());
      }
      if (data.getCtr() != null) {
        productMetrics.setCtr(data.getCtr());
      }
      if (data.getAverageRating() != null) {
        productMetrics.setAverageRating(data.getAverageRating());
      }
      if (data.getAvailableQuantity() != null) {
        productMetrics.setStockQuantity(data.getAvailableQuantity());
      }
      if (data.getUpdatedAt() != null) {
        productMetrics.setUpdatedAt(data.getUpdatedAt());
      }
    }

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
        .productMetrics(productMetrics)
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
    // Verificar se productMetrics não é null antes de acessar
    var productMetrics = dto.productMetrics();
    
    // totalViews não está disponível no ProductPayload, manter 0
    int totalViews = 0;
    
    // totalSales vem de totalSold no payload
    int totalSales = productMetrics != null && productMetrics.getTotalSales() != null ? productMetrics.getTotalSales() : 0;
    
    // totalReviews vem de reviewCount no payload
    int totalReviews = productMetrics != null && productMetrics.getTotalReviews() != null ? productMetrics.getTotalReviews() : 0;
    
    // averageRating vem do payload (string, precisa converter)
    double averageRating = 0.0;
    try {
      if (productMetrics != null && productMetrics.getAverageRating() != null && !productMetrics.getAverageRating().isBlank()) {
        averageRating = Double.parseDouble(productMetrics.getAverageRating());
      }
    } catch (NumberFormatException e) {
      // Manter 0.0 se não conseguir converter
    }
    
    // stockQuantity vem do payload
    int stockQuantity = dto.stockQuantity() != null ? dto.stockQuantity() : 0;
    
    // ctr vem de ctr no payload (string, precisa converter)
    double conversionRate = 0.0;
    try {
      if (productMetrics != null && productMetrics.getCtr() != null && !productMetrics.getCtr().isBlank()) {
        conversionRate = Double.parseDouble(productMetrics.getCtr());
      }
    } catch (NumberFormatException e) {
      // Manter 0.0 se não conseguir converter
    }
    
    // lastSale e lastView vêm do ProductMetricsPayload (timestamps em Long)
    Instant lastSale = null;
    Instant lastView = null;
    if (productMetrics != null) {
      if (productMetrics.getLastSale() != null) {
        lastSale = Instant.ofEpochMilli(productMetrics.getLastSale());
      }
      if (productMetrics.getLastView() != null) {
        lastView = Instant.ofEpochMilli(productMetrics.getLastView());
      }
    }

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

  private Category mapCategory(CategoryPayload dto) {
    return new Category(dto.getId(), dto.getName(), dto.getParentId(), dto.getPath());
  }

  private CategoryDTO mapCategoryToDTO(Category category) {
    return new CategoryDTO(
        category.getId(),
        category.getName(),
        category.getParentId(),
        category.getPath());
  }

  private Brand mapBrand(BrandPayload dto) {
    return new Brand(dto.getId(), dto.getName(), dto.getDescription());
  }

  private BrandDTO mapBrandToDTO(Brand brand) {
    return new BrandDTO(brand.id(), brand.name(), brand.description());
  }

  private Seller mapSeller(SellerPayload dto) {
    double score = dto.getScore() != null ? Double.parseDouble(dto.getScore()) : 0.0;
    int totalReviews = dto.getTotalReviews() != null ? dto.getTotalReviews() : 0;
    int positiveReviews = dto.getPositiveReviews() != null ? dto.getPositiveReviews() : 0;
    int neutralReviews = dto.getNeutralReviews() != null ? dto.getNeutralReviews() : 0;
    int negativeReviews = dto.getNegativeReviews() != null ? dto.getNegativeReviews() : 0;
    double cancellationRate = dto.getCancellationRate() != null ? Double.parseDouble(dto.getCancellationRate()) : 0.0;
    double deliveryPerformance = dto.getDeliveryPerformance() != null ? Double.parseDouble(dto.getDeliveryPerformance()) : 1.0;
    
    var reputation = new SellerReputation(score, totalReviews, positiveReviews, neutralReviews, negativeReviews, cancellationRate, deliveryPerformance);
    Instant updatedAt = dto.getUpdatedAt() != null ? Instant.ofEpochMilli(dto.getUpdatedAt()) : null;
    return new Seller(dto.getId(), dto.getName(), mapSellerType(dto.getType()), reputation, mapSellerStatus(dto.getStatus()), updatedAt);
  }

  private SellerDTO mapSellerToDTO(Seller seller) {
    return SellerDTO.builder()
        .id(seller.getId())
        .name(seller.getName())
        .type(seller.getType().name())
        .status(seller.getStatus().name())
        .reputation(mapSellerReputationToDTO(seller.getReputation()))
        .build();
  }

  private SellerReputation mapSellerReputation(SellerReputationDTO dto) {
    // Tratar valores null como 0 (valores padrão)
    // O script de geração agora garante consistência: totalReviews = positive + neutral + negative
    int positiveReviews = dto.positiveReviews() != null ? dto.positiveReviews() : 0;
    int neutralReviews = dto.neutralReviews() != null ? dto.neutralReviews() : 0;
    int negativeReviews = dto.negativeReviews() != null ? dto.negativeReviews() : 0;
    int totalReviews = dto.totalReviews() != null ? dto.totalReviews() : 0;
    
    return new SellerReputation(
        dto.score() != null ? dto.score() : 5.0,
        totalReviews,
        positiveReviews,
        neutralReviews,
        negativeReviews,
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