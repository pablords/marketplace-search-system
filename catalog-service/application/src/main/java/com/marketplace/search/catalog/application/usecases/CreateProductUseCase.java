package com.marketplace.search.catalog.application.usecases;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;


import com.marketplace.search.catalog.application.commands.ProductCommand;
import com.marketplace.search.catalog.application.mappers.ProductMapper;
import com.marketplace.search.catalog.domain.entities.Product;
import com.marketplace.search.catalog.domain.exceptions.ProductAlreadyExistsException;
import com.marketplace.search.catalog.domain.ports.DistributedLockPort;
import java.time.Duration;
import com.marketplace.search.catalog.domain.repositories.ProductRepository;


/**
 * Caso de uso responsável por orquestrar o fluxo de criação de um produto.
 * O produto é persistido no PostgreSQL via ProductRepository (port) e o Debezium (CDC) 
 * automaticamente captura a mudança e publica no Kafka para indexação no Elasticsearch.
 * 
 * Implementa idempotência: se o produto já existe, lança exceção para evitar duplicação.
 */
@Service
public class CreateProductUseCase {

    private static final Logger logger = LoggerFactory.getLogger(CreateProductUseCase.class);

    private final ProductMapper productMapper;
    private final ProductRepository productRepository;
    private final DistributedLockPort lockPort;
    private final MeterRegistry meterRegistry;

    public CreateProductUseCase(
            ProductMapper productMapper,
            ProductRepository productRepository,
            DistributedLockPort lockPort,
            MeterRegistry meterRegistry) {
        this.productMapper = productMapper;
        this.productRepository = productRepository;
        this.lockPort = lockPort;
        this.meterRegistry = meterRegistry;
    }

    /**
     * Cria um novo produto salvando no PostgreSQL.
     * O Debezium captura automaticamente a inserção via CDC e publica no Kafka.
     * 
     * Implementa idempotência: verifica se o produto já existe antes de criar,
     * evitando duplicação no banco e consequente duplicação no Kafka.
     *
     * @param productDTO dados do produto informado pelo cliente
     * @throws ProductAlreadyExistsException se o produto já existe
     */
    @Transactional
    public void execute(ProductCommand productDTO) {
        meterRegistry.counter("catalog.product.operations.total", "operation", "create").increment();
        Timer.Sample sample = Timer.start(meterRegistry);

        logger.info("Received request for create product: id={}, title='{}'",
            productDTO.id(), productDTO.title());

        // 1. Tenta adquirir Lock Distribuído por 5 segundos
        boolean acquired = lockPort.acquireLock(productDTO.id(), Duration.ofSeconds(5));
        
        if (!acquired) {
            meterRegistry.counter("catalog.product.lock.denied.total").increment();
            logger.warn("Não foi possível adquirir o lock para o produto {}. Outra instância pode estar processando.", 
                productDTO.id());
            // Se não conseguiu o lock, tratamos como conflito pois provavelmente já está sendo criado
            throw new ProductAlreadyExistsException(productDTO.id());
        }

        meterRegistry.counter("catalog.product.lock.acquired.total").increment();

        try {
            // 2. Verifica idempotência no Banco: se o produto já existe, lança exceção
            if (productRepository.existsById(productDTO.id())) {
                logger.warn("Product {} already exists. Skipping creation to avoid duplication.", 
                    productDTO.id());
                throw new ProductAlreadyExistsException(productDTO.id());
            }

            // 3. Converte DTO para domínio e Salva
            Product product = productMapper.toDomain(productDTO);
            productRepository.save(product);

            sample.stop(Timer.builder("catalog.db.operation.duration")
                .tag("operation", "save")
                .register(meterRegistry));

            logger.info("Product {} saved to PostgreSQL. Debezium will capture and publish to Kafka.", 
                productDTO.id());
                
        } finally {
            // 4. Libera o Lock
            lockPort.releaseLock(productDTO.id());
        }
    }
}

