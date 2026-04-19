package com.marketplace.search.indexing.domain.services;


import java.util.Map;
import java.util.regex.Pattern;

import com.marketplace.search.indexing.domain.entities.Product;

/**
 * Serviço responsável por extrair features de ML de produtos durante a indexação.
 * 
 * Calcula features que não dependem de query de busca (features estáticas).
 * Features que dependem de query (bm25_score, knn_score, exact_match, etc.) 
 * serão calculadas durante a busca pelo search-service.
 * 
 * Extrai 17 features, mas durante indexação calcula apenas as que não dependem de query:
 * - Qualidade do texto (lengths, ratios)
 * - Contexto (numbers)
 * - Popularidade (popularity, quality, CTR)
 */
public class FeatureExtractor {

    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\d+");

    /**
     * Extrai features estáticas de um produto (que não dependem de query de busca).
     * Features que dependem de query serão definidas como 0.0 e atualizadas durante a busca.
     * 
     * @param product Produto a ser indexado
     * @return Map com as 17 features nomeadas (features de query serão 0.0)
     */
    public Map<String, Double> extractStaticFeatures(Product product) {
        String title = product.getInfo().getTitle().toLowerCase();
        String description = product.getInfo().getDescription() != null 
            ? product.getInfo().getDescription().toLowerCase() 
            : "";
        
        return Map.ofEntries(
            // 1-3: Features de Relevância (dependem de query - serão 0.0 durante indexação)
            Map.entry("bm25_score", 0.0),
            Map.entry("knn_score", 0.0),
            Map.entry("hybrid_score", 0.0),
            
            // 4-5: Features de Match Textual (dependem de query - serão 0.0 durante indexação)
            Map.entry("exact_match", 0.0),
            Map.entry("term_coverage", 0.0),
            
            // 6-9: Features de Qualidade do Texto (podem ser calculadas)
            Map.entry("title_length", (double) title.length()),
            Map.entry("description_length", (double) description.length()),
            Map.entry("title_description_ratio", calculateTitleDescriptionRatio(title, description)),
            Map.entry("text_quality_score", calculateTextQualityScore(title, description)),
            
            // 10-13: Features de Contexto (parcialmente calculáveis)
            Map.entry("first_word_match", 0.0), // Depende de query
            Map.entry("has_numbers", hasNumbers(title) ? 1.0 : 0.0),
            Map.entry("brand_match", 0.0), // Depende de query
            Map.entry("category_match", 0.0), // Depende de query
            
            // 14-17: Features de Popularidade (podem ser calculadas)
            Map.entry("popularity_score", product.getMetrics().getPopularityScore()),
            Map.entry("quality_score", calculateQualityScore(product)),
            Map.entry("ctr", product.getMetrics().conversionRate()),
            Map.entry("sales_count_normalized", calculateNormalizedSalesCount(product))
        );
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
     * Feature 11: Verifica se o título contém números
     * Produtos com números (modelos, versões) podem ser mais específicos
     */
    private boolean hasNumbers(String text) {
        return NUMBER_PATTERN.matcher(text).find();
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

