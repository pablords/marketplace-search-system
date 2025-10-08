package com.marketplace.search.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import org.springframework.boot.actuator.health.Health;
import org.springframework.boot.actuator.health.HealthIndicator;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Health checks customizados para os serviços externos
 */
@Component
public class CustomHealthIndicators {

    /**
     * Health check para Elasticsearch
     */
    @Component("elasticsearch")
    public static class ElasticsearchHealthIndicator implements HealthIndicator {
        
        private final ElasticsearchClient elasticsearchClient;

        public ElasticsearchHealthIndicator(ElasticsearchClient elasticsearchClient) {
            this.elasticsearchClient = elasticsearchClient;
        }

        @Override
        public Health health() {
            try {
                var response = elasticsearchClient.cluster().health();
                
                if ("red".equals(response.status().jsonValue())) {
                    return Health.down()
                        .withDetail("cluster.status", response.status().jsonValue())
                        .withDetail("active_shards", response.activeShards())
                        .build();
                }
                
                return Health.up()
                    .withDetail("cluster.name", response.clusterName())
                    .withDetail("cluster.status", response.status().jsonValue())
                    .withDetail("active_shards", response.activeShards())
                    .withDetail("number_of_nodes", response.numberOfNodes())
                    .build();
                    
            } catch (Exception e) {
                return Health.down()
                    .withDetail("error", e.getMessage())
                    .build();
            }
        }
    }

    /**
     * Health check para Redis
     */
    @Component("redis")
    public static class RedisHealthIndicator implements HealthIndicator {
        
        private final RedisConnectionFactory redisConnectionFactory;

        public RedisHealthIndicator(RedisConnectionFactory redisConnectionFactory) {
            this.redisConnectionFactory = redisConnectionFactory;
        }

        @Override
        public Health health() {
            try {
                var connection = redisConnectionFactory.getConnection();
                var info = connection.info();
                connection.close();
                
                return Health.up()
                    .withDetail("version", parseRedisVersion(info))
                    .build();
                    
            } catch (Exception e) {
                return Health.down()
                    .withDetail("error", e.getMessage())
                    .build();
            }
        }
        
        private String parseRedisVersion(java.util.Properties info) {
            return info.getProperty("redis_version", "unknown");
        }
    }

    /**
     * Health check para Kafka
     */
    @Component("kafka")
    public static class KafkaHealthIndicator implements HealthIndicator {
        
        private final KafkaTemplate<String, String> kafkaTemplate;

        public KafkaHealthIndicator(KafkaTemplate<String, String> kafkaTemplate) {
            this.kafkaTemplate = kafkaTemplate;
        }

        @Override
        public Health health() {
            try {
                var metadata = kafkaTemplate.getProducerFactory()
                    .createProducer()
                    .partitionsFor("__consumer_offsets");
                
                if (metadata != null && !metadata.isEmpty()) {
                    return Health.up()
                        .withDetail("brokers", metadata.size())
                        .build();
                }
                
                return Health.down()
                    .withDetail("error", "No metadata available")
                    .build();
                    
            } catch (Exception e) {
                return Health.down()
                    .withDetail("error", e.getMessage())
                    .build();
            }
        }
    }
}