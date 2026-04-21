package com.marketplace.search.search.application.queries;

import java.util.Map;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO para dados de depuração do ranking
 */
public record RankingDebugData(
    @JsonProperty("final_score") double finalScore,
    @JsonProperty("features") Map<String, Double> features
) {
}
