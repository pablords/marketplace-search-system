package com.marketplace.search.search.infrastructure.opensearch.mappers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.marketplace.search.search.domain.entities.Category;
import com.marketplace.search.search.domain.entities.Product;
import com.marketplace.search.search.domain.entities.Seller;
import com.marketplace.search.search.domain.valueobjects.Brand;
import com.marketplace.search.search.domain.valueobjects.ProductId;
import com.marketplace.search.search.domain.valueobjects.ProductInfo;
import com.marketplace.search.search.domain.valueobjects.ProductMetrics;
import com.marketplace.search.search.domain.valueobjects.ProductStatus;
import com.marketplace.search.search.domain.valueobjects.SellerReputation;
import com.marketplace.search.search.domain.valueobjects.SellerStatus;
import com.marketplace.search.search.domain.valueobjects.SellerType;
import com.marketplace.search.search.infrastructure.opensearch.documents.BrandDocument;
import com.marketplace.search.search.infrastructure.opensearch.documents.CategoryDocument;
import com.marketplace.search.search.infrastructure.opensearch.documents.ProductMetricsDocument;
import com.marketplace.search.search.infrastructure.opensearch.documents.ProductSearchDocument;
import com.marketplace.search.search.infrastructure.opensearch.documents.ProductStatusDocument;
import com.marketplace.search.search.infrastructure.opensearch.documents.SellerDocument;
import com.marketplace.search.search.infrastructure.opensearch.documents.SellerReputationDocument;

/**
 * Mapper entre entidades de domínio e documentos do OpenSearch
 */
@Component
public class OpenSearchProductMapper {

	private static final Logger logger = LoggerFactory.getLogger(OpenSearchProductMapper.class);

	public ProductSearchDocument toDocument(Product product) {
		logger.debug("Mapeando produto para documento OpenSearch: {}", product);
		ProductSearchDocument document = new ProductSearchDocument();

		document.setId(product.getId().getValue());
		document.setTitle(product.getInfo().getTitle());
		document.setDescription(product.getInfo().getDescription());
		document.setPrice(product.getInfo().getPrice());
		document.setCurrency(product.getInfo().getCurrency());

		document.setCategory(mapCategoryToDocument(product.getInfo().getCategory()));
		document.setBrand(mapBrandToDocument(product.getInfo().getBrand()));
		document.setSeller(mapSellerToDocument(product.getSeller()));

		document.setImages(product.getInfo().getImages());
		document.setAttributes(product.getInfo().getAttributes());
		document.setTags(product.getInfo().getTags());

		document.setMetrics(mapMetricsToDocument(product.getMetrics()));
		document.setStatus(mapStatusToDocument(product.getStatus(), product.getMetrics().stockQuantity()));

		document.setCreatedAt(product.getCreatedAt());
		document.setUpdatedAt(product.getUpdatedAt());

		// Campos derivados para otimização de busca
		document.setSearchableText(buildSearchableText(product));
		document.setPriceRange(calculatePriceRange(product.getInfo().getPrice()));
		document.setPopularityScore(product.getMetrics().getPopularityScore());

		return document;
	}

	public Product toDomain(ProductSearchDocument document) {
		ProductId id = ProductId.from(document.getId());

		ProductInfo info = new ProductInfo(document.getTitle(), document.getDescription(), document.getPrice(),
				document.getCurrency(), mapCategoryToDomain(document.getCategory()),
				mapBrandToDomain(document.getBrand()), document.getImages(), document.getAttributes(),
				document.getTags());

		com.marketplace.search.search.domain.entities.Seller seller = mapSellerToDomain(document.getSeller());
		ProductMetrics metrics = mapMetricsToDomain(document.getMetrics());
		ProductStatus status = mapStatusToDomain(document.getStatus());

		return Product.builder().id(id).info(info).seller(seller).metrics(metrics).status(status)
				.createdAt(document.getCreatedAt()).updatedAt(document.getUpdatedAt()).build();
	}

	private CategoryDocument mapCategoryToDocument(Category category) {
		CategoryDocument doc = new CategoryDocument();
		doc.setId(category.getId());
		doc.setName(category.getName());
		doc.setPath(category.getPath());
		doc.setParentId(category.getParentId());
		return doc;
	}

	private Category mapCategoryToDomain(CategoryDocument document) {
		return new Category(document.getId(), document.getName(), document.getParentId(), document.getPath());
	}

	private BrandDocument mapBrandToDocument(Brand brand) {
		BrandDocument doc = new BrandDocument();
		doc.setId(brand.id());
		doc.setName(brand.name());
		doc.setDescription(brand.description());
		return doc;
	}

	private Brand mapBrandToDomain(BrandDocument document) {
		return new Brand(document.getId(), document.getName(), document.getDescription());
	}

	private SellerDocument mapSellerToDocument(Seller seller) {
		SellerDocument doc = new SellerDocument();
		doc.setId(seller.getId());
		doc.setName(seller.getName());
		doc.setType(seller.getType().name());
		doc.setReputation(mapSellerReputationToDocument(seller.getReputation()));
		doc.setStatus(seller.getStatus().name());
		return doc;
	}

	private SellerReputationDocument mapSellerReputationToDocument(SellerReputation reputation) {
		return new SellerReputationDocument(reputation.getScore(), reputation.getTotalReviews(), reputation.getPositiveReviews(), reputation.getNeutralReviews(), reputation.getNegativeReviews(), reputation.getCancellationRate(), reputation.getDeliveryPerformance());
	}

	private Seller mapSellerToDomain(SellerDocument document) {
		if (document == null) {
			logger.warn("SellerDocument é null, não é possível mapear para domínio");
			throw new IllegalArgumentException("SellerDocument não pode ser null");
		}
		return new Seller(document.getId(), document.getName(), mapSellerType(document.getType()), mapSellerReputationToDomain(document.getReputation()), mapSellerStatus(document.getStatus()), null);
	}

	private SellerReputation mapSellerReputationToDomain(SellerReputationDocument document) {
		if (document == null) {
			// Se o documento de reputação não estiver disponível, criar uma reputação padrão
			logger.warn("SellerReputationDocument é null, usando valores padrão");
			return new SellerReputation(5.0, 0, 0, 0, 0, 0.0, 1.0);
		}
		return new SellerReputation(document.getScore(), document.getTotalReviews(), document.getPositiveReviews(), document.getNeutralReviews(), document.getNegativeReviews(), document.getCancellationRate(), document.getDeliveryPerformance());
	}

	private ProductMetricsDocument mapMetricsToDocument(ProductMetrics metrics) {
		logger.debug("Mapeando métricas de produto: {}", metrics);
		ProductMetricsDocument doc = new ProductMetricsDocument();
		doc.setTotalViews((long) metrics.totalViews());
		doc.setTotalSales((long) metrics.totalSales());
		doc.setTotalReviews((long) metrics.totalReviews());
		doc.setAverageRating(metrics.averageRating());
		doc.setStockQuantity(metrics.stockQuantity());
		doc.setCtr(metrics.conversionRate());
		return doc;
	}

	private ProductMetrics mapMetricsToDomain(ProductMetricsDocument document) {
		return new ProductMetrics(
				document.getTotalViews() != null ? Math.toIntExact(document.getTotalViews()) : 0,
				document.getTotalSales() != null ? Math.toIntExact(document.getTotalSales()) : 0,
				document.getTotalReviews() != null ? Math.toIntExact(document.getTotalReviews()) : 0,
				document.getAverageRating() != null ? document.getAverageRating() : 0.0,
				document.getStockQuantity(), document.getCtr(), null, null);
	}

	private ProductStatusDocument mapStatusToDocument(ProductStatus status, int stockQuantity) {
		logger.debug("Mapeando status de produto. Estoque: {}", stockQuantity);
		ProductStatusDocument doc = new ProductStatusDocument();
		doc.setIsActive(status.isActive());
		doc.setHasStock(stockQuantity > 0);
		doc.setIsSuspended(status.isSuspended());
		return doc;
	}

	private ProductStatus mapStatusToDomain(ProductStatusDocument document) {
		boolean isActive = document.getIsActive() != null ? document.getIsActive() : false;
		boolean hasStock = document.getHasStock() != null ? document.getHasStock() : false;
		boolean isSuspended = document.getIsSuspended() != null ? document.getIsSuspended() : false;

		if (isSuspended) {
			return ProductStatus.suspended("Suspended");
		} else if (!isActive) {
			return ProductStatus.inactive();
		} else {
			return ProductStatus.active(hasStock);
		}
	}

	private String buildSearchableText(Product product) {
		StringBuilder searchableText = new StringBuilder();

		searchableText.append(product.getInfo().getTitle()).append(" ");
		searchableText.append(product.getInfo().getDescription()).append(" ");
		searchableText.append(product.getInfo().getBrand().name()).append(" ");
		searchableText.append(product.getInfo().getCategory().getName()).append(" ");

		// Adicionar atributos e tags
		product.getInfo().getAttributes().forEach(attr -> searchableText.append(attr).append(" "));
		product.getInfo().getTags().forEach(tag -> searchableText.append(tag).append(" "));

		return searchableText.toString().trim().toLowerCase();
	}

	private String calculatePriceRange(java.math.BigDecimal price) {
		double priceValue = price.doubleValue();

		if (priceValue < 50)
			return "0-50";
		if (priceValue < 100)
			return "50-100";
		if (priceValue < 200)
			return "100-200";
		if (priceValue < 500)
			return "200-500";
		if (priceValue < 1000)
			return "500-1000";

		return "1000+";
	}

	private SellerType mapSellerType(String type) {
		try {
			return SellerType.valueOf(type);
		} catch (Exception e) {
			return SellerType.REGULAR;
		}
	}

	private SellerStatus mapSellerStatus(String status) {
		try {
			return SellerStatus.valueOf(status);
		} catch (Exception e) {
			return SellerStatus.ACTIVE;
		}
	}
}

