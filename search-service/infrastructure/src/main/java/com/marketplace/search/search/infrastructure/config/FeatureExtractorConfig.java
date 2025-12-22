package com.marketplace.search.search.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.marketplace.search.search.domain.services.FeatureExtractor;

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

