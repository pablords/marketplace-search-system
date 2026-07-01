package com.marketplace.search.catalog.infrastructure.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.marketplace.search.catalog.domain.entities.Product;
import com.marketplace.search.catalog.domain.ports.ProductEventProducerPort;


@Component
public class KafkaProductEventProducerAdapter implements ProductEventProducerPort {

    private static final Logger logger = LoggerFactory.getLogger(KafkaProductEventProducerAdapter.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String topic;

    public KafkaProductEventProducerAdapter(
            KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${spring.kafka.template.default-topic:catalog.product.create.requests}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    @Override
    public void send(Product product) {
        try {
            // Usamos o JsonSerializer do Spring Kafka (configurado no application.yml)
            kafkaTemplate.send(topic, product.getId().value(), product)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        logger.debug("Mensagem enviada com sucesso para o Kafka topic={} key={}", topic, product.getId().value());
                    } else {
                        logger.error("Erro ao enviar mensagem para o Kafka topic={} key={}", topic, product.getId().value(), ex);
                    }
                });
        } catch (Exception e) {
            logger.error("Erro ao enviar o evento do produto para o Kafka", e);
            throw new RuntimeException("Erro ao processar envio para o Kafka", e);
        }
    }
}
