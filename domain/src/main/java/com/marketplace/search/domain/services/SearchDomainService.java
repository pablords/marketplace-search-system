package com.marketplace.search.domain.services;

import java.util.List;

import com.marketplace.search.domain.entities.Product;
import com.marketplace.search.domain.repositories.ProductIndexRepository;
import com.marketplace.search.domain.repositories.ProductSearchRepository;
import com.marketplace.search.domain.valueobjects.Category;
import com.marketplace.search.domain.valueobjects.SearchQuery;
import com.marketplace.search.domain.valueobjects.SearchResult;
import com.marketplace.search.domain.valueobjects.UserContext;

/**
 * Serviço de domínio para operações de busca avançada
 */
public class SearchDomainService {

  private final ProductSearchRepository searchRepository;
  private final ProductIndexRepository indexRepository;

  public SearchDomainService(ProductSearchRepository searchRepository,
      ProductIndexRepository indexRepository) {
    this.searchRepository = searchRepository;
    this.indexRepository = indexRepository;
  }

  /**
   * Executa busca inteligente com ranking personalizado
   */
  public SearchResult smartSearch(SearchQuery query, UserContext userContext) {
    // Validar se todos os produtos no índice estão disponíveis para busca
    SearchResult initialResult = searchRepository.search(query, userContext);

    // Aplicar regras de negócio para ranking personalizado
    List<Product> rankedProducts = applyBusinessRules(initialResult.getProducts(), userContext);

    return new SearchResult(
        rankedProducts,
        initialResult.getTotalCount(),
        initialResult.getPageSize(),
        initialResult.getPageNumber(),
        initialResult.getExecutionTime(),
        initialResult.getMetrics());
  }

  /**
   * Busca com fallback automático para termos similares
   */
  public SearchResult searchWithFallback(SearchQuery originalQuery, UserContext userContext) {
    SearchResult result = searchRepository.search(originalQuery, userContext);

    // Se não encontrou resultados suficientes, tenta busca mais ampla
    if (result.getProducts().size() < 3) {
      SearchQuery fallbackQuery = createFallbackQuery(originalQuery);
      result = searchRepository.search(fallbackQuery, userContext);
    }

    return result;
  }

  /**
   * Calcula similaridade entre dois produtos
   */
  public double calculateProductSimilarity(Product product1, Product product2) {
    double categoryScore = calculateCategorySimilarity(product1.getInfo().getCategory(),
        product2.getInfo().getCategory());

    double brandScore = product1.getInfo().getBrand().equals(product2.getInfo().getBrand()) ? 1.0 : 0.0;

    double priceScore = calculatePriceSimilarity(product1.getInfo().getPrice(),
        product2.getInfo().getPrice());

    double attributeScore = calculateAttributeSimilarity(product1.getInfo().getAttributes(),
        product2.getInfo().getAttributes());

    return (categoryScore * 0.4) + (brandScore * 0.3) + (priceScore * 0.2) + (attributeScore * 0.1);
  }

  /**
   * Valida se um produto deve aparecer nos resultados de busca
   */
  public boolean isProductSearchable(Product product, UserContext userContext) {
    // Verificações básicas
    if (!product.isSearchable()) {
      return false;
    }

    // Regras de negócio específicas
    if (product.getSeller().isSuspended()) {
      return false;
    }

    // Verificações baseadas na localização do usuário
    if (userContext != null && userContext.getLocation() != null) {
      // Aqui poderia ter regras de entrega por região, etc.
    }

    return true;
  }

  /**
   * Calcula boost de relevância baseado em métricas de negócio
   */
  public double calculateBusinessBoost(Product product, UserContext userContext) {
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

  private List<Product> applyBusinessRules(List<Product> products, UserContext userContext) {
    return products.stream()
        .filter(product -> isProductSearchable(product, userContext))
        .sorted((p1, p2) -> {
          double score1 = p1.calculateRelevanceScore(null, userContext).getValue() *
              calculateBusinessBoost(p1, userContext);
          double score2 = p2.calculateRelevanceScore(null, userContext).getValue() *
              calculateBusinessBoost(p2, userContext);
          return Double.compare(score2, score1); // Ordem decrescente
        })
        .toList();
  }

  private SearchQuery createFallbackQuery(SearchQuery originalQuery) {
    // Simplifica a query removendo filtros muito específicos
    return new SearchQuery(
        originalQuery.getTerms(),
        null, // Remove filtro de categoria
        List.of(), // Remove todos os filtros
        originalQuery.getSort(),
        originalQuery.getOffset(),
        originalQuery.getLimit() * 2 // Aumenta o limite
    );
  }

  private double calculateCategorySimilarity(Category cat1, Category cat2) {
    if (cat1.equals(cat2))
      return 1.0;
    if (cat1.isSubcategoryOf(cat2) || cat2.isSubcategoryOf(cat1))
      return 0.8;

    // Verifica se são da mesma categoria pai
    String[] path1 = cat1.getPath().split("/");
    String[] path2 = cat2.getPath().split("/");

    if (path1.length > 0 && path2.length > 0 && path1[0].equals(path2[0])) {
      return 0.5; // Mesmo nível superior
    }

    return 0.0;
  }

  private double calculatePriceSimilarity(java.math.BigDecimal price1, java.math.BigDecimal price2) {
    double p1 = price1.doubleValue();
    double p2 = price2.doubleValue();

    double ratio = Math.min(p1, p2) / Math.max(p1, p2);
    return ratio; // Quanto mais próximos os preços, maior a similaridade
  }

  private double calculateAttributeSimilarity(java.util.Set<String> attrs1, java.util.Set<String> attrs2) {
    java.util.Set<String> intersection = new java.util.HashSet<>(attrs1);
    intersection.retainAll(attrs2);

    java.util.Set<String> union = new java.util.HashSet<>(attrs1);
    union.addAll(attrs2);

    return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
  }
}