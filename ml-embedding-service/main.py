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
import threading
import asyncio

# Configurar logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

# Log inicial para verificar se o módulo está sendo carregado
logger.info("=" * 70)
logger.info("MÓDULO MAIN.PY CARREGADO")
logger.info("=" * 70)

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

# Variável global para o serviço
embedding_service = None
_model_loading_lock = threading.Lock()
_model_loading = False

def load_embedding_service():
    """Carrega o EmbeddingService em background"""
    global embedding_service, _model_loading
    try:
        logger.info("Iniciando carregamento do EmbeddingService em background...")
        with _model_loading_lock:
            _model_loading = True
        
        # Importação lazy para evitar bloqueio durante importação do módulo
        from services.embedding_service import EmbeddingService
        embedding_service = EmbeddingService()
        
        if embedding_service.is_model_loaded():
            logger.info("EmbeddingService inicializado com sucesso. Modelo carregado.")
        else:
            logger.warning("EmbeddingService inicializado, mas modelo NÃO está carregado!")
        
        with _model_loading_lock:
            _model_loading = False
            
    except Exception as e:
        logger.error(f"Erro ao inicializar EmbeddingService: {str(e)}", exc_info=True)
        embedding_service = None
        with _model_loading_lock:
            _model_loading = False


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
    with _model_loading_lock:
        is_loading = _model_loading
    
    if embedding_service is None:
        if is_loading:
            return {
                "status": "loading",
                "service": "embedding-service",
                "version": "1.0.0",
                "model_loaded": False,
                "message": "Modelo ainda está carregando..."
            }
        return {
            "status": "unhealthy",
            "service": "embedding-service",
            "version": "1.0.0",
            "model_loaded": False,
            "error": "EmbeddingService não foi inicializado"
        }
    
    model_loaded = embedding_service.is_model_loaded()
    status = "healthy" if model_loaded else "degraded"
    
    return {
        "status": status,
        "service": "embedding-service",
        "version": "1.0.0",
        "model_loaded": model_loaded,
        "dimension": embedding_service.get_dimension() if model_loaded else None
    }


@app.get("/")
async def root():
    """Root endpoint"""
    logger.info("Endpoint / acessado")
    return {
        "service": "Embedding Service",
        "version": "1.0.0",
        "status": "running",
        "endpoints": {
            "generate": "/api/v1/embeddings/generate",
            "query": "/api/v1/embeddings/query",
            "health": "/health"
        }
    }

@app.get("/test")
async def test():
    """Endpoint de teste simples"""
    logger.info("Endpoint /test acessado")
    return {
        "status": "ok",
        "message": "Servidor está funcionando!",
        "timestamp": __import__("datetime").datetime.now().isoformat()
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
    # Verificar se o serviço está disponível
    if embedding_service is None or not embedding_service.is_model_loaded():
        logger.error("Tentativa de gerar embeddings mas o modelo não está carregado")
        raise HTTPException(
            status_code=503,
            detail="Serviço de embedding não está disponível. Modelo não carregado."
        )
    
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


@app.on_event("startup")
async def startup_event():
    """Evento de startup - inicia carregamento do modelo em background"""
    logger.info("=" * 60)
    logger.info("Servidor Embedding Service iniciado na porta 8085!")
    logger.info("=" * 60)
    logger.info("Iniciando carregamento do modelo em background...")
    logger.info("NOTA: O modelo pode demorar alguns segundos para carregar na primeira vez.")
    logger.info("O servidor está disponível, mas retornará 503 até o modelo estar pronto.")
    # Aguardar um pouco para garantir que o servidor está totalmente iniciado
    await asyncio.sleep(0.1)
    # Iniciar carregamento do modelo em thread separada
    threading.Thread(target=load_embedding_service, daemon=True).start()

if __name__ == "__main__":
    import sys
    
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
        logger.error(f"Erro ao iniciar servidor: {str(e)}", exc_info=True)
        sys.exit(1)

