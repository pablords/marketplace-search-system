package com.marketplace.search.search.infrastructure.opensearch.repositories;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.CountRequest;
import org.opensearch.client.opensearch.core.CountResponse;
import org.opensearch.client.opensearch.core.GetRequest;
import org.opensearch.client.opensearch.core.GetResponse;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.Hit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import com.marketplace.search.search.domain.entities.Product;
import com.marketplace.search.search.domain.repositories.ProductSearchRepository;
import com.marketplace.search.search.domain.valueobjects.ProductId;
import com.marketplace.search.search.domain.valueobjects.SearchMetrics;
import com.marketplace.search.search.domain.valueobjects.SearchQuery;
import com.marketplace.search.search.domain.valueobjects.SearchResult;
import com.marketplace.search.search.domain.valueobjects.UserContext;
import com.marketplace.search.search.infrastructure.opensearch.documents.ProductSearchDocument;
import com.marketplace.search.search.infrastructure.opensearch.mappers.OpenSearchProductMapper;
import com.marketplace.search.search.infrastructure.opensearch.queries.OpenSearchQueryBuilder;

/**
 * Implementação do repositório de busca usando OpenSearch
 */
@Repository
public class OpenSearchProductSearchRepository implements ProductSearchRepository {

	private static final Logger logger = LoggerFactory.getLogger(OpenSearchProductSearchRepository.class);

	@Value("${opensearch.indices.products:products_index}")
	String INDEX_NAME;

	private final OpenSearchClient openSearchClient;
	private final OpenSearchProductMapper productMapper;
	private final OpenSearchQueryBuilder queryBuilder;
	private final ExecutorService executorService;

	public OpenSearchProductSearchRepository(OpenSearchClient openSearchClient,
			OpenSearchProductMapper productMapper, OpenSearchQueryBuilder queryBuilder) {
		this.openSearchClient = openSearchClient;
		this.productMapper = productMapper;
		this.queryBuilder = queryBuilder;
		// Executor para buscas paralelas
		this.executorService = Executors.newFixedThreadPool(2);
	}

	@Override
	public SearchResult search(SearchQuery query, UserContext userContext) {
		logger.debug("Executando busca no índice '{}': query='{}', limit={}", INDEX_NAME, query.terms(),
				query.limit());

		Instant startTime = Instant.now();

		try {
			// Construir query BM25 (o método search() não usa busca híbrida, apenas BM25)
			// A busca híbrida é feita apenas em searchCandidatesWithScores()
			Query osQuery = queryBuilder.buildBm25Query(query, userContext);
			
			logger.debug("OpenSearch Query: {}", osQuery);

			// Executar busca
			SearchRequest searchRequest = SearchRequest.of(s -> s.index(INDEX_NAME).query(osQuery)
					.from(query.offset()).size(query.limit()).sort(queryBuilder.buildSort(query.sort()))
					.trackTotalHits(t -> t.enabled(true)));

			logger.debug("SearchRequest {}", searchRequest);

			SearchResponse<ProductSearchDocument> response = openSearchClient.search(searchRequest,
					ProductSearchDocument.class);

			var hitsMetadata = response.hits();
			List<Hit<ProductSearchDocument>> hitList = hitsMetadata != null && hitsMetadata.hits() != null
					? hitsMetadata.hits()
					: List.of();

			var totalHitsMetadata = hitsMetadata != null ? hitsMetadata.total() : null;

			if (hitsMetadata == null || hitsMetadata.hits() == null) {
				logger.warn("Busca retornou hits nulos para query '{}'.", query.terms());
			} else if (totalHitsMetadata != null) {
				logger.debug("Total de hits da busca: {}", totalHitsMetadata.value());
			}

			// Mapear resultados
			List<Product> products = hitList.stream().map(hit -> productMapper.toDomain(hit.source()))
					.collect(Collectors.toList());

			long totalCount = totalHitsMetadata != null ? totalHitsMetadata.value() : products.size();
			Duration executionTime = Duration.between(startTime, Instant.now());

			SearchMetrics metrics = new SearchMetrics(100, // QPS estimado
					calculateAverageScore(response.hits().hits()), (int) totalCount, response.took(),
					false, // Cache usage
					response.shards().toString());

			logger.debug("Busca concluída: encontrados {} produtos em {}ms", products.size(),
					executionTime.toMillis());

			return new SearchResult(products, totalCount, query.limit(), query.offset() / query.limit(), executionTime,
					metrics);

		} catch (Exception e) {
			logger.error("Erro ao executar busca", e);
			throw new RuntimeException("Falha ao executar busca", e);
		}
	}

	@Override
	public List<Product> findSimilar(ProductId productId, int limit) {
		logger.debug("Buscando produtos similares para: {}", productId);

		try {
			// Primeiro, buscar o produto original
			Optional<Product> originalProduct = findById(productId);
			if (originalProduct.isEmpty()) {
				return List.of();
			}

			Product product = originalProduct.get();

			// Construir query de similaridade
			Query similarityQuery = queryBuilder.buildSimilarityQuery(product);

			SearchRequest searchRequest = SearchRequest.of(s -> s.index(INDEX_NAME).query(similarityQuery).size(limit)
					.source(src -> src.filter(f -> f.excludes("description")))); // Otimização

			SearchResponse<ProductSearchDocument> response = openSearchClient.search(searchRequest,
					ProductSearchDocument.class);

			return response.hits().hits().stream().map(hit -> productMapper.toDomain(hit.source()))
					.filter(p -> !p.getId().equals(productId)) // Excluir o produto original
					.collect(Collectors.toList());

		} catch (Exception e) {
			logger.error("Erro ao buscar produtos similares para: {}", productId, e);
			return List.of();
		}
	}

	@Override
	public List<Product> findByIds(List<ProductId> productIds) {
		logger.debug("Buscando produtos por IDs: {}", productIds.size());

		try {
			List<String> ids = productIds.stream().map(ProductId::getValue).collect(Collectors.toList());

			Query idsQuery = Query.of(q -> q.ids(i -> i.values(ids)));

			SearchRequest searchRequest = SearchRequest
					.of(s -> s.index(INDEX_NAME).query(idsQuery).size(productIds.size()));

			SearchResponse<ProductSearchDocument> response = openSearchClient.search(searchRequest,
					ProductSearchDocument.class);

			return response.hits().hits().stream().map(hit -> productMapper.toDomain(hit.source()))
					.collect(Collectors.toList());

		} catch (Exception e) {
			logger.error("Erro ao buscar produtos por IDs", e);
			return List.of();
		}
	}

	@Override
	public Optional<Product> findById(ProductId productId) {
		logger.debug("Buscando produto por ID: {}", productId);

		try {
			GetRequest getRequest = GetRequest.of(g -> g.index(INDEX_NAME).id(productId.getValue()));

			GetResponse<ProductSearchDocument> response = openSearchClient.get(getRequest, ProductSearchDocument.class);

			if (response.found()) {
				Product product = productMapper.toDomain(response.source());
				return Optional.of(product);
			}

			return Optional.empty();

		} catch (Exception e) {
			logger.error("Erro ao buscar produto por ID: {}", productId, e);
			return Optional.empty();
		}
	}

	@Override
	public List<String> getSuggestions(String partialTerm, int limit) {
		logger.debug("Obtendo sugestões para: '{}'", partialTerm);

		try {
			Query suggestionQuery = queryBuilder.buildSuggestionQuery(partialTerm);

			SearchRequest searchRequest = SearchRequest.of(s -> s.index(INDEX_NAME).query(suggestionQuery).size(0) // Não
																														// precisamos
																														// dos
																														// documentos
					.aggregations("suggestions",
							agg -> agg.terms(t -> t.field("title.keyword").size(limit))));

			SearchResponse<ProductSearchDocument> response = openSearchClient.search(searchRequest,
					ProductSearchDocument.class);

			// Extrair sugestões das agregações
			if (response.aggregations() != null && response.aggregations().containsKey("suggestions")) {
				var termsAgg = response.aggregations().get("suggestions").sterms();
				if (termsAgg != null && termsAgg.buckets() != null) {
					return termsAgg.buckets().array().stream().map(bucket -> bucket.key())
							.collect(Collectors.toList());
				}
			}

			return List.of();

		} catch (Exception e) {
			logger.error("Erro ao obter sugestões para: '{}'", partialTerm, e);
			return List.of();
		}
	}

	@Override
	public List<Product> findMostPopular(String categoryId, int limit) {
		logger.debug("Buscando produtos mais populares na categoria: {}", categoryId);

		try {
			Query popularityQuery = queryBuilder.buildPopularityQuery(categoryId);

			SearchRequest searchRequest = SearchRequest.of(s -> s.index(INDEX_NAME).query(popularityQuery).size(limit)
					.sort(sort -> sort.field(f -> f.field("metrics.total_sales").order(SortOrder.Desc))));

			SearchResponse<ProductSearchDocument> response = openSearchClient.search(searchRequest,
					ProductSearchDocument.class);

			return response.hits().hits().stream().map(hit -> productMapper.toDomain(hit.source()))
					.collect(Collectors.toList());

		} catch (Exception e) {
			logger.error("Erro ao buscar produtos mais populares", e);
			return List.of();
		}
	}

	@Override
	public List<Product> findOnSale(int limit) {
		logger.debug("Buscando produtos em promoção");

		try {
			Query saleQuery = queryBuilder.buildOnSaleQuery();

			SearchRequest searchRequest = SearchRequest.of(s -> s.index(INDEX_NAME).query(saleQuery).size(limit)
					.sort(sort -> sort.field(f -> f.field("price").order(SortOrder.Asc))));

			SearchResponse<ProductSearchDocument> response = openSearchClient.search(searchRequest,
					ProductSearchDocument.class);

			return response.hits().hits().stream().map(hit -> productMapper.toDomain(hit.source()))
					.collect(Collectors.toList());

		} catch (Exception e) {
			logger.error("Erro ao buscar produtos em promoção", e);
			return List.of();
		}
	}

	@Override
	public long count(SearchQuery query) {
		logger.debug("Contando produtos para query: '{}'", query.terms());

		try {
			Query osQuery = queryBuilder.buildQuery(query, null);

			CountRequest countRequest = CountRequest.of(c -> c.index(INDEX_NAME).query(osQuery));

			CountResponse response = openSearchClient.count(countRequest);

			return response.count();

		} catch (Exception e) {
			logger.error("Erro ao contar produtos", e);
			return 0;
		}
	}

	@Override
	public ProductSearchRepository.CandidatesWithScores searchCandidatesWithScores(SearchQuery query, UserContext userContext, Optional<float[]> queryEmbedding) {
		if (queryEmbedding.isPresent()) {
			logger.info("Buscando candidatos com busca híbrida (BM25 + k-NN): query='{}', limit=200", query.terms());
		} else {
			logger.info("Buscando candidatos apenas com BM25 (fallback - Embedding Service não disponível): query='{}', limit=200", query.terms());
		}

		Instant startTime = Instant.now();

		try {
			// Criar query modificada para buscar Top 200 candidatos
			SearchQuery candidatesQuery = new SearchQuery(
					query.terms(),
					query.category(),
					query.filters(),
					query.sort(),
					0, // offset = 0
					200 // limit = 200 para fase 1
			);

			// PASSO 1: Embedding já foi gerado pelo UseCase (não gerar aqui)
			// O embedding é passado como parâmetro para permitir controle do fluxo no UseCase
			// Se embedding não estiver disponível, fazer apenas busca BM25 (fallback)
			
			// PASSO 2: Construir query BM25 (sempre executada)
			Query bm25Query = queryBuilder.buildBm25Query(candidatesQuery, userContext);
			
			logger.debug("Query BM25 para candidatos: {}", bm25Query);

			// PASSO 3: Executar buscas
			// Se embedding disponível: BM25 + k-NN em paralelo
			// Se embedding não disponível: apenas BM25 (fallback)
			CompletableFuture<SearchResponse<ProductSearchDocument>> bm25Future = CompletableFuture.supplyAsync(() -> {
				try {
					SearchRequest bm25Request = SearchRequest.of(s -> s
							.index(INDEX_NAME)
							.query(bm25Query)
							.from(0)
							.size(200)
							.sort(queryBuilder.buildSort(candidatesQuery.sort()))
							.trackTotalHits(t -> t.enabled(true)));
					
					logger.debug("Executando busca BM25 em paralelo");
					return openSearchClient.search(bm25Request, ProductSearchDocument.class);
				} catch (Exception e) {
					logger.error("Erro ao executar busca BM25", e);
					return null;
				}
			}, executorService);

			CompletableFuture<SearchResponse<ProductSearchDocument>> knnFuture = queryEmbedding.map(embedding -> {
				return CompletableFuture.supplyAsync(() -> {
					try {
						String vectorField = queryBuilder.getVectorField();
						
						// Busca k-NN pura (sem BM25)
						// Aplicar apenas filtros de status e categoria, sem query de texto
						Query knnQuery = Query.of(q -> q.knn(k -> k
								.field(vectorField)
								.vector(embedding)
								.k(200) // Buscar até 200 candidatos para k-NN
								.boost(1.0f)));
						
						// Combinar k-NN com filtros usando bool query
						// k-NN no should, filtros no filter
						org.opensearch.client.opensearch._types.query_dsl.BoolQuery.Builder knnBoolBuilder = 
								new org.opensearch.client.opensearch._types.query_dsl.BoolQuery.Builder();
						
						// Adicionar k-NN como should
						knnBoolBuilder.should(knnQuery);
						
						// Adicionar filtros de status
						knnBoolBuilder.filter(queryBuilder.buildStatusFilters());
						
						// Adicionar filtros de categoria se houver
						if (candidatesQuery.hasCategoryFilter()) {
							knnBoolBuilder.filter(queryBuilder.buildCategoryFilter(candidatesQuery.category()));
						}
						
						// Adicionar outros filtros se houver
						if (candidatesQuery.hasFilters()) {
							List<Query> filters = candidatesQuery.filters().stream()
									.map(queryBuilder::buildFilter)
									.collect(Collectors.toList());
							knnBoolBuilder.filter(filters);
						}
						
						knnBoolBuilder.minimumShouldMatch("1");
						
						Query finalKnnQuery = Query.of(q -> q.bool(knnBoolBuilder.build()));
						
						SearchRequest knnRequest = SearchRequest.of(s -> s
								.index(INDEX_NAME)
								.query(finalKnnQuery)
								.from(0)
								.size(200)
								.trackTotalHits(t -> t.enabled(true)));
						
						logger.debug("Executando busca k-NN em paralelo");
						return openSearchClient.search(knnRequest, ProductSearchDocument.class);
					} catch (Exception e) {
						logger.error("Erro ao executar busca k-NN", e);
						return null;
					}
				}, executorService);
			}).orElse(CompletableFuture.completedFuture(null));

			// PASSO 4: Aguardar buscas completarem
			SearchResponse<ProductSearchDocument> bm25Response = bm25Future.join();
			SearchResponse<ProductSearchDocument> knnResponse = null;
			
			// Só aguardar k-NN se embedding estiver disponível
			if (queryEmbedding.isPresent()) {
				knnResponse = knnFuture.join();
			}

			// PASSO 5: Extrair hits das buscas
			List<Hit<ProductSearchDocument>> bm25Hits = bm25Response != null && bm25Response.hits() != null && bm25Response.hits().hits() != null
					? bm25Response.hits().hits()
					: List.of();
			
			List<Hit<ProductSearchDocument>> knnHits = List.of();
			if (queryEmbedding.isPresent() && knnResponse != null && knnResponse.hits() != null && knnResponse.hits().hits() != null) {
				knnHits = knnResponse.hits().hits();
			}

			if (queryEmbedding.isPresent()) {
				logger.debug("Busca híbrida: BM25 retornou {} hits, k-NN retornou {} hits", bm25Hits.size(), knnHits.size());
			} else {
				logger.debug("Busca BM25 (fallback): retornou {} hits", bm25Hits.size());
			}

			// PASSO 6: Normalizar scores de cada busca separadamente
			double maxBm25Score = bm25Hits.stream()
					.mapToDouble(hit -> {
						Double score = hit.score();
						return score != null ? score : 0.0;
					})
					.max()
					.orElse(1.0);

			double maxKnnScore = knnHits.stream()
					.mapToDouble(hit -> {
						Double score = hit.score();
						return score != null ? score : 0.0;
					})
					.max()
					.orElse(1.0);

			// PASSO 7: Combinar resultados e remover duplicados
			java.util.Map<String, ProductSearchRepository.ScorePair> scoresMap = new java.util.HashMap<>();
			java.util.Map<String, Product> productsMap = new java.util.HashMap<>();
			java.util.Set<String> seenProductIds = new java.util.HashSet<>();

			// Processar hits BM25
			for (Hit<ProductSearchDocument> hit : bm25Hits) {
				Product product = productMapper.toDomain(hit.source());
				String productId = product.getId().getValue();

				if (seenProductIds.contains(productId)) {
					// Produto já visto, atualizar scores se necessário
					ProductSearchRepository.ScorePair existing = scoresMap.get(productId);
					Double rawScore = hit.score();
					double normalizedBm25 = rawScore != null && maxBm25Score > 0 ? rawScore / maxBm25Score : 0.0;
					
					// Manter o maior score BM25
					if (normalizedBm25 > existing.bm25Score()) {
						scoresMap.put(productId, new ProductSearchRepository.ScorePair(normalizedBm25, existing.knnScore()));
					}
					continue;
				}

				seenProductIds.add(productId);
				Double rawScore = hit.score();
				double normalizedBm25 = rawScore != null && maxBm25Score > 0 ? rawScore / maxBm25Score : 0.0;

				scoresMap.put(productId, new ProductSearchRepository.ScorePair(normalizedBm25, 0.0));
				productsMap.put(productId, product);
			}

			// Processar hits k-NN (apenas se busca híbrida estiver ativa)
			// Se embedding não estiver disponível, knnHits será vazio e este loop não executará
			for (Hit<ProductSearchDocument> hit : knnHits) {
				Product product = productMapper.toDomain(hit.source());
				String productId = product.getId().getValue();

				Double rawScore = hit.score();
				double normalizedKnn = rawScore != null && maxKnnScore > 0 ? rawScore / maxKnnScore : 0.0;

				if (seenProductIds.contains(productId)) {
					// Produto já visto, atualizar score k-NN
					ProductSearchRepository.ScorePair existing = scoresMap.get(productId);
					scoresMap.put(productId, new ProductSearchRepository.ScorePair(existing.bm25Score(), normalizedKnn));
				} else {
					// Novo produto, adicionar
					seenProductIds.add(productId);
					scoresMap.put(productId, new ProductSearchRepository.ScorePair(0.0, normalizedKnn));
					productsMap.put(productId, product);
				}
			}

			// Converter Map para List
			List<Product> products = new java.util.ArrayList<>(productsMap.values());
			
			Duration executionTime = Duration.between(startTime, Instant.now());
			
			if (queryEmbedding.isPresent()) {
				logger.debug("Produtos após combinação e remoção de duplicados: {} (BM25: {}, k-NN: {}) em {}ms (busca híbrida)", 
						products.size(), bm25Hits.size(), knnHits.size(), executionTime.toMillis());
			} else {
				logger.debug("Produtos encontrados: {} em {}ms (busca BM25 - fallback)", 
						products.size(), executionTime.toMillis());
			}

			return new ProductSearchRepository.CandidatesWithScores(products, scoresMap);

		} catch (Exception e) {
			logger.error("Erro ao buscar candidatos para ML ranking", e);
			return new ProductSearchRepository.CandidatesWithScores(List.of(), new java.util.HashMap<>());
		}
	}

	private double calculateAverageScore(List<Hit<ProductSearchDocument>> hits) {
		if (hits.isEmpty()) {
			return 0.0;
		}

		// Calcular o score médio bruto do OpenSearch
		double rawAverageScore = hits.stream().mapToDouble(hit -> {
			Double score = hit.score();
			return score != null ? score : 0.0;
		}).average().orElse(0.0);

		// Encontrar o score máximo para normalizar
		double maxScore = hits.stream().mapToDouble(hit -> {
			Double score = hit.score();
			return score != null ? score : 0.0;
		}).max().orElse(1.0);

		// Normalizar para o intervalo [0.0, 1.0]
		return maxScore > 0 ? rawAverageScore / maxScore : 0.0;
	}
}

