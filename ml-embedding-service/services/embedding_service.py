"""
Embedding Service
Serviço que orquestra a geração de embeddings usando o modelo de embedding e cache Redis
"""

import time
from typing import List, Literal, Optional
import numpy as np
from models.embedding_model import EmbeddingModel
from cache.redis_cache import RedisCache
from opentelemetry import trace
from prometheus_client import Counter, Histogram
import logging

logger = logging.getLogger(__name__)
tracer = trace.get_tracer(__name__)

# Métricas Prometheus
EMBEDDING_CACHE_TOTAL = Counter(
    "embedding_cache_total", 
    "Total de consultas ao cache de embeddings", 
    ["status"]
)
EMBEDDING_GENERATION_DURATION = Histogram(
    "embedding_generation_duration_seconds", 
    "Tempo de geração de embeddings pelo modelo (inferência)"
)


class EmbeddingService:
    """
    Serviço de embedding que utiliza o modelo de embedding para gerar vetores.
    Integra cache Redis para melhor performance.
    """
    
    def __init__(self, redis_cache: Optional[RedisCache] = None):
        """
        Inicializa o serviço com o modelo de embedding e cache Redis.
        
        Args:
            redis_cache: Cliente Redis para cache (opcional)
        """
        self.model = EmbeddingModel()
        self.redis_cache = redis_cache
        
        # Tentar conectar ao Redis se fornecido
        if self.redis_cache:
            redis_connected = self.redis_cache.connect()
            if redis_connected:
                logger.info(
                    "EmbeddingService inicializado com cache Redis",
                    extra={"extra_fields": {
                        "redis_enabled": True,
                        "redis_connected": True
                    }}
                )
            else:
                logger.warning(
                    "EmbeddingService inicializado, mas Redis não está disponível. Usando apenas cache em memória.",
                    extra={"extra_fields": {
                        "redis_enabled": True,
                        "redis_connected": False
                    }}
                )
        else:
            logger.info(
                "EmbeddingService inicializado sem cache Redis",
                extra={"extra_fields": {
                    "redis_enabled": False
                }}
            )
    
    @tracer.start_as_current_span("generate_embeddings")
    def generate_embeddings(
        self,
        texts: List[str],
        embedding_type: Literal["product", "query"] = "product"
    ) -> List:
        """
        Gera embeddings para uma lista de textos.
        Verifica cache Redis primeiro, depois modelo, e salva no Redis.
        
        Args:
            texts: Lista de textos para gerar embeddings
            embedding_type: Tipo de embedding ('product' ou 'query')
            
        Returns:
            Lista de arrays numpy com embeddings (cada um com 384 dimensões)
        """
        if not texts:
            return []
        
        start_time = time.time()
        total_texts = len(texts)
        
        logger.info(
            f"Iniciando geração de embeddings",
            extra={"extra_fields": {
                "total_texts": total_texts,
                "embedding_type": embedding_type
            }}
        )
        
        # Preparar resultado
        result: List[Optional[np.ndarray]] = [None] * total_texts
        texts_to_generate: List[str] = []
        texts_to_generate_indices: List[int] = []
        redis_hits = 0
        redis_misses = 0
        
        # 1. Verificar cache Redis primeiro (se disponível)
        if self.redis_cache and self.redis_cache.is_connected():
            redis_cache_results = self.redis_cache.get_embeddings_batch(texts)
            
            for idx, text in enumerate(texts):
                if not text or not text.strip():
                    # Texto vazio - adicionar vetor zero
                    result[idx] = np.zeros(self.get_dimension())
                    continue
                
                normalized_text = text.strip().lower()
                if normalized_text in redis_cache_results:
                    # Cache hit no Redis
                    embedding_list = redis_cache_results[normalized_text]
                    result[idx] = np.array(embedding_list)
                    redis_hits += 1
                    EMBEDDING_CACHE_TOTAL.labels(status="hit").inc()
                else:
                    # Cache miss - precisa gerar
                    texts_to_generate.append(text)
                    texts_to_generate_indices.append(idx)
                    redis_misses += 1
                    EMBEDDING_CACHE_TOTAL.labels(status="miss").inc()
        else:
            # Redis não disponível - todos precisam ser gerados
            for idx, text in enumerate(texts):
                if not text or not text.strip():
                    result[idx] = np.zeros(self.get_dimension())
                else:
                    texts_to_generate.append(text)
                    texts_to_generate_indices.append(idx)
            redis_misses = len(texts_to_generate)
        
        # 2. Gerar embeddings para textos não encontrados no cache
        embeddings_to_cache: dict = {}
        if texts_to_generate:
            try:
                # Usar modelo (que tem cache LRU em memória como fallback)
                with EMBEDDING_GENERATION_DURATION.time():
                    generated_embeddings = self.model.embed_batch(texts_to_generate)
                
                # Preencher resultados e preparar para cache Redis
                for gen_idx, original_idx in enumerate(texts_to_generate_indices):
                    text = texts_to_generate[gen_idx]
                    embedding = generated_embeddings[gen_idx]
                    result[original_idx] = embedding
                    
                    # Preparar para salvar no Redis
                    if self.redis_cache and self.redis_cache.is_connected():
                        normalized_text = text.strip().lower()
                        embeddings_to_cache[normalized_text] = embedding.tolist()
                
            except Exception as e:
                logger.error(
                    f"Erro ao gerar embeddings: {str(e)}",
                    exc_info=True,
                    extra={"extra_fields": {
                        "texts_to_generate_count": len(texts_to_generate),
                        "error": str(e)
                    }}
                )
                raise
        
        # 3. Salvar embeddings gerados no cache Redis
        if embeddings_to_cache and self.redis_cache and self.redis_cache.is_connected():
            try:
                cached_count = self.redis_cache.set_embeddings_batch(embeddings_to_cache)
                logger.debug(
                    f"Embeddings salvos no cache Redis",
                    extra={"extra_fields": {
                        "cached_count": cached_count
                    }}
                )
            except Exception as e:
                logger.warning(
                    f"Erro ao salvar embeddings no cache Redis: {str(e)}",
                    extra={"extra_fields": {
                        "error": str(e)
                    }}
                )
        
        # Garantir que todos os resultados são arrays numpy
        final_result = []
        for embedding in result:
            if embedding is None:
                final_result.append(np.zeros(self.get_dimension()))
            else:
                final_result.append(embedding)
        
        elapsed = (time.time() - start_time) * 1000
        
        logger.info(
            f"Geração de embeddings concluída",
            extra={"extra_fields": {
                "total_texts": total_texts,
                "embedding_type": embedding_type,
                "redis_hits": redis_hits,
                "redis_misses": redis_misses,
                "redis_hit_rate": round(redis_hits / total_texts * 100, 2) if total_texts > 0 else 0,
                "generated_count": len(texts_to_generate),
                "dimension": self.get_dimension(),
                "elapsed_ms": round(elapsed, 2)
            }}
        )
        
        return final_result
    
    def get_model_version(self) -> str:
        """Retorna a versão do modelo utilizado"""
        return self.model.get_version()
    
    def get_dimension(self) -> int:
        """Retorna a dimensão dos vetores de embedding"""
        return self.model.get_dimension()
    
    def is_model_loaded(self) -> bool:
        """Verifica se o modelo está carregado"""
        return self.model.is_loaded()

