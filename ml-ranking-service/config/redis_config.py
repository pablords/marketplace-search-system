"""
Configuração do Redis para cache de rankings
"""

import os
from typing import Optional
from dataclasses import dataclass
import logging

logger = logging.getLogger(__name__)


@dataclass
class RedisConfig:
    """Configuração de conexão Redis"""
    host: str
    port: int
    password: Optional[str]
    db: int
    ttl_seconds: int
    timeout: int
    max_connections: int
    
    @classmethod
    def from_env(cls) -> "RedisConfig":
        """
        Cria configuração Redis a partir de variáveis de ambiente.
        
        Variáveis de ambiente:
        - REDIS_HOST: Host do Redis (padrão: localhost)
        - REDIS_PORT: Porta do Redis (padrão: 6379)
        - REDIS_PASSWORD: Senha do Redis (padrão: None)
        - REDIS_DB: Database do Redis (padrão: 0)
        - REDIS_TTL_SECONDS: TTL padrão para chaves (padrão: 3600)
        - REDIS_TIMEOUT: Timeout de conexão em segundos (padrão: 5)
        - REDIS_MAX_CONNECTIONS: Máximo de conexões no pool (padrão: 10)
        
        Returns:
            RedisConfig configurado
        """
        host = os.getenv("REDIS_HOST", "localhost")
        port = int(os.getenv("REDIS_PORT", "6379"))
        password = os.getenv("REDIS_PASSWORD") or None
        db = int(os.getenv("REDIS_DB", "0"))
        ttl_seconds = int(os.getenv("REDIS_TTL_SECONDS", "3600"))
        timeout = int(os.getenv("REDIS_TIMEOUT", "5"))
        max_connections = int(os.getenv("REDIS_MAX_CONNECTIONS", "10"))
        
        config = cls(
            host=host,
            port=port,
            password=password,
            db=db,
            ttl_seconds=ttl_seconds,
            timeout=timeout,
            max_connections=max_connections
        )
        
        logger.info(
            "Configuração Redis carregada",
            extra={
                "host": host,
                "port": port,
                "db": db,
                "ttl_seconds": ttl_seconds,
                "timeout": timeout,
                "max_connections": max_connections,
                "password_set": password is not None
            }
        )
        
        return config
    
    def get_connection_url(self) -> str:
        """
        Retorna a URL de conexão Redis.
        
        Returns:
            URL de conexão no formato redis://[password@]host:port/db
        """
        if self.password:
            return f"redis://:{self.password}@{self.host}:{self.port}/{self.db}"
        return f"redis://{self.host}:{self.port}/{self.db}"

