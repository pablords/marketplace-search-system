"""
Modelos Pydantic para request/response da API
"""

from pydantic import BaseModel, Field
from typing import List, Optional


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

