package com.marketplace.search.search.application.usecases;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.marketplace.search.search.domain.entities.Product;
import com.marketplace.search.search.domain.repositories.MLFeatureStore;
import com.marketplace.search.search.domain.services.FeatureExtractor;
import com.marketplace.search.search.domain.services.MLRankingService;
import com.marketplace.search.search.domain.valueobjects.SearchQuery;
import com.marketplace.search.search.domain.valueobjects.UserContext;


/**
 * Caso de uso para re-ranking de produtos usando Machine Learning
 * 
 * Orquestra:
 * 1. Busca de features em cache (Redis)
 * 2. Extração de features se não estiverem em cache
 * 3. Chamada ao ML Ranking Service
 * 4. Re-ranking dos resultados (Top 20)
 * 
 * Implementa fallback gracioso se ML service estiver indisponível
 */
@Service
public class RankWithMLUseCase {

    private static final Logger logger = LoggerFactory.getLogger(RankWithMLUseCase.class);
    
    private static final int TOP_CANDIDATES = 400;
    private static final int TOP_RESULTS = 20;

    private final MLRankingService mlRankingService;
    private final MLFeatureStore featureStore;
    private final FeatureExtractor featureExtractor;

    public RankWithMLUseCase(
            MLRankingService mlRankingService,
            MLFeatureStore featureStore,
            FeatureExtractor featureExtractor) {
        this.mlRankingService = mlRankingService;
        this.featureStore = featureStore;
        this.featureExtractor = featureExtractor;
    }

    /**
     * Re-ranqueia produtos candidatos usando ML
     * 
     * @param candidates Lista de produtos candidatos (até 400)
     * @param query Query de busca
     * @param userContext Contexto do usuário
     * @param scores Map de productId -> (bm25Score, knnScore) dos resultados do OpenSearch
     * @return Lista de produtos re-ranqueados (Top 20)
     */
    public List<Product> rank(List<Product> candidates, SearchQuery query, UserContext userContext,
                              Map<String, ScorePair> scores) {
        
        if (candidates == null || candidates.isEmpty()) {
            logger.warn("Lista de candidatos vazia para ranking ML");
            return List.of();
        }

        if (candidates.size() > TOP_CANDIDATES) {
            logger.warn("Lista de candidatos excede {} itens ({}), limitando", TOP_CANDIDATES, candidates.size());
            candidates = candidates.subList(0, TOP_CANDIDATES);
        }

        Instant startTime = Instant.now();
        logger.info("Iniciando re-ranking ML para {} candidatos (query: '{}')", candidates.size(), query.terms());

        try {
            // 1. Buscar ou calcular features para todos os candidatos
            List<MLRankingService.FeatureVector> featureVectors = prepareFeatureVectors(
                candidates, query, scores);

            if (featureVectors.isEmpty()) {
                logger.warn("Nenhuma feature foi preparada, retornando candidatos originais");
                return candidates.stream().limit(TOP_RESULTS).collect(Collectors.toList());
            }

            // 2. Chamar ML Ranking Service
            Optional<List<MLRankingService.RankedProduct>> rankedProducts = mlRankingService.rank(
                featureVectors, query.terms());

            if (rankedProducts.isEmpty() || rankedProducts.get().isEmpty()) {
                logger.warn("ML Ranking Service não retornou resultados, usando fallback");
                return fallbackRanking(candidates, query);
            }

            // 3. Re-ordenar produtos baseado no ranking ML
            List<Product> reRankedProducts = reorderProducts(candidates, rankedProducts.get());

            Duration executionTime = Duration.between(startTime, Instant.now());
            logger.info("Re-ranking ML concluído: {} produtos ranqueados em {}ms",
                reRankedProducts.size(), executionTime.toMillis());

            return reRankedProducts.stream().limit(TOP_RESULTS).collect(Collectors.toList());

        } catch (Exception e) {
            logger.error("Erro ao executar re-ranking ML para query: '{}'", query.terms(), e);
            return fallbackRanking(candidates, query);
        }
    }

    /**
     * Prepara feature vectors para todos os candidatos
     * Busca features em cache primeiro, calcula se necessário
     */
    private List<MLRankingService.FeatureVector> prepareFeatureVectors(
            List<Product> candidates, SearchQuery query, Map<String, ScorePair> scores) {
        
        List<MLRankingService.FeatureVector> featureVectors = new ArrayList<>();
        List<String> productIds = candidates.stream()
            .map(p -> p.getId().getValue())
            .collect(Collectors.toList());

        // Buscar features em lote do cache
        Map<String, Map<String, Double>> cachedFeatures = featureStore.getFeaturesBatch(productIds);

        // Preparar feature vectors
        for (Product candidate : candidates) {
            String productId = candidate.getId().getValue();
            Map<String, Double> features;

            // Tentar usar features do cache
            if (cachedFeatures.containsKey(productId)) {
                features = cachedFeatures.get(productId);
                logger.debug("Features do cache para produto: {}", productId);
            } else {
                // Calcular features on-the-fly
                ScorePair scorePair = scores.getOrDefault(productId, new ScorePair(0.0, 0.0));
                features = featureExtractor.extractFeatures(
                    candidate, query, scorePair.bm25Score(), scorePair.knnScore());
                
                // Cachear features calculadas
                try {
                    featureStore.saveFeatures(productId, features);
                    logger.debug("Features calculadas e cacheadas para produto: {}", productId);
                } catch (Exception e) {
                    logger.warn("Erro ao cachear features para produto: {}", productId, e);
                }
            }

            featureVectors.add(new MLRankingService.FeatureVector(productId, features));
        }

        return featureVectors;
    }

    /**
     * Re-ordena produtos baseado no ranking ML
     */
    private List<Product> reorderProducts(
            List<Product> candidates, List<MLRankingService.RankedProduct> rankedProducts) {
        
        Map<String, Product> productMap = candidates.stream()
            .collect(Collectors.toMap(p -> p.getId().getValue(), p -> p));

        List<Product> reordered = new ArrayList<>();
        
        // Adicionar produtos na ordem do ranking ML
        for (MLRankingService.RankedProduct ranked : rankedProducts) {
            Product product = productMap.get(ranked.productId());
            if (product != null) {
                reordered.add(product);
            }
        }

        // Adicionar produtos que não foram ranqueados (se houver)
        for (Product candidate : candidates) {
            if (!reordered.contains(candidate)) {
                reordered.add(candidate);
            }
        }

        return reordered;
    }

    /**
     * Fallback: ranking híbrido (BM25 + k-NN) quando ML service está indisponível
     */
    private List<Product> fallbackRanking(List<Product> candidates, SearchQuery query) {
        logger.info("Usando fallback ranking (híbrido) para {} candidatos", candidates.size());
        
        // Ordenar por score híbrido (simplificado)
        // Em produção, poderia usar scores do OpenSearch
        return candidates.stream()
            .sorted(Comparator.comparing((Product p) -> 
                p.getMetrics().getPopularityScore()).reversed())
            .limit(TOP_RESULTS)
            .collect(Collectors.toList());
    }

    /**
     * Verifica se o ML Ranking Service está disponível
     */
    public boolean isMLServiceAvailable() {
        return mlRankingService.isAvailable();
    }

    /**
     * Par de scores (BM25, k-NN) de um produto
     */
    public record ScorePair(double bm25Score, double knnScore) {}
}

