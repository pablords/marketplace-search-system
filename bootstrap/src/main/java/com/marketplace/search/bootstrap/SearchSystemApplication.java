package com.marketplace.search.bootstrap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Classe principal da aplicação Search System
 */
@SpringBootApplication
@EnableKafka
@EnableAsync
@ComponentScan(basePackages = {
    "com.marketplace.search.domain",
    "com.marketplace.search.application", 
    "com.marketplace.search.infrastructure",
    "com.marketplace.search.interfaces"
})
public class SearchSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(SearchSystemApplication.class, args);
    }
}