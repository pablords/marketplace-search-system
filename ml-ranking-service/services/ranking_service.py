"""
Ranking Service
Serviço que orquestra o re-ranking usando o modelo LTR
"""

import time
from typing import List, Optional, TYPE_CHECKING
from models.ltr_model import LearningToRankModel, InternalFeatureVector
from cache.redis_cache import RedisCache
import logging

if TYPE_CHECKING:
    from api.schemas import FeatureVector, RankedProduct

logger = logging.getLogger(__name__)


class RankingService:
    """
    Serviço de ranking que utiliza o modelo LTR para re-ranquear produtos.
    Integra cache Redis para melhor performance.
    """
    
    def __init__(self, redis_cache: Optional[RedisCache] = None):
        """
        Inicializa o serviço com o modelo LTR e cache Redis.
        
        Args:
            redis_cache: Cliente Redis para cache (opcional)
        """
        self.model = LearningToRankModel()
        self.redis_cache = redis_cache
        
        if self.redis_cache:
            redis_connected = self.redis_cache.connect()
            if redis_connected:
                logger.info(
                    "RankingService inicializado com cache Redis",
                    extra={
                        "redis_enabled": True,
                        "redis_connected": True
                    }
                )
            else:
                logger.warning(
                    "RankingService inicializado, mas Redis não está disponível. Usando apenas processamento direto.",
                    extra={
                        "redis_enabled": True,
                        "redis_connected": False
                    }
                )
        else:
            logger.info(
                "RankingService inicializado sem cache Redis",
                extra={
                    "redis_enabled": False
                }
            )
    
    def rank(
        self,
        candidates: List['FeatureVector'],
        query: str = None,
        top_k: int = 20
    ) -> List['RankedProduct']:
        """
        Re-ranqueia produtos candidatos usando o modelo ML.
        Verifica cache Redis primeiro, depois processa e salva no cache.
        
        Args:
            candidates: Lista de FeatureVectors com produtos candidatos
            query: Query de busca (opcional, para logging e cache)
            top_k: Número de produtos a retornar (padrão: 20)
            
        Returns:
            Lista de RankedProducts ordenados por score ML
        """
        if not candidates:
            return []
        
        start_time = time.time()
        candidate_ids = [c.product_id for c in candidates]
        
        logger.info(
            "Iniciando re-ranking de produtos",
            extra={
                "candidates_count": len(candidates),
                "top_k": top_k,
                "query": query or "N/A"
            }
        )
        
        # 1. Verificar cache Redis primeiro (se disponível)
        if self.redis_cache and self.redis_cache.is_connected():
            cached_ranking = self.redis_cache.get_ranking(query, candidate_ids)
            if cached_ranking:
                # Converter do cache para RankedProduct
                from api.schemas import RankedProduct
                ranked_products = [
                    RankedProduct(
                        product_id=item["product_id"],
                        ml_score=item["ml_score"],
                        rank=item["rank"]
                    )
                    for item in cached_ranking
                ]
                
                elapsed = (time.time() - start_time) * 1000
                logger.info(
                    "Ranking recuperado do cache Redis",
                    extra={
                        "candidates_count": len(candidates),
                        "ranking_count": len(ranked_products),
                        "elapsed_ms": round(elapsed, 2)
                    }
                )
                return ranked_products
        
        # 2. Processar ranking (cache miss ou Redis não disponível)
        # Converter FeatureVectors para formato interno
        feature_vectors = [
            self._to_internal_format(candidate)
            for candidate in candidates
        ]
        
        # Ranquear usando o modelo
        ranked_results = self.model.rank(feature_vectors, top_k=top_k)
        
        # Importar aqui para evitar importação circular
        from api.schemas import RankedProduct
        
        # Converter para RankedProduct
        ranked_products = [
            RankedProduct(
                product_id=product_id,
                ml_score=score,
                rank=idx + 1
            )
            for idx, (product_id, score) in enumerate(ranked_results)
        ]
        
        # 3. Salvar no cache Redis (se disponível)
        if ranked_products and self.redis_cache and self.redis_cache.is_connected():
            try:
                # Converter para formato de cache
                cache_data = [
                    {
                        "product_id": p.product_id,
                        "ml_score": p.ml_score,
                        "rank": p.rank
                    }
                    for p in ranked_products
                ]
                self.redis_cache.set_ranking(query, candidate_ids, cache_data)
            except Exception as e:
                logger.warning(
                    f"Erro ao salvar ranking no cache: {str(e)}",
                    extra={"error": str(e)}
                )
        
        elapsed = (time.time() - start_time) * 1000
        
        logger.info(
            "Re-ranking concluído",
            extra={
                "candidates_count": len(candidates),
                "ranking_count": len(ranked_products),
                "top_k": top_k,
                "query": query or "N/A",
                "elapsed_ms": round(elapsed, 2)
            }
        )
        
        return ranked_products
    
    def _to_internal_format(self, feature_vector: 'FeatureVector') -> InternalFeatureVector:
        """
        Converte FeatureVector do Pydantic para formato interno do modelo.
        """
        return InternalFeatureVector(
            product_id=feature_vector.product_id,
            bm25_score=feature_vector.bm25_score,
            knn_score=feature_vector.knn_score,
            hybrid_score=feature_vector.hybrid_score,
            exact_match=feature_vector.exact_match,
            term_coverage=feature_vector.term_coverage,
            title_length=feature_vector.title_length,
            description_length=feature_vector.description_length,
            title_description_ratio=feature_vector.title_description_ratio,
            text_quality_score=feature_vector.text_quality_score,
            first_word_match=feature_vector.first_word_match,
            has_numbers=feature_vector.has_numbers,
            brand_match=feature_vector.brand_match,
            category_match=feature_vector.category_match,
            popularity_score=feature_vector.popularity_score,
            quality_score=feature_vector.quality_score,
            ctr=feature_vector.ctr,
            sales_count_normalized=feature_vector.sales_count_normalized
        )
    
    def get_model_version(self) -> str:
        """Retorna a versão do modelo utilizado"""
        return self.model.get_version()
    
    def is_model_loaded(self) -> bool:
        """Verifica se o modelo está carregado"""
        return self.model is not None

