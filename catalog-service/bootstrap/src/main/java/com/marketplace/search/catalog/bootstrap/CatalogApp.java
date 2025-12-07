package com.marketplace.search.catalog.bootstrap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

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
public class CatalogApp {

    public static void main(String[] args) {
        SpringApplication.run(CatalogApp.class, args);
    }
}