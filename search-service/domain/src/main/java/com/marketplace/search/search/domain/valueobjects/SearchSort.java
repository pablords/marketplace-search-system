package com.marketplace.search.search.domain.valueobjects;

/**
 * Enum para opções de ordenação de busca
 */
public enum SearchSort {
    RELEVANCE("relevance", "Relevância"),
    PRICE_ASC("price_asc", "Menor preço"),
    PRICE_DESC("price_desc", "Maior preço"),
    NEWEST("newest", "Mais recentes"),
    OLDEST("oldest", "Mais antigos"),
    BEST_SELLERS("best_sellers", "Mais vendidos"),
    BEST_RATED("best_rated", "Melhor avaliados");

    private final String code;
    private final String description;

    SearchSort(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public static SearchSort fromCode(String code) {
        for (SearchSort sort : values()) {
            if (sort.code.equals(code)) {
                return sort;
            }
        }
        throw new IllegalArgumentException("Invalid sort code: " + code);
    }
}

