package com.marketplace.search.infrastructure.persistence.mappers;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.marketplace.search.domain.entities.Product;
import com.marketplace.search.domain.valueobjects.ProductInfo;
import com.marketplace.search.domain.valueobjects.ProductMetrics;
import com.marketplace.search.domain.valueobjects.Seller;
import com.marketplace.search.infrastructure.persistence.entities.ProductEntity;

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
        entity.setAvailableQuantity(metrics.getStockQuantity());
        
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
            entity.setBrandId(info.getBrand().getId());
            entity.setBrandName(info.getBrand().getName());
            entity.setBrandDescription(info.getBrand().getDescription());
        }
        
        // Vendedor
        if (seller != null) {
            entity.setSellerId(seller.getId());
            entity.setSellerNickname(seller.getName());
            entity.setSellerType(seller.getType().name());
            entity.setSellerStatus(seller.getStatus().name());
            
            if (seller.getReputation() != null) {
                entity.setSellerScore(BigDecimal.valueOf(seller.getReputation().getScore()));
                entity.setSellerTotalReviews(seller.getReputation().getTotalReviews());
                entity.setSellerCancellationRate(BigDecimal.valueOf(seller.getReputation().getCancellationRate()));
                entity.setSellerDeliveryPerformance(BigDecimal.valueOf(seller.getReputation().getDeliveryPerformance()));
            }
        }
        
        // Métricas
        entity.setTotalSold(metrics.getTotalSales());
        entity.setViewCount(metrics.getTotalViews());
        entity.setConversionRate(BigDecimal.valueOf(metrics.getConversionRate()));
        entity.setAverageRating(BigDecimal.valueOf(metrics.getAverageRating()));
        entity.setReviewCount(metrics.getTotalReviews());
        
        // Atributos
        Map<String, Object> attributeMap = new HashMap<>();
        info.getAttributes().forEach(attr -> attributeMap.put(attr, true));
        entity.setAttributes(attributeMap);
        
        entity.setCreatedAt(product.getCreatedAt());
        
        return entity;
    }
}
