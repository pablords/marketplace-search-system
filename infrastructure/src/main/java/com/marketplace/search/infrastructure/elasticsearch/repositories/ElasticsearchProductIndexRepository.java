package com.marketplace.search.infrastructure.elasticsearch.repositories;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import com.marketplace.search.domain.entities.Product;
import com.marketplace.search.domain.repositories.ProductIndexRepository;
import com.marketplace.search.domain.valueobjects.ProductId;
import com.marketplace.search.infrastructure.elasticsearch.documents.ProductDocument;
import com.marketplace.search.infrastructure.elasticsearch.mappers.ElasticsearchProductMapper;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.CountRequest;
import co.elastic.clients.elasticsearch.core.CountResponse;
import co.elastic.clients.elasticsearch.core.DeleteByQueryRequest;
import co.elastic.clients.elasticsearch.core.DeleteByQueryResponse;
import co.elastic.clients.elasticsearch.core.DeleteRequest;
import co.elastic.clients.elasticsearch.core.DeleteResponse;
import co.elastic.clients.elasticsearch.core.GetRequest;
import co.elastic.clients.elasticsearch.core.GetResponse;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import co.elastic.clients.elasticsearch.core.MgetRequest;
import co.elastic.clients.elasticsearch.core.MgetResponse;
import co.elastic.clients.elasticsearch.core.UpdateRequest;
import co.elastic.clients.elasticsearch.core.UpdateResponse;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;

/**
 * Implementação do repositório de indexação de produtos usando Elasticsearch
 */
@Repository
public class ElasticsearchProductIndexRepository implements ProductIndexRepository {

    private static final Logger logger = LoggerFactory.getLogger(ElasticsearchProductIndexRepository.class);

    private final ElasticsearchClient client;
    private final ElasticsearchProductMapper mapper;
    private final String indexName;

    public ElasticsearchProductIndexRepository(
            ElasticsearchClient client,
            ElasticsearchProductMapper mapper,
            @Value("${elasticsearch.indices.products:products}") String indexName) {
        this.client = client;
        this.mapper = mapper;
        this.indexName = indexName;
        
        // Criar índice se não existir
        createIndexIfNotExists();
    }

    @Override
    public void indexProduct(Product product) {
        try {
            ProductDocument document = mapper.toDocument(product);
            
            IndexRequest<ProductDocument> request = IndexRequest.of(i -> i
                .index(indexName)
                .id(product.getId().getValue())
                .document(document)
                .refresh(Refresh.WaitFor)
            );

            IndexResponse response = client.index(request);
            
            logger.debug("Product indexed successfully: {} with version: {}", 
                product.getId().getValue(), response.version());
                
        } catch (IOException | ElasticsearchException e) {
            logger.error("Failed to index product: {}", product.getId().getValue(), e);
            throw new RuntimeException("Failed to index product", e);
        }
    }

    @Override
    public void indexProducts(List<Product> products) {
        if (products.isEmpty()) {
            return;
        }

        try {
            BulkRequest.Builder bulkBuilder = new BulkRequest.Builder()
                .index(indexName)
                .refresh(Refresh.WaitFor);

            for (Product product : products) {
                ProductDocument document = mapper.toDocument(product);
                
                bulkBuilder.operations(op -> op
                    .index(idx -> idx
                        .id(product.getId().getValue())
                        .document(document)
                    )
                );
            }

            BulkResponse response = client.bulk(bulkBuilder.build());

            if (response.errors()) {
                long errorCount = response.items().stream()
                    .mapToLong(item -> item.error() != null ? 1 : 0)
                    .sum();
                logger.warn("Bulk indexing completed with {} errors out of {} products", 
                    errorCount, products.size());
            } else {
                logger.debug("Successfully bulk indexed {} products", products.size());
            }

        } catch (IOException | ElasticsearchException e) {
            logger.error("Failed to bulk index {} products", products.size(), e);
            throw new RuntimeException("Failed to bulk index products", e);
        }
    }

    @Override
    public void updateProduct(Product product) {
        try {
            ProductDocument document = mapper.toDocument(product);
            
            UpdateRequest<ProductDocument, ProductDocument> request = UpdateRequest.of(u -> u
                .index(indexName)
                .id(product.getId().getValue())
                .doc(document)
                .refresh(Refresh.WaitFor)
                .docAsUpsert(true) // Cria se não existir
            );

            UpdateResponse<ProductDocument> response = client.update(request, ProductDocument.class);
            
            logger.debug("Product updated successfully: {} with version: {}", 
                product.getId().getValue(), response.version());

        } catch (IOException | ElasticsearchException e) {
            logger.error("Failed to update product: {}", product.getId().getValue(), e);
            throw new RuntimeException("Failed to update product", e);
        }
    }

    @Override
    public void deleteProduct(ProductId productId) {
        try {
            DeleteRequest request = DeleteRequest.of(d -> d
                .index(indexName)
                .id(productId.getValue())
                .refresh(Refresh.WaitFor)
            );

            DeleteResponse response = client.delete(request);
            
            logger.debug("Product deleted successfully: {} with result: {}", 
                productId.getValue(), response.result());

        } catch (IOException | ElasticsearchException e) {
            logger.error("Failed to delete product: {}", productId.getValue(), e);
            throw new RuntimeException("Failed to delete product", e);
        }
    }

    @Override
    public void deleteProducts(List<ProductId> productIds) {
        if (productIds.isEmpty()) {
            return;
        }

        try {
            BulkRequest.Builder bulkBuilder = new BulkRequest.Builder()
                .index(indexName)
                .refresh(Refresh.WaitFor);

            for (ProductId productId : productIds) {
                bulkBuilder.operations(op -> op
                    .delete(del -> del
                        .id(productId.getValue())
                    )
                );
            }

            BulkResponse response = client.bulk(bulkBuilder.build());

            if (response.errors()) {
                long errorCount = response.items().stream()
                    .mapToLong(item -> item.error() != null ? 1 : 0)
                    .sum();
                logger.warn("Bulk deletion completed with {} errors out of {} products", 
                    errorCount, productIds.size());
            } else {
                logger.debug("Successfully bulk deleted {} products", productIds.size());
            }

        } catch (IOException | ElasticsearchException e) {
            logger.error("Failed to bulk delete {} products", productIds.size(), e);
            throw new RuntimeException("Failed to bulk delete products", e);
        }
    }

    @Override
    public Optional<Product> findById(ProductId productId) {
        try {
            GetRequest request = GetRequest.of(g -> g
                .index(indexName)
                .id(productId.getValue())
            );

            GetResponse<ProductDocument> response = client.get(request, ProductDocument.class);

            if (response.found()) {
                ProductDocument document = response.source();
                Product product = mapper.toDomain(document);
                return Optional.of(product);
            }

            return Optional.empty();

        } catch (IOException | ElasticsearchException e) {
            logger.error("Failed to find product: {}", productId.getValue(), e);
            throw new RuntimeException("Failed to find product", e);
        }
    }

    @Override
    public List<Product> findByIds(List<ProductId> productIds) {
        if (productIds.isEmpty()) {
            return List.of();
        }

        try {
            MgetRequest request = MgetRequest.of(m -> m
                .index(indexName)
                .ids(productIds.stream()
                    .map(ProductId::getValue)
                    .collect(Collectors.toList())
                )
            );

            MgetResponse<ProductDocument> response = client.mget(request, ProductDocument.class);

            return response.docs().stream()
                .filter(doc -> doc.result().found())
                .map(doc -> mapper.toDomain(doc.result().source()))
                .collect(Collectors.toList());

        } catch (IOException | ElasticsearchException e) {
            logger.error("Failed to find products by IDs", e);
            throw new RuntimeException("Failed to find products by IDs", e);
        }
    }

    @Override
    public boolean exists(ProductId productId) {
        try {
            co.elastic.clients.elasticsearch.core.ExistsRequest request = 
                co.elastic.clients.elasticsearch.core.ExistsRequest.of(e -> e
                    .index(indexName)
                    .id(productId.getValue())
                );

            return client.exists(request).value();

        } catch (IOException | ElasticsearchException e) {
            logger.error("Failed to check if product exists: {}", productId.getValue(), e);
            throw new RuntimeException("Failed to check if product exists", e);
        }
    }

    @Override
    public long count() {
        try {
            CountRequest request = CountRequest.of(c -> c
                .index(indexName)
            );

            CountResponse response = client.count(request);
            return response.count();

        } catch (IOException | ElasticsearchException e) {
            logger.error("Failed to count products", e);
            throw new RuntimeException("Failed to count products", e);
        }
    }

    @Override
    public void refreshIndex() {
        try {
            client.indices().refresh(r -> r.index(indexName));
            logger.debug("Index refreshed: {}", indexName);

        } catch (IOException | ElasticsearchException e) {
            logger.error("Failed to refresh index: {}", indexName, e);
            throw new RuntimeException("Failed to refresh index", e);
        }
    }

    @Override
    public void deleteAll() {
        try {
            DeleteByQueryRequest request = DeleteByQueryRequest.of(d -> d
                .index(indexName)
                .query(q -> q.matchAll(m -> m))
                .refresh(true)
            );

            DeleteByQueryResponse response = client.deleteByQuery(request);
            
            logger.info("Deleted all {} products from index: {}", 
                response.deleted(), indexName);

        } catch (IOException | ElasticsearchException e) {
            logger.error("Failed to delete all products from index: {}", indexName, e);
            throw new RuntimeException("Failed to delete all products", e);
        }
    }

    @Override
    public void optimize() {
        try {
            client.indices().forcemerge(f -> f
                .index(indexName)
                .maxNumSegments(1L)
                .flush(true)
            );
            
            logger.info("Optimized index: {}", indexName);

        } catch (IOException | ElasticsearchException e) {
            logger.error("Failed to optimize index: {}", indexName, e);
            throw new RuntimeException("Failed to optimize index", e);
        }
    }

    /**
     * Cria o índice se não existir
     */
    private void createIndexIfNotExists() {
        try {
            co.elastic.clients.elasticsearch.indices.ExistsRequest existsRequest = 
                co.elastic.clients.elasticsearch.indices.ExistsRequest.of(e -> e.index(indexName));
            
            if (!client.indices().exists(existsRequest).value()) {
                CreateIndexRequest createRequest = CreateIndexRequest.of(c -> c
                    .index(indexName)
                    .mappings(m -> m
                        .properties("id", p -> p
                            .keyword(k -> k.store(true))
                        )
                        .properties("title", p -> p
                            .text(t -> t
                                .analyzer("standard")
                                .searchAnalyzer("standard")
                            )
                        )
                        .properties("description", p -> p
                            .text(t -> t
                                .analyzer("standard")
                            )
                        )
                        .properties("price", p -> p
                            .double_(d -> d)
                        )
                        .properties("category", p -> p
                            .object(o -> o
                                .properties("id", pp -> pp.keyword(k -> k))
                                .properties("name", pp -> pp.text(t -> t))
                                .properties("path", pp -> pp.keyword(k -> k))
                            )
                        )
                        .properties("brand", p -> p
                            .object(o -> o
                                .properties("id", pp -> pp.keyword(k -> k))
                                .properties("name", pp -> pp.text(t -> t))
                            )
                        )
                        .properties("seller", p -> p
                            .object(o -> o
                                .properties("id", pp -> pp.keyword(k -> k))
                                .properties("name", pp -> pp.text(t -> t))
                                .properties("status", pp -> pp.keyword(k -> k))
                            )
                        )
                        .properties("status", p -> p
                            .object(o -> o
                                .properties("is_active", pp -> pp.boolean_(b -> b))
                                .properties("is_suspended", pp -> pp.boolean_(b -> b))
                                .properties("has_stock", pp -> pp.boolean_(b -> b))
                            )
                        )
                        .properties("metrics", p -> p
                            .object(o -> o
                                .properties("total_sales", pp -> pp.long_(l -> l))
                                .properties("total_views", pp -> pp.long_(l -> l))
                                .properties("total_reviews", pp -> pp.long_(l -> l))
                                .properties("average_rating", pp -> pp.double_(d -> d))
                            )
                        )
                        .properties("searchable_text", p -> p
                            .text(t -> t.analyzer("standard"))
                        )
                        .properties("created_at", p -> p
                            .date(d -> d.format("yyyy-MM-dd'T'HH:mm:ss.SSSZ"))
                        )
                        .properties("updated_at", p -> p
                            .date(d -> d.format("yyyy-MM-dd'T'HH:mm:ss.SSSZ"))
                        )
                    )
                    .settings(s -> s
                        .numberOfShards("1")
                        .numberOfReplicas("0")
                        .refreshInterval(t -> t.time("1s"))
                    )
                );

                client.indices().create(createRequest);
                logger.info("Created Elasticsearch index: {}", indexName);
            }

        } catch (IOException | ElasticsearchException e) {
            logger.error("Failed to create index: {}", indexName, e);
            throw new RuntimeException("Failed to create index", e);
        }
    }
}