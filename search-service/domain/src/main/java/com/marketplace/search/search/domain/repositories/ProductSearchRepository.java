package com.marketplace.search.search.domain.repositories;

import com.marketplace.search.search.domain.entities.Product;
import com.marketplace.search.search.domain.valueobjects.ProductId;
import com.marketplace.search.search.domain.valueobjects.SearchQuery;
import com.marketplace.search.search.domain.valueobjects.SearchResult;
import com.marketplace.search.search.domain.valueobjects.UserContext;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Interface do repositório para operações de busca de produtos
 */
public interface ProductSearchRepository {

  /**
   * Busca produtos baseado na query de busca e contexto do usuário
   */
  SearchResult search(SearchQuery query, UserContext userContext);

  /**
   * Busca candidatos para re-ranking ML (Top 400) e retorna produtos com seus scores
   * 
   * @param query Query de busca
   * @param userContext Contexto do usuário
   * @return Resultado com produtos e mapa de productId -> (bm25Score, knnScore)
   */
  CandidatesWithScores searchCandidatesWithScores(SearchQuery query, UserContext userContext);

  /**
   * Busca produtos similares a um produto específico
   */
  List<Product> findSimilar(ProductId productId, int limit);

  /**
   * Busca produtos por IDs específicos
   */
  List<Product> findByIds(List<ProductId> productIds);

  /**
   * Busca um produto específico por ID
   */
  Optional<Product> findById(ProductId productId);

  /**
   * Obtém sugestões de busca baseadas em um termo parcial
   */
  List<String> getSuggestions(String partialTerm, int limit);

  /**
   * Obtém produtos mais populares em uma categoria
   */
  List<Product> findMostPopular(String categoryId, int limit);

  /**
   * Obtém produtos em promoção
   */
  List<Product> findOnSale(int limit);

  /**
   * Conta o total de produtos que correspondem à query
   */
  long count(SearchQuery query);

  /**
   * Record para retornar candidatos com seus scores
   */
  record CandidatesWithScores(
      List<Product> products,
      Map<String, ScorePair> scores
  ) {}

  /**
   * Record para armazenar scores de um candidato
   */
  record ScorePair(
      double bm25Score,
      double knnScore
  ) {}
}

