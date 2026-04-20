package com.marketplace.search.search.application.ports;

import java.time.Duration;

/**
 * Port defining the search cache configuration required by the application layer.
 */
public interface SearchCacheSettings {
    boolean isEnabled();
    Duration getDefaultTtl();
    Duration getSearchResultsTtl();
    Duration getPopularSearchesTtl();
    String getKeyPrefix();
    boolean hasValidSearchTtl();
}
