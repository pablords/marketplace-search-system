package com.marketplace.search.gateway.bootstrap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

import lombok.extern.slf4j.Slf4j;

/**
 * Classe principal da aplicação API Gateway
 */
@SpringBootApplication
@ComponentScan(basePackages = {
        "com.marketplace.search.gateway.interfaces"
})
@Slf4j
public class GatewayApp implements CommandLineRunner {

    @Value("${spring.application.name}")
    String appName;

    @Override
    public void run(String... args) {
        log.info("{} app is running", appName);
    }

    public static void main(String[] args) {
        SpringApplication.run(GatewayApp.class, args);
    }
}

