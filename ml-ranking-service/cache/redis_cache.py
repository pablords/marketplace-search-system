"""
Cliente Redis para cache de rankings
"""

import json
import hashlib
import time
from typing import List, Optional, Dict
import logging
import redis
from redis.exceptions import RedisError, ConnectionError, TimeoutError

from config.redis_config import RedisConfig

logger = logging.getLogger(__name__)


class RedisCache:
    """
    Cliente Redis para armazenar e recuperar resultados de ranking em cache.
    
    Chave: ranking:{hash_da_query_e_candidatos}
    Valor: JSON com lista de produtos ranqueados
    """
    
    KEY_PREFIX = "ranking:"
    
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
                decode_responses=False
            )
            
            # Testar conexão
            self.client.ping()
            self._connected = True
            self._connection_attempts = 0
            
            logger.info(
                "Conectado ao Redis com sucesso",
                extra={
                    "host": self.config.host,
                    "port": self.config.port,
                    "db": self.config.db
                }
            )
            
            return True
            
        except (ConnectionError, TimeoutError) as e:
            self._connected = False
            self._connection_attempts += 1
            
            logger.warning(
                f"Falha ao conectar ao Redis (tentativa {self._connection_attempts}): {str(e)}",
                extra={
                    "host": self.config.host,
                    "port": self.config.port,
                    "error": str(e)
                }
            )
            
            return False
            
        except Exception as e:
            self._connected = False
            logger.error(
                f"Erro inesperado ao conectar ao Redis: {str(e)}",
                exc_info=True,
                extra={
                    "host": self.config.host,
                    "port": self.config.port,
                    "error": str(e)
                }
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
    
    def _generate_cache_key(self, query: Optional[str], candidate_ids: List[str]) -> str:
        """
        Gera chave de cache baseada na query e IDs dos candidatos.
        
        Args:
            query: Query de busca (opcional)
            candidate_ids: Lista de IDs dos produtos candidatos
            
        Returns:
            Chave de cache
        """
        # Criar hash da query + IDs dos candidatos ordenados
        cache_data = {
            "query": query or "",
            "candidates": sorted(candidate_ids)
        }
        cache_str = json.dumps(cache_data, sort_keys=True)
        cache_hash = hashlib.sha256(cache_str.encode()).hexdigest()[:16]
        
        return f"{self.KEY_PREFIX}{cache_hash}"
    
    def get_ranking(self, query: Optional[str], candidate_ids: List[str]) -> Optional[List[Dict]]:
        """
        Busca resultado de ranking do cache Redis.
        
        Args:
            query: Query de busca (opcional)
            candidate_ids: Lista de IDs dos produtos candidatos
            
        Returns:
            Lista de produtos ranqueados ou None se não encontrado
        """
        if not self.is_connected():
            return None
        
        start_time = time.time()
        key = self._generate_cache_key(query, candidate_ids)
        
        try:
            cached_data = self.client.get(key)
            
            if cached_data is None:
                elapsed = (time.time() - start_time) * 1000
                logger.debug(
                    f"Cache miss para ranking",
                    extra={
                        "key": key,
                        "candidates_count": len(candidate_ids),
                        "elapsed_ms": round(elapsed, 2)
                    }
                )
                return None
            
            # Deserializar JSON
            ranking = json.loads(cached_data.decode('utf-8'))
            elapsed = (time.time() - start_time) * 1000
            
            logger.debug(
                f"Cache hit para ranking",
                extra={
                    "key": key,
                    "candidates_count": len(candidate_ids),
                    "ranking_count": len(ranking),
                    "elapsed_ms": round(elapsed, 2)
                }
            )
            
            return ranking
            
        except json.JSONDecodeError as e:
            logger.error(
                f"Erro ao deserializar ranking do cache: {str(e)}",
                extra={
                    "key": key,
                    "error": str(e)
                }
            )
            return None
            
        except RedisError as e:
            logger.warning(
                f"Erro Redis ao buscar ranking: {str(e)}",
                extra={
                    "key": key,
                    "error": str(e)
                }
            )
            self._connected = False
            return None
    
    def set_ranking(
        self,
        query: Optional[str],
        candidate_ids: List[str],
        ranked_products: List[Dict],
        ttl: Optional[int] = None
    ) -> bool:
        """
        Armazena resultado de ranking no cache Redis.
        
        Args:
            query: Query de busca (opcional)
            candidate_ids: Lista de IDs dos produtos candidatos
            ranked_products: Lista de produtos ranqueados
            ttl: TTL em segundos (usa config padrão se None)
            
        Returns:
            True se armazenado com sucesso, False caso contrário
        """
        if not self.is_connected():
            return False
        
        ttl = ttl or self.config.ttl_seconds
        key = self._generate_cache_key(query, candidate_ids)
        start_time = time.time()
        
        try:
            # Serializar para JSON
            ranking_json = json.dumps(ranked_products)
            
            # Armazenar no Redis
            self.client.setex(key, ttl, ranking_json)
            
            elapsed = (time.time() - start_time) * 1000
            
            logger.debug(
                f"Ranking armazenado no cache",
                extra={
                    "key": key,
                    "candidates_count": len(candidate_ids),
                    "ranking_count": len(ranked_products),
                    "ttl_seconds": ttl,
                    "elapsed_ms": round(elapsed, 2)
                }
            )
            
            return True
            
        except RedisError as e:
            logger.warning(
                f"Erro Redis ao armazenar ranking: {str(e)}",
                extra={
                    "key": key,
                    "error": str(e)
                }
            )
            self._connected = False
            return False

