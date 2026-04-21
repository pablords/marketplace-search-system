package com.marketplace.search.search.domain.valueobjects;

import java.util.Map;

/**
 * Value Object para depuração do ranking
 */
public record RankingDebug(
    double finalScore,
    Map<String, Double> features
) {
}
