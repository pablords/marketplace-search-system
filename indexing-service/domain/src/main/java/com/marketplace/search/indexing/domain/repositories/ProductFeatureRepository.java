package com.marketplace.search.indexing.domain.repositories;

import com.marketplace.search.indexing.domain.valueobjects.ProductFeatures;

public interface ProductFeatureRepository {
	void save(ProductFeatures features)	throws Exception;
}
