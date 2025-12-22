package com.marketplace.search.search.infrastructure.embedding;

import java.time.Duration;
import java.util.List;
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

import reactor.util.retry.Retry;

/**
 * Cliente HTTP para comunicação com o Embedding Service
 * Implementa retry com backoff exponencial e fallback gracioso
 * Usado para gerar embeddings de queries de busca
 */
@Component
public class EmbeddingClient {

    private static final Logger logger = LoggerFactory.getLogger(EmbeddingClient.class);
    
    private final WebClient webClient;
    private final int maxRetries;
    private final Duration timeout;
    private final boolean enabled;

    public EmbeddingClient(
            @Value("${embedding.service.url:http://embedding-service:8085}") String baseUrl,
            @Value("${embedding.service.timeout-seconds:5}") int timeoutSeconds,
            @Value("${embedding.service.max-retries:3}") int maxRetries,
            @Value("${embedding.service.enabled:true}") boolean enabled,
            WebClient.Builder webClientBuilder) {
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.maxRetries = maxRetries;
        this.enabled = enabled;
        
        this.webClient = webClientBuilder
            .baseUrl(baseUrl)
            .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .build();
    }

    /**
     * Gera embedding para uma query de busca
     * 
     * @param query Texto da query de busca
     * @return Vetor de embedding (float[]) ou Optional.empty() em caso de erro
     */
    public Optional<float[]> generateQueryEmbedding(String query) {
        if (!enabled) {
            logger.debug("Embedding Service desabilitado. Retornando vazio.");
            return Optional.empty();
        }

        if (query == null || query.trim().isEmpty()) {
            logger.warn("Query vazia ou nula para geração de embedding");
            return Optional.empty();
        }

        try {
            EmbeddingRequest request = new EmbeddingRequest(List.of(query), "query");
            
            EmbeddingResponse response = webClient.post()
                .uri("/api/v1/embeddings/query")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(EmbeddingResponse.class)
                .timeout(timeout)
                .retryWhen(Retry.backoff(maxRetries, Duration.ofMillis(100))
                    .filter(this::isRetryableError)
                    .doBeforeRetry(retrySignal -> 
                        logger.warn("Tentativa {} de {} para Embedding Service", 
                            retrySignal.totalRetries() + 1, maxRetries)))
                .doOnError(error -> logger.error("Erro ao chamar Embedding Service: {}", error.getMessage()))
                .block();

            if (response == null || response.embeddings == null || response.embeddings.isEmpty()) {
                logger.warn("Resposta vazia do Embedding Service");
                return Optional.empty();
            }

            // Extrair o primeiro embedding (já que enviamos apenas uma query)
            EmbeddingItem item = response.embeddings.get(0);
            if (item.vector == null || item.vector.isEmpty()) {
                logger.warn("Embedding vazio retornado pelo Embedding Service");
                return Optional.empty();
            }

            // Converter para float[]
            float[] embedding = new float[item.vector.size()];
            for (int i = 0; i < item.vector.size(); i++) {
                embedding[i] = item.vector.get(i).floatValue();
            }

            logger.debug("Embedding gerado para query '{}' (modelo: {}, dimensão: {})",
                query.substring(0, Math.min(50, query.length())), response.modelVersion, response.dimension);

            return Optional.of(embedding);

        } catch (WebClientResponseException e) {
            logger.error("Erro HTTP ao chamar Embedding Service: {} - {}", e.getStatusCode(), e.getMessage());
            return Optional.empty();
        } catch (WebClientException e) {
            logger.error("Erro de conexão com Embedding Service: {}", e.getMessage());
            return Optional.empty();
        } catch (Exception e) {
            logger.error("Erro inesperado ao chamar Embedding Service", e);
            return Optional.empty();
        }
    }

    /**
     * Verifica se o Embedding Service está disponível
     * 
     * @return true se o serviço está disponível, false caso contrário
     */
    public boolean isAvailable() {
        if (!enabled) {
            return false;
        }

        try {
            HealthResponse response = webClient.get()
                .uri("/health")
                .retrieve()
                .bodyToMono(HealthResponse.class)
                .timeout(Duration.ofSeconds(2))
                .block();

            boolean available = response != null && "healthy".equals(response.status);
            logger.debug("Embedding Service disponível: {}", available);
            return available;

        } catch (Exception e) {
            logger.debug("Embedding Service não disponível: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Verifica se o serviço está habilitado
     * 
     * @return true se habilitado, false caso contrário
     */
    public boolean isEnabled() {
        return enabled;
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
    private record EmbeddingRequest(
        @JsonProperty("texts") List<String> texts,
        @JsonProperty("type") String type
    ) {}

    private record EmbeddingResponse(
        @JsonProperty("embeddings") List<EmbeddingItem> embeddings,
        @JsonProperty("model_version") String modelVersion,
        @JsonProperty("dimension") int dimension
    ) {}

    private record EmbeddingItem(
        @JsonProperty("text") String text,
        @JsonProperty("vector") List<Double> vector
    ) {}

    private record HealthResponse(
        @JsonProperty("status") String status,
        @JsonProperty("service") String service,
        @JsonProperty("version") String version,
        @JsonProperty("model_loaded") Boolean modelLoaded
    ) {}
}

