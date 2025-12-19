package com.marketplace.search.search.domain.valueobjects;

import java.util.Objects;

/**
 * Value Object representando o status de um produto
 */
public class ProductStatus {

  private final ProductState state;

  private final boolean hasStock;

  private final boolean isActive;

  private final boolean isSuspended;

  private final String suspensionReason;

  public ProductStatus(ProductState state, boolean hasStock, boolean isActive,
      boolean isSuspended, String suspensionReason) {
    this.state = Objects.requireNonNull(state, "Product state cannot be null");
    this.hasStock = hasStock;
    this.isActive = isActive;
    this.isSuspended = isSuspended;
    this.suspensionReason = suspensionReason;

    validateState();
  }

  private void validateState() {
    if (isSuspended && isActive) {
      throw new IllegalArgumentException("Product cannot be both active and suspended");
    }

    if (isSuspended && suspensionReason == null) {
      throw new IllegalArgumentException("Suspension reason is required when product is suspended");
    }
  }

  public static ProductStatus active(boolean hasStock) {
    return new ProductStatus(ProductState.ACTIVE, hasStock, true, false, null);
  }

  public static ProductStatus inactive() {
    return new ProductStatus(ProductState.INACTIVE, false, false, false, null);
  }

  public static ProductStatus suspended(String reason) {
    return new ProductStatus(ProductState.SUSPENDED, false, false, true, reason);
  }

  public static ProductStatus outOfStock() {
    return new ProductStatus(ProductState.ACTIVE, false, true, false, null);
  }

  public boolean isAvailableForSearch() {
    return isActive && !isSuspended && state == ProductState.ACTIVE;
  }

  public boolean isAvailableForPurchase() {
    return isAvailableForSearch() && hasStock;
  }

  // Getters
  public ProductState getState() {
    return state;
  }

  public boolean hasStock() {
    return hasStock;
  }

  public boolean isActive() {
    return isActive;
  }

  public boolean isSuspended() {
    return isSuspended;
  }

  public String getSuspensionReason() {
    return suspensionReason;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    ProductStatus that = (ProductStatus) o;
    return hasStock == that.hasStock &&
        isActive == that.isActive &&
        isSuspended == that.isSuspended &&
        state == that.state &&
        Objects.equals(suspensionReason, that.suspensionReason);
  }

  @Override
  public int hashCode() {
    return Objects.hash(state, hasStock, isActive, isSuspended, suspensionReason);
  }

  @Override
  public String toString() {
    return "ProductStatus{" +
        "state=" + state +
        ", hasStock=" + hasStock +
        ", isActive=" + isActive +
        ", isSuspended=" + isSuspended +
        (suspensionReason != null ? ", suspensionReason='" + suspensionReason + '\'' : "") +
        '}';
  }
}

enum ProductState {
  DRAFT,
  ACTIVE,
  INACTIVE,
  SUSPENDED,
  DELETED
}

