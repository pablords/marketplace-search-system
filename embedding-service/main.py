"""
Embedding Service - FastAPI application
Serviço de geração de embeddings para produtos e queries do marketplace
"""

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field
from typing import List, Optional, Literal
import logging
import uvicorn

from services.embedding_service import EmbeddingService

# Configurar logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

# Criar aplicação FastAPI
app = FastAPI(
    title="Embedding Service",
    description="Serviço de geração de embeddings para produtos e queries",
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

# Inicializar serviço
embedding_service = EmbeddingService()


# Modelos Pydantic para request/response
class EmbeddingRequest(BaseModel):
    """Request para geração de embeddings"""
    texts: List[str] = Field(..., min_length=1, description="Lista de textos para gerar embeddings")
    type: Literal["product", "query"] = Field(
        default="product",
        description="Tipo de embedding: 'product' para produtos, 'query' para queries de busca"
    )


class EmbeddingItem(BaseModel):
    """Item com texto e seu embedding"""
    text: str = Field(..., description="Texto original")
    vector: List[float] = Field(..., description="Vetor de embedding")


class EmbeddingResponse(BaseModel):
    """Response com embeddings gerados"""
    embeddings: List[EmbeddingItem] = Field(..., description="Lista de embeddings gerados")
    model_version: str = Field(..., description="Versão do modelo utilizado")
    dimension: int = Field(..., description="Dimensão dos vetores de embedding")


@app.get("/health")
async def health_check():
    """Health check endpoint"""
    return {
        "status": "healthy",
        "service": "embedding-service",
        "version": "1.0.0",
        "model_loaded": embedding_service.is_model_loaded()
    }


@app.get("/")
async def root():
    """Root endpoint"""
    return {
        "service": "Embedding Service",
        "version": "1.0.0",
        "endpoints": {
            "generate": "/api/v1/embeddings/generate",
            "query": "/api/v1/embeddings/query",
            "health": "/health"
        }
    }


@app.post("/api/v1/embeddings/generate", response_model=EmbeddingResponse)
async def generate_embeddings(request: EmbeddingRequest):
    """
    Gera embeddings para uma lista de textos (produtos ou queries).
    
    Args:
        request: EmbeddingRequest com lista de textos e tipo
        
    Returns:
        EmbeddingResponse com embeddings gerados
    """
    try:
        # Validar lista de textos
        if not request.texts or len(request.texts) == 0:
            raise HTTPException(
                status_code=400,
                detail="Lista de textos não pode estar vazia"
            )
        
        # Limitar batch size para evitar sobrecarga
        max_batch_size = 100
        if len(request.texts) > max_batch_size:
            logger.warning(
                f"Recebidos {len(request.texts)} textos, "
                f"limitando a {max_batch_size}"
            )
            request.texts = request.texts[:max_batch_size]
        
        # Gerar embeddings
        embeddings = embedding_service.generate_embeddings(
            texts=request.texts,
            embedding_type=request.type
        )
        
        logger.info(
            f"Gerados embeddings para {len(request.texts)} textos "
            f"(tipo: {request.type})"
        )
        
        # Converter para formato de resposta
        embedding_items = [
            EmbeddingItem(text=text, vector=vector.tolist())
            for text, vector in zip(request.texts, embeddings)
        ]
        
        return EmbeddingResponse(
            embeddings=embedding_items,
            model_version=embedding_service.get_model_version(),
            dimension=embedding_service.get_dimension()
        )
        
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Erro ao gerar embeddings: {str(e)}", exc_info=True)
        raise HTTPException(
            status_code=500,
            detail=f"Erro interno ao gerar embeddings: {str(e)}"
        )


@app.post("/api/v1/embeddings/query", response_model=EmbeddingResponse)
async def generate_query_embedding(request: EmbeddingRequest):
    """
    Gera embedding para uma query de busca.
    
    Este endpoint é um alias para /generate com type="query".
    
    Args:
        request: EmbeddingRequest com query de busca
        
    Returns:
        EmbeddingResponse com embedding da query
    """
    # Forçar tipo como query
    request.type = "query"
    return await generate_embeddings(request)


if __name__ == "__main__":
    uvicorn.run(
        "main:app",
        host="0.0.0.0",
        port=8085,
        reload=True,
        log_level="info"
    )

