package com.marketplace.search.search.infrastructure.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.marketplace.search.search.application.ports.SearchCacheSettings;

/**
 * Infrastructure implementation of SearchCacheSettings using Spring @Value.
 */
@Component
public class SearchCacheProperties implements SearchCacheSettings {

    private final boolean enabled;
    private final Duration defaultTtl;
    private final Duration searchResultsTtl;
    private final Duration popularSearchesTtl;
    private final String keyPrefix;

    public SearchCacheProperties(
            @Value("${marketplace.search.cache.enabled:true}") boolean enabled,
            @Value("${marketplace.search.cache.default-ttl:PT1H}") Duration defaultTtl,
            @Value("${marketplace.search.cache.search-results-ttl:PT10M}") Duration searchResultsTtl,
            @Value("${marketplace.search.cache.popular-searches-ttl:PT24H}") Duration popularSearchesTtl,
            @Value("${marketplace.search.cache.key-prefix:search:results}") String keyPrefix) {
        this.enabled = enabled;
        this.defaultTtl = defaultTtl != null ? defaultTtl : Duration.ofHours(1);
        this.searchResultsTtl = searchResultsTtl != null ? searchResultsTtl : this.defaultTtl;
        this.popularSearchesTtl = popularSearchesTtl != null ? popularSearchesTtl : this.defaultTtl;
        this.keyPrefix = keyPrefix != null && !keyPrefix.isBlank() ? keyPrefix : "search:results";
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public Duration getDefaultTtl() {
        return defaultTtl;
    }

    @Override
    public Duration getSearchResultsTtl() {
        return searchResultsTtl;
    }

    @Override
    public Duration getPopularSearchesTtl() {
        return popularSearchesTtl;
    }

    @Override
    public String getKeyPrefix() {
        return keyPrefix;
    }

    @Override
    public boolean hasValidSearchTtl() {
        return !searchResultsTtl.isZero() && !searchResultsTtl.isNegative();
    }
}
