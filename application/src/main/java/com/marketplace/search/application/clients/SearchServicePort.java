package com.marketplace.search.application.clients;

import java.util.List;

/**
 * Porta (interface) para comunicação com o search-service.
 * Esta interface está na camada de aplicação para evitar dependências circulares.
 */
public interface SearchServicePort {

  /**
   * Busca produtos no search-service.
   * 
   * @param query Termo de busca
   * @param categoryId ID da categoria (opcional)
   * @param page Número da página (padrão: 0)
   * @param size Tamanho da página (padrão: 20)
   * @param sort Campo de ordenação (padrão: RELEVANCE)
   * @param userId ID do usuário para personalização (opcional)
   * @return SearchResultDTO contendo os resultados da busca
   * @throws SearchServiceException se houver erro na comunicação
   */
  Object searchProducts(String query, String categoryId, Integer page, Integer size, String sort, String userId);

  /**
   * Obtém sugestões de busca do search-service.
   * 
   * @param term Termo parcial para sugestões
   * @param limit Limite de sugestões (padrão: 10)
   * @return Lista de sugestões
   * @throws SearchServiceException se houver erro na comunicação
   */
  List<String> getSuggestions(String term, Integer limit);

  /**
   * Busca um produto específico por ID no search-service.
   * 
   * @param productId ID do produto
   * @return ProductDTO do produto encontrado
   * @throws SearchServiceException se houver erro na comunicação ou produto não encontrado
   */
  Object getProduct(String productId);

  /**
   * Exceção customizada para erros de comunicação com o search-service.
   */
  class SearchServiceException extends RuntimeException {
    public SearchServiceException(String message) {
      super(message);
    }

    public SearchServiceException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}

