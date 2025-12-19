package com.marketplace.search.gateway.interfaces.rest.clients;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.marketplace.search.gateway.interfaces.rest.clients.SearchServicePort;
import com.marketplace.search.gateway.interfaces.rest.dtos.BrandDTO;
import com.marketplace.search.gateway.interfaces.rest.dtos.CategoryDTO;
import com.marketplace.search.gateway.interfaces.rest.dtos.ProductDTO;
import com.marketplace.search.gateway.interfaces.rest.dtos.SearchMetricsDTO;
import com.marketplace.search.gateway.interfaces.rest.dtos.SearchResultDTO;
import com.marketplace.search.gateway.interfaces.rest.dtos.SellerDTO;
import com.marketplace.search.gateway.interfaces.rest.dtos.SellerReputationDTO;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Testes unitários para SearchServiceClient.
 * Testa a comunicação HTTP com o search-service usando mocks do WebClient.
 */
class SearchServiceClientTest {

    private WebClient webClient;
    private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;
    private WebClient.RequestHeadersSpec requestHeadersSpec;
    private WebClient.ResponseSpec responseSpec;
    private SearchServiceClient searchServiceClient;

    @BeforeEach
    void setUp() {
        webClient = mock(WebClient.class);
        requestHeadersUriSpec = mock(WebClient.RequestHeadersUriSpec.class);
        requestHeadersSpec = mock(WebClient.RequestHeadersSpec.class);
        responseSpec = mock(WebClient.ResponseSpec.class);

        searchServiceClient = new SearchServiceClient(webClient);
    }

    @Test
    void shouldSearchProductsSuccessfully() {
        // Arrange
        String query = "smartphone";
        SearchResultDTO expectedResult = createSampleSearchResultDTO();

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(any(java.util.function.Function.class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(SearchResultDTO.class))
                .thenReturn(Mono.just(expectedResult));

        // Act & Assert
        StepVerifier.create(searchServiceClient.searchProductsAsync(query, null, null, null, null, null))
                .expectNext(expectedResult)
                .verifyComplete();
    }

    @Test
    void shouldSearchProductsWithAllParameters() {
        // Arrange
        String query = "laptop";
        String categoryId = "cat-001";
        Integer page = 0;
        Integer size = 20;
        String sort = "PRICE_ASC";
        String userId = "user-001";
        SearchResultDTO expectedResult = createSampleSearchResultDTO();

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(any(java.util.function.Function.class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(SearchResultDTO.class))
                .thenReturn(Mono.just(expectedResult));

        // Act & Assert
        StepVerifier.create(searchServiceClient.searchProductsAsync(query, categoryId, page, size, sort, userId))
                .expectNext(expectedResult)
                .verifyComplete();
    }

    @Test
    void shouldRetryOnServerError() {
        // Arrange
        String query = "smartphone";
        SearchResultDTO expectedResult = createSampleSearchResultDTO();

        WebClientResponseException serverError = WebClientResponseException.create(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Internal Server Error",
                HttpHeaders.EMPTY,
                null,
                null);

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(any(java.util.function.Function.class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(SearchResultDTO.class))
                .thenReturn(Mono.error(serverError))
                .thenReturn(Mono.just(expectedResult));

        // Act & Assert - O retry deve tentar novamente e ter sucesso
        StepVerifier.create(searchServiceClient.searchProductsAsync(query, null, null, null, null, null))
                .expectNext(expectedResult)
                .verifyComplete();
    }

    @Test
    void shouldThrowExceptionOnClientError() {
        // Arrange
        String query = "smartphone";

        WebClientResponseException clientError = WebClientResponseException.create(
                HttpStatus.BAD_REQUEST.value(),
                "Bad Request",
                HttpHeaders.EMPTY,
                null,
                null);

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(any(java.util.function.Function.class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(SearchResultDTO.class))
                .thenReturn(Mono.error(clientError));

        // Act & Assert
        StepVerifier.create(searchServiceClient.searchProductsAsync(query, null, null, null, null, null))
                .expectErrorMatches(throwable -> throwable instanceof SearchServicePort.SearchServiceException
                        && throwable.getMessage().contains("Erro ao buscar produtos no search-service"))
                .verify();
    }

    @Test
    void shouldGetSuggestionsSuccessfully() {
        // Arrange
        String term = "smart";
        Integer limit = 10;
        List<String> expectedSuggestions = List.of("smartphone", "smartwatch", "smart tv");

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(any(java.util.function.Function.class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToFlux(String.class))
                .thenReturn(Flux.fromIterable(expectedSuggestions));

        // Act & Assert
        StepVerifier.create(searchServiceClient.getSuggestionsAsync(term, limit))
                .expectNext(expectedSuggestions)
                .verifyComplete();
    }

    @Test
    void shouldGetSuggestionsWithNullLimit() {
        // Arrange
        String term = "smart";
        List<String> expectedSuggestions = List.of("smartphone", "smartwatch");

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(any(java.util.function.Function.class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToFlux(String.class))
                .thenReturn(Flux.fromIterable(expectedSuggestions));

        // Act & Assert
        StepVerifier.create(searchServiceClient.getSuggestionsAsync(term, null))
                .expectNext(expectedSuggestions)
                .verifyComplete();
    }

    @Test
    void shouldReturnEmptyListOnSuggestionsError() {
        // Arrange
        String term = "smart";

        WebClientResponseException error = WebClientResponseException.create(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Internal Server Error",
                HttpHeaders.EMPTY,
                null,
                null);

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(any(java.util.function.Function.class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToFlux(String.class))
                .thenReturn(Flux.error(error));

        // Act & Assert - Deve retornar lista vazia em caso de erro
        StepVerifier.create(searchServiceClient.getSuggestionsAsync(term, null))
                .expectNext(Collections.emptyList())
                .verifyComplete();
    }

    @Test
    void shouldGetProductSuccessfully() {
        // Arrange
        String productId = "prod-001";
        ProductDTO expectedProduct = createSampleProductDTO();

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString(), anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(com.marketplace.search.gateway.interfaces.rest.dtos.ProductDTO.class))
                .thenReturn(Mono.just(expectedProduct));

        // Act & Assert
        StepVerifier.create(searchServiceClient.getProductAsync(productId))
                .expectNext(expectedProduct)
                .verifyComplete();
    }

    @Test
    void shouldThrowExceptionWhenProductNotFound() {
        // Arrange
        String productId = "non-existent";

        WebClientResponseException notFoundError = WebClientResponseException.create(
                HttpStatus.NOT_FOUND.value(),
                "Not Found",
                HttpHeaders.EMPTY,
                null,
                null);

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString(), anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(ProductDTO.class))
                .thenReturn(Mono.error(notFoundError));

        // Act & Assert
        StepVerifier.create(searchServiceClient.getProductAsync(productId))
                .expectErrorMatches(throwable -> throwable instanceof SearchServicePort.SearchServiceException
                        && throwable.getMessage().contains("Produto não encontrado"))
                .verify();
    }

    @Test
    void shouldHandleUnexpectedErrors() {
        // Arrange
        String query = "smartphone";
        RuntimeException unexpectedError = new RuntimeException("Connection timeout");

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(any(java.util.function.Function.class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(SearchResultDTO.class))
                .thenReturn(Mono.error(unexpectedError));

        // Act & Assert
        StepVerifier.create(searchServiceClient.searchProductsAsync(query, null, null, null, null, null))
                .expectErrorMatches(throwable -> throwable instanceof SearchServicePort.SearchServiceException
                        && throwable.getMessage().contains("Erro inesperado ao buscar produtos no search-service"))
                .verify();
    }

    @Test
    void shouldRetryOnServerErrorForGetProduct() {
        // Arrange
        String productId = "prod-001";
        ProductDTO expectedProduct = createSampleProductDTO();

        WebClientResponseException serverError = WebClientResponseException.create(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Internal Server Error",
                HttpHeaders.EMPTY,
                null,
                null);

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString(), anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(ProductDTO.class))
                .thenReturn(Mono.error(serverError))
                .thenReturn(Mono.just(expectedProduct));

        // Act & Assert - O retry deve tentar novamente e ter sucesso
        StepVerifier.create(searchServiceClient.getProductAsync(productId))
                .expectNext(expectedProduct)
                .verifyComplete();
    }

    private SearchResultDTO createSampleSearchResultDTO() {
        ProductDTO product1 = createSampleProductDTO();
        ProductDTO product2 = ProductDTO.builder()
                .id("prod-002")
                .title("Laptop")
                .description("A great laptop")
                .price(new BigDecimal("1299.99"))
                .currency("USD")
                .category(new CategoryDTO("cat-001", "Electronics", null, "/electronics/"))
                .brand(new BrandDTO("brand-002", "Dell", "Dell Technologies"))
                .seller(createSampleSellerDTO())
                .images(List.of("laptop1.jpg"))
                .attributes(Collections.emptySet())
                .tags(Set.of("laptop", "electronics"))
                .stockQuantity(50)
                .condition("NEW")
                .isActive(true)
                .build();

        SearchMetricsDTO metrics = SearchMetricsDTO.builder()
                .queriesPerSecond(100)
                .averageScore(0.85)
                .indexedDocuments(10000)
                .indexSize(5000000L)
                .usedCache(true)
                .shardInfo("shard-1")
                .build();

        return SearchResultDTO.builder()
                .products(List.of(product1, product2))
                .totalCount(2)
                .pageSize(20)
                .pageNumber(0)
                .totalPages(1)
                .hasNextPage(false)
                .hasPreviousPage(false)
                .executionTimeMs(50)
                .metrics(metrics)
                .build();
    }

    private ProductDTO createSampleProductDTO() {
        CategoryDTO category = new CategoryDTO("cat-001", "Electronics", null, "/electronics/");
        BrandDTO brand = new BrandDTO("brand-001", "Samsung", "Samsung Electronics");
        SellerDTO seller = createSampleSellerDTO();

        return ProductDTO.builder()
                .id("prod-001")
                .title("Smartphone")
                .description("A great smartphone")
                .price(new BigDecimal("999.99"))
                .currency("USD")
                .category(category)
                .brand(brand)
                .seller(seller)
                .images(List.of("image1.jpg", "image2.jpg"))
                .attributes(Set.of("color:black", "storage:128GB"))
                .tags(Set.of("smartphone", "electronics"))
                .stockQuantity(100)
                .condition("NEW")
                .isActive(true)
                .build();
    }

    private SellerDTO createSampleSellerDTO() {
        SellerReputationDTO reputation = SellerReputationDTO.builder()
                .score(4.5)
                .totalReviews(1000)
                .positiveReviews(850)
                .neutralReviews(100)
                .negativeReviews(50)
                .cancellationRate(0.01)
                .deliveryPerformance(0.95)
                .build();

        return SellerDTO.builder()
                .id("seller-001")
                .name("Seller Name")
                .type("REGULAR")
                .reputation(reputation)
                .status("ACTIVE")
                .memberSince("2020-01-01T00:00:00Z")
                .build();
    }
}

