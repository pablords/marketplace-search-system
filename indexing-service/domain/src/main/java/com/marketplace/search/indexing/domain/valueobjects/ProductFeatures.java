package com.marketplace.search.indexing.domain.valueobjects;

public record ProductFeatures(
    String id,
    Double price,
    Double ctr,
    Double popularity,
    Double sellerScore,
    Double deliveryPerformance,
    Boolean isPromoted
) {}
