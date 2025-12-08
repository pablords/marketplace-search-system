package com.marketplace.search.indexing.domain.valueobjects;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.marketplace.search.indexing.domain.entities.Category;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Value Object contendo informações básicas do produto
 */
public class ProductInfo {

  @NotNull
  @NotBlank
  private final String title;

  @NotNull
  private final String description;

  @NotNull
  @Positive
  private final BigDecimal price;

  @NotNull
  private final String currency;

  @NotNull
  private final Category category;

  @NotNull
  private final Brand brand;

  @NotNull
  private final List<String> images;

  @NotNull
  private final Set<String> attributes;

  @NotNull
  private final Set<String> tags;

  public ProductInfo(String title, String description, BigDecimal price,
      String currency, Category category, Brand brand,
      List<String> images, Set<String> attributes, Set<String> tags) {
    this.title = validateTitle(title);
    this.description = Objects.requireNonNull(description, "Description cannot be null");
    this.price = validatePrice(price);
    this.currency = validateCurrency(currency);
    this.category = Objects.requireNonNull(category, "Category cannot be null");
    this.brand = Objects.requireNonNull(brand, "Brand cannot be null");
    this.images = Objects.requireNonNull(images, "Images cannot be null");
    this.attributes = Objects.requireNonNull(attributes, "Attributes cannot be null");
    this.tags = Objects.requireNonNull(tags, "Tags cannot be null");
  }

  private String validateTitle(String title) {
    if (title == null || title.trim().isEmpty()) {
      throw new IllegalArgumentException("Title cannot be null or empty");
    }
    if (title.length() > 200) {
      throw new IllegalArgumentException("Title cannot exceed 200 characters");
    }
    return title.trim();
  }

  private BigDecimal validatePrice(BigDecimal price) {
    if (price == null) {
      throw new IllegalArgumentException("Price cannot be null");
    }
    if (price.compareTo(BigDecimal.ZERO) <= 0) {
      throw new IllegalArgumentException("Price must be positive");
    }
    return price;
  }

  private String validateCurrency(String currency) {
    if (currency == null || currency.trim().isEmpty()) {
      throw new IllegalArgumentException("Currency cannot be null or empty");
    }
    if (currency.length() != 3) {
      throw new IllegalArgumentException("Currency must be a 3-letter ISO code");
    }
    return currency.toUpperCase();
  }

  /**
   * Verifica se o produto contém palavras-chave bloqueadas
   */
  public boolean hasBlockedKeywords() {
    Set<String> blockedWords = Set.of("replica", "fake", "contrabando", "pirata");
    String fullText = (title + " " + description).toLowerCase();

    return blockedWords.stream().anyMatch(fullText::contains);
  }

  /**
   * Obtém todas as palavras-chave para indexação
   */
  public Set<String> getSearchableKeywords() {
    String normalized = (title + " " + description + " " + brand.name())
      .toLowerCase()
      .replaceAll("[^a-z0-9\\s]", " ");

    String[] tokens = normalized.split("\\s+");

    Set<String> result = Arrays.stream(tokens)
      .filter(s -> s != null && !s.isBlank())
      .map(String::trim)
      .collect(Collectors.toCollection(java.util.HashSet::new));

    result.addAll(tags);
    result.addAll(attributes);

    return result;
  }

  // Getters
  public String getTitle() {
    return title;
  }

  public String getDescription() {
    return description;
  }

  public BigDecimal getPrice() {
    return price;
  }

  public String getCurrency() {
    return currency;
  }

  public Category getCategory() {
    return category;
  }

  public Brand getBrand() {
    return brand;
  }

  public List<String> getImages() {
    return images;
  }

  public Set<String> getAttributes() {
    return attributes;
  }

  public Set<String> getTags() {
    return tags;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    ProductInfo that = (ProductInfo) o;
    return Objects.equals(title, that.title) &&
        Objects.equals(description, that.description) &&
        Objects.equals(price, that.price) &&
        Objects.equals(currency, that.currency) &&
        Objects.equals(category, that.category) &&
        Objects.equals(brand, that.brand);
  }

  @Override
  public int hashCode() {
    return Objects.hash(title, description, price, currency, category, brand);
  }

  @Override
  public String toString() {
    return "ProductInfo{" +
        "title='" + title + '\'' +
        ", price=" + price + " " + currency +
        ", category=" + category +
        ", brand=" + brand +
        '}';
  }
}