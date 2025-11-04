package com.marketplace.search.domain.entities;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.marketplace.search.domain.valueobjects.Brand;
import com.marketplace.search.domain.valueobjects.ProductId;
import com.marketplace.search.domain.valueobjects.ProductInfo;
import com.marketplace.search.domain.valueobjects.ProductMetrics;
import com.marketplace.search.domain.valueobjects.ProductStatus;
import com.marketplace.search.domain.valueobjects.SellerReputation;
import com.marketplace.search.domain.valueobjects.SellerStatus;
import com.marketplace.search.domain.valueobjects.SellerType;

public class ProductTest {
  Product product;
  ProductId productId;
  ProductInfo productInfo;
  Category category;
  Brand brand;
  Seller seller;
  SellerReputation reputation;
  SellerStatus sellerStatus;
  SellerType type;
  ProductMetrics metrics;
  ProductStatus status;

  @BeforeEach
  void setup() {
    productId = new ProductId("prod-001");
    category = new Category("cat-001", "CategoryName", "Category Description", "/cat-001/");
    brand = new Brand("brand-001", "BrandName", "Brand Description");
    reputation = new SellerReputation(4.5, 1000, 850, 100, 50, 1.0, 0.95);
    sellerStatus = SellerStatus.ACTIVE;
    type = SellerType.REGULAR;
    seller = new Seller("seller-001", "SellerName", type, reputation, sellerStatus, Instant.now().minusSeconds(86400));
    productInfo = new ProductInfo("Sample Product", "This is a sample product description.",
        new BigDecimal("99.99"), "USD", category, brand,
        List.of("image1.jpg", "image2.jpg"),
        Set.of("attribute1", "attribute2"),
        Set.of("tag1", "tag2"));

    metrics = new ProductMetrics(10000, 500, 200, 4.3, 150, 0.05, Instant.now().minusSeconds(3600),
        Instant.now().minusSeconds(1800));
    status = ProductStatus.active(true);
    product = Product.builder()
        .id(productId)
        .info(productInfo)
        .seller(seller)
        .metrics(metrics)
        .status(status)
        .createdAt(Instant.now())
        .updatedAt(Instant.now())
        .build();
  }

  @Test
  public void shouldCreateProduct() {

    assertEquals(product.getId(), productId);
    assertEquals(product.getInfo().getTitle(), productInfo.getTitle());
    assertEquals(product.getInfo().getDescription(), productInfo.getDescription());
    assertEquals(product.getInfo().getPrice(), productInfo.getPrice());
    assertEquals(product.getInfo().getCurrency(), productInfo.getCurrency());
  }
}
