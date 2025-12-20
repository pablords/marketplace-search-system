package com.marketplace.search.gateway.interfaces.rest.clients;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.marketplace.search.gateway.interfaces.rest.dtos.BrandDTO;
import com.marketplace.search.gateway.interfaces.rest.dtos.CategoryDTO;
import com.marketplace.search.gateway.interfaces.rest.dtos.ProductDTO;
import com.marketplace.search.gateway.interfaces.rest.dtos.SellerDTO;
import com.marketplace.search.gateway.interfaces.rest.dtos.SellerReputationDTO;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Testes unitários para CatalogServiceClient.
 * Testa a comunicação HTTP com o catalog-service usando mocks do WebClient.
 */
class CatalogServiceClientTest {

    private WebClient webClient;
    private WebClient.RequestBodyUriSpec requestBodyUriSpec;
    private WebClient.RequestBodySpec requestBodySpec;
    private WebClient.RequestHeadersSpec requestHeadersSpec;
    private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;
    private WebClient.ResponseSpec responseSpec;
    private CatalogServiceClient catalogServiceClient;

    @BeforeEach
    void setUp() {
        webClient = mock(WebClient.class);
        requestBodyUriSpec = mock(WebClient.RequestBodyUriSpec.class);
        requestBodySpec = mock(WebClient.RequestBodySpec.class);
        requestHeadersSpec = mock(WebClient.RequestHeadersSpec.class);
        requestHeadersUriSpec = mock(WebClient.RequestHeadersUriSpec.class);
        responseSpec = mock(WebClient.ResponseSpec.class);

        catalogServiceClient = new CatalogServiceClient(webClient);
    }

    @Test
    void shouldCreateProductSuccessfully() {
        // Arrange
        ProductDTO productDTO = createSampleProductDTO();
        URI expectedUri = URI.create("/products/prod-001");
        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(expectedUri);

        ResponseEntity<Void> responseEntity = ResponseEntity.status(HttpStatus.CREATED)
                .headers(headers)
                .build();

        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.contentType(any(MediaType.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.toBodilessEntity()).thenReturn(Mono.just(responseEntity));

        // Act & Assert
        StepVerifier.create(catalogServiceClient.createProductAsync(productDTO))
                .expectNext(expectedUri)
                .verifyComplete();
    }

    @Test
    void shouldCreateProductWithLocationHeader() {
        // Arrange
        ProductDTO productDTO = createSampleProductDTO();
        URI expectedUri = URI.create("http://localhost:8081/api/v1/products/prod-001");
        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(expectedUri);

        ResponseEntity<Void> responseEntity = ResponseEntity.status(HttpStatus.CREATED)
                .headers(headers)
                .build();

        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.contentType(any(MediaType.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.toBodilessEntity()).thenReturn(Mono.just(responseEntity));

        // Act & Assert
        StepVerifier.create(catalogServiceClient.createProductAsync(productDTO))
                .expectNext(expectedUri)
                .verifyComplete();
    }

    @Test
    void shouldRetryOnServerError() {
        // Arrange
        ProductDTO productDTO = createSampleProductDTO();

        WebClientResponseException serverError = WebClientResponseException.create(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Internal Server Error",
                HttpHeaders.EMPTY,
                null,
                null);

        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.contentType(any(MediaType.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        ResponseEntity<Void> successResponse = ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.LOCATION, "/products/prod-001")
                .build();
        when(responseSpec.toBodilessEntity())
                .thenReturn(Mono.error(serverError))
                .thenReturn(Mono.just(successResponse));

        // Act & Assert - O retry deve tentar novamente e ter sucesso
        StepVerifier.create(catalogServiceClient.createProductAsync(productDTO))
                .expectNext(URI.create("/products/prod-001"))
                .verifyComplete();
    }

    @Test
    void shouldThrowExceptionOnClientError() {
        // Arrange
        ProductDTO productDTO = createSampleProductDTO();

        WebClientResponseException clientError = WebClientResponseException.create(
                HttpStatus.BAD_REQUEST.value(),
                "Bad Request",
                HttpHeaders.EMPTY,
                null,
                null);

        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.contentType(any(MediaType.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.toBodilessEntity()).thenReturn(Mono.error(clientError));

        // Act & Assert
        StepVerifier.create(catalogServiceClient.createProductAsync(productDTO))
                .expectErrorMatches(throwable -> throwable instanceof CatalogServiceClient.CatalogServiceException
                        && throwable.getMessage().contains("Erro ao criar produto no catalog-service"))
                .verify();
    }

    @Test
    void shouldHandleUnexpectedErrors() {
        // Arrange
        ProductDTO productDTO = createSampleProductDTO();
        RuntimeException unexpectedError = new RuntimeException("Connection timeout");

        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.contentType(any(MediaType.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.toBodilessEntity()).thenReturn(Mono.error(unexpectedError));

        // Act & Assert
        StepVerifier.create(catalogServiceClient.createProductAsync(productDTO))
                .expectErrorMatches(throwable -> throwable instanceof CatalogServiceClient.CatalogServiceException
                        && throwable.getMessage().contains("Erro inesperado ao criar produto no catalog-service"))
                .verify();
    }

    @Test
    void shouldImplementCatalogServicePort() {
        // Arrange
        ProductDTO productDTO = createSampleProductDTO();
        URI expectedUri = URI.create("/products/prod-001");
        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(expectedUri);

        ResponseEntity<Void> responseEntity = ResponseEntity.status(HttpStatus.CREATED)
                .headers(headers)
                .build();

        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.contentType(any(MediaType.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.toBodilessEntity()).thenReturn(Mono.just(responseEntity));

        // Act
        URI result = catalogServiceClient.createProduct(productDTO);

        // Assert
        assertNotNull(result);
        assertEquals(expectedUri, result);
    }

    @Test
    void shouldThrowExceptionWhenProductObjectIsNotProductDTO() {
        // Arrange
        Object invalidObject = "not a ProductDTO";

        // Act & Assert
        assertThrows(CatalogServiceClient.CatalogServiceException.class,
                () -> catalogServiceClient.createProduct(invalidObject),
                "Objeto deve ser do tipo ProductDTO");
    }

    private ProductDTO createSampleProductDTO() {
        CategoryDTO category = new CategoryDTO("cat-001", "Electronics", null, "/electronics/");
        BrandDTO brand = new BrandDTO("brand-001", "Samsung", "Samsung Electronics");
        SellerReputationDTO reputation = SellerReputationDTO.builder()
                .score(4.5)
                .totalReviews(1000)
                .positiveReviews(850)
                .neutralReviews(100)
                .negativeReviews(50)
                .cancellationRate(0.01)
                .deliveryPerformance(0.95)
                .build();
        SellerDTO seller = SellerDTO.builder()
                .id("seller-001")
                .name("Seller Name")
                .type("REGULAR")
                .reputation(reputation)
                .status("ACTIVE")
                .memberSince("2020-01-01T00:00:00Z")
                .build();

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
}

