package com.marketplace.search.infrastructure.elasticsearch;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import com.marketplace.search.domain.entities.Product;
import com.marketplace.search.domain.repositories.ProductSearchRepository;
import com.marketplace.search.domain.valueobjects.ProductId;
import com.marketplace.search.domain.valueobjects.SearchMetrics;
import com.marketplace.search.domain.valueobjects.SearchQuery;
import com.marketplace.search.domain.valueobjects.SearchResult;
import com.marketplace.search.domain.valueobjects.UserContext;
import com.marketplace.search.infrastructure.elasticsearch.documents.ProductDocument;
import com.marketplace.search.infrastructure.elasticsearch.mappers.ElasticsearchProductMapper;
import com.marketplace.search.infrastructure.elasticsearch.queries.ElasticsearchQueryBuilder;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.CountRequest;
import co.elastic.clients.elasticsearch.core.CountResponse;
import co.elastic.clients.elasticsearch.core.GetRequest;
import co.elastic.clients.elasticsearch.core.GetResponse;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;

/**
 * Implementação do repositório de busca usando Elasticsearch
 */
@Repository
public class ElasticsearchProductSearchRepository implements ProductSearchRepository {
    
    private static final Logger logger = LoggerFactory.getLogger(ElasticsearchProductSearchRepository.class);

    @Value("${elasticsearch.indices.products:products}") 
    String INDEX_NAME;
    
    private final ElasticsearchClient elasticsearchClient;
    private final ElasticsearchProductMapper productMapper;
    private final ElasticsearchQueryBuilder queryBuilder;

    public ElasticsearchProductSearchRepository(ElasticsearchClient elasticsearchClient,
                                               ElasticsearchProductMapper productMapper,
                                               ElasticsearchQueryBuilder queryBuilder) {
        this.elasticsearchClient = elasticsearchClient;
        this.productMapper = productMapper;
        this.queryBuilder = queryBuilder;
    }

    @Override
    public SearchResult search(SearchQuery query, UserContext userContext) {
    logger.debug("Executing search on index '{}': query='{}', limit={}", INDEX_NAME, query.terms(), query.limit());
        
        Instant startTime = Instant.now();
        
        try {
            // Construir query do Elasticsearch
            Query esQuery = queryBuilder.buildQuery(query, userContext);
            logger.debug("EsQuery: {}", esQuery);
            
            // Executar busca
            SearchRequest searchRequest = SearchRequest.of(s -> s
                .index(INDEX_NAME)
                .query(esQuery)
                .from(query.offset())
                .size(query.limit())
                .sort(queryBuilder.buildSort(query.sort()))
                .trackTotalHits(th -> th.enabled(true))
            );

            logger.debug("SearchRequest {}", searchRequest);
            
            SearchResponse<ProductDocument> response = elasticsearchClient.search(
                searchRequest, ProductDocument.class);

            var hitsMetadata = response.hits();
            List<Hit<ProductDocument>> hitList = hitsMetadata != null && hitsMetadata.hits() != null
                ? hitsMetadata.hits()
                : List.of();

            var totalHitsMetadata = hitsMetadata != null ? hitsMetadata.total() : null;

            if (hitsMetadata == null || hitsMetadata.hits() == null) {
                logger.warn("Search returned null hits for query '{}'.", query.terms());
            } else if (totalHitsMetadata != null) {
                logger.debug("Search hits total: {}", totalHitsMetadata.value());
            }
            
            // Mapear resultados
            List<Product> products = hitList.stream()
                .map(hit -> productMapper.toDomain(hit.source()))
                .collect(Collectors.toList());
            
            long totalCount = totalHitsMetadata != null
                ? totalHitsMetadata.value()
                : products.size();
            Duration executionTime = Duration.between(startTime, Instant.now());
            
            SearchMetrics metrics = new SearchMetrics(
                100, // QPS estimado
                calculateAverageScore(response.hits().hits()),
                (int) totalCount,
                response.took(),
                false, // Cache usage
                response.shards().toString()
            );
            
            logger.debug("Search completed: found {} products in {}ms", 
                        products.size(), executionTime.toMillis());
            
            return new SearchResult(
                products,
                totalCount,
                query.limit(),
                query.offset() / query.limit(),
                executionTime,
                metrics
            );
            
        } catch (Exception e) {
            logger.error("Error executing search", e);
            throw new RuntimeException("Failed to execute search", e);
        }
    }

    @Override
    public List<Product> findSimilar(ProductId productId, int limit) {
        logger.debug("Finding similar products for: {}", productId);
        
        try {
            // Primeiro, buscar o produto original
            Optional<Product> originalProduct = findById(productId);
            if (originalProduct.isEmpty()) {
                return List.of();
            }
            
            Product product = originalProduct.get();
            
            // Construir query de similaridade
            Query similarityQuery = queryBuilder.buildSimilarityQuery(product);
            
            SearchRequest searchRequest = SearchRequest.of(s -> s
                .index(INDEX_NAME)
                .query(similarityQuery)
                .size(limit)
                .source(src -> src.filter(f -> f.excludes("description"))) // Otimização
            );
            
            SearchResponse<ProductDocument> response = elasticsearchClient.search(
                searchRequest, ProductDocument.class);
            
            return response.hits().hits().stream()
                .map(hit -> productMapper.toDomain(hit.source()))
                .filter(p -> !p.getId().equals(productId)) // Excluir o produto original
                .collect(Collectors.toList());
            
        } catch (Exception e) {
            logger.error("Error finding similar products for: {}", productId, e);
            return List.of();
        }
    }

    @Override
    public List<Product> findByIds(List<ProductId> productIds) {
        logger.debug("Finding products by IDs: {}", productIds.size());
        
        try {
            List<String> ids = productIds.stream()
                .map(ProductId::getValue)
                .collect(Collectors.toList());
            
            Query idsQuery = Query.of(q -> q
                .ids(i -> i.values(ids))
            );
            
            SearchRequest searchRequest = SearchRequest.of(s -> s
                .index(INDEX_NAME)
                .query(idsQuery)
                .size(productIds.size())
            );
            
            SearchResponse<ProductDocument> response = elasticsearchClient.search(
                searchRequest, ProductDocument.class);
            
            return response.hits().hits().stream()
                .map(hit -> productMapper.toDomain(hit.source()))
                .collect(Collectors.toList());
            
        } catch (Exception e) {
            logger.error("Error finding products by IDs", e);
            return List.of();
        }
    }

    @Override
    public Optional<Product> findById(ProductId productId) {
        logger.debug("Finding product by ID: {}", productId);
        
        try {
            GetRequest getRequest = GetRequest.of(g -> g
                .index(INDEX_NAME)
                .id(productId.getValue())
            );
            
            GetResponse<ProductDocument> response = elasticsearchClient.get(
                getRequest, ProductDocument.class);
            
            if (response.found()) {
                Product product = productMapper.toDomain(response.source());
                return Optional.of(product);
            }
            
            return Optional.empty();
            
        } catch (Exception e) {
            logger.error("Error finding product by ID: {}", productId, e);
            return Optional.empty();
        }
    }

    @Override
    public List<String> getSuggestions(String partialTerm, int limit) {
        logger.debug("Getting suggestions for: '{}'", partialTerm);
        
        try {
            Query suggestionQuery = queryBuilder.buildSuggestionQuery(partialTerm);
            
            SearchRequest searchRequest = SearchRequest.of(s -> s
                .index(INDEX_NAME)
                .query(suggestionQuery)
                .size(0) // Não precisamos dos documentos
                .aggregations("suggestions", agg -> agg
                    .terms(t -> t
                        .field("title.keyword")
                        .size(limit)
                    )
                )
            );
            
            SearchResponse<ProductDocument> response = elasticsearchClient.search(
                searchRequest, ProductDocument.class);
            
            return response.aggregations()
                .get("suggestions")
                .sterms()
                .buckets()
                .array()
                .stream()
                .map(bucket -> bucket.key().stringValue())
                .collect(Collectors.toList());
            
        } catch (Exception e) {
            logger.error("Error getting suggestions for: '{}'", partialTerm, e);
            return List.of();
        }
    }

    @Override
    public List<Product> findMostPopular(String categoryId, int limit) {
        logger.debug("Finding most popular products in category: {}", categoryId);
        
        try {
            Query popularityQuery = queryBuilder.buildPopularityQuery(categoryId);
            
            SearchRequest searchRequest = SearchRequest.of(s -> s
                .index(INDEX_NAME)
                .query(popularityQuery)
                .size(limit)
                .sort(sort -> sort
                    .field(f -> f
                        .field("metrics.total_sales")
                        .order(co.elastic.clients.elasticsearch._types.SortOrder.Desc)
                    )
                )
            );
            
            SearchResponse<ProductDocument> response = elasticsearchClient.search(
                searchRequest, ProductDocument.class);
            
            return response.hits().hits().stream()
                .map(hit -> productMapper.toDomain(hit.source()))
                .collect(Collectors.toList());
            
        } catch (Exception e) {
            logger.error("Error finding most popular products", e);
            return List.of();
        }
    }

    @Override
    public List<Product> findOnSale(int limit) {
        logger.debug("Finding products on sale");
        
        try {
            Query saleQuery = queryBuilder.buildOnSaleQuery();
            
            SearchRequest searchRequest = SearchRequest.of(s -> s
                .index(INDEX_NAME)
                .query(saleQuery)
                .size(limit)
                .sort(sort -> sort
                    .field(f -> f
                        .field("price")
                        .order(co.elastic.clients.elasticsearch._types.SortOrder.Asc)
                    )
                )
            );
            
            SearchResponse<ProductDocument> response = elasticsearchClient.search(
                searchRequest, ProductDocument.class);
            
            return response.hits().hits().stream()
                .map(hit -> productMapper.toDomain(hit.source()))
                .collect(Collectors.toList());
            
        } catch (Exception e) {
            logger.error("Error finding products on sale", e);
            return List.of();
        }
    }

    @Override
    public long count(SearchQuery query) {
        logger.debug("Counting products for query: '{}'", query.terms());
        
        try {
            Query esQuery = queryBuilder.buildQuery(query, null);
            
            CountRequest countRequest = CountRequest.of(c -> c
                .index(INDEX_NAME)
                .query(esQuery)
            );
            
            CountResponse response = elasticsearchClient.count(countRequest);
            
            return response.count();
            
        } catch (Exception e) {
            logger.error("Error counting products", e);
            return 0;
        }
    }

    private double calculateAverageScore(List<Hit<ProductDocument>> hits) {
        if (hits.isEmpty()) {
            return 0.0;
        }
        
        // Calcular o score médio bruto do Elasticsearch
        double rawAverageScore = hits.stream()
            .mapToDouble(hit -> {
                Double score = hit.score();
                return score != null ? score : 0.0;
            })
            .average()
            .orElse(0.0);
        
        // Encontrar o score máximo para normalizar
        double maxScore = hits.stream()
            .mapToDouble(hit -> {
                Double score = hit.score();
                return score != null ? score : 0.0;
            })
            .max()
            .orElse(1.0);
        
        // Normalizar para o intervalo [0.0, 1.0]
        return maxScore > 0 ? rawAverageScore / maxScore : 0.0;
    }
}