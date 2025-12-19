package com.marketplace.search.search.domain.services;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.marketplace.search.search.domain.entities.Category;
import com.marketplace.search.search.domain.entities.Product;
import com.marketplace.search.search.domain.repositories.ProductSearchRepository;
import com.marketplace.search.search.domain.valueobjects.SearchQuery;
import com.marketplace.search.search.domain.valueobjects.SearchResult;
import com.marketplace.search.search.domain.valueobjects.UserContext;

/**
 * Serviço de domínio para operações de busca avançada
 */
public class SearchDomainService {

  private final ProductSearchRepository searchRepository;
  private static final Logger logger = LoggerFactory.getLogger(SearchDomainService.class);

  public SearchDomainService(ProductSearchRepository searchRepository) {
    this.searchRepository = searchRepository;
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

    logger.debug("Verificações básicas {}", product.isSearchable());
    logger.debug("Vendedor suspenso {}", product.getSeller().isSuspended());

    if (!product.isSearchable()) {
      return false;
    }

    if (product.getSeller().isSuspended()) {
      return false;
    }

    // Verificações baseadas na localização do usuário
    if (userContext != null && userContext.location() != null) {
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

  private List<Product> applyBusinessRules(List<Product> products, SearchQuery query, UserContext userContext) {
    int sliceSize = Math.min(3, products.size());
    List<Product> slice = products.subList(0, sliceSize);
    logger.debug("Produtos antes de aplicar regras {}", slice);
    return products.stream()
        .filter(product -> isProductSearchable(product, userContext))
        .sorted((p1, p2) -> {
          double score1 = p1.calculateRelevanceScore(query, userContext).getValue() *
              calculateBusinessBoost(p1, userContext);
          double score2 = p2.calculateRelevanceScore(query, userContext).getValue() *
              calculateBusinessBoost(p2, userContext);
          return Double.compare(score2, score1); // Ordem decrescente
        })
        .toList();
  }

  private SearchQuery createFallbackQuery(SearchQuery originalQuery) {
    // Simplifica a query removendo filtros muito específicos
    return new SearchQuery(
        originalQuery.terms(),
        null, // Remove filtro de categoria
        List.of(), // Remove todos os filtros
        originalQuery.sort(),
        originalQuery.offset(),
        originalQuery.limit() * 2 // Aumenta o limite
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

