"""
Modelos Pydantic para request/response da API
"""

from pydantic import BaseModel, Field
from typing import List, Literal


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

