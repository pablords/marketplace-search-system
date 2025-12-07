package com.marketplace.search.catalog.domain.services;

import java.time.Instant;
import java.util.UUID;

import com.marketplace.search.catalog.domain.entities.Product;
import com.marketplace.search.catalog.domain.repositories.ProductRepository;
import com.marketplace.search.catalog.domain.valueobjects.ProductCreateSpec;
import com.marketplace.search.catalog.domain.valueobjects.ProductId;

public class ProductService {
	private final ProductRepository productRepository;

	public ProductService(ProductRepository productRepository) {
		this.productRepository = productRepository;
	}

	public Product createProduct(ProductCreateSpec spec) {
		var productId = new ProductId(UUID.randomUUID().toString());
		var now = Instant.now();
		Product product = Product.builder()
				.id(productId) // set id
				.createdAt(now)
				.updatedAt(now)
				.info(spec.getInfo())
				.seller(spec.getSeller())
				.metrics(spec.getMetrics())
				.status(spec.getStatus())
				.build();
		productRepository.save(product);
		return product;
	}

}
