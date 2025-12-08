package com.marketplace.search.indexing.bootstrap.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.stereotype.Component;

@Component
public class KafkaStartupChecker implements CommandLineRunner {

  private static final Logger log = LoggerFactory.getLogger(KafkaStartupChecker.class);

  private final KafkaListenerEndpointRegistry registry;

  public KafkaStartupChecker(KafkaListenerEndpointRegistry registry) {
    this.registry = registry;
  }

  @Override
  public void run(String... args) throws Exception {
    // Log each listener container id and running state
    registry.getListenerContainers().forEach(c -> {
      String id = c.getListenerId() != null ? c.getListenerId() : "<no-id>";
      log.info("Kafka listener container id={} running={}", id, c.isRunning());
    });

    // If no containers registered, log a warning to help debugging
    if (registry.getListenerContainers().isEmpty()) {
      log.warn("No Kafka listener containers found in registry. Check @KafkaListener beans and component scan packages.");
    }
  }

}