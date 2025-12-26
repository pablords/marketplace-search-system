package com.marketplace.search.search.domain.services;

import java.util.Optional;

/**
 * Serviço de domínio para geração de embeddings
 * Abstrai a comunicação com o Embedding Service
 */
public interface EmbeddingService {
    
    /**
     * Gera embedding para uma query de busca
     * 
     * @param query Texto da query de busca
     * @return Vetor de embedding (float[]) ou Optional.empty() em caso de erro
     */
    Optional<float[]> generateQueryEmbedding(String query);
    
    /**
     * Verifica se o serviço está disponível
     * 
     * @return true se o serviço está disponível, false caso contrário
     */
    boolean isAvailable();
}

