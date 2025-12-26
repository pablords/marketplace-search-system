"""
Configuração da aplicação FastAPI
"""

import os
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
import logging

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
    
    logger.info(
        "Aplicação FastAPI criada e configurada",
        extra={"extra_fields": {
            "title": app.title,
            "version": app.version,
            "cors_origins": allow_origins
        }}
    )
    
    return app

