package com.marketplace.search.search.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.aop.ObservedAspect;

/**
 * Configuração de observabilidade com Micrometer Observation API.
 * 
 * Registra o ObservedAspect que habilita a anotação @Observed
 * para criar spans automaticamente em métodos anotados.
 */
@Configuration
public class ObservabilityConfig {

    @Bean
    public ObservedAspect observedAspect(ObservationRegistry observationRegistry) {
        return new ObservedAspect(observationRegistry);
    }
}
