package com.marketplace.search.domain.entities;

import java.time.Instant;
import java.util.Objects;

import com.marketplace.search.domain.valueobjects.ProductId;
import com.marketplace.search.domain.valueobjects.ProductInfo;
import com.marketplace.search.domain.valueobjects.ProductMetrics;
import com.marketplace.search.domain.valueobjects.ProductStatus;
import com.marketplace.search.domain.valueobjects.SearchQuery;
import com.marketplace.search.domain.valueobjects.SearchScore;
import com.marketplace.search.domain.valueobjects.Seller;
import com.marketplace.search.domain.valueobjects.UserContext;

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

  public Product(ProductId id, ProductInfo info, Seller seller,
      ProductMetrics metrics, ProductStatus status,
      Instant createdAt, Instant updatedAt) {
    this.id = Objects.requireNonNull(id, "Product ID cannot be null");
    this.info = Objects.requireNonNull(info, "Product info cannot be null");
    this.seller = Objects.requireNonNull(seller, "Seller cannot be null");
    this.metrics = Objects.requireNonNull(metrics, "Metrics cannot be null");
    this.status = Objects.requireNonNull(status, "Status cannot be null");
    this.createdAt = Objects.requireNonNull(createdAt, "Created at cannot be null");
    this.updatedAt = Objects.requireNonNull(updatedAt, "Updated at cannot be null");
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
   * Verifica se o produto está disponível para busca
   */
  public boolean isSearchable() {
    return status.isActive() &&
        status.hasStock() &&
        seller.isActive() &&
        !isBlocked();
  }

  /**
   * Verifica se o produto está bloqueado por regras de negócio
   */
  public boolean isBlocked() {
    return status.isSuspended() ||
        seller.isSuspended() ||
        info.hasBlockedKeywords();
  }

  private double calculateTextRelevance(SearchQuery query) {
    // Implementação simplificada - seria mais complexa em produção
    String searchTerms = query.getTerms().toLowerCase();
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
    long daysSinceUpdate = java.time.Duration.between(updatedAt, Instant.now()).toDays();
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
    return "Product{" +
        "id=" + id +
        ", title='" + info.getTitle() + '\'' +
        ", seller=" + seller.getName() +
        ", status=" + status +
        '}';
  }
}