package com.marketplace.search.indexing.infrastructure.embedding;

import java.time.Duration;
import java.util.ArrayList;
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
 */
@Component
public class EmbeddingClient {

    private static final Logger logger = LoggerFactory.getLogger(EmbeddingClient.class);
    
    private final WebClient webClient;
    private final int maxRetries;
    private final Duration timeout;

    public EmbeddingClient(
            @Value("${embedding.service.url:http://embedding-service:8085}") String baseUrl,
            @Value("${embedding.service.timeout-seconds:10}") int timeoutSeconds,
            @Value("${embedding.service.max-retries:3}") int maxRetries,
            WebClient.Builder webClientBuilder) {
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.maxRetries = maxRetries;
        
        this.webClient = webClientBuilder
            .baseUrl(baseUrl)
            .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .build();
    }

    /**
     * Gera embeddings para uma lista de textos (produtos)
     * 
     * @param texts Lista de textos para gerar embeddings
     * @return Lista de vetores de embedding (float[]) ou Optional.empty() em caso de erro
     */
    public Optional<List<float[]>> generateEmbeddings(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            logger.warn("Lista de textos vazia ou nula para geração de embeddings");
            return Optional.empty();
        }

        // Limitar batch size para evitar sobrecarga
        if (texts.size() > 100) {
            logger.warn("Lista de textos excede 100 itens ({}), limitando a 100", texts.size());
            texts = texts.subList(0, 100);
        }

        try {
            EmbeddingRequest request = new EmbeddingRequest(texts, "product");
            
            EmbeddingResponse response = webClient.post()
                .uri("/api/v1/embeddings/generate")
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

            // Converter para lista de float[]
            List<float[]> embeddings = new ArrayList<>();
            for (EmbeddingItem item : response.embeddings) {
                if (item.vector != null && !item.vector.isEmpty()) {
                    float[] vector = new float[item.vector.size()];
                    for (int i = 0; i < item.vector.size(); i++) {
                        vector[i] = item.vector.get(i).floatValue();
                    }
                    embeddings.add(vector);
                }
            }

            logger.info("Embedding Service retornou {} embeddings de {} textos solicitados (modelo: {}, dimensão: {})",
                embeddings.size(), texts.size(), response.modelVersion, response.dimension);

            return Optional.of(embeddings);

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

