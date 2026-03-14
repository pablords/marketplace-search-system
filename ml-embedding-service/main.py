"""
Embedding Service - FastAPI application
Serviço de geração de embeddings para produtos e queries do marketplace
"""

import sys
import asyncio
import uvicorn
import logging

from config.logging_config import setup_logging, get_logger
from config.app_config import create_app
from config.redis_config import RedisConfig
from cache.redis_cache import RedisCache
from services.service_manager import ServiceManager
from services.embedding_service import EmbeddingService
from api.routes import register_routes

# Configurar logging primeiro
setup_logging()
logger = get_logger(__name__)

# Criar aplicação FastAPI
app = create_app()

# Variáveis globais para serviços
service_manager: ServiceManager = None

# Carregar variáveis de ambiente do .env
from dotenv import load_dotenv
load_dotenv()

# Inicializar service_manager antes de registrar rotas
try:
    # Configurar Redis
    redis_config = RedisConfig.from_env()
    redis_cache = RedisCache(redis_config)
    
    # Criar service manager com Redis cache
    service_manager = ServiceManager(redis_cache=redis_cache)
    
    logger.info("ServiceManager inicializado")
except Exception as e:
    logger.error(
        f"Erro ao inicializar ServiceManager: {str(e)}",
        exc_info=True
    )
    # Criar sem Redis como fallback
    service_manager = ServiceManager()


@app.on_event("startup")
async def startup_event():
    """Evento de startup - carrega modelo em background"""
    global service_manager
    
    logger.info("=" * 60)
    logger.info("Servidor Embedding Service iniciado na porta 8085!")
    logger.info("=" * 60)
    
    try:
        # Aguardar um pouco para garantir que o servidor está totalmente iniciado
        await asyncio.sleep(0.1)
        
        logger.info("Iniciando carregamento do modelo em background...")
        logger.info("NOTA: O modelo pode demorar alguns segundos para carregar na primeira vez.")
        logger.info("O servidor está disponível, mas retornará 503 até o modelo estar pronto.")
        
        # Iniciar carregamento do modelo em thread separada
        service_manager.start_loading_in_background()
        
    except Exception as e:
        logger.error(
            f"Erro ao inicializar serviços no startup: {str(e)}",
            exc_info=True,
            extra={"error": str(e)}
        )


# Registrar rotas
register_routes(app, service_manager)


if __name__ == "__main__":
    logger.info("=" * 70)
    logger.info("INICIANDO SERVIDOR EMBEDDING SERVICE")
    logger.info("=" * 70)
    logger.info(f"Python: {sys.executable}")
    logger.info(f"Versão Python: {sys.version}")
    logger.info("Porta: 8085")
    logger.info("NOTA: O modelo será carregado em background. O servidor estará disponível imediatamente.")
    logger.info("=" * 70)
    
    try:
        uvicorn.run(
            "main:app",
            host="0.0.0.0",
            port=8085,
            reload=True,
            log_level="info"
        )
    except KeyboardInterrupt:
        logger.info("Servidor interrompido pelo usuário")
    except Exception as e:
        logger.error(
            f"Erro ao iniciar servidor: {str(e)}",
            exc_info=True,
            extra={"error": str(e)}
        )
        sys.exit(1)
