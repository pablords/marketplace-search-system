package com.marketplace.search.application.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.marketplace.search.domain.repositories.ProductIndexRepository;
import com.marketplace.search.domain.repositories.ProductSearchRepository;
import com.marketplace.search.domain.services.SearchDomainService;

/**
 * Configuração dos serviços de domínio
 * 
 * Esta classe é responsável por instanciar os serviços de domínio 
 * mantendo-os livres de dependências do Spring Framework
 */
@Configuration
public class DomainConfig {

    /**
     * Configura o serviço de domínio para busca
     */
    @Bean
    public SearchDomainService searchDomainService(
            ProductSearchRepository searchRepository,
            ProductIndexRepository indexRepository) {
        return new SearchDomainService(searchRepository, indexRepository);
    }
}