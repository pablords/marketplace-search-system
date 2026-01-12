package com.marketplace.search.indexing.bootstrap.config;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.ListTopicsResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.stereotype.Component;

@Component
public class KafkaStartupChecker implements CommandLineRunner {

  private static final Logger log = LoggerFactory.getLogger(KafkaStartupChecker.class);

  private final KafkaListenerEndpointRegistry registry;

  @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
  private String bootstrapServers;

  public KafkaStartupChecker(KafkaListenerEndpointRegistry registry) {
    this.registry = registry;
  }

  @Override
  public void run(String... args) throws Exception {
    log.info("Verificando conectividade com Kafka em {}", bootstrapServers);
    
    // Tentar conectar ao Kafka com retry
    boolean kafkaAvailable = waitForKafka(30, 2);
    
    if (!kafkaAvailable) {
      log.error("Kafka não está disponível após 30 tentativas. Os listeners podem não funcionar corretamente.");
    } else {
      log.info("Kafka está disponível. Iniciando listeners...");
    }

    // Log each listener container id and running state
    registry.getListenerContainers().forEach(c -> {
      String id = c.getListenerId() != null ? c.getListenerId() : "<no-id>";
      boolean isRunning = c.isRunning();
      log.info("Kafka listener container id={} running={}", id, isRunning);
      
      // Se não estiver rodando e o Kafka estiver disponível, tentar iniciar
      if (!isRunning && kafkaAvailable) {
        try {
          if (!c.isRunning()) {
            log.info("Iniciando listener container id={}", id);
            c.start();
            // Aguardar um pouco para verificar se iniciou
            Thread.sleep(1000);
            log.info("Listener container id={} agora está running={}", id, c.isRunning());
          }
        } catch (Exception e) {
          log.warn("Erro ao iniciar listener container id={}: {}", id, e.getMessage());
        }
      }
    });

    // If no containers registered, log a warning to help debugging
    if (registry.getListenerContainers().isEmpty()) {
      log.warn("No Kafka listener containers found in registry. Check @KafkaListener beans and component scan packages.");
    }
  }

  private boolean waitForKafka(int maxAttempts, int delaySeconds) {
    try (AdminClient adminClient = createAdminClient()) {
      for (int i = 0; i < maxAttempts; i++) {
        try {
          ListTopicsResult result = adminClient.listTopics();
          result.names().get(5, TimeUnit.SECONDS);
          log.info("Conexão com Kafka estabelecida com sucesso");
          return true;
        } catch (Exception e) {
          if (i < maxAttempts - 1) {
            log.debug("Tentativa {}/{} de conectar ao Kafka falhou: {}. Aguardando {} segundos...", 
                i + 1, maxAttempts, e.getMessage(), delaySeconds);
            Thread.sleep(Duration.ofSeconds(delaySeconds).toMillis());
          } else {
            log.warn("Não foi possível conectar ao Kafka após {} tentativas: {}", maxAttempts, e.getMessage());
          }
        }
      }
    } catch (Exception e) {
      log.error("Erro ao criar AdminClient do Kafka: {}", e.getMessage());
    }
    return false;
  }

  private AdminClient createAdminClient() {
    java.util.Properties props = new java.util.Properties();
    props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    props.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 5000);
    props.put(AdminClientConfig.CONNECTIONS_MAX_IDLE_MS_CONFIG, 10000);
    return AdminClient.create(props);
  }

}