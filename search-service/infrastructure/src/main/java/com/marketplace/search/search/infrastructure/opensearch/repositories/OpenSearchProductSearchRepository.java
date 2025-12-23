package com.marketplace.search.search.infrastructure.opensearch.repositories;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
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
import com.marketplace.search.search.infrastructure.embedding.EmbeddingClient;
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
	private final EmbeddingClient embeddingClient;

	public OpenSearchProductSearchRepository(OpenSearchClient openSearchClient,
			OpenSearchProductMapper productMapper, OpenSearchQueryBuilder queryBuilder,
			EmbeddingClient embeddingClient) {
		this.openSearchClient = openSearchClient;
		this.productMapper = productMapper;
		this.queryBuilder = queryBuilder;
		this.embeddingClient = embeddingClient;
	}

	@Override
	public SearchResult search(SearchQuery query, UserContext userContext) {
		logger.debug("Executando busca no índice '{}': query='{}', limit={}", INDEX_NAME, query.terms(),
				query.limit());

		Instant startTime = Instant.now();

		try {
			// Tentar gerar embedding para busca híbrida
			Optional<float[]> queryEmbedding = embeddingClient.generateQueryEmbedding(query.terms());
			
			// Construir query do OpenSearch (híbrida se embedding disponível, senão apenas BM25)
			Query osQuery = queryEmbedding
					.map(embedding -> {
						logger.debug("Usando busca híbrida (BM25 + k-NN) para query: '{}'", query.terms());
						return queryBuilder.buildQuery(query, userContext, Optional.of(embedding));
					})
					.orElseGet(() -> {
						logger.debug("Usando busca apenas BM25 (embedding não disponível) para query: '{}'", query.terms());
						return queryBuilder.buildQuery(query, userContext);
					});
			
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
	public ProductSearchRepository.CandidatesWithScores searchCandidatesWithScores(SearchQuery query, UserContext userContext) {
		logger.debug("Buscando candidatos para ML ranking: query='{}', limit=400", query.terms());

		Instant startTime = Instant.now();

		try {
			// Criar query modificada para buscar Top 400 candidatos
			SearchQuery candidatesQuery = new SearchQuery(
					query.terms(),
					query.category(),
					query.filters(),
					query.sort(),
					0, // offset = 0
					400 // limit = 400 para fase 1
			);

			// Tentar gerar embedding para busca híbrida
			Optional<float[]> queryEmbedding = embeddingClient.generateQueryEmbedding(query.terms());
			
			// Construir query do OpenSearch (híbrida se embedding disponível, senão apenas BM25)
			Query osQuery = queryEmbedding
					.map(embedding -> {
						logger.debug("Usando busca híbrida (BM25 + k-NN) para candidatos: '{}'", query.terms());
						return queryBuilder.buildQuery(candidatesQuery, userContext, Optional.of(embedding));
					})
					.orElseGet(() -> {
						logger.debug("Usando busca apenas BM25 (embedding não disponível) para candidatos: '{}'", query.terms());
						return queryBuilder.buildQuery(candidatesQuery, userContext);
					});
			
			logger.debug("OpenSearch Query para candidatos: {}", osQuery);

			// Executar busca
			SearchRequest searchRequest = SearchRequest.of(s -> s.index(INDEX_NAME).query(osQuery)
					.from(0).size(400).sort(queryBuilder.buildSort(candidatesQuery.sort()))
					.trackTotalHits(t -> t.enabled(true)));

			SearchResponse<ProductSearchDocument> response = openSearchClient.search(searchRequest,
					ProductSearchDocument.class);

			var hitsMetadata = response.hits();
			List<Hit<ProductSearchDocument>> hitList = hitsMetadata != null && hitsMetadata.hits() != null
					? hitsMetadata.hits()
					: List.of();

			// Extrair produtos e scores
			java.util.Map<String, ProductSearchRepository.ScorePair> scoresMap = new java.util.HashMap<>();
			List<Product> products = new java.util.ArrayList<>();

			// Normalizar scores para o intervalo [0.0, 1.0]
			double maxScore = hitList.stream()
					.mapToDouble(hit -> {
						Double score = hit.score();
						return score != null ? score : 0.0;
					})
					.max()
					.orElse(1.0);

			// Se busca híbrida foi usada, tentar extrair scores separados
			// Caso contrário, usar estimativas baseadas no score combinado
			boolean isHybridSearch = queryEmbedding.isPresent();
			
			for (Hit<ProductSearchDocument> hit : hitList) {
				Product product = productMapper.toDomain(hit.source());
				String productId = product.getId().getValue();

				Double rawScore = hit.score();
				double normalizedScore = rawScore != null && maxScore > 0 ? rawScore / maxScore : 0.0;

				// Para busca híbrida, o OpenSearch combina BM25 e k-NN automaticamente
				// Como não temos acesso direto aos scores separados, estimamos baseado no score combinado
				// Em uma implementação mais avançada, poderíamos usar explicações do OpenSearch
				double bm25Score;
				double knnScore;
				
				if (isHybridSearch) {
					// Na busca híbrida, assumimos que o score é uma combinação
					// Estimamos: 60% BM25 + 40% k-NN (baseado na FeatureExtractor)
					bm25Score = normalizedScore * 0.6;
					knnScore = normalizedScore * 0.4;
				} else {
					// Apenas BM25, k-NN é zero
					bm25Score = normalizedScore;
					knnScore = 0.0;
				}

				scoresMap.put(productId, new ProductSearchRepository.ScorePair(bm25Score, knnScore));
				products.add(product);
			}

			Duration executionTime = Duration.between(startTime, Instant.now());
			logger.debug("Candidatos buscados: {} produtos em {}ms", products.size(), executionTime.toMillis());

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

