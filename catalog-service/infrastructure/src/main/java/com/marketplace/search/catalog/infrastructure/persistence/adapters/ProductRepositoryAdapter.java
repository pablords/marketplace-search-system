package com.marketplace.search.catalog.infrastructure.persistence.adapters;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.marketplace.search.catalog.domain.entities.Product;
import com.marketplace.search.catalog.domain.repositories.ProductRepository;
import com.marketplace.search.catalog.infrastructure.persistence.entities.ProductEntity;
import com.marketplace.search.catalog.infrastructure.persistence.mappers.ProductEntityMapper;
import com.marketplace.search.catalog.infrastructure.persistence.repositories.ProductJpaRepository;

/**
 * Adapter que implementa o ProductRepository usando JPA.
 * Converte entidades de domínio para entidades JPA e persiste no PostgreSQL.
 */
@Component
public class ProductRepositoryAdapter implements ProductRepository {

    private static final Logger logger = LoggerFactory.getLogger(ProductRepositoryAdapter.class);

    private final ProductJpaRepository productJpaRepository;
    private final ProductEntityMapper productEntityMapper;

    public ProductRepositoryAdapter(
            ProductJpaRepository productJpaRepository,
            ProductEntityMapper productEntityMapper) {
        this.productJpaRepository = productJpaRepository;
        this.productEntityMapper = productEntityMapper;
    }

    @Override
    public void save(Product product) {
        logger.debug("Saving product {} to PostgreSQL", product.getId().getValue());
        
        ProductEntity entity = productEntityMapper.toEntity(product);
        productJpaRepository.save(entity);
        
        logger.debug("Product {} saved successfully", product.getId().getValue());
    }

    @Override
    public void update(Product product) {
        logger.debug("Updating product {} in PostgreSQL", product.getId().getValue());
        
        ProductEntity entity = productEntityMapper.toEntity(product);
        productJpaRepository.save(entity);
        
        logger.debug("Product {} updated successfully", product.getId().getValue());
    }

    @Override
    public void delete(String productId) {
        logger.debug("Deleting product {} from PostgreSQL", productId);
        
        productJpaRepository.deleteById(productId);
        
        logger.debug("Product {} deleted successfully", productId);
    }

    @Override
    public boolean existsById(String productId) {
        logger.debug("Checking if product {} exists in PostgreSQL", productId);
        
        boolean exists = productJpaRepository.existsById(productId);
        
        logger.debug("Product {} exists: {}", productId, exists);
        
        return exists;
    }
}
