package com.marketplace.search.catalog.bootstrap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

import lombok.extern.slf4j.Slf4j;

/**
 * Classe principal da aplicação Search System
 */
@SpringBootApplication
@ComponentScan(basePackages = {
        "com.marketplace.search.catalog.domain",
        "com.marketplace.search.catalog.application",
        "com.marketplace.search.catalog.infrastructure",
        "com.marketplace.search.catalog.interfaces"
})
@Slf4j
public class CatalogApp implements CommandLineRunner {

    @Value("${spring.application.name}")
    String appName;

    @Override
    public void run(String... args) {
        log.info("{} app is running", appName);
    }

    public static void main(String[] args) {
        SpringApplication.run(CatalogApp.class, args);
    }
}