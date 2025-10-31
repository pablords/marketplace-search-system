package com.marketplace.search.infrastructure.kafka.mappers;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.marketplace.search.application.dto.BrandDTO;
import com.marketplace.search.application.dto.CategoryDTO;
import com.marketplace.search.application.dto.ProductDTO;
import com.marketplace.search.application.dto.SellerDTO;
import com.marketplace.search.application.dto.SellerReputationDTO;
import com.marketplace.search.infrastructure.kafka.dto.ProductPayloadDTO;

/**
 * Mapper responsável por converter o payload do Debezium (PostgreSQL row)
 * para o ProductDTO esperado pela camada de aplicação.
 */
@Component
public class DebeziumProductMapper {

  

  /**
   * Converte o payload do Debezium para ProductDTO
   */
  public ProductDTO toProductDTO(ProductPayloadDTO payload) {
    if (payload == null) {
      return null;
    }

    ProductDTO dto = new ProductDTO();
    dto.setId(payload.getId());
    dto.setTitle(payload.getTitle());
    dto.setDescription(payload.getDescription());
    dto.setPrice(payload.getPrice() != null ? BigDecimal.valueOf(payload.getPrice()) : null);
    dto.setCurrency(payload.getCurrency());
    dto.setStockQuantity(payload.getStockQuantity());
    dto.setCondition(payload.getCondition());
    dto.setIsActive(payload.getIsActive() != null ? payload.getIsActive() : true);

    // Category
    CategoryDTO category = new CategoryDTO();
    category.setId(payload.getCategoryId());
    category.setName(payload.getCategoryName());
    category.setParentId(payload.getCategoryParentId());
    category.setPath(payload.getCategoryPath());
    dto.setCategory(category);

    // Brand
    BrandDTO brand = new BrandDTO();
    brand.setId(payload.getBrandId());
    brand.setName(payload.getBrandName());
    brand.setDescription(payload.getBrandDescription());
    dto.setBrand(brand);

    // Seller
    SellerDTO seller = new SellerDTO();
    seller.setId(payload.getSellerId());
    seller.setName(payload.getSellerName());
    seller.setType(payload.getSellerType());
    seller.setStatus(payload.getSellerStatus());
    seller.setMemberSince(payload.getSellerMemberSince());

    // Reputation
    SellerReputationDTO reputation = new SellerReputationDTO();
    reputation.setScore(parseDouble(payload.getSellerScore()));
    reputation.setTotalReviews(payload.getSellerTotalReviews());
    reputation.setPositiveReviews(payload.getSellerReputationPositiveReviews());
    reputation.setNeutralReviews(payload.getSellerReputationNeutralReviews());
    reputation.setNegativeReviews(payload.getSellerReputationNegativeReviews());
    reputation.setCancellationRate(parseDouble(payload.getSellerCancellationRate()));
    reputation.setDeliveryPerformance(parseDouble(payload.getSellerDeliveryPerformance()));
    seller.setReputation(reputation);
    dto.setSeller(seller);

    // JSONB fields
    dto.setImages(jsonNodeToList(payload.getImages()));
    dto.setAttributes(new HashSet<>(jsonNodeToList(payload.getAttributes())));
    dto.setTags(new HashSet<>(jsonNodeToList(payload.getTags())));

    return dto;
  }

  /**
   * Converte JsonNode (JSONB) para List<String>
   */
  private List<String> jsonNodeToList(JsonNode node) {
    List<String> list = new ArrayList<>();
    if (node != null && node.isArray()) {
      node.forEach(item -> {
        if (item.isTextual()) {
          list.add(item.asText());
        } else {
          list.add(item.toString());
        }
      });
    }
    return list;
  }

  private Double parseDouble(String value) {
    if (value == null)
      return null;
    try {
      return Double.valueOf(value);
    } catch (NumberFormatException e) {
      return null;
    }
  }
}
