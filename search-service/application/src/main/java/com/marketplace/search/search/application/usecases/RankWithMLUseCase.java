package com.marketplace.search.search.application.usecases;

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

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;


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
    private final MeterRegistry meterRegistry;

    public RankWithMLUseCase(
            MLRankingService mlRankingService,
            MLFeatureStore featureStore,
            FeatureExtractor featureExtractor,
            MeterRegistry meterRegistry) {
        this.mlRankingService = mlRankingService;
        this.featureStore = featureStore;
        this.featureExtractor = featureExtractor;
        this.meterRegistry = meterRegistry;
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

        logger.info("Iniciando re-ranking ML para {} candidatos (query: '{}')", candidates.size(), query.terms());

        Timer.Sample rankingSample = Timer.start(meterRegistry);
        try {
            // 1. Buscar ou calcular features para todos os candidatos
            Map<String, Map<String, Double>> featuresMap = collectFeatures(candidates, query, scores);
            
            List<MLRankingService.FeatureVector> featureVectors = featuresMap.entrySet().stream()
                .map(e -> new MLRankingService.FeatureVector(e.getKey(), e.getValue()))
                .collect(Collectors.toList());

            if (featureVectors.isEmpty()) {
                logger.warn("Nenhuma feature foi preparada, retornando candidatos originais");
                return candidates.stream().limit(TOP_RESULTS).collect(Collectors.toList());
            }

            // 2. Chamar ML Ranking Service
            Optional<List<MLRankingService.RankedProduct>> rankedProducts = mlRankingService.rank(
                featureVectors, query.terms());

            if (rankedProducts.isEmpty() || rankedProducts.get().isEmpty()) {
                logger.warn("ML Ranking Service não retornou resultados, usando fallback");
                meterRegistry.counter("search.ml.ranking.fallback.total", "reason", "no_results").increment();
                return fallbackRanking(candidates, query);
            }

            // 3. Re-ordenar produtos baseado no ranking ML
            List<Product> reorderProducts = reorderProducts(candidates, rankedProducts.get(), featuresMap, query.rankingDebug());

            rankingSample.stop(Timer.builder("search.ml.ranking.duration")
                .description("Tempo total do processo de re-ranking ML")
                .register(meterRegistry));
            
            meterRegistry.counter("search.ml.ranking.success.total").increment();

            return reorderProducts.stream().limit(TOP_RESULTS).collect(Collectors.toList());

        } catch (Exception e) {
            meterRegistry.counter("search.ml.ranking.errors.total").increment();
            logger.error("Erro ao executar re-ranking ML para query: '{}'", query.terms(), e);
            return fallbackRanking(candidates, query);
        }
    }

    /**
     * Coleta feature vectors para todos os candidatos
     * Busca features em cache primeiro, calcula se necessário
     */
    private Map<String, Map<String, Double>> collectFeatures(
            List<Product> candidates, SearchQuery query, Map<String, ScorePair> scores) {
        
        Map<String, Map<String, Double>> featuresMap = new java.util.HashMap<>();
        List<String> productIds = candidates.stream()
            .map(p -> p.getId().getValue())
            .collect(Collectors.toList());

        // Buscar features em lote do cache
        Map<String, Map<String, Double>> cachedFeatures = featureStore.getFeaturesBatch(productIds);

        // Preparar features
        for (Product candidate : candidates) {
            String productId = candidate.getId().getValue();
            Map<String, Double> features;
            
            ScorePair scorePair = scores.getOrDefault(productId, new ScorePair(0.0, 0.0));
            Map<String, Double> dynamicFeatures = featureExtractor.extractDynamicFeatures(
                candidate, query, scorePair.bm25Score(), scorePair.knnScore());

            // Tentar usar features do cache
            if (cachedFeatures.containsKey(productId)) {
                features = new java.util.HashMap<>(cachedFeatures.get(productId));
                features.putAll(dynamicFeatures);
                meterRegistry.counter("search.ml.features.cache.total", "status", "hit").increment();
                logger.debug("Features do cache (estáticas) + dinâmicas calculadas para produto: {}", productId);
            } else {
                // Calcular features on-the-fly
                meterRegistry.counter("search.ml.features.cache.total", "status", "miss").increment();
                
                // Extrair features estáticas
                Map<String, Double> staticFeatures = featureExtractor.extractStaticFeatures(candidate);
                
                // Combinar para o mapa final
                features = new java.util.HashMap<>(staticFeatures);
                features.putAll(dynamicFeatures);
                
                // Cachear apenas as features estáticas (para evitar scores obsoletos no cache)
                try {
                    featureStore.saveFeatures(productId, staticFeatures);
                    logger.debug("Features estáticas calculadas e cacheadas para produto: {}", productId);
                } catch (Exception e) {
                    logger.warn("Erro ao cachear features para produto: {}", productId, e);
                }
            }

            featuresMap.put(productId, features);
        }

        return featuresMap;
    }

    /**
     * Re-ordena produtos baseado no ranking ML e popula dados de depuração se solicitado
     */
    private List<Product> reorderProducts(
            List<Product> candidates, 
            List<MLRankingService.RankedProduct> rankedProducts,
            Map<String, Map<String, Double>> features,
            boolean rankingDebug) {
        
        Map<String, Product> productMap = candidates.stream()
            .collect(Collectors.toMap(p -> p.getId().getValue(), p -> p));

        List<Product> reordered = new ArrayList<>();
        
        // Adicionar produtos na ordem do ranking ML
        for (MLRankingService.RankedProduct ranked : rankedProducts) {
            Product product = productMap.get(ranked.productId());
            if (product != null) {
                if (rankingDebug) {
                    Map<String, Double> productFeatures = features.get(ranked.productId());
                    product.setRankingDebug(new com.marketplace.search.search.domain.valueobjects.RankingDebug(
                        ranked.mlScore(), 
                        productFeatures != null ? productFeatures : Map.of()
                    ));
                }
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
        
        if (query.rankingDebug()) {
            for (Product p : candidates) {
                Map<String, Double> features = new java.util.HashMap<>();
                features.put("fallback_popularity_score", p.getMetrics().getPopularityScore());
                p.setRankingDebug(new com.marketplace.search.search.domain.valueobjects.RankingDebug(
                    p.getMetrics().getPopularityScore(), features));
            }
        }

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

