"""
Endpoints HTTP da API do ML Ranking Service
"""

import time
from fastapi import APIRouter, HTTPException
import logging

from api.schemas import RankRequest, RankResponse, RankedProduct
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


@router.get("/health")
async def health_check():
    """
    Health check endpoint com informações detalhadas do serviço.
    """
    start_time = time.time()
    
    try:
        service_manager = _service_manager
        is_loading = service_manager.is_loading() if service_manager else False
        ranking_service = service_manager.get_service() if service_manager else None
        
        if ranking_service is None:
            if is_loading:
                status = "loading"
                message = "Modelo ainda está carregando..."
            else:
                status = "unhealthy"
                message = "RankingService não foi inicializado"
            
            response = {
                "status": status,
                "service": "ml-ranking-service",
                "version": "1.0.0",
                "model_loaded": False,
                "message": message
            }
        else:
            model_loaded = ranking_service.is_model_loaded()
            status = "healthy" if model_loaded else "degraded"
            
            response = {
                "status": status,
                "service": "ml-ranking-service",
                "version": "1.0.0",
                "model_loaded": model_loaded,
                "model_version": ranking_service.get_model_version() if model_loaded else None
            }
            
            # Adicionar informações do Redis se disponível
            if ranking_service.redis_cache:
                response["redis"] = {
                    "enabled": True,
                    "connected": ranking_service.redis_cache.is_connected()
                }
        
        elapsed = (time.time() - start_time) * 1000
        
        logger.info(
            "Health check executado",
            extra={
                "status": response["status"],
                "model_loaded": response.get("model_loaded", False),
                "elapsed_ms": round(elapsed, 2)
            }
        )
        
        return response
        
    except Exception as e:
        logger.error(
            f"Erro no health check: {str(e)}",
            exc_info=True,
            extra={"error": str(e)}
        )
        return {
            "status": "error",
            "service": "ml-ranking-service",
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
        extra={"endpoint": "/"}
    )
    
    return {
        "service": "ML Ranking Service",
        "version": "1.0.0",
        "endpoints": {
            "rank": "/api/v1/ml/rank",
            "health": "/health"
        }
    }


@router.post("/api/v1/ml/rank", response_model=RankResponse)
async def rank_products(request: RankRequest):
    """
    Re-ranqueia produtos candidatos usando modelo de Machine Learning.
    
    Recebe até 400 candidatos com suas 17 features e retorna os Top 20
    ordenados por score ML.
    
    Args:
        request: RankRequest com lista de candidatos e features
        
    Returns:
        RankResponse com produtos ranqueados (Top 20)
    """
    start_time = time.time()
    request_id = id(request)
    
    logger.info(
        "Request recebido para re-ranking de produtos",
        extra={
            "request_id": request_id,
            "endpoint": "/api/v1/ml/rank",
            "candidates_count": len(request.candidates),
            "query": request.query or "N/A"
        }
    )
    
    # Verificar se o serviço está disponível
    service_manager = _service_manager
    ranking_service = service_manager.get_service() if service_manager else None
    
    if ranking_service is None or not ranking_service.is_model_loaded():
        logger.error(
            "Tentativa de ranquear produtos mas o serviço não está disponível",
            extra={
                "request_id": request_id,
                "service_available": ranking_service is not None,
                "model_loaded": ranking_service.is_model_loaded() if ranking_service else False
            }
        )
        raise HTTPException(
            status_code=503,
            detail="Serviço de ranking não está disponível. Modelo não carregado."
        )
    
    try:
        # Validar número de candidatos
        if len(request.candidates) == 0:
            logger.warning(
                "Request com lista de candidatos vazia",
                extra={"request_id": request_id}
            )
            raise HTTPException(
                status_code=400,
                detail="Lista de candidatos não pode estar vazia"
            )
        
        original_count = len(request.candidates)
        max_candidates = 400
        if len(request.candidates) > max_candidates:
            logger.warning(
                f"Recebidos {len(request.candidates)} candidatos, limitando a {max_candidates}",
                extra={
                    "request_id": request_id,
                    "original_count": original_count,
                    "max_candidates": max_candidates
                }
            )
            request.candidates = request.candidates[:max_candidates]
        
        # Re-ranquear usando o serviço
        ranked_products = ranking_service.rank(
            candidates=request.candidates,
            query=request.query,
            top_k=20
        )
        
        response = RankResponse(
            ranked_products=ranked_products,
            total_candidates=len(request.candidates),
            model_version=ranking_service.get_model_version()
        )
        
        elapsed = (time.time() - start_time) * 1000
        
        logger.info(
            "Re-ranking concluído com sucesso",
            extra={
                "request_id": request_id,
                "candidates_count": len(request.candidates),
                "ranking_count": len(ranked_products),
                "query": request.query or "N/A",
                "model_version": response.model_version,
                "elapsed_ms": round(elapsed, 2)
            }
        )
        
        return response
        
    except HTTPException:
        elapsed = (time.time() - start_time) * 1000
        logger.warning(
            "Request falhou com HTTPException",
            extra={
                "request_id": request_id,
                "elapsed_ms": round(elapsed, 2)
            }
        )
        raise
        
    except Exception as e:
        elapsed = (time.time() - start_time) * 1000
        logger.error(
            f"Erro ao re-ranquear produtos: {str(e)}",
            exc_info=True,
            extra={
                "request_id": request_id,
                "error": str(e),
                "elapsed_ms": round(elapsed, 2)
            }
        )
        raise HTTPException(
            status_code=500,
            detail=f"Erro interno ao processar ranking: {str(e)}"
        )

