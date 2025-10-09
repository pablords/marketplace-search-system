package com.marketplace.search.infrastructure.elasticsearch.queries;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.marketplace.search.domain.entities.Product;
import com.marketplace.search.domain.valueobjects.Category;
import com.marketplace.search.domain.valueobjects.SearchFilter;
import com.marketplace.search.domain.valueobjects.SearchQuery;
import com.marketplace.search.domain.valueobjects.SearchSort;
import com.marketplace.search.domain.valueobjects.UserContext;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import co.elastic.clients.json.JsonData;

/**
 * Builder para construção de queries do Elasticsearch
 */
@Component
public class ElasticsearchQueryBuilder {

    /**
     * Constrói query principal de busca
     */
    public Query buildQuery(SearchQuery searchQuery, UserContext userContext) {
        BoolQuery.Builder boolQueryBuilder = new BoolQuery.Builder();
        
        // Query de texto principal
        Query textQuery = buildTextQuery(searchQuery.getTerms());
        boolQueryBuilder.must(textQuery);
        
        // Filtros de categoria
        if (searchQuery.hasCategoryFilter()) {
            Query categoryFilter = buildCategoryFilter(searchQuery.getCategory());
            boolQueryBuilder.filter(categoryFilter);
        }
        
        // Filtros adicionais
        if (searchQuery.hasFilters()) {
            List<Query> filters = searchQuery.getFilters().stream()
                .map(this::buildFilter)
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
        return Query.of(q -> q
            .multiMatch(m -> m
                .query(terms)
                .fields("title^3", "description^1", "brand.name^2", "searchable_text^0.5")
                .type(TextQueryType.BestFields)
                .fuzziness("AUTO")
                .prefixLength(1)
                .maxExpansions(50)
            )
        );
    }

    /**
     * Constrói filtro de categoria
     */
    private Query buildCategoryFilter(Category category) {
        return Query.of(q -> q
            .bool(b -> b
                .should(s -> s
                    .term(t -> t
                        .field("category.id")
                        .value(category.getId())
                    )
                )
                .should(s -> s
                    .prefix(p -> p
                        .field("category.path")
                        .value(category.getPath())
                    )
                )
            )
        );
    }

    /**
     * Constrói filtro individual
     */
    private Query buildFilter(SearchFilter filter) {
        return switch (filter.getType()) {
            case TERM -> buildTermFilter(filter);
            case TERMS -> buildTermsFilter(filter);
            case RANGE -> buildRangeFilter(filter);
            case BOOLEAN -> buildBooleanFilter(filter);
        };
    }

    private Query buildTermFilter(SearchFilter filter) {
        return Query.of(q -> q
            .term(t -> t
                .field(mapFilterField(filter.getName()))
                .value(filter.getSingleValue())
            )
        );
    }

    private Query buildTermsFilter(SearchFilter filter) {
        return Query.of(q -> q
            .terms(t -> t
                .field(mapFilterField(filter.getName()))
                .terms(terms -> terms.value(
                    filter.getValues().stream()
                        .map(FieldValue::of)
                        .collect(Collectors.toList())
                ))
            )
        );
    }

    private Query buildRangeFilter(SearchFilter filter) {
        if (filter.getValues().size() < 2) {
            throw new IllegalArgumentException("Range filter requires exactly 2 values");
        }
        
        String field = mapFilterField(filter.getName());
        String minValue = filter.getValues().get(0);
        String maxValue = filter.getValues().get(1);
        
        return Query.of(q -> q
            .range(r -> r
                .field(field)
                .gte(JsonData.of(minValue))
                .lte(JsonData.of(maxValue))
            )
        );
    }

    private Query buildBooleanFilter(SearchFilter filter) {
        boolean value = Boolean.parseBoolean(filter.getSingleValue());
        
        return Query.of(q -> q
            .term(t -> t
                .field(mapFilterField(filter.getName()))
                .value(value)
            )
        );
    }

    /**
     * Filtros de status básicos
     */
    private Query buildStatusFilters() {
        return Query.of(q -> q
            .bool(b -> b
                .must(m -> m
                    .term(t -> t
                        .field("status.is_active")
                        .value(true)
                    )
                )
                .must(m -> m
                    .term(t -> t
                        .field("status.is_suspended")
                        .value(false)
                    )
                )
                .must(m -> m
                    .term(t -> t
                        .field("seller.status")
                        .value("ACTIVE")
                    )
                )
            )
        );
    }

    /**
     * Constrói boosts de personalização
     */
    private List<Query> buildPersonalizationBoosts(UserContext userContext) {
        List<Query> boosts = new java.util.ArrayList<>();
        
        // Boost para categorias preferidas
        if (!userContext.getPreferredCategories().isEmpty()) {
            Query categoryBoost = Query.of(q -> q
                .terms(t -> t
                    .field("category.id")
                    .terms(terms -> terms.value(
                        userContext.getPreferredCategories().stream()
                            .map(FieldValue::of)
                            .collect(Collectors.toList())
                    ))
                    .boost(1.2f)
                )
            );
            boosts.add(categoryBoost);
        }
        
        // Boost para vendedores com compras anteriores
        if (!userContext.getPurchaseHistory().isEmpty()) {
            Query sellerBoost = Query.of(q -> q
                .terms(t -> t
                    .field("seller.id")
                    .terms(terms -> terms.value(
                        userContext.getPurchaseHistory().stream()
                            .map(FieldValue::of)
                            .collect(Collectors.toList())
                    ))
                    .boost(1.15f)
                )
            );
            boosts.add(sellerBoost);
        }
        
        return boosts;
    }

    /**
     * Constrói query de similaridade
     */
    public Query buildSimilarityQuery(Product product) {
        return Query.of(q -> q
            .bool(b -> b
                .should(s -> s
                    .term(t -> t
                        .field("category.id")
                        .value(product.getInfo().getCategory().getId())
                        .boost(2.0f)
                    )
                )
                .should(s -> s
                    .term(t -> t
                        .field("brand.id")
                        .value(product.getInfo().getBrand().getId())
                        .boost(1.5f)
                    )
                )
                .should(s -> s
                    .range(r -> r
                        .field("price")
                        .gte(JsonData.of(product.getInfo().getPrice().multiply(java.math.BigDecimal.valueOf(0.7))))
                        .lte(JsonData.of(product.getInfo().getPrice().multiply(java.math.BigDecimal.valueOf(1.3))))
                        .boost(1.0f)
                    )
                )
                .minimumShouldMatch("1")
            )
        );
    }

    /**
     * Constrói query de sugestão
     */
    public Query buildSuggestionQuery(String partialTerm) {
        return Query.of(q -> q
            .bool(b -> b
                .should(s -> s
                    .prefix(p -> p
                        .field("title")
                        .value(partialTerm)
                    )
                )
                .should(s -> s
                    .wildcard(w -> w
                        .field("title")
                        .value("*" + partialTerm + "*")
                    )
                )
            )
        );
    }

    /**
     * Constrói query de produtos populares
     */
    public Query buildPopularityQuery(String categoryId) {
        BoolQuery.Builder boolQuery = new BoolQuery.Builder();
        
        if (categoryId != null) {
            boolQuery.filter(f -> f
                .term(t -> t
                    .field("category.id")
                    .value(categoryId)
                )
            );
        }
        
        boolQuery.must(m -> m
            .range(r -> r
                .field("metrics.total_sales")
                .gte(JsonData.of(10))
            )
        );
        
        return Query.of(q -> q.bool(boolQuery.build()));
    }

    /**
     * Constrói query de produtos em promoção
     */
    public Query buildOnSaleQuery() {
        return Query.of(q -> q
            .bool(b -> b
                .must(m -> m
                    .exists(e -> e.field("price"))
                )
                .must(m -> m
                    .term(t -> t
                        .field("status.is_active")
                        .value(true)
                    )
                )
                .must(m -> m
                    .term(t -> t
                        .field("status.has_stock")
                        .value(true)
                    )
                )
            )
        );
    }

    /**
     * Constrói opções de ordenação
     */
    public List<SortOptions> buildSort(SearchSort sort) {
        return switch (sort) {
            case RELEVANCE -> List.of(
                SortOptions.of(s -> s.score(sc -> sc.order(SortOrder.Desc)))
            );
            case PRICE_ASC -> List.of(
                SortOptions.of(s -> s.field(f -> f.field("price").order(SortOrder.Asc)))
            );
            case PRICE_DESC -> List.of(
                SortOptions.of(s -> s.field(f -> f.field("price").order(SortOrder.Desc)))
            );
            case NEWEST -> List.of(
                SortOptions.of(s -> s.field(f -> f.field("created_at").order(SortOrder.Desc)))
            );
            case OLDEST -> List.of(
                SortOptions.of(s -> s.field(f -> f.field("created_at").order(SortOrder.Asc)))
            );
            case BEST_SELLERS -> List.of(
                SortOptions.of(s -> s.field(f -> f.field("metrics.total_sales").order(SortOrder.Desc))),
                SortOptions.of(s -> s.score(sc -> sc.order(SortOrder.Desc)))
            );
            case BEST_RATED -> List.of(
                SortOptions.of(s -> s.field(f -> f.field("metrics.average_rating").order(SortOrder.Desc))),
                SortOptions.of(s -> s.field(f -> f.field("metrics.total_reviews").order(SortOrder.Desc)))
            );
        };
    }

    /**
     * Mapeia nomes de filtros para campos do Elasticsearch
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