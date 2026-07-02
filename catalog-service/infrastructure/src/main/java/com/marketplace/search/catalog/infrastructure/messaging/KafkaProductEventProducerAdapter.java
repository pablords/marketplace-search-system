package com.marketplace.search.catalog.infrastructure.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.marketplace.search.catalog.domain.entities.Product;
import com.marketplace.search.catalog.domain.ports.ProductEventProducerPort;
import com.marketplace.search.catalog.infrastructure.avro.ProductAvro;
import com.marketplace.search.catalog.infrastructure.avro.CategoryAvro;
import com.marketplace.search.catalog.infrastructure.avro.BrandAvro;
import com.marketplace.search.catalog.infrastructure.avro.SellerAvro;
import com.marketplace.search.catalog.infrastructure.avro.MetricsAvro;

import java.util.ArrayList;

@Component
public class KafkaProductEventProducerAdapter implements ProductEventProducerPort {

    private static final Logger logger = LoggerFactory.getLogger(KafkaProductEventProducerAdapter.class);

    private final KafkaTemplate<String, ProductAvro> kafkaTemplate;
    private final String topic;

    public KafkaProductEventProducerAdapter(
            KafkaTemplate<String, ProductAvro> kafkaTemplate,
            @Value("${spring.kafka.template.default-topic:catalog.product.create.requests}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    @Override
    public void send(Product product) {
        try {
            ProductAvro avro = ProductAvro.newBuilder()
                .setId(product.getId().value())
                .setTitle(product.getInfo().getTitle())
                .setDescription(product.getInfo().getDescription())
                .setPrice(product.getInfo().getPrice().toString())
                .setCurrency(product.getInfo().getCurrency())
                .setCategory(CategoryAvro.newBuilder()
                    .setId(product.getInfo().getCategory().getId())
                    .setName(product.getInfo().getCategory().getName())
                    .build())
                .setBrand(BrandAvro.newBuilder()
                    .setId(product.getInfo().getBrand().id())
                    .setName(product.getInfo().getBrand().name())
                    .build())
                .setSeller(SellerAvro.newBuilder()
                    .setId(product.getSeller().getId())
                    .setName(product.getSeller().getName())
                    .build())
                .setMetrics(MetricsAvro.newBuilder()
                    .setViews(product.getMetrics().totalViews())
                    .setSales(product.getMetrics().totalSales())
                    .setStock(product.getMetrics().stockQuantity())
                    .setScore(product.getMetrics().averageRating())
                    .build())
                .setStatus(product.getStatus().getStateName())
                .setImages(product.getInfo().getImages() != null ? new ArrayList<>(product.getInfo().getImages()) : new ArrayList<>())
                .setAttributes(product.getInfo().getAttributes() != null ? new ArrayList<>(product.getInfo().getAttributes()) : new ArrayList<>())
                .setTags(product.getInfo().getTags() != null ? new ArrayList<>(product.getInfo().getTags()) : new ArrayList<>())
                .setCreatedAt(product.getCreatedAt().toEpochMilli())
                .setUpdatedAt(product.getUpdatedAt().toEpochMilli())
                .build();

            kafkaTemplate.send(topic, product.getId().value(), avro)
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
