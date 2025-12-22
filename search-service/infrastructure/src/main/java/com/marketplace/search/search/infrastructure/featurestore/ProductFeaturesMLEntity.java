package com.marketplace.search.search.infrastructure.featurestore;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entidade JPA para armazenar features de ML no Feature Store Offline (PostgreSQL)
 * Usa composite key (product_id, calculated_at, version) para permitir histórico
 */
@Entity
@Table(name = "product_features_ml")
@IdClass(ProductFeaturesMLId.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductFeaturesMLEntity {

    @Id
    @Column(name = "product_id", nullable = false, length = 255)
    private String productId;

    @Id
    @Column(name = "calculated_at", nullable = false)
    private LocalDateTime calculatedAt;

    @Id
    @Column(name = "version", nullable = false, length = 50)
    private String version;

    @Column(name = "features_json", nullable = false, columnDefinition = "JSONB")
    private String featuresJson;

    /**
     * Converte o JSON de features para Map<String, Double>
     */
    public java.util.Map<String, Double> getFeaturesAsMap() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(featuresJson, new TypeReference<java.util.Map<String, Double>>() {});
        } catch (Exception e) {
            throw new RuntimeException("Erro ao converter features JSON para Map", e);
        }
    }

    /**
     * Converte Map<String, Double> para JSON string
     */
    public void setFeaturesFromMap(java.util.Map<String, Double> features) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            this.featuresJson = mapper.writeValueAsString(features);
        } catch (Exception e) {
            throw new RuntimeException("Erro ao converter Map para features JSON", e);
        }
    }
}

