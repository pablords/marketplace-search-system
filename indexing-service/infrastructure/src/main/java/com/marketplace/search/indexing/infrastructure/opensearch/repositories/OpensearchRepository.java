package com.marketplace.search.indexing.infrastructure.opensearch.repositories;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.mapping.Property;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.BulkResponse;
import org.opensearch.client.opensearch.core.bulk.BulkResponseItem;
import org.opensearch.client.opensearch.indices.CreateIndexRequest;
import org.opensearch.client.opensearch.indices.DeleteIndexRequest;
import org.opensearch.client.opensearch.indices.ExistsRequest;
import org.opensearch.client.opensearch.indices.GetMappingRequest;
import org.opensearch.client.opensearch.indices.GetMappingResponse;
import org.springframework.stereotype.Repository;

import com.marketplace.search.indexing.domain.entities.Product;
import com.marketplace.search.indexing.domain.repositories.ProductIndexRepository;
import com.marketplace.search.indexing.domain.valueobjects.ProductId;
import com.marketplace.search.indexing.infrastructure.embedding.EmbeddingClient;
import com.marketplace.search.indexing.infrastructure.opensearch.mappers.ProductDocumentMapper;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Repository
public class OpensearchRepository implements ProductIndexRepository {

	private final OpenSearchClient client;
	private final EmbeddingClient embeddingClient;
	private final ProductDocumentMapper mapper;
	private static final String INDEX_NAME = "products_index";
	private static final String VECTOR_FIELD = "product_vector";

	public OpensearchRepository(
			OpenSearchClient client,
			EmbeddingClient embeddingClient,
			ProductDocumentMapper mapper) {
		this.client = client;
		this.embeddingClient = embeddingClient;
		this.mapper = mapper;
	}

	@Override
	public void createKnnIndex(int vectorDim) throws Exception {
		// Verificar se o índice já existe
		boolean indexExists = client.indices().exists(new ExistsRequest.Builder().index(INDEX_NAME).build()).value();
		
		if (indexExists) {
			// Verificar se o campo product_vector está configurado como knn_vector
			if (!isKnnVectorFieldConfigured()) {
				log.warn("Índice existe mas campo '{}' não está configurado como knn_vector. Recriando índice...", VECTOR_FIELD);
				// Deletar índice existente
				client.indices().delete(new DeleteIndexRequest.Builder().index(INDEX_NAME).build());
				log.info("Índice deletado. Recriando com mapeamento correto...");
			} else {
				log.debug("Índice '" + INDEX_NAME + "' já existe com mapeamento correto. Pulando criação.");
				return;
			}
		}

		log.debug("Criando índice para busca híbrida (BM25 + Semântica): " + INDEX_NAME);

		// Criar o índice com k-NN habilitado e campos otimizados para BM25
		CreateIndexRequest.Builder builder = new CreateIndexRequest.Builder();
		builder.index(INDEX_NAME);
		builder.settings(s -> s.index(i -> i.knn(true)));
		
		// Configurar mapeamentos
		builder.mappings(m -> m
				.properties(VECTOR_FIELD, p -> p
						.knnVector(kv -> kv
								.dimension(vectorDim)
								.method(method -> method
										.name("hnsw")
										.spaceType("cosinesimil")
										.engine("lucene"))))
				.properties("title", p -> p
						.text(t -> t
								.analyzer("standard")
								.fields("keyword", f -> f.keyword(k -> k))))
				.properties("description", p -> p
						.text(t -> t.analyzer("standard")))
				.properties("category", p -> p
						.object(o -> o
								.properties("id", id -> id.keyword(k -> k))
								.properties("name", name -> name.keyword(k -> k))
								.properties("path", path -> path.keyword(k -> k))
								.properties("parent_id", parentId -> parentId.keyword(k -> k))))
				.properties("brand", p -> p
						.object(o -> o
								.properties("id", id -> id.keyword(k -> k))
								.properties("name", name -> name.text(t -> t.analyzer("standard")))
								.properties("description", desc -> desc.text(t -> t.analyzer("standard")))))
				.properties("seller", p -> p
						.object(o -> o
								.properties("id", id -> id.keyword(k -> k))
								.properties("name", name -> name.keyword(k -> k))
								.properties("status", status -> status.keyword(k -> k))
								.properties("type", type -> type.keyword(k -> k))
								.properties("reputation", rep -> rep.object(repObj -> repObj)))));
		
		CreateIndexRequest createReq = builder.build();

		client.indices().create(createReq);
		log.info("Índice '{}' criado com sucesso com suporte a k-NN (dimensão: {})", INDEX_NAME, vectorDim);
	}

	/**
	 * Verifica se o campo product_vector está configurado como knn_vector
	 */
	private boolean isKnnVectorFieldConfigured() {
		try {
			GetMappingResponse mapping = client.indices().getMapping(
					new GetMappingRequest.Builder().index(INDEX_NAME).build());
			
			if (mapping.result().isEmpty()) {
				log.debug("Nenhum mapeamento encontrado para o índice {}", INDEX_NAME);
				return false;
			}
			
			var indexMapping = mapping.result().get(INDEX_NAME);
			if (indexMapping == null || indexMapping.mappings() == null) {
				log.debug("Mapeamento vazio para o índice {}", INDEX_NAME);
				return false;
			}
			
			var properties = indexMapping.mappings().properties();
			if (properties == null || properties.isEmpty()) {
				log.debug("Nenhuma propriedade encontrada no mapeamento do índice {}", INDEX_NAME);
				return false;
			}
			
			Property vectorProperty = properties.get(VECTOR_FIELD);
			if (vectorProperty == null) {
				log.debug("Campo '{}' não encontrado no mapeamento", VECTOR_FIELD);
				return false;
			}
			
			// Verificar se é do tipo knn_vector
			boolean isKnnVector = vectorProperty._kind() == Property.Kind.KnnVector;
			log.debug("Campo '{}' é do tipo knn_vector: {}", VECTOR_FIELD, isKnnVector);
			return isKnnVector;
			
		} catch (Exception e) {
			log.warn("Erro ao verificar mapeamento do campo {}: {}", VECTOR_FIELD, e.getMessage());
			return false;
		}
	}

	@Override
	public void indexDocumentsBatch(List<Product> products) throws Exception {
		if (products == null || products.isEmpty()) {
			log.debug("Nenhum documento para indexar.");
			return;
		}

		long startTime = System.currentTimeMillis();
		List<float[]> embeddings = new ArrayList<>();

		// Gerar embeddings se o serviço estiver habilitado

		log.debug("Gerando embeddings em batch para " + products.size() + " documentos...");

		// Extrair títulos dos produtos para gerar embeddings
		List<String> titles = new ArrayList<>();
		for (Product product : products) {
			String title = product.getInfo().getTitle();
			if (title != null && !title.trim().isEmpty()) {
				titles.add(title);
			} else {
				// Se não houver título, usar descrição ou string vazia
				String description = product.getInfo().getDescription();
				titles.add(description != null && !description.trim().isEmpty() ? description : "");
			}
		}

		// Chamar Embedding Service para gerar embeddings em batch
		Optional<List<float[]>> embeddingsOptional = embeddingClient.generateEmbeddings(titles);

		if (embeddingsOptional.isPresent()) {
			embeddings = embeddingsOptional.get();
			long embeddingTime = System.currentTimeMillis() - startTime;
			log.debug("Embeddings gerados em " + embeddingTime + "ms para " + embeddings.size() + " textos");
		} else {
			log.warn("Falha ao gerar embeddings. Indexando documentos sem vetores (apenas BM25).");
		}

		// Criar requisição Bulk
		BulkRequest.Builder bulkBuilder = new BulkRequest.Builder();

		for (int i = 0; i < products.size(); i++) {
			var product = products.get(i);
			String productId = product.getId().getValue();

			// Criar documento completo usando o mapper
			Map<String, Object> docBody = mapper.toDocumentMap(product);

			// Incluir vetor se disponível
			if (i < embeddings.size()) {
				float[] vector = embeddings.get(i);
				docBody.put(VECTOR_FIELD, vector);
			}

			// Adicionar ao bulk usando o ID do produto
			bulkBuilder.operations(op -> op
					.index(idx -> idx
							.index(INDEX_NAME)
							.id(productId)
							.document(docBody)));
		}

		// Executar bulk indexing
		log.debug("Indexando " + products.size() + " documentos via Bulk API...");
		BulkResponse response = client.bulk(bulkBuilder.build());

		// Verificar erros
		if (response.errors()) {
			log.error("Erros durante bulk indexing:");
			for (BulkResponseItem item : response.items()) {
				var error = item.error();
				if (error != null) {
					log.error("Erro no documento " + item.id() + ": " + error.reason());
				}
			}
		} else {
			int withVectors = embeddings.size();
			log.debug("✓ " + products.size() + " documentos indexados com sucesso! " +
					(withVectors > 0 ? "(" + withVectors + " com vetores)" : "(sem vetores)"));
		}

		long totalTime = System.currentTimeMillis() - startTime;
		log.debug("Tempo total: " + totalTime + "ms (" + (totalTime / products.size()) + "ms por documento)");

		// Refresh do índice
		log.debug("Refreshing índice...");
		client.indices().refresh(r -> r.index(INDEX_NAME));
	}

	@Override
	public boolean exists(ProductId productId) throws Exception {
		return client.exists(e -> e.index(INDEX_NAME).id(productId.getValue())).value();
	}

	@Override
	public void updateProduct(Product product) throws Exception {
		// Criar documento completo usando o mapper
		Map<String, Object> docBody = mapper.toDocumentMap(product);

		String title = product.getInfo().getTitle();
		String description = product.getInfo().getDescription();
		String textForEmbedding = title;
		if (textForEmbedding == null || textForEmbedding.trim().isEmpty()) {
			textForEmbedding = (description != null && !description.trim().isEmpty()) ? description : "";
		}

		if (!textForEmbedding.isEmpty()) {
			List<String> texts = List.of(textForEmbedding);
			Optional<List<float[]>> embeddingsOptional = embeddingClient.generateEmbeddings(texts);

			if (embeddingsOptional.isPresent() && !embeddingsOptional.get().isEmpty()) {
				float[] vector = embeddingsOptional.get().get(0);
				docBody.put(VECTOR_FIELD, vector);
				log.debug("Embedding gerado e incluído na atualização do produto " + product.getId().getValue());
			} else {
				log.warn("Falha ao gerar embedding para atualização do produto " + product.getId().getValue()
						+ ". Atualizando sem vetor.");
			}
		}

		// Usar index para fazer upsert (atualiza se existir, cria se não existir)
		client.index(i -> i
				.index(INDEX_NAME)
				.id(product.getId().getValue())
				.document(docBody));
	}

	@Override
	public void indexProduct(Product product) throws Exception {
		// Criar documento completo usando o mapper
		Map<String, Object> docBody = mapper.toDocumentMap(product);

		String title = product.getInfo().getTitle();
		String description = product.getInfo().getDescription();
		String textForEmbedding = title;
		if (textForEmbedding == null || textForEmbedding.trim().isEmpty()) {
			textForEmbedding = (description != null && !description.trim().isEmpty()) ? description : "";
		}

		if (!textForEmbedding.isEmpty()) {
			List<String> texts = List.of(textForEmbedding);
			Optional<List<float[]>> embeddingsOptional = embeddingClient.generateEmbeddings(texts);

			if (embeddingsOptional.isPresent() && !embeddingsOptional.get().isEmpty()) {
				float[] vector = embeddingsOptional.get().get(0);
				docBody.put(VECTOR_FIELD, vector);
				log.debug("Embedding gerado e incluído no documento do produto " + product.getId().getValue());
			} else {
				log.warn("Falha ao gerar embedding para produto " + product.getId().getValue()
						+ ". Indexando sem vetor.");
			}
		}

		client.index(i -> i
				.index(INDEX_NAME)
				.id(product.getId().getValue())
				.document(docBody));
	}

}
