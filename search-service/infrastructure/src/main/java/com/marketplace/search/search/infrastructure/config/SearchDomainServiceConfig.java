package com.marketplace.search.search.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.marketplace.search.search.domain.repositories.CacheRepository;
import com.marketplace.search.search.domain.repositories.ProductSearchRepository;
import com.marketplace.search.search.domain.services.SearchDomainService;

/**
 * Configuração do SearchDomainService como bean do Spring
 */
@Configuration
public class SearchDomainServiceConfig {

    @Bean
    public SearchDomainService searchDomainService(
            ProductSearchRepository productSearchRepository,
            CacheRepository cacheRepository) {
        return new SearchDomainService(productSearchRepository, cacheRepository);
    }
}

