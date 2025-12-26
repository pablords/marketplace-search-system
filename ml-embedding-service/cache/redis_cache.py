"""
Cliente Redis para cache de embeddings
"""

import json
import time
from typing import List, Optional, Dict
import logging
import redis
from redis.exceptions import RedisError, ConnectionError, TimeoutError

from config.redis_config import RedisConfig

logger = logging.getLogger(__name__)


class RedisCache:
    """
    Cliente Redis para armazenar e recuperar embeddings em cache.
    
    Chave: embedding:{texto_normalizado}
    Valor: JSON array de floats
    """
    
    KEY_PREFIX = "embedding:"
    
    def __init__(self, config: RedisConfig):
        """
        Inicializa o cliente Redis.
        
        Args:
            config: Configuração Redis
        """
        self.config = config
        self.client: Optional[redis.Redis] = None
        self._connected = False
        self._connection_attempts = 0
        
    def connect(self) -> bool:
        """
        Conecta ao Redis.
        
        Returns:
            True se conectado com sucesso, False caso contrário
        """
        try:
            self.client = redis.Redis(
                host=self.config.host,
                port=self.config.port,
                password=self.config.password,
                db=self.config.db,
                socket_timeout=self.config.timeout,
                socket_connect_timeout=self.config.timeout,
                max_connections=self.config.max_connections,
                decode_responses=False  # Trabalhar com bytes para melhor performance
            )
            
            # Testar conexão
            self.client.ping()
            self._connected = True
            self._connection_attempts = 0
            
            logger.info(
                "Conectado ao Redis com sucesso",
                extra={"extra_fields": {
                    "host": self.config.host,
                    "port": self.config.port,
                    "db": self.config.db
                }}
            )
            
            return True
            
        except (ConnectionError, TimeoutError) as e:
            self._connected = False
            self._connection_attempts += 1
            
            logger.warning(
                f"Falha ao conectar ao Redis (tentativa {self._connection_attempts}): {str(e)}",
                extra={"extra_fields": {
                    "host": self.config.host,
                    "port": self.config.port,
                    "error": str(e)
                }}
            )
            
            return False
            
        except Exception as e:
            self._connected = False
            logger.error(
                f"Erro inesperado ao conectar ao Redis: {str(e)}",
                exc_info=True,
                extra={"extra_fields": {
                    "host": self.config.host,
                    "port": self.config.port,
                    "error": str(e)
                }}
            )
            return False
    
    def is_connected(self) -> bool:
        """Verifica se está conectado ao Redis"""
        if not self._connected or self.client is None:
            return False
        
        try:
            self.client.ping()
            return True
        except Exception:
            self._connected = False
            return False
    
    def _normalize_text(self, text: str) -> str:
        """
        Normaliza texto para usar como chave de cache.
        
        Args:
            text: Texto original
            
        Returns:
            Texto normalizado (lowercase, trimmed)
        """
        return text.strip().lower()
    
    def _build_key(self, text: str) -> str:
        """
        Constrói a chave Redis para um texto.
        
        Args:
            text: Texto normalizado
            
        Returns:
            Chave Redis completa
        """
        normalized = self._normalize_text(text)
        return f"{self.KEY_PREFIX}{normalized}"
    
    def get_embedding(self, text: str) -> Optional[List[float]]:
        """
        Busca embedding do cache Redis.
        
        Args:
            text: Texto para buscar embedding
            
        Returns:
            Lista de floats com embedding ou None se não encontrado
        """
        if not self.is_connected():
            return None
        
        start_time = time.time()
        key = self._build_key(text)
        
        try:
            cached_data = self.client.get(key)
            
            if cached_data is None:
                elapsed = (time.time() - start_time) * 1000
                logger.debug(
                    f"Cache miss para texto: {text[:50]}...",
                    extra={"extra_fields": {
                        "text_preview": text[:50],
                        "key": key,
                        "elapsed_ms": round(elapsed, 2)
                    }}
                )
                return None
            
            # Deserializar JSON
            embedding = json.loads(cached_data.decode('utf-8'))
            elapsed = (time.time() - start_time) * 1000
            
            logger.debug(
                f"Cache hit para texto: {text[:50]}...",
                extra={"extra_fields": {
                    "text_preview": text[:50],
                    "key": key,
                    "embedding_dimension": len(embedding),
                    "elapsed_ms": round(elapsed, 2)
                }}
            )
            
            return embedding
            
        except json.JSONDecodeError as e:
            logger.error(
                f"Erro ao deserializar embedding do cache: {str(e)}",
                extra={"extra_fields": {
                    "key": key,
                    "error": str(e)
                }}
            )
            return None
            
        except RedisError as e:
            logger.warning(
                f"Erro Redis ao buscar embedding: {str(e)}",
                extra={"extra_fields": {
                    "key": key,
                    "error": str(e)
                }}
            )
            self._connected = False
            return None
    
    def set_embedding(self, text: str, embedding: List[float], ttl: Optional[int] = None) -> bool:
        """
        Armazena embedding no cache Redis.
        
        Args:
            text: Texto original
            embedding: Lista de floats com embedding
            ttl: TTL em segundos (usa config padrão se None)
            
        Returns:
            True se armazenado com sucesso, False caso contrário
        """
        if not self.is_connected():
            return False
        
        ttl = ttl or self.config.ttl_seconds
        key = self._build_key(text)
        start_time = time.time()
        
        try:
            # Serializar para JSON
            embedding_json = json.dumps(embedding)
            
            # Armazenar no Redis
            self.client.setex(key, ttl, embedding_json)
            
            elapsed = (time.time() - start_time) * 1000
            
            logger.debug(
                f"Embedding armazenado no cache: {text[:50]}...",
                extra={"extra_fields": {
                    "text_preview": text[:50],
                    "key": key,
                    "embedding_dimension": len(embedding),
                    "ttl_seconds": ttl,
                    "elapsed_ms": round(elapsed, 2)
                }}
            )
            
            return True
            
        except RedisError as e:
            logger.warning(
                f"Erro Redis ao armazenar embedding: {str(e)}",
                extra={"extra_fields": {
                    "key": key,
                    "error": str(e)
                }}
            )
            self._connected = False
            return False
    
    def get_embeddings_batch(self, texts: List[str]) -> Dict[str, List[float]]:
        """
        Busca múltiplos embeddings do cache Redis (usando pipeline para performance).
        
        Args:
            texts: Lista de textos para buscar
            
        Returns:
            Dicionário {texto_normalizado: embedding} apenas para textos encontrados
        """
        if not self.is_connected() or not texts:
            return {}
        
        start_time = time.time()
        keys = [self._build_key(text) for text in texts]
        results = {}
        
        try:
            # Usar pipeline para buscar múltiplas chaves de uma vez
            pipe = self.client.pipeline()
            for key in keys:
                pipe.get(key)
            cached_data_list = pipe.execute()
            
            # Processar resultados
            for text, key, cached_data in zip(texts, keys, cached_data_list):
                if cached_data is None:
                    continue
                
                try:
                    embedding = json.loads(cached_data.decode('utf-8'))
                    normalized_text = self._normalize_text(text)
                    results[normalized_text] = embedding
                except json.JSONDecodeError as e:
                    logger.warning(
                        f"Erro ao deserializar embedding do cache: {str(e)}",
                        extra={"extra_fields": {
                            "key": key,
                            "error": str(e)
                        }}
                    )
            
            elapsed = (time.time() - start_time) * 1000
            hits = len(results)
            misses = len(texts) - hits
            
            logger.debug(
                f"Batch cache: {hits} hits, {misses} misses",
                extra={"extra_fields": {
                    "total_texts": len(texts),
                    "cache_hits": hits,
                    "cache_misses": misses,
                    "hit_rate": round(hits / len(texts) * 100, 2) if texts else 0,
                    "elapsed_ms": round(elapsed, 2)
                }}
            )
            
            return results
            
        except RedisError as e:
            logger.warning(
                f"Erro Redis ao buscar embeddings em batch: {str(e)}",
                extra={"extra_fields": {
                    "total_texts": len(texts),
                    "error": str(e)
                }}
            )
            self._connected = False
            return {}
    
    def set_embeddings_batch(self, embeddings: Dict[str, List[float]], ttl: Optional[int] = None) -> int:
        """
        Armazena múltiplos embeddings no cache Redis (usando pipeline para performance).
        
        Args:
            embeddings: Dicionário {texto: embedding}
            ttl: TTL em segundos (usa config padrão se None)
            
        Returns:
            Número de embeddings armazenados com sucesso
        """
        if not self.is_connected() or not embeddings:
            return 0
        
        ttl = ttl or self.config.ttl_seconds
        start_time = time.time()
        stored_count = 0
        
        try:
            # Usar pipeline para armazenar múltiplas chaves de uma vez
            pipe = self.client.pipeline()
            
            for text, embedding in embeddings.items():
                key = self._build_key(text)
                embedding_json = json.dumps(embedding)
                pipe.setex(key, ttl, embedding_json)
            
            pipe.execute()
            stored_count = len(embeddings)
            
            elapsed = (time.time() - start_time) * 1000
            
            logger.debug(
                f"Batch de embeddings armazenado no cache",
                extra={"extra_fields": {
                    "total_embeddings": len(embeddings),
                    "stored_count": stored_count,
                    "ttl_seconds": ttl,
                    "elapsed_ms": round(elapsed, 2)
                }}
            )
            
            return stored_count
            
        except RedisError as e:
            logger.warning(
                f"Erro Redis ao armazenar embeddings em batch: {str(e)}",
                extra={"extra_fields": {
                    "total_embeddings": len(embeddings),
                    "error": str(e)
                }}
            )
            self._connected = False
            return stored_count
    
    def clear_cache(self) -> bool:
        """
        Limpa todas as chaves de embedding do cache.
        
        Returns:
            True se limpeza foi bem-sucedida, False caso contrário
        """
        if not self.is_connected():
            return False
        
        try:
            # Buscar todas as chaves com prefixo
            pattern = f"{self.KEY_PREFIX}*"
            keys = self.client.keys(pattern)
            
            if keys:
                deleted = self.client.delete(*keys)
                logger.info(
                    f"Cache limpo: {deleted} chaves removidas",
                    extra={"extra_fields": {
                        "keys_deleted": deleted,
                        "pattern": pattern
                    }}
                )
            else:
                logger.info("Cache já estava vazio")
            
            return True
            
        except RedisError as e:
            logger.error(
                f"Erro ao limpar cache: {str(e)}",
                extra={"extra_fields": {
                    "error": str(e)
                }}
            )
            return False

