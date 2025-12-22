package com.marketplace.search.catalog.bootstrap.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Configuração geral da aplicação
 */
@Configuration
@EnableScheduling
@EnableJpaRepositories(basePackages = "com.marketplace.search.catalog.infrastructure.persistence.repositories")
@EntityScan(basePackages = "com.marketplace.search.catalog.infrastructure.persistence.entities")
public class ApplicationConfig {
}