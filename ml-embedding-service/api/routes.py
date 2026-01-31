"""
Endpoints HTTP da API do ML Embedding Service
"""

import time
from datetime import datetime
from fastapi import APIRouter, HTTPException
import logging

from api.schemas import EmbeddingRequest, EmbeddingResponse, EmbeddingItem
from services.service_manager import ServiceManager

logger = logging.getLogger(__name__)

router = APIRouter()

# Variável global para o service manager (será definida em register_routes)
_service_manager: ServiceManager = None


def register_routes(app, service_manager: ServiceManager):
    """
    Registra todas as rotas no app FastAPI.
    
    Args:
        app: Aplicação FastAPI
        service_manager: Gerenciador de serviços
    """
    global _service_manager
    _service_manager = service_manager
    app.include_router(router)


@router.get("/api/v1/health")
async def health_check():
    """
    Health check endpoint com informações detalhadas do serviço.
    """
    start_time = time.time()
    
    try:
        service_manager = _service_manager
        is_loading = service_manager.is_loading() if service_manager else False
        embedding_service = service_manager.get_service() if service_manager else None
        
        if embedding_service is None:
            if is_loading:
                status = "loading"
                message = "Modelo ainda está carregando..."
            else:
                status = "unhealthy"
                message = "EmbeddingService não foi inicializado"
            
            response = {
                "status": status,
                "service": "ml-embedding-service",
                "version": "1.0.0",
                "model_loaded": False,
                "message": message
            }
        else:
            model_loaded = embedding_service.is_model_loaded()
            status = "healthy" if model_loaded else "degraded"
            
            response = {
                "status": status,
                "service": "ml-embedding-service",
                "version": "1.0.0",
                "model_loaded": model_loaded,
                "dimension": embedding_service.get_dimension() if model_loaded else None,
                "model_version": embedding_service.get_model_version() if model_loaded else None
            }
            
            # Adicionar informações do Redis se disponível
            if embedding_service.redis_cache:
                response["redis"] = {
                    "enabled": True,
                    "connected": embedding_service.redis_cache.is_connected()
                }
        
        elapsed = (time.time() - start_time) * 1000
        
        logger.info(
            "Health check executado",
            extra={"extra_fields": {
                "status": response["status"],
                "model_loaded": response.get("model_loaded", False),
                "elapsed_ms": round(elapsed, 2)
            }}
        )
        
        return response
        
    except Exception as e:
        logger.error(
            f"Erro no health check: {str(e)}",
            exc_info=True,
            extra={"extra_fields": {
                "error": str(e)
            }}
        )
        return {
            "status": "error",
            "service": "ml-embedding-service",
            "version": "1.0.0",
            "error": str(e)
        }


@router.get("/")
async def root():
    """
    Root endpoint com informações do serviço.
    """
    logger.info(
        "Endpoint root acessado",
        extra={"extra_fields": {
            "endpoint": "/"
        }}
    )
    
    return {
        "service": "Embedding Service",
        "version": "1.0.0",
        "status": "running",
        "endpoints": {
            "generate": "/api/v1/embeddings/generate",
            "query": "/api/v1/embeddings/query",
            "health": "/api/v1/health"
        }
    }


@router.get("/test")
async def test():
    """
    Endpoint de teste simples.
    """
    logger.info(
        "Endpoint de teste acessado",
        extra={"extra_fields": {
            "endpoint": "/test"
        }}
    )
    
    return {
        "status": "ok",
        "message": "Servidor está funcionando!",
        "timestamp": datetime.now().isoformat()
    }


@router.post("/api/v1/embeddings/generate", response_model=EmbeddingResponse)
async def generate_embeddings(request: EmbeddingRequest):
    """
    Gera embeddings para uma lista de textos (produtos ou queries).
    
    Args:
        request: EmbeddingRequest com lista de textos e tipo
        
    Returns:
        EmbeddingResponse com embeddings gerados
    """
    start_time = time.time()
    request_id = id(request)  # ID simples para rastreamento
    
    logger.info(
        "Request recebido para gerar embeddings",
        extra={"extra_fields": {
            "request_id": request_id,
            "endpoint": "/api/v1/embeddings/generate",
            "texts_count": len(request.texts),
            "embedding_type": request.type
        }}
    )
    
    # Verificar se o serviço está disponível
    service_manager = _service_manager
    embedding_service = service_manager.get_service() if service_manager else None
    
    if embedding_service is None or not embedding_service.is_model_loaded():
        logger.error(
            "Tentativa de gerar embeddings mas o modelo não está carregado",
            extra={"extra_fields": {
                "request_id": request_id,
                "service_available": embedding_service is not None,
                "model_loaded": embedding_service.is_model_loaded() if embedding_service else False
            }}
        )
        raise HTTPException(
            status_code=503,
            detail="Serviço de embedding não está disponível. Modelo não carregado."
        )
    
    try:
        # Validar lista de textos
        if not request.texts or len(request.texts) == 0:
            logger.warning(
                "Request com lista de textos vazia",
                extra={"extra_fields": {
                    "request_id": request_id
                }}
            )
            raise HTTPException(
                status_code=400,
                detail="Lista de textos não pode estar vazia"
            )
        
        # Limitar batch size para evitar sobrecarga
        max_batch_size = 100
        original_count = len(request.texts)
        if len(request.texts) > max_batch_size:
            logger.warning(
                f"Recebidos {len(request.texts)} textos, limitando a {max_batch_size}",
                extra={"extra_fields": {
                    "request_id": request_id,
                    "original_count": original_count,
                    "max_batch_size": max_batch_size
                }}
            )
            request.texts = request.texts[:max_batch_size]
        
        # Gerar embeddings
        embeddings = embedding_service.generate_embeddings(
            texts=request.texts,
            embedding_type=request.type
        )
        
        # Converter para formato de resposta
        embedding_items = [
            EmbeddingItem(text=text, vector=vector.tolist())
            for text, vector in zip(request.texts, embeddings)
        ]
        
        response = EmbeddingResponse(
            embeddings=embedding_items,
            model_version=embedding_service.get_model_version(),
            dimension=embedding_service.get_dimension()
        )
        
        elapsed = (time.time() - start_time) * 1000
        
        logger.info(
            "Embeddings gerados com sucesso",
            extra={"extra_fields": {
                "request_id": request_id,
                "texts_count": len(request.texts),
                "embedding_type": request.type,
                "model_version": response.model_version,
                "dimension": response.dimension,
                "elapsed_ms": round(elapsed, 2)
            }}
        )
        
        return response
        
    except HTTPException:
        elapsed = (time.time() - start_time) * 1000
        logger.warning(
            "Request falhou com HTTPException",
            extra={"extra_fields": {
                "request_id": request_id,
                "elapsed_ms": round(elapsed, 2)
            }}
        )
        raise
        
    except Exception as e:
        elapsed = (time.time() - start_time) * 1000
        logger.error(
            f"Erro ao gerar embeddings: {str(e)}",
            exc_info=True,
            extra={"extra_fields": {
                "request_id": request_id,
                "error": str(e),
                "elapsed_ms": round(elapsed, 2)
            }}
        )
        raise HTTPException(
            status_code=500,
            detail=f"Erro interno ao gerar embeddings: {str(e)}"
        )


@router.post("/api/v1/embeddings/query", response_model=EmbeddingResponse)
async def generate_query_embedding(request: EmbeddingRequest):
    """
    Gera embedding para uma query de busca.
    
    Este endpoint é um alias para /generate com type="query".
    
    Args:
        request: EmbeddingRequest com query de busca
        
    Returns:
        EmbeddingResponse com embedding da query
    """
    logger.info(
        "Request recebido para gerar embedding de query",
        extra={"extra_fields": {
            "endpoint": "/api/v1/embeddings/query",
            "texts_count": len(request.texts)
        }}
    )
    
    # Forçar tipo como query
    request.type = "query"
    return await generate_embeddings(request)

