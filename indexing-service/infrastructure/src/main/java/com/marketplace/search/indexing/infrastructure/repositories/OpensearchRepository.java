package com.marketplace.search.indexing.infrastructure.repositories;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.BulkResponse;
import org.opensearch.client.opensearch.core.UpdateRequest;
import org.opensearch.client.opensearch.core.bulk.BulkResponseItem;
import org.opensearch.client.opensearch.indices.CreateIndexRequest;
import org.opensearch.client.opensearch.indices.ExistsRequest;
import org.springframework.stereotype.Repository;

import com.marketplace.search.indexing.domain.entities.Product;
import com.marketplace.search.indexing.domain.repositories.ProductIndexRepository;
import com.marketplace.search.indexing.domain.valueobjects.ProductId;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Repository
public class OpensearchRepository implements ProductIndexRepository {

	private final OpenSearchClient client;
	private static final String INDEX_NAME = "products_index";
	private static final String VECTOR_FIELD = "product_vector";

	public OpensearchRepository(OpenSearchClient client) {
		this.client = client;
	}

	@Override
	public void createKnnIndex(int vectorDim) throws Exception {
		// Verificar se o índice já existe
		if (client.indices().exists(new ExistsRequest.Builder().index(INDEX_NAME).build()).value()) {
			log.debug("Índice '" + INDEX_NAME + "' já existe. Pulando criação.");
			return;
		}

		log.debug("Criando índice para busca híbrida (BM25 + Semântica): " + INDEX_NAME);

		// Criar o índice com k-NN habilitado e campos otimizados para BM25
		CreateIndexRequest createReq = new CreateIndexRequest.Builder()
				.index(INDEX_NAME)
				.settings(s -> s
						.index(i -> i
								.knn(true) // Habilitar k-NN no índice
						))
				.mappings(m -> m
						// Campo de vetor para busca semântica
						.properties(VECTOR_FIELD, p -> p
								.knnVector(kv -> kv
										.dimension(vectorDim)
										.method(method -> method
												.name("hnsw") // Algoritmo HNSW
												.spaceType("cosinesimil") // Similaridade de cosseno
												.engine("lucene"))))
						// Campo title para BM25 (com boost)
						.properties("title", p -> p
								.text(t -> t
										.analyzer("standard") // Analisador padrão para português/inglês
										.fields("keyword", f -> f.keyword(k -> k)))) // Subcampo keyword para exact match
						// Campo description para BM25
						.properties("description", p -> p
								.text(t -> t
										.analyzer("standard")))
						// Campo category para filtros
						.properties("category", p -> p
								.keyword(k -> k)))
				.build();

		client.indices().create(createReq);
	}

	@Override
	public void indexDocumentsBatch(List<Product> products) throws Exception {
		if (products == null || products.isEmpty()) {
			log.debug("Nenhum documento para indexar.");
			return;
		}

		log.debug("Gerando embeddings em batch para " + products.size() + " documentos...");
		long startTime = System.currentTimeMillis();

		// Gerar todos os embeddings em batch (mais eficiente)
		// List<float[]> embeddings = model.embedBatch(texts);

		long embeddingTime = System.currentTimeMillis() - startTime;
		log.debug("Embeddings gerados em " + embeddingTime + "ms");

		// Criar requisição Bulk
		BulkRequest.Builder bulkBuilder = new BulkRequest.Builder();

		for (int i = 0; i < products.size(); i++) {
			final int docIndex = i;
			var product = products.get(i);
			String title = product.getInfo().getTitle();
			String description = product.getInfo().getDescription();
			String category = product.getInfo().getCategory().getName();
			// float[] vector = embeddings.get(i);

			// Criar documento com TODOS os campos para busca híbrida
			Map<String, Object> docBody = new HashMap<>();
			docBody.put("title", title);
			docBody.put("description", description);
			docBody.put("category", category);
			// docBody.put(VECTOR_FIELD, vector);

			// Adicionar ao bulk
			bulkBuilder.operations(op -> op
					.index(idx -> idx
							.index(INDEX_NAME)
							.id("doc_" + docIndex)
							.document(docBody)));
		}

		// Executar bulk indexing
		log.debug("Indexando " + products.size() + " documentos via Bulk API...");
		BulkResponse response = client.bulk(bulkBuilder.build());

		// Verificar erros
		if (response.errors()) {
			System.err.println("Erros durante bulk indexing:");
			for (BulkResponseItem item : response.items()) {
				if (item.error() != null) {
					System.err.println("Erro no documento " + item.id() + ": " + item.error().reason());
				}
			}
		} else {
			log.debug("✓ " + products.size() + " documentos indexados com sucesso!");
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
		client.update(new UpdateRequest.Builder<Product, Product>()
				.index(INDEX_NAME)
				.id(product.getId().getValue())
				.doc(product)
				.build(), Product.class);
		log.debug("Produto atualizado no índice: " + product.getId());
	}

	@Override
	public void indexProduct(Product product) throws Exception {
		client.index(i -> i
				.index(INDEX_NAME)
				.id(product.getId().getValue())
				.document(product));
		log.debug("Produto indexado: " + product.getId());
	}

}
