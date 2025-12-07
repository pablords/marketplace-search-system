package com.marketplace.search.catalog.infrastructure.persistence.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.marketplace.search.catalog.infrastructure.persistence.entities.ProductEntity;

/**
 * Repositório JPA para a entidade ProductEntity.
 * Gerencia a persistência de produtos no PostgreSQL.
 */
@Repository
public interface ProductJpaRepository extends JpaRepository<ProductEntity, String> {
}
