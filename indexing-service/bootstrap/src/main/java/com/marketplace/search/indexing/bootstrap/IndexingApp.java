package com.marketplace.search.indexing.bootstrap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableAsync;

import lombok.extern.slf4j.Slf4j;

@SpringBootApplication
@Slf4j
@ComponentScan(basePackages = {
		"com.marketplace.search.indexing.domain",
		"com.marketplace.search.indexing.application",
		"com.marketplace.search.indexing.infrastructure",
		"com.marketplace.search.indexing.bootstrap"
})

@Import({
		com.marketplace.search.indexing.infrastructure.config.KafkaConfig.class
})
@EnableKafka
@EnableAsync
public class IndexingApp implements CommandLineRunner {

	@Value("${spring.application.name}")
	String appName;

	public static void main(String[] args) {
		SpringApplication.run(IndexingApp.class, args);
	}

	@Override
	public void run(String... args) throws Exception {
		log.info("{} app is running", appName);
	}

}
