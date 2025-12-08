package com.marketplace.search.catalog.infrastructure.persistence.mappers;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.marketplace.search.catalog.domain.entities.Product;
import com.marketplace.search.catalog.domain.entities.Seller;
import com.marketplace.search.catalog.domain.valueobjects.ProductInfo;
import com.marketplace.search.catalog.domain.valueobjects.ProductMetrics;
import com.marketplace.search.catalog.infrastructure.persistence.entities.BrandEntity;
import com.marketplace.search.catalog.infrastructure.persistence.entities.CategoryEntity;
import com.marketplace.search.catalog.infrastructure.persistence.entities.ProductEntity;
import com.marketplace.search.catalog.infrastructure.persistence.entities.ProductMetricsEntity;
import com.marketplace.search.catalog.infrastructure.persistence.entities.SellerEntity;

@Component
public class ProductEntityMapper {

    public ProductEntity toEntity(Product product) {
        if (product == null)
            return null;

        ProductEntity entity = new ProductEntity();
        ProductInfo info = product.getInfo();
        ProductMetrics metrics = product.getMetrics();
        Seller seller = product.getSeller();

        // 1. Mapeamento de Campos Básicos
        entity.setId(product.getId().getValue());
        entity.setTitle(info.getTitle());
        entity.setDescription(info.getDescription());
        entity.setPrice(info.getPrice());
        entity.setCurrency(info.getCurrency());
        entity.setAvailableQuantity(metrics.stockQuantity());
        entity.setActive(product.getStatus().isActive());

        // Lógica de condição (Mantida do seu original)
        String condition = "NEW";
        if (info.getAttributes() != null) {
            condition = info.getAttributes().stream()
                    .filter(attr -> attr.toLowerCase().contains("used"))
                    .findFirst()
                    .map(attr -> "USED")
                    .orElse("NEW");
        }
        entity.setCondition(condition);

        // 2. Mapeamento de Categoria (Relacionamento)
        if (info.getCategory() != null) {
            CategoryEntity categoryEntity = new CategoryEntity();
            categoryEntity.setId(info.getCategory().getId());
            categoryEntity.setName(info.getCategory().getName());
            categoryEntity.setPath(info.getCategory().getPath());

            entity.setCategory(categoryEntity);
        }

        // 3. Mapeamento de Marca (Relacionamento)
        if (info.getBrand() != null) {
            BrandEntity brandEntity = new BrandEntity();
            brandEntity.setId(info.getBrand().id());
            brandEntity.setName(info.getBrand().name());
            brandEntity.setDescription(info.getBrand().description());

            entity.setBrand(brandEntity);
        }

        // 4. Mapeamento de Vendedor (Relacionamento)
        if (seller != null) {
            SellerEntity sellerEntity = new SellerEntity();
            sellerEntity.setId(seller.getId());
            sellerEntity.setName(seller.getName());

            // Reputação agora vive dentro da entidade Seller
            if (seller.getReputation() != null) {
                sellerEntity.setCancellationRate(BigDecimal.valueOf(seller.getReputation().getCancellationRate()));
                sellerEntity
                        .setDeliveryPerformance(BigDecimal.valueOf(seller.getReputation().getDeliveryPerformance()));
            }

            entity.setSeller(sellerEntity);
        }

        // 5. Mapeamento de Métricas (OneToOne)
        // Atenção: Métricas precisam estar vinculadas ao produto
        if (metrics != null) {
            ProductMetricsEntity metricsEntity = new ProductMetricsEntity();
            // O ID é mapeado automaticamente pelo @MapsId na entidade, mas o vínculo é
            // necessário
            metricsEntity.setTotalSales(metrics.totalSales());
            metricsEntity.setCtr((BigDecimal.valueOf(metrics.conversionRate())));
            metricsEntity.setAverageRating(BigDecimal.valueOf(metrics.averageRating()));
            metricsEntity.setTotalReviews(metrics.totalReviews());
            metricsEntity.setStockQuantity(metrics.stockQuantity());
            metricsEntity.setLastSale(metrics.lastSale());
            metricsEntity.setLastView(metrics.lastView());
            metricsEntity.setQuality(metrics.quality());


            // Método helper bidirecional (importante para JPA salvar corretamente)
            entity.setMetrics(metricsEntity);
        }

        // 6. Atributos (JSONB)
        if (info.getAttributes() != null) {
            Map<String, Object> attributeMap = new HashMap<>();
            // Mantendo sua lógica de transformar List<String> em Map<String, Boolean>
            info.getAttributes().forEach(attr -> attributeMap.put(attr, true));
            entity.setAttributes(attributeMap);
        }

        // Timestamps (Geralmente gerados pelo banco/JPA, mas podemos mapear se vier do
        // domínio)
        entity.setCreatedAt(product.getCreatedAt());

        return entity;
    }
}