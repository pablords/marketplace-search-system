package com.marketplace.search.search.bootstrap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.sql.init.SqlInitializationAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

import lombok.extern.slf4j.Slf4j;

/**
 * Classe principal da aplicação Search Service
 */
@SpringBootApplication(exclude = {SqlInitializationAutoConfiguration.class})
@ComponentScan(basePackages = {
        "com.marketplace.search.search.domain",
        "com.marketplace.search.search.application",
        "com.marketplace.search.search.infrastructure",
        "com.marketplace.search.search.interfaces"
})
@Slf4j
public class SearchApp implements CommandLineRunner {

    @Value("${spring.application.name}")
    String appName;

    @Override
    public void run(String... args) {
        log.info("{} app is running", appName);
    }

    public static void main(String[] args) {
        SpringApplication.run(SearchApp.class, args);
    }
}

