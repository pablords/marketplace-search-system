package com.marketplace.search.catalog.infrastructure.persistence.mappers;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.marketplace.search.catalog.domain.entities.Product;
import com.marketplace.search.catalog.domain.entities.Seller;
import com.marketplace.search.catalog.domain.valueobjects.ProductInfo;
import com.marketplace.search.catalog.domain.valueobjects.ProductMetrics;
import com.marketplace.search.catalog.infrastructure.persistence.entities.ProductEntity;

/**
 * Mapper para converter entre Product (domínio) e ProductEntity (JPA).
 */
@Component
public class ProductEntityMapper {

    /**
     * Converte Product de domínio para ProductEntity (JPA).
     */
    public ProductEntity toEntity(Product product) {
        ProductEntity entity = new ProductEntity();
        
        ProductInfo info = product.getInfo();
        ProductMetrics metrics = product.getMetrics();
        Seller seller = product.getSeller();
        
        entity.setId(product.getId().getValue());
        entity.setTitle(info.getTitle());
        entity.setDescription(info.getDescription());
        entity.setPrice(info.getPrice());
        entity.setCurrency(info.getCurrency());
        entity.setAvailableQuantity(metrics.stockQuantity());
        
        // Condição baseada em atributos
        String condition = info.getAttributes().stream()
            .filter(attr -> attr.toLowerCase().contains("used"))
            .findFirst()
            .map(attr -> "USED")
            .orElse("NEW");
        entity.setCondition(condition);
        
        entity.setStatus(product.getStatus().isActive() ? "ACTIVE" : "INACTIVE");
        
        // Categoria
        if (info.getCategory() != null) {
            entity.setCategoryId(info.getCategory().getId());
            entity.setCategoryName(info.getCategory().getName());
            entity.setCategoryPath(info.getCategory().getPath());
        }
        
        // Marca
        if (info.getBrand() != null) {
            entity.setBrandId(info.getBrand().id());
            entity.setBrandName(info.getBrand().name());
            entity.setBrandDescription(info.getBrand().description());
        }
        
        // Vendedor
        if (seller != null) {
            entity.setSellerId(seller.getId());
            entity.setSellerName(seller.getName());
            entity.setSellerType(seller.getType().name());
            entity.setSellerStatus(seller.getStatus().name());
            
            if (seller.getReputation() != null) {
                entity.setSellerScore(BigDecimal.valueOf(seller.getReputation().getScore()));
                entity.setSellerPositiveReviews(seller.getReputation().getPositiveReviews());
                entity.setSellerNegativeReviews(seller.getReputation().getNegativeReviews());
                entity.setSellerNeutralReviews(seller.getReputation().getNeutralReviews());
                entity.setSellerTotalReviews(seller.getReputation().getTotalReviews());
                entity.setSellerCancellationRate(BigDecimal.valueOf(seller.getReputation().getCancellationRate()));
                entity.setSellerDeliveryPerformance(BigDecimal.valueOf(seller.getReputation().getDeliveryPerformance()));
            }
        }
        
        // Métricas
        entity.setTotalSold(metrics.totalSales());
        entity.setViewCount(metrics.totalReviews());
        entity.setConversionRate(BigDecimal.valueOf(metrics.conversionRate()));
        entity.setAverageRating(BigDecimal.valueOf(metrics.averageRating()));
        entity.setReviewCount(metrics.totalReviews());
        
        // Atributos
        Map<String, Object> attributeMap = new HashMap<>();
        info.getAttributes().forEach(attr -> attributeMap.put(attr, true));
        entity.setAttributes(attributeMap);
        
        entity.setCreatedAt(product.getCreatedAt());
        
        return entity;
    }
}
