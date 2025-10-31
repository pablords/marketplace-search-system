package com.marketplace.search.infrastructure.elasticsearch.mappers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.marketplace.search.domain.entities.Product;
import com.marketplace.search.domain.valueobjects.Brand;
import com.marketplace.search.domain.valueobjects.Category;
import com.marketplace.search.domain.valueobjects.ProductId;
import com.marketplace.search.domain.valueobjects.ProductInfo;
import com.marketplace.search.domain.valueobjects.ProductMetrics;
import com.marketplace.search.domain.valueobjects.ProductStatus;
import com.marketplace.search.domain.valueobjects.Seller;
import com.marketplace.search.domain.valueobjects.SellerReputation;
import com.marketplace.search.infrastructure.elasticsearch.documents.BrandDocument;
import com.marketplace.search.infrastructure.elasticsearch.documents.CategoryDocument;
import com.marketplace.search.infrastructure.elasticsearch.documents.ProductDocument;
import com.marketplace.search.infrastructure.elasticsearch.documents.ProductMetricsDocument;
import com.marketplace.search.infrastructure.elasticsearch.documents.ProductStatusDocument;
import com.marketplace.search.infrastructure.elasticsearch.documents.SellerDocument;

/**
 * Mapper entre entidades de domínio e documentos do Elasticsearch
 */
@Component
public class ElasticsearchProductMapper {

  private static final Logger logger = LoggerFactory.getLogger(ElasticsearchProductMapper.class);

  public ProductDocument toDocument(Product product) {
    logger.debug("Produto {}", product);
    ProductDocument document = new ProductDocument();

    document.setId(product.getId().getValue());
    document.setTitle(product.getInfo().getTitle());
    document.setDescription(product.getInfo().getDescription());
    document.setPrice(product.getInfo().getPrice());
    document.setCurrency(product.getInfo().getCurrency());

    document.setCategory(mapCategoryToDocument(product.getInfo().getCategory()));
    document.setBrand(mapBrandToDocument(product.getInfo().getBrand()));
    document.setSeller(mapSellerToDocument(product.getSeller()));

    document.setImages(product.getInfo().getImages());
    document.setAttributes(product.getInfo().getAttributes());
    document.setTags(product.getInfo().getTags());

    document.setMetrics(mapMetricsToDocument(product.getMetrics()));
    document.setStatus(mapStatusToDocument(product.getStatus(), product.getMetrics().getStockQuantity()));

    document.setCreatedAt(product.getCreatedAt());
    document.setUpdatedAt(product.getUpdatedAt());

    // Campos derivados para otimização de busca
    document.setSearchableText(buildSearchableText(product));
    document.setPriceRange(calculatePriceRange(product.getInfo().getPrice()));
    document.setPopularityScore(product.getMetrics().getPopularityScore());

    return document;
  }

  public Product toDomain(ProductDocument document) {
    ProductId id = ProductId.from(document.getId());

    ProductInfo info = new ProductInfo(
        document.getTitle(),
        document.getDescription(),
        document.getPrice(),
        document.getCurrency(),
        mapCategoryToDomain(document.getCategory()),
        mapBrandToDomain(document.getBrand()),
        document.getImages(),
        document.getAttributes(),
        document.getTags());

    Seller seller = mapSellerToDomain(document.getSeller());
    ProductMetrics metrics = mapMetricsToDomain(document.getMetrics());
    ProductStatus status = mapStatusToDomain(document.getStatus());

    return new Product(
        id, info, seller, metrics, status,
        document.getCreatedAt(),
        document.getUpdatedAt());
  }

  private CategoryDocument mapCategoryToDocument(Category category) {
    CategoryDocument doc = new CategoryDocument();
    doc.setId(category.getId());
    doc.setName(category.getName());
    doc.setPath(category.getPath());
    doc.setParentId(category.getParentId());
    return doc;
  }

  private Category mapCategoryToDomain(CategoryDocument document) {
    return new Category(
        document.getId(),
        document.getName(),
        document.getParentId(),
        document.getPath());
  }

  private BrandDocument mapBrandToDocument(Brand brand) {
    BrandDocument doc = new BrandDocument();
    doc.setId(brand.getId());
    doc.setName(brand.getName());
    doc.setDescription(brand.getDescription());
    return doc;
  }

  private Brand mapBrandToDomain(BrandDocument document) {
    return new Brand(
        document.getId(),
        document.getName(),
        document.getDescription());
  }

  private SellerDocument mapSellerToDocument(Seller seller) {
    SellerDocument doc = new SellerDocument();
    doc.setId(seller.getId());
    doc.setName(seller.getName());
    doc.setType(seller.getType().name());
    doc.setReputationScore(seller.getReputationScore());
    doc.setStatus(seller.getStatus().name());
    return doc;
  }

  private Seller mapSellerToDomain(SellerDocument document) {
    // Recrear reputação com valores padrão - em um cenário real seria mais completo
    SellerReputation reputation = new SellerReputation(
        document.getReputationScore() > 0 ? document.getReputationScore() : 5.0,
        0, 0, 0, 0, 0.0, 1.0);

    return new Seller(
        document.getId(),
        document.getName(),
        mapSellerType(document.getType()),
        reputation,
        mapSellerStatus(document.getStatus()),
        null);
  }

  private ProductMetricsDocument mapMetricsToDocument(ProductMetrics metrics) {
    logger.debug("Metricas de Produto {}", metrics);
    ProductMetricsDocument doc = new ProductMetricsDocument();
    doc.setTotalViews((long) metrics.getTotalViews());
    doc.setTotalSales((long) metrics.getTotalSales());
    doc.setTotalReviews((long) metrics.getTotalReviews());
    doc.setAverageRating(metrics.getAverageRating());
    doc.setStockQuantity(metrics.getStockQuantity());
    doc.setConversionRate(metrics.getConversionRate());
    return doc;
  }

  private ProductMetrics mapMetricsToDomain(ProductMetricsDocument document) {
    return new ProductMetrics(
        document.getTotalViews() != null ? Math.toIntExact(document.getTotalViews()) : 0,
        document.getTotalSales() != null ? Math.toIntExact(document.getTotalSales()) : 0,
        document.getTotalReviews() != null ? Math.toIntExact(document.getTotalReviews()) : 0,
        document.getAverageRating() != null ? document.getAverageRating() : 0.0,
        document.getStockQuantity(),
        document.getConversionRate(),
        null,
        null);
  }

  private ProductStatusDocument mapStatusToDocument(ProductStatus status, int stockQuantity) {
    logger.debug("Estoque {}", stockQuantity);
    ProductStatusDocument doc = new ProductStatusDocument();
    doc.setIsActive(status.isActive());
    doc.setHasStock(stockQuantity > 0 ? true : false);
    doc.setIsSuspended(status.isSuspended());
    return doc;
  }

  private ProductStatus mapStatusToDomain(ProductStatusDocument document) {
    boolean isActive = document.getIsActive() != null ? document.getIsActive() : false;
    boolean hasStock = document.getHasStock() != null ? document.getHasStock() : false;
    boolean isSuspended = document.getIsSuspended() != null ? document.getIsSuspended() : false;

    if (isSuspended) {
      return ProductStatus.suspended("Suspended");
    } else if (!isActive) {
      return ProductStatus.inactive();
    } else {
      return ProductStatus.active(hasStock);
    }
  }

  private String buildSearchableText(Product product) {
    StringBuilder searchableText = new StringBuilder();

    searchableText.append(product.getInfo().getTitle()).append(" ");
    searchableText.append(product.getInfo().getDescription()).append(" ");
    searchableText.append(product.getInfo().getBrand().getName()).append(" ");
    searchableText.append(product.getInfo().getCategory().getName()).append(" ");

    // Adicionar atributos e tags
    product.getInfo().getAttributes().forEach(attr -> searchableText.append(attr).append(" "));
    product.getInfo().getTags().forEach(tag -> searchableText.append(tag).append(" "));

    return searchableText.toString().trim().toLowerCase();
  }

  private String calculatePriceRange(java.math.BigDecimal price) {
    double priceValue = price.doubleValue();

    if (priceValue < 50)
      return "0-50";
    if (priceValue < 100)
      return "50-100";
    if (priceValue < 200)
      return "100-200";
    if (priceValue < 500)
      return "200-500";
    if (priceValue < 1000)
      return "500-1000";

    return "1000+";
  }

  private com.marketplace.search.domain.valueobjects.SellerType mapSellerType(String type) {
    try {
      return com.marketplace.search.domain.valueobjects.SellerType.valueOf(type);
    } catch (Exception e) {
      return com.marketplace.search.domain.valueobjects.SellerType.REGULAR;
    }
  }

  private com.marketplace.search.domain.valueobjects.SellerStatus mapSellerStatus(String status) {
    try {
      return com.marketplace.search.domain.valueobjects.SellerStatus.valueOf(status);
    } catch (Exception e) {
      return com.marketplace.search.domain.valueobjects.SellerStatus.ACTIVE;
    }
  }
}