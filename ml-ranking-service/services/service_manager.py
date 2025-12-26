"""
Gerenciador de inicialização do RankingService em background
"""

import threading
import logging
import time
from typing import Optional

from services.ranking_service import RankingService
from cache.redis_cache import RedisCache

logger = logging.getLogger(__name__)


class ServiceManager:
    """
    Gerencia a inicialização e estado do RankingService.
    """
    
    def __init__(self, redis_cache: Optional[RedisCache] = None):
        """
        Inicializa o gerenciador de serviços.
        
        Args:
            redis_cache: Cliente Redis para cache (opcional)
        """
        self.ranking_service: Optional[RankingService] = None
        self._model_loading_lock = threading.Lock()
        self._model_loading = False
        self._initialization_start_time: Optional[float] = None
        self.redis_cache = redis_cache
    
    def load_ranking_service(self) -> None:
        """
        Carrega o RankingService em background.
        Este método deve ser executado em uma thread separada.
        """
        try:
            self._initialization_start_time = time.time()
            logger.info("Iniciando carregamento do RankingService em background...")
            
            with self._model_loading_lock:
                self._model_loading = True
            
            # Importação e inicialização do serviço com Redis cache
            self.ranking_service = RankingService(redis_cache=self.redis_cache)
            
            elapsed = time.time() - self._initialization_start_time
            
            if self.ranking_service.is_model_loaded():
                logger.info(
                    "RankingService inicializado com sucesso",
                    extra={
                        "model_loaded": True,
                        "initialization_time_seconds": round(elapsed, 2),
                        "model_version": self.ranking_service.get_model_version(),
                        "redis_enabled": self.redis_cache is not None,
                        "redis_connected": self.redis_cache.is_connected() if self.redis_cache else False
                    }
                )
            else:
                logger.warning(
                    "RankingService inicializado, mas modelo NÃO está carregado!",
                    extra={
                        "model_loaded": False,
                        "initialization_time_seconds": round(elapsed, 2)
                    }
                )
            
            with self._model_loading_lock:
                self._model_loading = False
                
        except Exception as e:
            elapsed = time.time() - self._initialization_start_time if self._initialization_start_time else 0
            logger.error(
                f"Erro ao inicializar RankingService: {str(e)}",
                exc_info=True,
                extra={
                    "error": str(e),
                    "initialization_time_seconds": round(elapsed, 2)
                }
            )
            self.ranking_service = None
            with self._model_loading_lock:
                self._model_loading = False
    
    def start_loading_in_background(self) -> None:
        """
        Inicia o carregamento do RankingService em uma thread separada.
        """
        logger.info("Iniciando carregamento do modelo em background...")
        logger.info("NOTA: O modelo pode demorar alguns segundos para carregar na primeira vez.")
        logger.info("O servidor está disponível, mas retornará 503 até o modelo estar pronto.")
        
        thread = threading.Thread(target=self.load_ranking_service, daemon=True)
        thread.start()
    
    def is_loading(self) -> bool:
        """
        Verifica se o modelo está sendo carregado.
        
        Returns:
            True se está carregando, False caso contrário
        """
        with self._model_loading_lock:
            return self._model_loading
    
    def get_service(self) -> Optional[RankingService]:
        """
        Retorna o RankingService se estiver disponível.
        
        Returns:
            RankingService ou None se não estiver disponível
        """
        return self.ranking_service
    
    def is_service_ready(self) -> bool:
        """
        Verifica se o serviço está pronto para uso.
        
        Returns:
            True se o serviço está disponível e o modelo está carregado
        """
        if self.ranking_service is None:
            return False
        return self.ranking_service.is_model_loaded()

