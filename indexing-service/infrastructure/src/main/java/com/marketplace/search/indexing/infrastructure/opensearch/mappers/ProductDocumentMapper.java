package com.marketplace.search.indexing.infrastructure.opensearch.mappers;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.marketplace.search.indexing.domain.entities.Category;
import com.marketplace.search.indexing.domain.entities.Product;
import com.marketplace.search.indexing.domain.entities.Seller;
import com.marketplace.search.indexing.domain.valueobjects.Brand;
import com.marketplace.search.indexing.domain.valueobjects.ProductMetrics;
import com.marketplace.search.indexing.domain.valueobjects.ProductStatus;

import lombok.extern.slf4j.Slf4j;

/**
 * Mapper que converte Product (domínio) → HashMap<String, Object> completo
 * para indexação no OpenSearch, incluindo todos os campos necessários para
 * que o search-service possa ler os documentos corretamente.
 */
@Slf4j
@Component
public class ProductDocumentMapper {

	/**
	 * Método principal que converte um Product em um Map completo para indexação
	 * 
	 * @param product Produto do domínio
	 * @return HashMap com todos os campos necessários para indexação
	 */
	public Map<String, Object> toDocumentMap(Product product) {
		if (product == null) {
			log.warn("Tentativa de mapear produto nulo");
			return new HashMap<>();
		}

		Map<String, Object> document = new HashMap<>();

		// Campos básicos
		document.put("id", product.getId().getValue());
		document.put("title", product.getInfo().getTitle());
		document.put("description", product.getInfo().getDescription());
		document.put("price", product.getInfo().getPrice());
		document.put("currency", product.getInfo().getCurrency());

		// Objetos aninhados
		document.put("category", mapCategory(product.getInfo().getCategory()));
		document.put("brand", mapBrand(product.getInfo().getBrand()));
		document.put("seller", mapSeller(product.getSeller()));

		// Listas e Sets
		document.put("images", product.getInfo().getImages());
		document.put("attributes", convertSetToList(product.getInfo().getAttributes()));
		document.put("tags", convertSetToList(product.getInfo().getTags()));

		// Métricas e Status
		document.put("metrics", mapMetrics(product.getMetrics()));
		document.put("status", mapStatus(product.getStatus()));

		// Timestamps
		document.put("created_at", product.getCreatedAt());
		document.put("updated_at", product.getUpdatedAt());

		// Campos derivados
		document.put("searchable_text", buildSearchableText(product));
		document.put("price_range", calculatePriceRange(product.getInfo().getPrice()));
		document.put("popularity_score", product.getMetrics().getPopularityScore());

		return document;
	}

	/**
	 * Mapeia Category para Map
	 * 
	 * @param category Categoria do domínio (pode ser null)
	 * @return Map com campos: id, name, path, parent_id (parent_id pode ser null)
	 */
	private Map<String, Object> mapCategory(Category category) {
		if (category == null) {
			log.warn("Tentativa de mapear categoria nula");
			return new HashMap<>();
		}

		Map<String, Object> categoryMap = new HashMap<>();
		categoryMap.put("id", category.getId());
		categoryMap.put("name", category.getName());
		categoryMap.put("path", category.getPath());
		// parent_id é opcional e pode ser null
		categoryMap.put("parent_id", category.getParentId());
		return categoryMap;
	}

	/**
	 * Mapeia Brand para Map
	 */
	private Map<String, Object> mapBrand(Brand brand) {
		Map<String, Object> brandMap = new HashMap<>();
		brandMap.put("id", brand.id());
		brandMap.put("name", brand.name());
		brandMap.put("description", brand.description());
		return brandMap;
	}

	/**
	 * Mapeia Seller para Map
	 */
	private Map<String, Object> mapSeller(Seller seller) {
		Map<String, Object> sellerMap = new HashMap<>();
		sellerMap.put("id", seller.getId());
		sellerMap.put("name", seller.getName());
		sellerMap.put("status", seller.getStatus().name());
		sellerMap.put("type", seller.getType().name());
		sellerMap.put("reputation_score", seller.getReputationScore());
		return sellerMap;
	}

	/**
	 * Mapeia ProductMetrics para Map
	 */
	private Map<String, Object> mapMetrics(ProductMetrics metrics) {
		Map<String, Object> metricsMap = new HashMap<>();
		metricsMap.put("total_sales", (long) metrics.totalSales());
		metricsMap.put("total_views", (long) metrics.totalViews());
		metricsMap.put("total_reviews", (long) metrics.totalReviews());
		metricsMap.put("average_rating", metrics.averageRating());
		metricsMap.put("available_quantity", metrics.stockQuantity());
		metricsMap.put("ctr", metrics.conversionRate());
		return metricsMap;
	}

	/**
	 * Mapeia ProductStatus para Map
	 */
	private Map<String, Object> mapStatus(ProductStatus status) {
		Map<String, Object> statusMap = new HashMap<>();
		statusMap.put("is_active", status.isActive());
		statusMap.put("is_suspended", status.isSuspended());
		statusMap.put("has_stock", status.hasStock());
		return statusMap;
	}

	/**
	 * Constrói texto pesquisável combinando título, descrição, marca, categoria,
	 * atributos e tags
	 */
	private String buildSearchableText(Product product) {
		StringBuilder searchableText = new StringBuilder();

		searchableText.append(product.getInfo().getTitle()).append(" ");
		searchableText.append(product.getInfo().getDescription()).append(" ");
		searchableText.append(product.getInfo().getBrand().name()).append(" ");
		searchableText.append(product.getInfo().getCategory().getName()).append(" ");

		// Adicionar atributos e tags
		product.getInfo().getAttributes()
				.forEach(attr -> searchableText.append(attr).append(" "));
		product.getInfo().getTags()
				.forEach(tag -> searchableText.append(tag).append(" "));

		return searchableText.toString().trim().toLowerCase();
	}

	/**
	 * Calcula faixa de preço baseada no valor
	 */
	private String calculatePriceRange(BigDecimal price) {
		if (price == null) {
			return "0-50";
		}

		double priceValue = price.doubleValue();

		if (priceValue < 50)
			return "0-50";
		if (priceValue < 100)
			return "50-100";
		if (priceValue < 200)
			return "100-200";
		if (priceValue < 500)
			return "200-500";
		if (priceValue < 1000)
			return "500-1000";

		return "1000+";
	}

	/**
	 * Converte Set para List (necessário para serialização JSON)
	 */
	private List<String> convertSetToList(Set<String> set) {
		if (set == null) {
			return new ArrayList<>();
		}
		return new ArrayList<>(set);
	}
}

