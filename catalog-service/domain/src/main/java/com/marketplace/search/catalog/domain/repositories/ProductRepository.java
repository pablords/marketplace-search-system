package com.marketplace.search.catalog.domain.repositories;

import java.util.List;

import com.marketplace.search.catalog.domain.entities.Product;

/**
 * Port (interface) para persistência de produtos.
 * Implementado pela camada de infraestrutura (Adapter).
 */
public interface ProductRepository {

	/**
	 * Salva um produto no repositório.
	 * 
	 * @param product produto a ser salvo
	 */
	void save(Product product);

	/**
	 * Salva uma lista de produtos no repositório em batch.
	 * 
	 * @param products lista de produtos a serem salvos
	 */
	void saveAll(List<Product> products);

	/**
	 * Atualiza um produto existente.
	 * 
	 * @param product produto a ser atualizado
	 */
	void update(Product product);

	/**
	 * Deleta um produto por ID.
	 * 
	 * @param productId ID do produto
	 */
	void delete(String productId);

	/**
	 * Verifica se um produto existe pelo ID.
	 * 
	 * @param productId ID do produto
	 * @return true se o produto existe, false caso contrário
	 */
	boolean existsById(String productId);

	/**
	 * Retorna apenas os IDs dos produtos que já existem no repositório.
	 * 
	 * @param productIds Lista de IDs de produtos a verificar
	 * @return Lista de IDs encontrados
	 */
	List<String> findExistingIds(List<String> productIds);
}
