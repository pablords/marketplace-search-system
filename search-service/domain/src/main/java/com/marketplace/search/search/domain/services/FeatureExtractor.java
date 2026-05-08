package com.marketplace.search.search.domain.services;


import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.marketplace.search.search.domain.entities.Product;
import com.marketplace.search.search.domain.valueobjects.SearchQuery;

/**
 * Serviço responsável por extrair features de ML de produtos candidatos
 * para re-ranking com modelo de Machine Learning.
 * 
 * Extrai 17 features agrupadas em:
 * - Relevância (BM25, k-NN, híbrido)
 * - Match textual (exact match, term coverage)
 * - Qualidade do texto (lengths, ratios)
 * - Contexto (first word, numbers, brand)
 * - Popularidade (popularity, quality, CTR)
 */
public class FeatureExtractor {

    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\d+");

    /**
     * Extrai todas as 17 features de um produto candidato baseado na query de busca
     * 
     * @param product Produto candidato
     * @param query Query de busca do usuário
     * @param bm25Score Score BM25 do OpenSearch (normalizado 0-1)
     * @param knnScore Score k-NN de similaridade vetorial (normalizado 0-1)
     * @return Map com as 17 features nomeadas
     */
    public Map<String, Double> extractFeatures(Product product, SearchQuery query, 
                                                double bm25Score, double knnScore) {
        Map<String, Double> features = new java.util.HashMap<>();
        
        // 1. Extrair features estáticas (independentes da query)
        features.putAll(extractStaticFeatures(product));
        
        // 2. Extrair features dinâmicas (dependentes da query)
        features.putAll(extractDynamicFeatures(product, query, bm25Score, knnScore));
        
        return features;
    }

    /**
     * Extrai apenas features estáticas do produto (podem ser cacheadas com segurança)
     */
    public Map<String, Double> extractStaticFeatures(Product product) {
        String title = product.getInfo().getTitle();
        String description = product.getInfo().getDescription() != null ? product.getInfo().getDescription() : "";
        
        return Map.ofEntries(
            Map.entry("title_length", (double) title.length()),
            Map.entry("description_length", (double) description.length()),
            Map.entry("title_description_ratio", calculateTitleDescriptionRatio(title, description)),
            Map.entry("text_quality_score", calculateTextQualityScore(title, description)),
            Map.entry("has_numbers", hasNumbers(title) ? 1.0 : 0.0),
            Map.entry("popularity_score", product.getMetrics().getPopularityScore()),
            Map.entry("quality_score", calculateQualityScore(product)),
            Map.entry("ctr", product.getMetrics().conversionRate()),
            Map.entry("sales_count_normalized", calculateNormalizedSalesCount(product))
        );
    }

    /**
     * Extrai apenas features dinâmicas (dependentes da query)
     */
    public Map<String, Double> extractDynamicFeatures(Product product, SearchQuery query, 
                                                       double bm25Score, double knnScore) {
        String queryTerms = query.terms().toLowerCase();
        String title = product.getInfo().getTitle().toLowerCase();
        String description = product.getInfo().getDescription() != null 
            ? product.getInfo().getDescription().toLowerCase() 
            : "";
        String brandName = product.getInfo().getBrand().name().toLowerCase();
        String categoryName = product.getInfo().getCategory().getName().toLowerCase();
        
        Set<String> queryKeywords = query.getKeywords();
        
        double normalizedBm25 = normalizeScore(bm25Score);
        double normalizedKnn = normalizeScore(knnScore);
        
        return Map.ofEntries(
            Map.entry("bm25_score", normalizedBm25),
            Map.entry("knn_score", normalizedKnn),
            Map.entry("hybrid_score", calculateHybridScore(normalizedBm25, normalizedKnn)),
            Map.entry("exact_match", calculateExactMatch(queryTerms, title)),
            Map.entry("term_coverage", calculateTermCoverage(queryKeywords, title, description)),
            Map.entry("first_word_match", calculateFirstWordMatch(queryTerms, title)),
            Map.entry("brand_match", calculateBrandMatch(queryTerms, brandName)),
            Map.entry("category_match", calculateCategoryMatch(queryTerms, categoryName))
        );
    }

    /**
     * Normaliza um score para o intervalo [0.0, 1.0]
     */
    private double normalizeScore(double score) {
        if (score < 0) return 0.0;
        if (score > 1) return 1.0;
        return score;
    }

    /**
     * Feature 3: Score híbrido combinando BM25 e k-NN
     * Combinação ponderada: 60% BM25 + 40% k-NN
     */
    private double calculateHybridScore(double bm25, double knn) {
        return (bm25 * 0.6) + (knn * 0.4);
    }

    /**
     * Feature 4: Exact match - verifica se a query aparece exatamente no título
     * Retorna 1.0 se a query completa está no título, 0.0 caso contrário
     */
    private double calculateExactMatch(String query, String title) {
        return title.contains(query) ? 1.0 : 0.0;
    }

    /**
     * Feature 5: Term coverage - proporção de termos da query que aparecem no produto
     * Calcula quantos termos da query aparecem no título ou descrição
     */
    private double calculateTermCoverage(Set<String> queryKeywords, String title, String description) {
        if (queryKeywords.isEmpty()) {
            return 0.0;
        }
        
        String fullText = title + " " + description;
        long matchedTerms = queryKeywords.stream()
            .filter(keyword -> fullText.contains(keyword.toLowerCase()))
            .count();
        
        return (double) matchedTerms / queryKeywords.size();
    }

    /**
     * Feature 8: Ratio entre comprimento do título e descrição
     * Retorna título.length() / (descrição.length() + 1) para evitar divisão por zero
     */
    private double calculateTitleDescriptionRatio(String title, String description) {
        if (description.isEmpty()) {
            return title.length() > 0 ? 1.0 : 0.0;
        }
        return Math.min(1.0, (double) title.length() / description.length());
    }

    /**
     * Feature 9: Score de qualidade do texto baseado em comprimento e estrutura
     * Considera comprimento adequado do título (30-100 caracteres) e descrição não vazia
     */
    private double calculateTextQualityScore(String title, String description) {
        double score = 0.0;
        
        // Título com comprimento adequado (30-100 caracteres) recebe boost
        int titleLength = title.length();
        if (titleLength >= 30 && titleLength <= 100) {
            score += 0.5;
        } else if (titleLength > 0 && titleLength < 200) {
            score += 0.3;
        }
        
        // Descrição não vazia recebe boost
        if (!description.isEmpty() && description.length() >= 50) {
            score += 0.5;
        } else if (!description.isEmpty()) {
            score += 0.2;
        }
        
        return Math.min(1.0, score);
    }

    /**
     * Feature 10: First word match - verifica se a primeira palavra da query está no título
     * Boost importante para relevância
     */
    private double calculateFirstWordMatch(String query, String title) {
        String[] queryWords = query.trim().split("\\s+");
        if (queryWords.length == 0) {
            return 0.0;
        }
        
        String firstWord = queryWords[0].toLowerCase();
        return title.contains(firstWord) ? 1.0 : 0.0;
    }

    /**
     * Feature 11: Verifica se o título contém números
     * Produtos com números (modelos, versões) podem ser mais específicos
     */
    private boolean hasNumbers(String text) {
        return NUMBER_PATTERN.matcher(text).find();
    }

    /**
     * Feature 12: Brand match - verifica se a marca está mencionada na query
     * Boost para produtos de marcas buscadas
     */
    private double calculateBrandMatch(String query, String brandName) {
        if (brandName.isEmpty()) {
            return 0.0;
        }
        
        // Verifica se o nome da marca (ou partes dele) aparece na query
        String[] brandWords = brandName.split("\\s+");
        for (String brandWord : brandWords) {
            if (brandWord.length() > 2 && query.contains(brandWord)) {
                return 1.0;
            }
        }
        
        return 0.0;
    }

    /**
     * Feature 13: Category match - verifica se a categoria está mencionada na query
     */
    private double calculateCategoryMatch(String query, String categoryName) {
        if (categoryName.isEmpty()) {
            return 0.0;
        }
        
        String[] categoryWords = categoryName.split("\\s+");
        for (String categoryWord : categoryWords) {
            if (categoryWord.length() > 3 && query.contains(categoryWord)) {
                return 1.0;
            }
        }
        
        return 0.0;
    }

    /**
     * Feature 15: Quality score baseado em rating e reviews
     * Combina rating normalizado (0-5 -> 0-1) com quantidade de reviews
     */
    private double calculateQualityScore(Product product) {
        var metrics = product.getMetrics();
        double ratingScore = metrics.averageRating() / 5.0; // Normaliza 0-5 para 0-1
        
        // Boost para produtos com muitas reviews (confiabilidade)
        double reviewBoost = Math.min(0.3, Math.log1p(metrics.totalReviews()) / 10.0);
        
        return Math.min(1.0, ratingScore + reviewBoost);
    }

    /**
     * Feature 17: Sales count normalizado usando log
     * Usa log1p para reduzir viés de produtos extremamente populares
     */
    private double calculateNormalizedSalesCount(Product product) {
        long sales = product.getMetrics().totalSales();
        // Normaliza usando log: log1p(sales) / log1p(10000)
        // Produtos com 10000+ vendas recebem score próximo a 1.0
        double normalized = Math.log1p(sales) / Math.log1p(10000);
        return Math.min(1.0, normalized);
    }
}

