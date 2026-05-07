"""
Configuração da aplicação FastAPI
"""

import os
import logging
from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse
from fastapi.middleware.cors import CORSMiddleware
from opentelemetry import trace
from opentelemetry.trace import Status, StatusCode

logger = logging.getLogger(__name__)


def create_app() -> FastAPI:
    """
    Cria e configura a aplicação FastAPI.
    
    Returns:
        Aplicação FastAPI configurada
    """
    app = FastAPI(
        title="Embedding Service",
        description="Serviço de geração de embeddings para produtos e queries",
        version="1.0.0"
    )
    
    # Configurar CORS
    cors_origins = os.getenv("CORS_ORIGINS", "*")
    if cors_origins == "*":
        allow_origins = ["*"]
    else:
        allow_origins = [origin.strip() for origin in cors_origins.split(",")]
    
    app.add_middleware(
        CORSMiddleware,
        allow_origins=allow_origins,
        allow_credentials=True,
        allow_methods=["*"],
        allow_headers=["*"],
    )

    # Exception Handler Global para OpenTelemetry
    @app.exception_handler(Exception)
    async def global_exception_handler(request: Request, exc: Exception):
        logger.error(f"Erro não tratado capturado: {str(exc)}", exc_info=True)
        
        # Marcar span do OpenTelemetry como erro
        span = trace.get_current_span()
        if span.is_recording():
            span.set_status(Status(StatusCode.ERROR, str(exc)))
            span.record_exception(exc)
            span.set_attribute("error", True)
            
        return JSONResponse(
            status_code=500,
            content={
                "detail": "Erro interno do servidor",
                "message": str(exc),
                "type": type(exc).__name__
            }
        )
    
    logger.info(
        "Aplicação FastAPI criada e configurada com OTel error handling",
        extra={"extra_fields": {
            "title": app.title,
            "version": app.version,
            "cors_origins": allow_origins
        }}
    )
    
    return app
