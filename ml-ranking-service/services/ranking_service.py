"""
Ranking Service
Serviço que orquestra o re-ranking usando o modelo LTR
"""

from typing import List, TYPE_CHECKING
from models.ltr_model import LearningToRankModel, InternalFeatureVector
import logging

if TYPE_CHECKING:
    from main import FeatureVector, RankedProduct

logger = logging.getLogger(__name__)


class RankingService:
    """
    Serviço de ranking que utiliza o modelo LTR para re-ranquear produtos.
    """
    
    def __init__(self):
        """Inicializa o serviço com o modelo LTR"""
        self.model = LearningToRankModel()
        logger.info("RankingService inicializado")
    
    def rank(
        self,
        candidates: List['FeatureVector'],
        query: str = None,
        top_k: int = 20
    ) -> List['RankedProduct']:
        """
        Re-ranqueia produtos candidatos usando o modelo ML.
        
        Args:
            candidates: Lista de FeatureVectors com produtos candidatos
            query: Query de busca (opcional, para logging)
            top_k: Número de produtos a retornar (padrão: 20)
            
        Returns:
            Lista de RankedProducts ordenados por score ML
        """
        if not candidates:
            return []
        
        # Converter FeatureVectors para formato interno
        feature_vectors = [
            self._to_internal_format(candidate)
            for candidate in candidates
        ]
        
        # Ranquear usando o modelo
        ranked_results = self.model.rank(feature_vectors, top_k=top_k)
        
        # Importar aqui para evitar importação circular
        from main import RankedProduct
        
        # Converter para RankedProduct
        ranked_products = [
            RankedProduct(
                product_id=product_id,
                ml_score=score,
                rank=idx + 1
            )
            for idx, (product_id, score) in enumerate(ranked_results)
        ]
        
        if query:
            logger.info(
                f"Query: '{query}' - Ranqueados {len(ranked_products)} produtos "
                f"de {len(candidates)} candidatos"
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

