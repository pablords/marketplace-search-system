package com.marketplace.search.indexing.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import com.marketplace.search.indexing.domain.repositories.ProductIndexRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * Inicializador do índice OpenSearch
 * Cria ou verifica o índice na inicialização do serviço
 */
@Slf4j
@Component
public class OpenSearchIndexInitializer implements CommandLineRunner {

	private final ProductIndexRepository indexRepository;
	
	@Value("${embedding.service.vector-dimension:384}")
	private int vectorDimension;
	
	// Dimensão padrão do modelo all-MiniLM-L6-v2
	private static final int DEFAULT_VECTOR_DIMENSION = 384;

	public OpenSearchIndexInitializer(ProductIndexRepository indexRepository) {
		this.indexRepository = indexRepository;
	}

	@Override
	public void run(String... args) throws Exception {
		// Usar dimensão configurada ou padrão
		int dim = vectorDimension > 0 ? vectorDimension : DEFAULT_VECTOR_DIMENSION;
		log.info("Inicializando índice OpenSearch com suporte a k-NN (dimensão: {})...", dim);
		
		try {
			indexRepository.createKnnIndex(dim);
			log.info("Índice OpenSearch inicializado com sucesso");
		} catch (Exception e) {
			log.error("Erro ao inicializar índice OpenSearch: {}", e.getMessage(), e);
			throw e;
		}
	}
}

