package com.marketplace.search.search.domain.entities;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

import com.marketplace.search.search.domain.valueobjects.ProductId;
import com.marketplace.search.search.domain.valueobjects.ProductInfo;
import com.marketplace.search.search.domain.valueobjects.ProductMetrics;
import com.marketplace.search.search.domain.valueobjects.ProductStatus;
import com.marketplace.search.search.domain.valueobjects.SearchQuery;
import com.marketplace.search.search.domain.valueobjects.SearchScore;
import com.marketplace.search.search.domain.valueobjects.UserContext;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * Entidade principal representando um produto no sistema de busca.
 * Agregada root do domínio de produto.
 */
public class Product {

  @NotNull
  private final ProductId id;

  @NotNull
  @Valid
  private final ProductInfo info;

  @NotNull
  @Valid
  private final Seller seller;

  @NotNull
  @Valid
  private final ProductMetrics metrics;

  @NotNull
  @Valid
  private final ProductStatus status;

  @NotNull
  private final Instant createdAt;

  @NotNull
  private final Instant updatedAt;

  private Product(Builder builder) {
    this.id = Objects.requireNonNull(builder.id, "Product ID cannot be null");
    this.info = Objects.requireNonNull(builder.info, "Product info cannot be null");
    this.seller = Objects.requireNonNull(builder.seller, "Seller cannot be null");
    this.metrics = Objects.requireNonNull(builder.metrics, "Metrics cannot be null");
    this.status = Objects.requireNonNull(builder.status, "Status cannot be null");
    this.createdAt = Objects.requireNonNull(builder.createdAt, "Created at cannot be null");
    this.updatedAt = Objects.requireNonNull(builder.updatedAt, "Updated at cannot be null");
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private ProductId id;
    private ProductInfo info;
    private Seller seller;
    private ProductMetrics metrics;
    private ProductStatus status;
    private Instant createdAt;
    private Instant updatedAt;

    public Builder id(ProductId id) {
      this.id = id;
      return this;
    }

    public Builder info(ProductInfo info) {
      this.info = info;
      return this;
    }

    public Builder seller(Seller seller) {
      this.seller = seller;
      return this;
    }

    public Builder metrics(ProductMetrics metrics) {
      this.metrics = metrics;
      return this;
    }

    public Builder status(ProductStatus status) {
      this.status = status;
      return this;
    }

    public Builder createdAt(Instant createdAt) {
      this.createdAt = createdAt;
      return this;
    }

    public Builder updatedAt(Instant updatedAt) {
      this.updatedAt = updatedAt;
      return this;
    }

    public Product build() {
      return new Product(this);
    }
  }

  /**
   * Calcula o score de relevância do produto baseado em vários fatores
   */
  public SearchScore calculateRelevanceScore(SearchQuery query, UserContext userContext) {
    double textScore = calculateTextRelevance(query);
    double popularityScore = metrics.getPopularityScore();
    double sellerScore = seller.getReputationScore();
    double freshnessPenalty = calculateFreshnessPenalty();
    

    // Aplicar personalização baseada no contexto do usuário
    double personalizationBoost = calculatePersonalizationBoost(userContext);

    double finalScore = (textScore * 0.4) +
        (popularityScore * 0.25) +
        (sellerScore * 0.2) +
        (personalizationBoost * 0.15) -
        freshnessPenalty;

    return new SearchScore(Math.max(0, Math.min(1, finalScore)));
  }

  /**
   * Calcula similaridade de preço entre dois produtos
   */
  private static double calculatePriceSimilarity(java.math.BigDecimal price1, java.math.BigDecimal price2) {
    double p1 = price1.doubleValue();
    double p2 = price2.doubleValue();

    double ratio = Math.min(p1, p2) / Math.max(p1, p2);
    return ratio; // Quanto mais próximos os preços, maior a similaridade
  }

  public static double calculateAttributeSimilarity(java.util.Set<String> attrs1, java.util.Set<String> attrs2) {
    java.util.Set<String> intersection = new java.util.HashSet<>(attrs1);
    intersection.retainAll(attrs2);

    java.util.Set<String> union = new java.util.HashSet<>(attrs1);
    union.addAll(attrs2);

    return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
  }

  /**
   * Valida se um produto deve aparecer nos resultados de busca
   */
  public static boolean isProductSearchable(Product product, UserContext userContext) {

    if (!product.isSearchable()) {
      return false;
    }

    if (product.getSeller().isSuspended()) {
      return false;
    }

    // Verificações baseadas na localização do usuário
    if (userContext != null && userContext.location() != null) {
      // Aqui poderia ter regras de entrega por região, etc.
    }

    return true;
  }

  /**
   * Calcula similaridade entre dois produtos
   */
  public static double calculateProductSimilarity(Product product1, Product product2) {
    double categoryScore = Category.calculateCategorySimilarity(product1.getInfo().getCategory(),
        product2.getInfo().getCategory());

    double brandScore = product1.getInfo().getBrand().equals(product2.getInfo().getBrand()) ? 1.0 : 0.0;

    double priceScore = calculatePriceSimilarity(product1.getInfo().getPrice(),
        product2.getInfo().getPrice());

    double attributeScore = calculateAttributeSimilarity(product1.getInfo().getAttributes(),
        product2.getInfo().getAttributes());

    return (categoryScore * 0.4) + (brandScore * 0.3) + (priceScore * 0.2) + (attributeScore * 0.1);
  }

  /**
   * Verifica se o produto está disponível para busca
   */
  @JsonIgnore
  public boolean isSearchable() {
    return status.isActive() &&
        status.hasStock() &&
        seller.isActive() &&
        !isBlocked();
  }

  /**
   * Verifica se o produto está bloqueado por regras de negócio
   */
  @JsonIgnore
  public boolean isBlocked() {
    return status.isSuspended() ||
        seller.isSuspended() ||
        info.hasBlockedKeywords();
  }

  private double calculateTextRelevance(SearchQuery query) {
    // Implementação simplificada - seria mais complexa em produção
    String searchTerms = query.terms().toLowerCase();
    String title = info.getTitle().toLowerCase();
    String description = info.getDescription().toLowerCase();

    double titleMatch = calculateStringMatch(searchTerms, title) * 2.0; // Título tem peso maior
    double descriptionMatch = calculateStringMatch(searchTerms, description);

    return (titleMatch + descriptionMatch) / 3.0;
  }

  private double calculateStringMatch(String query, String text) {
    String[] queryWords = query.split("\\s+");
    int matches = 0;

    for (String word : queryWords) {
      if (text.contains(word)) {
        matches++;
      }
    }

    return queryWords.length > 0 ? (double) matches / queryWords.length : 0.0;
  }

  private double calculateFreshnessPenalty() {
    long daysSinceUpdate = Duration.between(updatedAt, Instant.now()).toDays();
    return daysSinceUpdate > 30 ? 0.1 : 0.0; // Penalidade por produtos não atualizados
  }

  private double calculatePersonalizationBoost(UserContext userContext) {
    if (userContext == null)
      return 0.0;

    // Boost baseado em histórico de compras e navegação
    if (userContext.hasInterestInCategory(info.getCategory())) {
      return 0.2;
    }

    if (userContext.hasPreviousPurchaseFromSeller(seller.getId())) {
      return 0.15;
    }

    return 0.0;
  }

  // Getters
  public ProductId getId() {
    return id;
  }

  public ProductInfo getInfo() {
    return info;
  }

  public Seller getSeller() {
    return seller;
  }

  public ProductMetrics getMetrics() {
    return metrics;
  }

  public ProductStatus getStatus() {
    return status;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    Product product = (Product) o;
    return Objects.equals(id, product.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }

  @Override
  public String toString() {
    return "Product [id=" + id + ", info=" + info + ", seller=" + seller + ", metrics=" + metrics + ", status=" + status
        + ", createdAt=" + createdAt + ", updatedAt=" + updatedAt + "]";
  }
}
