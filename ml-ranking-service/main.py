"""
ML Ranking Service - FastAPI application
Serviço de re-ranking com Machine Learning para produtos do marketplace
"""

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field
from typing import List, Optional
import logging
import uvicorn

from models.ltr_model import LearningToRankModel
from services.ranking_service import RankingService

# Configurar logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

# Criar aplicação FastAPI
app = FastAPI(
    title="ML Ranking Service",
    description="Serviço de re-ranking com Machine Learning para produtos",
    version="1.0.0"
)

# Configurar CORS
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Inicializar serviços
logger.info("Inicializando RankingService...")
try:
    ranking_service = RankingService()
    logger.info("RankingService inicializado com sucesso. Modelo carregado.")
except Exception as e:
    logger.error(f"Erro ao inicializar RankingService: {str(e)}", exc_info=True)
    ranking_service = None


# Modelos Pydantic para request/response
class FeatureVector(BaseModel):
    """Vetor de features de um produto candidato"""
    product_id: str = Field(..., description="ID do produto")
    bm25_score: float = Field(..., ge=0.0, le=1.0, description="Score BM25 normalizado (0-1)")
    knn_score: float = Field(..., ge=0.0, le=1.0, description="Score k-NN normalizado (0-1)")
    hybrid_score: float = Field(..., ge=0.0, le=1.0, description="Score híbrido (BM25 + k-NN)")
    exact_match: float = Field(..., ge=0.0, le=1.0, description="Exact match (0 ou 1)")
    term_coverage: float = Field(..., ge=0.0, le=1.0, description="Cobertura de termos (0-1)")
    title_length: float = Field(..., ge=0.0, description="Comprimento do título")
    description_length: float = Field(..., ge=0.0, description="Comprimento da descrição")
    title_description_ratio: float = Field(..., ge=0.0, le=1.0, description="Ratio título/descrição")
    text_quality_score: float = Field(..., ge=0.0, le=1.0, description="Score de qualidade do texto")
    first_word_match: float = Field(..., ge=0.0, le=1.0, description="Match da primeira palavra (0 ou 1)")
    has_numbers: float = Field(..., ge=0.0, le=1.0, description="Contém números (0 ou 1)")
    brand_match: float = Field(..., ge=0.0, le=1.0, description="Match de marca (0 ou 1)")
    category_match: float = Field(..., ge=0.0, le=1.0, description="Match de categoria (0 ou 1)")
    popularity_score: float = Field(..., ge=0.0, le=100.0, description="Score de popularidade (0-100)")
    quality_score: float = Field(..., ge=0.0, le=1.0, description="Score de qualidade (0-1)")
    ctr: float = Field(..., ge=0.0, le=1.0, description="Click-through rate (0-1)")
    sales_count_normalized: float = Field(..., ge=0.0, le=1.0, description="Vendas normalizadas (0-1)")


class RankRequest(BaseModel):
    """Request para re-ranking de produtos"""
    candidates: List[FeatureVector] = Field(..., description="Lista de candidatos com features (até 400)")
    query: Optional[str] = Field(None, description="Query de busca (opcional, para logging)")


class RankedProduct(BaseModel):
    """Produto ranqueado com score ML"""
    product_id: str = Field(..., description="ID do produto")
    ml_score: float = Field(..., description="Score de ML (quanto maior, melhor)")
    rank: int = Field(..., description="Posição no ranking (1-based)")


class RankResponse(BaseModel):
    """Response com produtos ranqueados"""
    ranked_products: List[RankedProduct] = Field(..., description="Produtos ordenados por score ML (Top 20)")
    total_candidates: int = Field(..., description="Total de candidatos recebidos")
    model_version: str = Field(..., description="Versão do modelo utilizado")


@app.get("/health")
async def health_check():
    """Health check endpoint"""
    if ranking_service is None:
        return {
            "status": "unhealthy",
            "service": "ml-ranking-service",
            "version": "1.0.0",
            "error": "RankingService não foi inicializado"
        }
    
    model_loaded = ranking_service.is_model_loaded()
    status = "healthy" if model_loaded else "degraded"
    
    return {
        "status": status,
        "service": "ml-ranking-service",
        "version": "1.0.0",
        "model_loaded": model_loaded,
        "model_version": ranking_service.get_model_version() if model_loaded else None
    }


@app.get("/")
async def root():
    """Root endpoint"""
    return {
        "service": "ML Ranking Service",
        "version": "1.0.0",
        "endpoints": {
            "rank": "/api/v1/ml/rank",
            "health": "/api/v1/health"
        }
    }


@app.post("/api/v1/ml/rank", response_model=RankResponse)
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
    # Verificar se o serviço está disponível
    if ranking_service is None or not ranking_service.is_model_loaded():
        logger.error("Tentativa de ranquear produtos mas o serviço não está disponível")
        raise HTTPException(
            status_code=503,
            detail="Serviço de ranking não está disponível. Modelo não carregado."
        )
    
    try:
        # Validar número de candidatos
        if len(request.candidates) == 0:
            raise HTTPException(
                status_code=400,
                detail="Lista de candidatos não pode estar vazia"
            )
        
        if len(request.candidates) > 400:
            logger.warning(
                f"Recebidos {len(request.candidates)} candidatos, "
                f"limitando a 400"
            )
            request.candidates = request.candidates[:400]
        
        # Re-ranquear usando o serviço
        ranked_products = ranking_service.rank(
            candidates=request.candidates,
            query=request.query
        )
        
        logger.info(
            f"Re-ranqueados {len(request.candidates)} candidatos, "
            f"retornando Top {len(ranked_products)}"
        )
        
        return RankResponse(
            ranked_products=ranked_products,
            total_candidates=len(request.candidates),
            model_version=ranking_service.get_model_version()
        )
        
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Erro ao re-ranquear produtos: {str(e)}", exc_info=True)
        raise HTTPException(
            status_code=500,
            detail=f"Erro interno ao processar ranking: {str(e)}"
        )


if __name__ == "__main__":
    logger.info("Iniciando servidor ML Ranking Service na porta 8084...")
    uvicorn.run(
        "main:app",
        host="0.0.0.0",
        port=8084,
        reload=True,
        log_level="info"
    )


