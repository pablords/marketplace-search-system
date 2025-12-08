package com.marketplace.search.indexing.domain.repositories;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Repositório de cache para armazenamento de dados temporários
 */
public interface CacheRepository {

  // Operações básicas de cache
  <T> void put(String key, T value, Duration ttl);

  <T> void put(String key, T value);

  <T> Optional<T> get(String key, Class<T> type);

  void evict(String key);

  void evictPattern(String pattern);

  boolean exists(String key);

  void expire(String key, Duration ttl);

  Duration getTtl(String key);

  // Operações de contador
  void increment(String key);

  void increment(String key, long delta);

  Long getCounter(String key);

  // Operações de Set
  void addToSet(String key, String value);

  void removeFromSet(String key, String value);

  Set<String> getSet(String key);

  boolean isInSet(String key, String value);

  // Operações de Lista
  void addToList(String key, String value);

  List<String> getList(String key);

  List<String> getList(String key, long start, long end);

  void trimList(String key, long start, long end);

  // Operações de Hash
  void putHash(String key, String field, String value);

  String getHash(String key, String field);

  void deleteHash(String key, String field);

  Set<String> getHashKeys(String key);

  // Operações administrativas
  void clear();

  long size();
}