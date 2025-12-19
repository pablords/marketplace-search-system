package com.marketplace.search.search.infrastructure.opensearch.queries;

import java.util.List;
import java.util.stream.Collectors;

import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.SortOptions;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch._types.query_dsl.TextQueryType;
import org.opensearch.client.json.JsonData;
import org.springframework.stereotype.Component;

import com.marketplace.search.search.domain.entities.Category;
import com.marketplace.search.search.domain.entities.Product;
import com.marketplace.search.search.domain.valueobjects.SearchFilter;
import com.marketplace.search.search.domain.valueobjects.SearchQuery;
import com.marketplace.search.search.domain.valueobjects.SearchSort;
import com.marketplace.search.search.domain.valueobjects.UserContext;

/**
 * Builder para construção de queries do OpenSearch
 */
@Component
public class OpenSearchQueryBuilder {

	/**
	 * Constrói query principal de busca
	 */
	public Query buildQuery(SearchQuery searchQuery, UserContext userContext) {
		BoolQuery.Builder boolQueryBuilder = new BoolQuery.Builder();

		// Query de texto principal
		Query textQuery = buildTextQuery(searchQuery.terms());
		boolQueryBuilder.must(textQuery);

		// Filtros de categoria
		if (searchQuery.hasCategoryFilter()) {
			Query categoryFilter = buildCategoryFilter(searchQuery.category());
			boolQueryBuilder.filter(categoryFilter);
		}

		// Filtros adicionais
		if (searchQuery.hasFilters()) {
			List<Query> filters = searchQuery.filters().stream().map(this::buildFilter)
					.collect(Collectors.toList());
			boolQueryBuilder.filter(filters);
		}

		// Filtros de status (produtos ativos, com estoque, etc.)
		boolQueryBuilder.filter(buildStatusFilters());

		// Boost baseado no contexto do usuário
		if (userContext != null && !userContext.isAnonymous()) {
			List<Query> boostQueries = buildPersonalizationBoosts(userContext);
			boolQueryBuilder.should(boostQueries);
			boolQueryBuilder.minimumShouldMatch("0"); // Opcional
		}

		return Query.of(q -> q.bool(boolQueryBuilder.build()));
	}

	/**
	 * Constrói query de texto com multiple match
	 */
	private Query buildTextQuery(String terms) {
		return Query.of(q -> q.multiMatch(m -> m.query(terms)
				.fields("title^3", "description^1", "brand.name^2", "searchable_text^0.5")
				.type(TextQueryType.BestFields).fuzziness("AUTO").prefixLength(1).maxExpansions(50)));
	}

	/**
	 * Constrói filtro de categoria
	 */
	private Query buildCategoryFilter(Category category) {
		return Query.of(q -> q.bool(b -> b.should(s -> s.term(t -> t.field("category.id").value(org.opensearch.client.opensearch._types.FieldValue.of(category.getId()))))
				.should(s -> s.prefix(p -> p.field("category.path").value(category.getPath())))));
	}

	/**
	 * Constrói filtro individual
	 */
	private Query buildFilter(SearchFilter filter) {
		return switch (filter.type()) {
		case TERM -> buildTermFilter(filter);
		case TERMS -> buildTermsFilter(filter);
		case RANGE -> buildRangeFilter(filter);
		case BOOLEAN -> buildBooleanFilter(filter);
		};
	}

	private Query buildTermFilter(SearchFilter filter) {
		return Query.of(q -> q.term(t -> t.field(mapFilterField(filter.name())).value(v -> v.stringValue(filter.getSingleValue()))));
	}

	private Query buildTermsFilter(SearchFilter filter) {
		return Query.of(q -> q.terms(t -> t.field(mapFilterField(filter.name()))
				.terms(terms -> terms.value(filter.values().stream()
						.map(v -> org.opensearch.client.opensearch._types.FieldValue.of(v))
						.collect(Collectors.toList())))));
	}

	private Query buildRangeFilter(SearchFilter filter) {
		if (filter.values().size() < 2) {
			throw new IllegalArgumentException("Range filter requires exactly 2 values");
		}

		String field = mapFilterField(filter.name());
		String minValue = filter.values().get(0);
		String maxValue = filter.values().get(1);

		return Query.of(q -> q.range(r -> r.field(field).gte(JsonData.of(minValue)).lte(JsonData.of(maxValue))));
	}

	private Query buildBooleanFilter(SearchFilter filter) {
		boolean value = Boolean.parseBoolean(filter.getSingleValue());

		return Query.of(q -> q.term(t -> t.field(mapFilterField(filter.name())).value(v -> v.booleanValue(value))));
	}

	/**
	 * Filtros de status básicos
	 */
	private Query buildStatusFilters() {
		return Query.of(q -> q.bool(b -> b.must(m -> m.term(t -> t.field("status.is_active").value(v -> v.booleanValue(true))))
				.must(m -> m.term(t -> t.field("status.is_suspended").value(v -> v.booleanValue(false))))));
	}

	/**
	 * Constrói boosts de personalização
	 */
	private List<Query> buildPersonalizationBoosts(UserContext userContext) {
		List<Query> boosts = new java.util.ArrayList<>();

		// Boost para categorias preferidas
		if (!userContext.preferredCategories().isEmpty()) {
			Query categoryBoost = Query.of(q -> q.terms(t -> t.field("category.id")
					.terms(terms -> terms.value(userContext.preferredCategories().stream()
							.map(v -> org.opensearch.client.opensearch._types.FieldValue.of(v))
							.collect(Collectors.toList())))
					.boost(1.2f)));
			boosts.add(categoryBoost);
		}

		// Boost para vendedores com compras anteriores
		if (!userContext.purchaseHistory().isEmpty()) {
			Query sellerBoost = Query.of(q -> q.terms(t -> t.field("seller.id")
					.terms(terms -> terms.value(userContext.purchaseHistory().stream()
							.map(v -> org.opensearch.client.opensearch._types.FieldValue.of(v))
							.collect(Collectors.toList())))
					.boost(1.15f)));
			boosts.add(sellerBoost);
		}

		return boosts;
	}

	/**
	 * Constrói query de similaridade
	 */
	public Query buildSimilarityQuery(Product product) {
		return Query.of(q -> q.bool(b -> b.should(s -> s.term(t -> t.field("category.id")
				.value(v -> v.stringValue(product.getInfo().getCategory().getId())).boost(2.0f)))
				.should(s -> s.term(t -> t.field("brand.id")
						.value(v -> v.stringValue(product.getInfo().getBrand().id())).boost(1.5f)))
				.should(s -> s.range(r -> r.field("price")
						.gte(JsonData.of(product.getInfo().getPrice().multiply(java.math.BigDecimal.valueOf(0.7))))
						.lte(JsonData.of(product.getInfo().getPrice().multiply(java.math.BigDecimal.valueOf(1.3))))
						.boost(1.0f)))
				.minimumShouldMatch("1")));
	}

	/**
	 * Constrói query de sugestão
	 */
	public Query buildSuggestionQuery(String partialTerm) {
		return Query.of(q -> q.bool(b -> b.should(s -> s.prefix(p -> p.field("title").value(partialTerm)))
				.should(s -> s.wildcard(w -> w.field("title").value("*" + partialTerm + "*")))));
	}

	/**
	 * Constrói query de produtos populares
	 */
	public Query buildPopularityQuery(String categoryId) {
		BoolQuery.Builder boolQuery = new BoolQuery.Builder();

		if (categoryId != null) {
			boolQuery.filter(f -> f.term(t -> t.field("category.id").value(v -> v.stringValue(categoryId))));
		}

		boolQuery.must(m -> m.range(r -> r.field("metrics.total_sales").gte(JsonData.of(10))));

		return Query.of(q -> q.bool(boolQuery.build()));
	}

	/**
	 * Constrói query de produtos em promoção
	 */
	public Query buildOnSaleQuery() {
		return Query.of(q -> q.bool(b -> b.must(m -> m.exists(e -> e.field("price")))
				.must(m -> m.term(t -> t.field("status.is_active").value(v -> v.booleanValue(true))))
				.must(m -> m.term(t -> t.field("status.has_stock").value(v -> v.booleanValue(true))))));
	}

	/**
	 * Constrói opções de ordenação
	 */
	public List<SortOptions> buildSort(SearchSort sort) {
		return switch (sort) {
		case RELEVANCE -> List.of(SortOptions.of(s -> s.score(sc -> sc.order(SortOrder.Desc))));
		case PRICE_ASC -> List.of(SortOptions.of(s -> s.field(f -> f.field("price").order(SortOrder.Asc))));
		case PRICE_DESC -> List.of(SortOptions.of(s -> s.field(f -> f.field("price").order(SortOrder.Desc))));
		case NEWEST -> List.of(SortOptions.of(s -> s.field(f -> f.field("created_at").order(SortOrder.Desc))));
		case OLDEST -> List.of(SortOptions.of(s -> s.field(f -> f.field("created_at").order(SortOrder.Asc))));
		case BEST_SELLERS -> List.of(
				SortOptions.of(s -> s.field(f -> f.field("metrics.total_sales").order(SortOrder.Desc))),
				SortOptions.of(s -> s.score(sc -> sc.order(SortOrder.Desc))));
		case BEST_RATED -> List.of(
				SortOptions.of(s -> s.field(f -> f.field("metrics.average_rating").order(SortOrder.Desc))),
				SortOptions.of(s -> s.field(f -> f.field("metrics.total_reviews").order(SortOrder.Desc))));
		};
	}

	/**
	 * Mapeia nomes de filtros para campos do OpenSearch
	 */
	private String mapFilterField(String filterName) {
		return switch (filterName) {
		case "price" -> "price";
		case "brand" -> "brand.id";
		case "category" -> "category.id";
		case "condition" -> "condition";
		case "free_shipping" -> "free_shipping";
		case "attributes" -> "attributes";
		default -> filterName;
		};
	}
}

