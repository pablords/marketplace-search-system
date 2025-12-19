package com.marketplace.search.interfaces.rest.commands.mappers;

import org.springframework.stereotype.Component;

import com.marketplace.search.application.commands.ProductCommand;
import com.marketplace.search.application.queries.BrandData;
import com.marketplace.search.application.queries.CategoryData;
import com.marketplace.search.application.queries.SellerData;
import com.marketplace.search.application.queries.SellerReputationData;
import com.marketplace.search.interfaces.rest.dtos.CategoryDTO;
import com.marketplace.search.interfaces.rest.dtos.ProductDTO;
import com.marketplace.search.interfaces.rest.dtos.SellerDTO;
import com.marketplace.search.interfaces.rest.dtos.SellerReputationDTO;

@Component("ProductMapperRest")
public class ProductMapper {
  
  /**
   * Converte ProductDTO do monolito para ProductDTO do catalog-service.
   * O catalog-service espera um ProductDTO com productMetrics (opcional).
   */
  public com.marketplace.search.catalog.interfaces.rest.dtos.ProductDTO toCatalogServiceDTO(
      ProductDTO monolithDTO) {
    if (monolithDTO == null) {
      return null;
    }

    com.marketplace.search.catalog.interfaces.rest.dtos.CategoryDTO categoryDTO = 
        new com.marketplace.search.catalog.interfaces.rest.dtos.CategoryDTO(
            monolithDTO.category().id(),
            monolithDTO.category().name(),
            monolithDTO.category().parentId(),
            monolithDTO.category().path());

    com.marketplace.search.catalog.interfaces.rest.dtos.BrandDTO brandDTO = 
        new com.marketplace.search.catalog.interfaces.rest.dtos.BrandDTO(
            monolithDTO.brand().id(),
            monolithDTO.brand().name(),
            monolithDTO.brand().description());

    com.marketplace.search.catalog.interfaces.rest.dtos.SellerReputationDTO reputationDTO = null;
    if (monolithDTO.seller().reputation() != null) {
      reputationDTO = new com.marketplace.search.catalog.interfaces.rest.dtos.SellerReputationDTO(
          monolithDTO.seller().reputation().score(),
          monolithDTO.seller().reputation().totalReviews(),
          monolithDTO.seller().reputation().positiveReviews(),
          monolithDTO.seller().reputation().neutralReviews(),
          monolithDTO.seller().reputation().negativeReviews(),
          monolithDTO.seller().reputation().cancellationRate(),
          monolithDTO.seller().reputation().deliveryPerformance());
    }

    com.marketplace.search.catalog.interfaces.rest.dtos.SellerDTO sellerDTO = 
        com.marketplace.search.catalog.interfaces.rest.dtos.SellerDTO.builder()
            .id(monolithDTO.seller().id())
            .name(monolithDTO.seller().name())
            .type(monolithDTO.seller().type())
            .reputation(reputationDTO)
            .status(monolithDTO.seller().status())
            .memberSince(monolithDTO.seller().memberSince())
            .build();

    return com.marketplace.search.catalog.interfaces.rest.dtos.ProductDTO.builder()
        .id(monolithDTO.id())
        .title(monolithDTO.title())
        .description(monolithDTO.description())
        .price(monolithDTO.price())
        .currency(monolithDTO.currency())
        .category(categoryDTO)
        .brand(brandDTO)
        .seller(sellerDTO)
        .images(monolithDTO.images())
        .attributes(monolithDTO.attributes())
        .tags(monolithDTO.tags())
        .stockQuantity(monolithDTO.stockQuantity())
        .condition(monolithDTO.condition())
        .isActive(monolithDTO.isActive())
        .productMetrics(null) // ProductMetrics será preenchido pelo catalog-service se necessário
        .build();
  }

  /**
   * Converte ProductDTO do catalog-service para ProductDTO do monolito.
   * Remove o campo productMetrics que não existe no DTO do monolito.
   */
  public ProductDTO fromCatalogServiceDTO(
      com.marketplace.search.catalog.interfaces.rest.dtos.ProductDTO catalogDTO) {
    if (catalogDTO == null) {
      return null;
    }

    CategoryDTO categoryDTO = new CategoryDTO(
        catalogDTO.category().id(),
        catalogDTO.category().name(),
        catalogDTO.category().parentId(),
        catalogDTO.category().path());

    com.marketplace.search.interfaces.rest.dtos.BrandDTO brandDTO = 
        new com.marketplace.search.interfaces.rest.dtos.BrandDTO(
            catalogDTO.brand().id(),
            catalogDTO.brand().name(),
            catalogDTO.brand().description());

    SellerReputationDTO reputationDTO = null;
    if (catalogDTO.seller().reputation() != null) {
      reputationDTO = SellerReputationDTO.builder()
          .score(catalogDTO.seller().reputation().score())
          .totalReviews(catalogDTO.seller().reputation().totalReviews())
          .positiveReviews(catalogDTO.seller().reputation().positiveReviews())
          .neutralReviews(catalogDTO.seller().reputation().neutralReviews())
          .negativeReviews(catalogDTO.seller().reputation().negativeReviews())
          .cancellationRate(catalogDTO.seller().reputation().cancellationRate())
          .deliveryPerformance(catalogDTO.seller().reputation().deliveryPerformance())
          .build();
    }

    SellerDTO sellerDTO = SellerDTO.builder()
        .id(catalogDTO.seller().id())
        .name(catalogDTO.seller().name())
        .type(catalogDTO.seller().type())
        .reputation(reputationDTO)
        .status(catalogDTO.seller().status())
        .memberSince(catalogDTO.seller().memberSince())
        .build();

    return ProductDTO.builder()
        .id(catalogDTO.id())
        .title(catalogDTO.title())
        .description(catalogDTO.description())
        .price(catalogDTO.price())
        .currency(catalogDTO.currency())
        .category(categoryDTO)
        .brand(brandDTO)
        .seller(sellerDTO)
        .images(catalogDTO.images())
        .attributes(catalogDTO.attributes())
        .tags(catalogDTO.tags())
        .stockQuantity(catalogDTO.stockQuantity())
        .condition(catalogDTO.condition())
        .isActive(catalogDTO.isActive())
        .build();
  }
  public ProductCommand toCommand(ProductDTO dto) {

    SellerData seller = mapSeller(dto.seller());

    return ProductCommand.builder()
        .id(dto.id())
        .title(dto.title())
        .description(dto.description())
        .price(dto.price())
        .currency(dto.currency())
        .category(mapCategory(dto.category()))
        .brand(dto.brand() != null ? new BrandData(
            dto.brand().id(), dto.brand().name(), dto.brand().description()) : null)
        .seller(seller)
        .images(dto.images())
        .attributes(dto.attributes())
        .build();
  }

  private CategoryData mapCategory(CategoryDTO dto) {
    return new CategoryData(dto.id(), dto.name(), dto.parentId(), dto.path());
  }

  private SellerData mapSeller(SellerDTO dto) {
    SellerReputationData reputation = dto.reputation() != null ? mapSellerReputation(dto.reputation())
        : new SellerReputationData(5.0, 0, 0, 0, 0, 0.0, 1.0);

    return new SellerData(
        dto.id(),
        dto.name(),
        dto.type(),
        reputation,
        dto.status(),
        dto.memberSince());
  }

  private SellerReputationData mapSellerReputation(SellerReputationDTO dto) {
    return new SellerReputationData(
        dto.score() != null ? dto.score() : 5.0,
        dto.totalReviews() != null ? dto.totalReviews() : 0,
        dto.positiveReviews() != null ? dto.positiveReviews() : 0,
        dto.neutralReviews() != null ? dto.neutralReviews() : 0,
        dto.negativeReviews() != null ? dto.negativeReviews() : 0,
        dto.cancellationRate() != null ? dto.cancellationRate() : 0.0,
        dto.deliveryPerformance() != null ? dto.deliveryPerformance() : 1.0);
  }

}
