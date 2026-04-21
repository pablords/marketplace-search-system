package com.marketplace.search.search.domain.services;


import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.marketplace.search.search.domain.entities.Product;
import com.marketplace.search.search.domain.repositories.CacheRepository;
import com.marketplace.search.search.domain.repositories.ProductSearchRepository;
import com.marketplace.search.search.domain.valueobjects.SearchQuery;
import com.marketplace.search.search.domain.valueobjects.SearchResult;
import com.marketplace.search.search.domain.valueobjects.UserContext;
import java.time.Duration;

/**
 * Serviço de domínio para operações de busca avançada
 */
public class SearchDomainService {

  private final ProductSearchRepository searchRepository;
  private final CacheRepository cacheRepository;
  private static final Logger logger = LoggerFactory.getLogger(SearchDomainService.class);

  public SearchDomainService(ProductSearchRepository searchRepository, CacheRepository cacheRepository) {
    this.searchRepository = searchRepository;
    this.cacheRepository = cacheRepository;
  }

  /**
   * Busca no cache um resultado de domínio
   */
  public Optional<SearchResult> getFromCache(String cacheKey) {
    return cacheRepository.get(cacheKey, SearchResult.class);
  }

  /**
   * Armazena no cache um resultado de domínio
   */
  public void storeInCache(String cacheKey, SearchResult result, Duration ttl) {
    if (result != null && result.hasResults()) {
      cacheRepository.put(cacheKey, result, ttl);
      logger.debug("Resultado armazenado no cache para chave: {}", cacheKey);
    }
  }

  /**
   * Executa busca inteligente com ranking personalizado
   */
    public SearchResult smartSearch(SearchQuery query, UserContext userContext) {
    SearchResult initialResult = searchRepository.search(query, userContext);
    logger.debug("Validar se todos os produtos no índice estão disponíveis para busca {}", initialResult.toString());

    List<Product> rankedProducts = applyBusinessRules(initialResult.products(), query, userContext);
    if (!rankedProducts.isEmpty()) {
      logger.debug("Aplicar regras de negócio para ranking personalizado {}", rankedProducts.get(0).toString());
    }

    return new SearchResult(
        rankedProducts,
        initialResult.totalCount(),
        initialResult.pageSize(),
        initialResult.pageNumber(),
        initialResult.executionTime(),
        initialResult.metrics());
  }

  /**
   * Busca e valida candidatos para o ranking de ML, aplicando regras de negócio de domínio.
   */
  public ProductSearchRepository.CandidatesWithScores fetchAndValidateCandidates(
      SearchQuery query, 
      UserContext userContext, 
      Optional<float[]> queryEmbedding) {
    
    logger.debug("Buscando candidatos no repositório para query: {}", query.terms());
    ProductSearchRepository.CandidatesWithScores rawCandidates = 
        searchRepository.searchCandidatesWithScores(query, userContext, queryEmbedding);

    if (rawCandidates.products().isEmpty()) {
      return rawCandidates;
    }

    logger.debug("Aplicando regras de negócio de domínio em {} candidatos", rawCandidates.products().size());
    List<Product> validatedProducts = applyBusinessRules(rawCandidates.products(), query, userContext);

    // Filtrar o mapa de scores para conter apenas os produtos validados
    Map<String, ProductSearchRepository.ScorePair> validatedScores = validatedProducts.stream()
        .collect(Collectors.toMap(
            p -> p.getId().toString(),
            p -> rawCandidates.scores().get(p.getId().toString()),
            (v1, v2) -> v1 // Em caso de duplicata (não deveria ocorrer), manter o primeiro
        ));

    return new ProductSearchRepository.CandidatesWithScores(validatedProducts, validatedScores);
  }

  /**
   * Busca com fallback automático para termos similares
   */
    public SearchResult searchWithFallback(SearchQuery originalQuery, UserContext userContext) {
    SearchResult result = searchRepository.search(originalQuery, userContext);

    // Se não encontrou resultados suficientes, tenta busca mais ampla
    if (result.products().size() < 3) {
      SearchQuery fallbackQuery = createFallbackQuery(originalQuery);
      result = searchRepository.search(fallbackQuery, userContext);
    }

    return result;
  }


  /**
   * Calcula boost de relevância baseado em métricas de negócio
   */
    public double calculateBusinessBoost(Product product, UserContext userContext) {
    logger.debug("Calculando boost de relevância para produto {}", product.getId().getValue());
    double boost = 1.0;

    // Boost para produtos populares
    if (product.getMetrics().isPopular()) {
      boost += 0.2;
    }

    // Boost para vendedores de alta qualidade
    if (product.getSeller().getReputation().isHighQuality()) {
      boost += 0.15;
    }

    // Boost para produtos bem avaliados
    if (product.getMetrics().isHighlyRated()) {
      boost += 0.1;
    }

    // Personalização baseada no usuário
    if (userContext != null && !userContext.isAnonymous()) {
      if (userContext.hasPreviousPurchaseFromSeller(product.getSeller().getId())) {
        boost += 0.1;
      }
    }

    return Math.min(2.0, boost); // Máximo de 2x boost
  }

  private List<Product> applyBusinessRules(List<Product> products, SearchQuery query, UserContext userContext) {
    logger.debug("Produtos antes de aplicar regras {}", products);

    List<Product> filteredProducts = products.stream()
        .filter(product -> Product.isProductSearchable(product, userContext))
        .toList();

    if (filteredProducts.isEmpty()) {
      return List.of();
    }

    // Usamos o primeiro produto (mais relevante inicialmente) como referência para cálculo de similaridade
    Product referenceProduct = filteredProducts.get(0);

    return filteredProducts.stream()
        .map(product -> {
          double textScore = product.calculateRelevanceScore(query, userContext).getValue();
          double similarity = Product.calculateProductSimilarity(product, referenceProduct);
          double businessBoost = calculateBusinessBoost(product, userContext);

          double finalScore = (textScore + (similarity * 0.15)) * businessBoost;

          if (query.rankingDebug()) {
            java.util.Map<String, Double> features = new java.util.HashMap<>();
            features.put("base_relevance", textScore);
            features.put("similarity_boost", similarity * 0.15);
            features.put("business_boost", businessBoost);
            product.setRankingDebug(new com.marketplace.search.search.domain.valueobjects.RankingDebug(finalScore, features));
          }

          return new Object() {
            Product p = product;
            double score = finalScore;
          };
        })
        .sorted((o1, o2) -> Double.compare(o2.score, o1.score))
        .map(o -> o.p)
        .toList();
  }

  private SearchQuery createFallbackQuery(SearchQuery originalQuery) {
    return new SearchQuery(
        originalQuery.terms(),
        null, // Remove filtro de categoria
        List.of(), // Remove todos os filtros
        originalQuery.sort(),
        originalQuery.offset(),
        originalQuery.limit() * 2, // Aumenta o limite
        originalQuery.rankingDebug()
    );
  }




}

