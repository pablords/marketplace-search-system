package com.marketplace.search.domain.entities;

import java.time.Instant;
import java.util.Objects;

import com.marketplace.search.domain.valueobjects.SellerReputation;
import com.marketplace.search.domain.valueobjects.SellerStatus;
import com.marketplace.search.domain.valueobjects.SellerType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class Seller {

  @NotNull
  @NotBlank
  private final String id;

  @NotNull
  @NotBlank
  private final String name;

  @NotNull
  private final SellerType type;

  @NotNull
  private final SellerReputation reputation;

  @NotNull
  private final SellerStatus status;

  private final Instant memberSince;

  public Seller(String id, String name, SellerType type,
      SellerReputation reputation, SellerStatus status,
      Instant memberSince) {
    this.id = validateId(id);
    this.name = validateName(name);
    this.type = Objects.requireNonNull(type, "Seller type cannot be null");
    this.reputation = Objects.requireNonNull(reputation, "Reputation cannot be null");
    this.status = Objects.requireNonNull(status, "Status cannot be null");
    this.memberSince = memberSince;
  }

  private String validateId(String id) {
    if (id == null || id.trim().isEmpty()) {
      throw new IllegalArgumentException("Seller ID cannot be null or empty");
    }
    return id.trim();
  }

  private String validateName(String name) {
    if (name == null || name.trim().isEmpty()) {
      throw new IllegalArgumentException("Seller name cannot be null or empty");
    }
    return name.trim();
  }

  /**
   * Calcula o score de reputação do vendedor (0.0 a 1.0)
   */
  public double getReputationScore() {
    double baseScore = reputation.getNormalizedScore();

    // Boost para MercadoLíder
    if (type == SellerType.MERCADO_LIDER) {
      baseScore += 0.2;
    }

    // Penalidade para vendedores novos
    if (memberSince != null) {
      long daysSinceMember = java.time.Duration.between(memberSince, Instant.now()).toDays();
      if (daysSinceMember < 30) {
        baseScore -= 0.1; // Penalidade para vendedores muito novos
      }
    }

    return Math.max(0.0, Math.min(1.0, baseScore));
  }

  public boolean isActive() {
    return status == SellerStatus.ACTIVE;
  }

  public boolean isSuspended() {
    return status == SellerStatus.SUSPENDED || status == SellerStatus.BLOCKED;
  }

  // Getters
  public String getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public SellerType getType() {
    return type;
  }

  public SellerReputation getReputation() {
    return reputation;
  }

  public SellerStatus getStatus() {
    return status;
  }

  public Instant getMemberSince() {
    return memberSince;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    Seller seller = (Seller) o;
    return Objects.equals(id, seller.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }

  @Override
  public String toString() {
    return "Seller{" +
        "id='" + id + '\'' +
        ", name='" + name + '\'' +
        ", type=" + type +
        ", status=" + status +
        '}';
  }
}