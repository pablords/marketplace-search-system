package com.marketplace.search.search.application.usecases;


import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.marketplace.search.search.application.exceptions.SearchException;
import com.marketplace.search.search.application.mappers.SearchMapper;
import com.marketplace.search.search.application.ports.SearchCacheSettings;
import com.marketplace.search.search.application.queries.SearchRequestQuery;
import com.marketplace.search.search.application.queries.SearchResultQuery;
import com.marketplace.search.search.domain.entities.Product;
import com.marketplace.search.search.domain.services.EmbeddingService;
import com.marketplace.search.search.domain.services.SearchDomainService;
import com.marketplace.search.search.domain.valueobjects.SearchMetrics;
import com.marketplace.search.search.domain.valueobjects.SearchQuery;
import com.marketplace.search.search.domain.valueobjects.SearchResult;
import com.marketplace.search.search.domain.valueobjects.UserContext;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;


/**
 * Caso de uso para busca de produtos
 */
@Service
public class SearchProductsUseCase {

  private static final Logger logger = LoggerFactory.getLogger(SearchProductsUseCase.class);

  private final SearchDomainService searchDomainService;
  private final SearchMapper searchMapper;
  private final SearchCacheSettings cacheProperties;
  private final RankWithMLUseCase rankWithMLUseCase;
  private final EmbeddingService embeddingService;
  private final ObservationRegistry observationRegistry;
  private final MeterRegistry meterRegistry;

  public SearchProductsUseCase(SearchDomainService searchDomainService,
      SearchMapper searchMapper,
      SearchCacheSettings cacheProperties,
      RankWithMLUseCase rankWithMLUseCase,
      EmbeddingService embeddingService,
      ObservationRegistry observationRegistry,
      MeterRegistry meterRegistry) {
    this.searchDomainService = searchDomainService;
    this.searchMapper = searchMapper;
    this.cacheProperties = cacheProperties;
    this.rankWithMLUseCase = rankWithMLUseCase;
    this.embeddingService = embeddingService;
    this.observationRegistry = observationRegistry;
    this.meterRegistry = meterRegistry;
  }

  /**
   * Executa busca padrão de produtos com fluxo de 2 fases:
   * Fase 1: Busca Top 200 candidatos no OpenSearch
   * Fase 2: Extração de features, ML ranking e retorno Top 20
   */
    public SearchResultQuery execute(SearchRequestQuery request) {
    logger.info("Executing search with 2-phase flow: query='{}', limit={}, offset={}",
        request.query(), request.limit(), request.offset());

    Timer.Sample sample = Timer.start(meterRegistry);
    Instant startTime = Instant.now();

    try {
      // Mapear DTOs para objetos de domínio
      SearchQuery query = searchMapper.toDomain(request);
      UserContext userContext = searchMapper.mapUserContext(request.userContext());

      String cacheKey = buildCacheKey(query, userContext, "standard");
      
      if (cacheProperties.isEnabled()) {
        Optional<SearchResult> cachedResult = Observation.createNotStarted("search.cache.lookup", observationRegistry)
            .observe(() -> searchDomainService.getFromCache(cacheKey));
            
        if (cachedResult != null && cachedResult.isPresent()) {
            return searchMapper.toDTO(cachedResult.get());
        }
      }

      // PASSO 1: Chamar Embedding Service para gerar vetor da query
      // Se falhar, continuar apenas com busca BM25 (fallback)
      logger.debug("PASSO 1: Gerando embedding para query: '{}'", query.terms());
      Optional<float[]> queryEmbedding = Optional.empty();
      
      try {
        Timer.Sample embeddingSample = Timer.start(meterRegistry);
        queryEmbedding = embeddingService.generateQueryEmbedding(query.terms());
        embeddingSample.stop(Timer.builder("search.phase.duration")
            .tag("phase", "embedding")
            .register(meterRegistry));

        if (queryEmbedding.isPresent()) {
          logger.info("Embedding gerado com sucesso para query: '{}' - busca híbrida será usada", query.terms());
        } else {
          logger.warn("Embedding Service retornou vazio para query: '{}' - usando apenas busca BM25 (fallback)", query.terms());
        }
      } catch (Exception e) {
        meterRegistry.counter("search.embedding.errors").increment();
        logger.warn("Erro ao gerar embedding para query: '{}' - usando apenas busca BM25 (fallback). Erro: {}", 
            query.terms(), e.getMessage());
        queryEmbedding = Optional.empty();
      }

      // FASE 1: Buscar Top 200 candidatos no OpenSearch
      String searchType = queryEmbedding.isPresent() ? "hybrid" : "lexical";
      meterRegistry.counter("search.requests.type", "type", searchType).increment();

      Timer.Sample retrievalSample = Timer.start(meterRegistry);
      var candidatesWithScores = searchDomainService.fetchAndValidateCandidates(query, userContext, queryEmbedding);
      retrievalSample.stop(Timer.builder("search.phase.duration").tag("phase", "retrieval").tag("type", searchType).register(meterRegistry));
      
      List<Product> candidates = candidatesWithScores.products();
      if (candidates.isEmpty()) {
          meterRegistry.counter("search.empty.results").increment();
          return createEmptyResult(query, Duration.between(startTime, Instant.now()));
      }

      // Mapear scores para o formato do ML
      Map<String, RankWithMLUseCase.ScorePair> scorePairs = new HashMap<>();
      candidatesWithScores.scores().forEach((id, score) -> 
          scorePairs.put(id, new RankWithMLUseCase.ScorePair(score.bm25Score(), score.knnScore()))
      );

      // FASE 2: ML Ranking
      Timer.Sample rankingSample = Timer.start(meterRegistry);
      List<Product> rankedProducts = rankWithMLUseCase.rank(candidates, query, userContext, scorePairs);
      rankingSample.stop(Timer.builder("search.phase.duration").tag("phase", "ranking").register(meterRegistry));

      // Paginação simples
      int fromIndex = Math.min(query.offset(), rankedProducts.size());
      int toIndex = Math.min(query.offset() + query.limit(), rankedProducts.size());
      List<Product> finalProducts = rankedProducts.subList(fromIndex, toIndex);

      double averageScore = candidatesWithScores.scores().values().stream()
          .mapToDouble(score -> (score.bm25Score() + score.knnScore()) / 2.0)
          .average().orElse(0.0);

      DistributionSummary.builder("search.candidates.count")
          .description("Number of candidates retrieved in phase 1")
          .tag("type", searchType)
          .register(meterRegistry)
          .record(candidates.size());

      DistributionSummary.builder("search.results.count")
          .description("Number of results returned after ranking")
          .tag("type", searchType)
          .register(meterRegistry)
          .record(finalProducts.size());

      DistributionSummary.builder("search.relevance.average")
          .description("Average relevance score of search results")
          .tag("type", searchType)
          .register(meterRegistry)
          .record(averageScore);

      Duration executionTime = Duration.between(startTime, Instant.now());
      SearchMetrics metrics = new SearchMetrics(100, averageScore, candidates.size(), 0L, false, "");
      SearchResult result = new SearchResult(finalProducts, candidates.size(), query.limit(), query.offset() / query.limit(), executionTime, metrics);

      if (cacheProperties.isEnabled()) {
          searchDomainService.storeInCache(cacheKey, result, cacheProperties.getSearchResultsTtl());
      }

      return searchMapper.toDTO(result);

    } catch (Exception e) {
      logger.error("Error executing search for query: {}", request.query(), e);
      throw new SearchException("Failed to execute search", e);
    }
  }

  /**
   * Cria um resultado vazio quando não há candidatos
   */
  private SearchResultQuery createEmptyResult(SearchQuery query, Duration executionTime) {
    SearchResult result = new SearchResult(
        List.of(),
        0,
        query.limit(),
        query.offset() / query.limit(),
        executionTime,
        SearchMetrics.empty()
    );
    return searchMapper.toDTO(result);
  }

  // Métodos auxiliares privados foram removidos pois a lógica foi para o domínio

  /**
   * Executa busca de forma assíncrona
   */
  @Async("taskExecutor")
    public CompletableFuture<SearchResultQuery> executeAsync(SearchRequestQuery request) {
    try {
      SearchResultQuery result = execute(request);
      return CompletableFuture.completedFuture(result);
    } catch (Exception e) {
      return CompletableFuture.failedFuture(e);
    }
  }

  /**
   * Executa busca com fallback automático
   */
    public SearchResultQuery executeWithFallback(SearchRequestQuery request) {
    logger.info("Executing search with fallback: query='{}'", request.query());

    try {
      SearchQuery query = searchMapper.toDomain(request);
      UserContext userContext = searchMapper.mapUserContext(request.userContext());

      String cacheKey = buildCacheKey(query, userContext, "fallback");
      
      if (cacheProperties.isEnabled()) {
          Optional<SearchResult> cachedResult = searchDomainService.getFromCache(cacheKey);
          if (cachedResult.isPresent()) {
              return searchMapper.toDTO(cachedResult.get());
          }
      }

      SearchResult result = searchDomainService.searchWithFallback(query, userContext);
      
      DistributionSummary.builder("search.results.count")
          .description("Number of results returned after fallback")
          .tag("type", "fallback")
          .register(meterRegistry)
          .record(result.products().size());
      
      if (cacheProperties.isEnabled()) {
          searchDomainService.storeInCache(cacheKey, result, cacheProperties.getSearchResultsTtl());
      }

      return searchMapper.toDTO(result);

    } catch (Exception e) {
      logger.error("Error executing search with fallback for query: {}", request.query(), e);
      throw new SearchException("Failed to execute search with fallback", e);
    }
  }

  private String buildCacheKey(SearchQuery query, UserContext userContext, String mode) {
    StringBuilder builder = new StringBuilder(cacheProperties.getKeyPrefix());
    builder.append(":mode=").append(mode);
    builder.append(":q=").append(query.terms());
    builder.append(":o=").append(query.offset());
    builder.append(":l=").append(query.limit());
    builder.append(":s=").append(query.sort().name());
    builder.append(":rd=").append(query.rankingDebug());

    if (query.hasCategoryFilter() && query.category() != null) {
      builder.append(":c=").append(query.category().getId());
    }

    if (!query.filters().isEmpty()) {
      String filtersKey = query.filters().stream()
          .sorted(Comparator.comparing(f -> f.name().trim()))
          .map(filter -> filter.name() + "=" + String.join(",", filter.values()))
          .collect(Collectors.joining("|"));
      builder.append(":f=").append(Integer.toHexString(filtersKey.hashCode()));
    }

    if (userContext != null) {
      if (!userContext.isAnonymous() && userContext.userId() != null) {
        builder.append(":u=").append(userContext.userId());
      } else if (userContext.location() != null) {
        builder.append(":loc=")
            .append(userContext.location().country())
            .append("-")
            .append(userContext.location().state());
      } else {
        builder.append(":u=anon");
      }
    } else {
      builder.append(":u=anon");
    }

    return builder.toString().toLowerCase();
  }
}

