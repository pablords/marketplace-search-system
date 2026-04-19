package com.marketplace.search.search.domain.services;


import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Serviço de domínio para re-ranking com Machine Learning
 * Interface que abstrai a comunicação com o ML Ranking Service
 */
public interface MLRankingService {

    /**
     * Re-ranqueia produtos candidatos usando modelo de Machine Learning
     * 
     * @param candidates Lista de candidatos com suas features (até 400)
     * @param query Query de busca (opcional, para logging)
     * @return Lista de produtos ranqueados com scores ML (Top 20), ou empty se falhar
     */
    Optional<List<RankedProduct>> rank(List<FeatureVector> candidates, String query);

    /**
     * Verifica se o serviço ML está disponível
     * 
     * @return true se o serviço está saudável
     */
    boolean isAvailable();

    /**
     * Representa um produto candidato com suas features para ranking ML
     */
    record FeatureVector(
        String productId,
        Map<String, Double> features
    ) {}

    /**
     * Representa um produto ranqueado com score ML
     */
    record RankedProduct(
        String productId,
        double mlScore,
        int rank
    ) {}
}

