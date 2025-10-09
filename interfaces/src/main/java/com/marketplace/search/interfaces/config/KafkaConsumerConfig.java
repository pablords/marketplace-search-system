package com.marketplace.search.interfaces.config;

import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;

@Configuration
@EnableKafka
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

    @Value("${spring.kafka.consumer.auto-offset-reset}")
    private String autoOffsetReset;

    @Value("${spring.kafka.consumer.max-poll-records}")
    private int maxPollRecords;

    // Configurações opcionais com valores padrão
    private final int maxPollIntervalMs = 300000; // 5 minutos
    private final int sessionTimeoutMs = 30000;   // 30 segundos  
    private final int heartbeatIntervalMs = 3000; // 3 segundos

    @Bean("interface-consumer")
    public ConsumerFactory<String, String> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        
        // Configurações básicas
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, autoOffsetReset);
        
        // Deserializadores
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        
        // Configurações de performance
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, maxPollRecords);
        props.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, maxPollIntervalMs);
        props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, sessionTimeoutMs);
        props.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, heartbeatIntervalMs);
        
        // Configurações de segurança e confiabilidade
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false); // Manual commit
        props.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, 1024); // 1KB mínimo
        props.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, 500); // 500ms máximo de espera
        
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean("interface-container-factory")
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, String> factory = 
            new ConcurrentKafkaListenerContainerFactory<>();
        
        factory.setConsumerFactory(consumerFactory());
        
        // Configurações do container
        factory.setConcurrency(3); // 3 threads por listener
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        factory.getContainerProperties().setSyncCommits(true);
        
        // Error handling
        factory.setCommonErrorHandler(new org.springframework.kafka.listener.DefaultErrorHandler(
            new org.springframework.util.backoff.FixedBackOff(1000L, 3L) // 3 tentativas com 1s de intervalo
        ));
        
        return factory;
    }
}