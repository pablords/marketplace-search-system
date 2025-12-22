package com.marketplace.search.search.infrastructure.ml;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.marketplace.search.search.domain.services.MLRankingService;

import reactor.util.retry.Retry;

/**
 * Cliente HTTP para comunicação com o ML Ranking Service
 * Implementa retry com backoff exponencial e fallback gracioso
 */
@Component
public class MlRankingClient implements MLRankingService {

    private static final Logger logger = LoggerFactory.getLogger(MlRankingClient.class);
    
    private final WebClient webClient;
    private final int maxRetries;
    private final Duration timeout;

    public MlRankingClient(
            @Value("${ml.ranking.service.url:http://ml-ranking-service:8084}") String baseUrl,
            @Value("${ml.ranking.service.timeout-seconds:5}") int timeoutSeconds,
            @Value("${ml.ranking.service.max-retries:3}") int maxRetries,
            WebClient.Builder webClientBuilder) {
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.maxRetries = maxRetries;
        
        this.webClient = webClientBuilder
            .baseUrl(baseUrl)
            .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .build();
    }

    @Override
    public Optional<List<RankedProduct>> rank(List<FeatureVector> candidates, String query) {
        if (candidates == null || candidates.isEmpty()) {
            logger.warn("Lista de candidatos vazia ou nula para ranking ML");
            return Optional.empty();
        }

        if (candidates.size() > 400) {
            logger.warn("Lista de candidatos excede 400 itens ({}), limitando a 400", candidates.size());
            candidates = candidates.subList(0, 400);
        }

        try {
            RankRequest request = buildRankRequest(candidates, query);
            
            RankResponse response = webClient.post()
                .uri("/api/v1/ml/rank")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(RankResponse.class)
                .timeout(timeout)
                .retryWhen(Retry.backoff(maxRetries, Duration.ofMillis(100))
                    .filter(this::isRetryableError)
                    .doBeforeRetry(retrySignal -> 
                        logger.warn("Tentativa {} de {} para ML Ranking Service", 
                            retrySignal.totalRetries() + 1, maxRetries)))
                .doOnError(error -> logger.error("Erro ao chamar ML Ranking Service: {}", error.getMessage()))
                .block();

            if (response == null || response.rankedProducts == null) {
                logger.warn("Resposta vazia do ML Ranking Service");
                return Optional.empty();
            }

            List<RankedProduct> rankedProducts = response.rankedProducts.stream()
                .map(rp -> new RankedProduct(rp.productId, rp.mlScore, rp.rank))
                .toList();

            logger.info("ML Ranking Service retornou {} produtos ranqueados de {} candidatos (modelo: {})",
                rankedProducts.size(), response.totalCandidates, response.modelVersion);

            return Optional.of(rankedProducts);

        } catch (WebClientResponseException e) {
            logger.error("Erro HTTP ao chamar ML Ranking Service: {} - {}", e.getStatusCode(), e.getMessage());
            return Optional.empty();
        } catch (WebClientException e) {
            logger.error("Erro de conexão com ML Ranking Service: {}", e.getMessage());
            return Optional.empty();
        } catch (Exception e) {
            logger.error("Erro inesperado ao chamar ML Ranking Service", e);
            return Optional.empty();
        }
    }

    @Override
    public boolean isAvailable() {
        try {
            HealthResponse response = webClient.get()
                .uri("/health")
                .retrieve()
                .bodyToMono(HealthResponse.class)
                .timeout(Duration.ofSeconds(2))
                .block();

            boolean available = response != null && "healthy".equals(response.status);
            logger.debug("ML Ranking Service disponível: {}", available);
            return available;

        } catch (Exception e) {
            logger.debug("ML Ranking Service não disponível: {}", e.getMessage());
            return false;
        }
    }

    private RankRequest buildRankRequest(List<FeatureVector> candidates, String query) {
        List<FeatureVectorDTO> candidateDTOs = new ArrayList<>();
        
        for (FeatureVector candidate : candidates) {
            Map<String, Double> features = candidate.features();
            
            // Extrair features individuais do mapa
            FeatureVectorDTO dto = new FeatureVectorDTO(
                candidate.productId(),
                features.getOrDefault("bm25_score", 0.0),
                features.getOrDefault("knn_score", 0.0),
                features.getOrDefault("hybrid_score", 0.0),
                features.getOrDefault("exact_match", 0.0),
                features.getOrDefault("term_coverage", 0.0),
                features.getOrDefault("title_length", 0.0),
                features.getOrDefault("description_length", 0.0),
                features.getOrDefault("title_description_ratio", 0.0),
                features.getOrDefault("text_quality_score", 0.0),
                features.getOrDefault("first_word_match", 0.0),
                features.getOrDefault("has_numbers", 0.0),
                features.getOrDefault("brand_match", 0.0),
                features.getOrDefault("category_match", 0.0),
                features.getOrDefault("popularity_score", 0.0),
                features.getOrDefault("quality_score", 0.0),
                features.getOrDefault("ctr", 0.0),
                features.getOrDefault("sales_count_normalized", 0.0)
            );
            
            candidateDTOs.add(dto);
        }
        
        return new RankRequest(candidateDTOs, query);
    }

    private boolean isRetryableError(Throwable throwable) {
        if (throwable instanceof WebClientResponseException e) {
            // Retry apenas para erros 5xx (servidor) e 408 (timeout)
            int statusCode = e.getStatusCode().value();
            return statusCode >= 500 || statusCode == 408;
        }
        // Retry para erros de conexão/timeout
        return throwable instanceof WebClientException;
    }

    // DTOs para serialização JSON
    private record RankRequest(
        @JsonProperty("candidates") List<FeatureVectorDTO> candidates,
        @JsonProperty("query") String query
    ) {}

    private record FeatureVectorDTO(
        @JsonProperty("product_id") String productId,
        @JsonProperty("bm25_score") double bm25Score,
        @JsonProperty("knn_score") double knnScore,
        @JsonProperty("hybrid_score") double hybridScore,
        @JsonProperty("exact_match") double exactMatch,
        @JsonProperty("term_coverage") double termCoverage,
        @JsonProperty("title_length") double titleLength,
        @JsonProperty("description_length") double descriptionLength,
        @JsonProperty("title_description_ratio") double titleDescriptionRatio,
        @JsonProperty("text_quality_score") double textQualityScore,
        @JsonProperty("first_word_match") double firstWordMatch,
        @JsonProperty("has_numbers") double hasNumbers,
        @JsonProperty("brand_match") double brandMatch,
        @JsonProperty("category_match") double categoryMatch,
        @JsonProperty("popularity_score") double popularityScore,
        @JsonProperty("quality_score") double qualityScore,
        @JsonProperty("ctr") double ctr,
        @JsonProperty("sales_count_normalized") double salesCountNormalized
    ) {}

    private record RankResponse(
        @JsonProperty("ranked_products") List<RankedProductDTO> rankedProducts,
        @JsonProperty("total_candidates") int totalCandidates,
        @JsonProperty("model_version") String modelVersion
    ) {}

    private record RankedProductDTO(
        @JsonProperty("product_id") String productId,
        @JsonProperty("ml_score") double mlScore,
        @JsonProperty("rank") int rank
    ) {}

    private record HealthResponse(
        @JsonProperty("status") String status,
        @JsonProperty("service") String service,
        @JsonProperty("version") String version
    ) {}
}

