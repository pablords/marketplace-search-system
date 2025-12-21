package com.marketplace.search.indexing.application.services;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Componente para rastrear métricas de enriquecimento
 */
@Component
public class EnrichmentMetrics {

  private static final Logger logger = LoggerFactory.getLogger(EnrichmentMetrics.class);

  private final AtomicLong totalEnrichments = new AtomicLong(0);
  private final AtomicLong successfulEnrichments = new AtomicLong(0);
  private final AtomicLong failedEnrichments = new AtomicLong(0);
  private final AtomicLong incompleteEnrichments = new AtomicLong(0);
  private final AtomicLong totalEnrichmentTime = new AtomicLong(0);

  public void recordEnrichment(boolean success, boolean complete, Duration duration) {
    totalEnrichments.incrementAndGet();
    if (success) {
      successfulEnrichments.incrementAndGet();
      if (!complete) {
        incompleteEnrichments.incrementAndGet();
      }
    } else {
      failedEnrichments.incrementAndGet();
    }
    totalEnrichmentTime.addAndGet(duration.toMillis());
  }

  public void logMetrics() {
    long total = totalEnrichments.get();
    if (total == 0) {
      return;
    }

    long successful = successfulEnrichments.get();
    long failed = failedEnrichments.get();
    long incomplete = incompleteEnrichments.get();
    long avgTime = totalEnrichmentTime.get() / total;

    logger.info("Enrichment Metrics - Total: {}, Successful: {}, Failed: {}, Incomplete: {}, AvgTime: {}ms",
        total, successful, failed, incomplete, avgTime);
  }

  public long getTotalEnrichments() {
    return totalEnrichments.get();
  }

  public long getSuccessfulEnrichments() {
    return successfulEnrichments.get();
  }

  public long getFailedEnrichments() {
    return failedEnrichments.get();
  }

  public long getIncompleteEnrichments() {
    return incompleteEnrichments.get();
  }

  public double getAverageEnrichmentTime() {
    long total = totalEnrichments.get();
    if (total == 0) {
      return 0.0;
    }
    return (double) totalEnrichmentTime.get() / total;
  }
}

