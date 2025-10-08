package com.marketplace.search.application.usecases;

import com.marketplace.search.application.dto.SearchRequestDTO;
import com.marketplace.search.application.dto.SearchResultDTO;
import com.marketplace.search.application.mappers.SearchMapper;
import com.marketplace.search.domain.repositories.ProductSearchRepository;
import com.marketplace.search.domain.services.SearchDomainService;
import com.marketplace.search.domain.valueobjects.SearchQuery;
import com.marketplace.search.domain.valueobjects.SearchResult;
import com.marketplace.search.domain.valueobjects.UserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Caso de uso para busca de produtos
 */
@Service
public class SearchProductsUseCase {
    
    private static final Logger logger = LoggerFactory.getLogger(SearchProductsUseCase.class);
    
    private final ProductSearchRepository searchRepository;
    private final SearchDomainService searchDomainService;
    private final SearchMapper searchMapper;

    public SearchProductsUseCase(ProductSearchRepository searchRepository,
                                SearchDomainService searchDomainService,
                                SearchMapper searchMapper) {
        this.searchRepository = searchRepository;
        this.searchDomainService = searchDomainService;
        this.searchMapper = searchMapper;
    }

    /**
     * Executa busca padrão de produtos
     */
    public SearchResultDTO execute(SearchRequestDTO request) {
        logger.info("Executing search: query='{}', limit={}, offset={}", 
                   request.getQuery(), request.getLimit(), request.getOffset());
        
        try {
            // Mapear DTOs para objetos de domínio
            SearchQuery query = searchMapper.toDomain(request);
            UserContext userContext = searchMapper.mapUserContext(request.getUserContext());
            
            // Executar busca usando o serviço de domínio
            SearchResult result = searchDomainService.smartSearch(query, userContext);
            
            // Mapear resultado para DTO
            SearchResultDTO resultDTO = searchMapper.toDTO(result);
            
            logger.info("Search completed: found {} products in {}ms", 
                       result.getProducts().size(), result.getExecutionTime().toMillis());
            
            return resultDTO;
            
        } catch (Exception e) {
            logger.error("Error executing search for query: {}", request.getQuery(), e);
            throw new SearchException("Failed to execute search", e);
        }
    }

    /**
     * Executa busca com fallback automático
     */
    public SearchResultDTO executeWithFallback(SearchRequestDTO request) {
        logger.info("Executing search with fallback: query='{}'", request.getQuery());
        
        try {
            SearchQuery query = searchMapper.toDomain(request);
            UserContext userContext = searchMapper.mapUserContext(request.getUserContext());
            
            SearchResult result = searchDomainService.searchWithFallback(query, userContext);
            SearchResultDTO resultDTO = searchMapper.toDTO(result);
            
            logger.info("Search with fallback completed: found {} products", 
                       result.getProducts().size());
            
            return resultDTO;
            
        } catch (Exception e) {
            logger.error("Error executing search with fallback for query: {}", request.getQuery(), e);
            throw new SearchException("Failed to execute search with fallback", e);
        }
    }
}

/**
 * Exceção específica para erros de busca
 */
class SearchException extends RuntimeException {
    public SearchException(String message, Throwable cause) {
        super(message, cause);
    }
}