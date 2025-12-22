package com.marketplace.search.search.infrastructure.featurestore;

import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Composite key para ProductFeaturesMLEntity
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductFeaturesMLId implements Serializable {

    private static final long serialVersionUID = 1L;

    private String productId;
    private LocalDateTime calculatedAt;
    private String version;
}

