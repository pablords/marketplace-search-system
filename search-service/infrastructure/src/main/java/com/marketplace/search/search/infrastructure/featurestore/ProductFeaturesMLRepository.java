package com.marketplace.search.search.infrastructure.featurestore;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repositório JPA para ProductFeaturesMLEntity
 */
@Repository
public interface ProductFeaturesMLRepository extends JpaRepository<ProductFeaturesMLEntity, ProductFeaturesMLId> {

    /**
     * Busca a versão mais recente das features de um produto
     */
    @Query("SELECT f FROM ProductFeaturesMLEntity f WHERE f.productId = :productId ORDER BY f.calculatedAt DESC, f.version DESC")
    List<ProductFeaturesMLEntity> findLatestByProductId(@Param("productId") String productId);

    /**
     * Busca features por productId e version
     */
    @Query("SELECT f FROM ProductFeaturesMLEntity f WHERE f.productId = :productId AND f.version = :version ORDER BY f.calculatedAt DESC")
    List<ProductFeaturesMLEntity> findByProductIdAndVersion(@Param("productId") String productId, @Param("version") String version);

    /**
     * Busca todas as features de múltiplos produtos (última versão de cada)
     */
    @Query("SELECT f FROM ProductFeaturesMLEntity f WHERE f.productId IN :productIds AND f.calculatedAt = (SELECT MAX(f2.calculatedAt) FROM ProductFeaturesMLEntity f2 WHERE f2.productId = f.productId AND f2.version = f.version)")
    List<ProductFeaturesMLEntity> findLatestByProductIds(@Param("productIds") List<String> productIds);

    /**
     * Busca features em um intervalo de tempo (útil para treinamento de modelos)
     */
    @Query("SELECT f FROM ProductFeaturesMLEntity f WHERE f.calculatedAt BETWEEN :startDate AND :endDate ORDER BY f.calculatedAt DESC")
    List<ProductFeaturesMLEntity> findByDateRange(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);
}

