package com.marketplace.search.indexing.application.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.marketplace.search.indexing.domain.services.FeatureExtractor;

/**
 * Configuração do FeatureExtractor como bean do Spring
 */
@Configuration
public class FeatureExtractorConfig {

    @Bean
    public FeatureExtractor featureExtractor() {
        return new FeatureExtractor();
    }
}

