"""
Learning to Rank (LTR) Model
Modelo baseado em pesos para re-ranking de produtos
"""

from typing import List, Any
import logging

logger = logging.getLogger(__name__)


class InternalFeatureVector:
    """Classe interna para representar features de um produto"""
    def __init__(self, **kwargs):
        self.product_id = kwargs.get('product_id', '')
        self.bm25_score = kwargs.get('bm25_score', 0.0)
        self.knn_score = kwargs.get('knn_score', 0.0)
        self.hybrid_score = kwargs.get('hybrid_score', 0.0)
        self.exact_match = kwargs.get('exact_match', 0.0)
        self.term_coverage = kwargs.get('term_coverage', 0.0)
        self.title_length = kwargs.get('title_length', 0.0)
        self.description_length = kwargs.get('description_length', 0.0)
        self.title_description_ratio = kwargs.get('title_description_ratio', 0.0)
        self.text_quality_score = kwargs.get('text_quality_score', 0.0)
        self.first_word_match = kwargs.get('first_word_match', 0.0)
        self.has_numbers = kwargs.get('has_numbers', 0.0)
        self.brand_match = kwargs.get('brand_match', 0.0)
        self.category_match = kwargs.get('category_match', 0.0)
        self.popularity_score = kwargs.get('popularity_score', 0.0)
        self.quality_score = kwargs.get('quality_score', 0.0)
        self.ctr = kwargs.get('ctr', 0.0)
        self.sales_count_normalized = kwargs.get('sales_count_normalized', 0.0)


class LearningToRankModel:
    """
    Modelo de Learning to Rank baseado em pesos.
    
    Inicialmente usa pesos fixos baseados em heurísticas.
    Futuramente pode ser substituído por modelo treinado (XGBoost, LightGBM).
    """
    
    def __init__(self):
        """Inicializa o modelo com pesos baseados em heurísticas"""
        # Pesos para cada grupo de features
        # Baseado nas regras de negócio: Text Score (40%), Popularity (25%), etc.
        self.weights = {
            # Relevância (40% do peso total)
            'bm25_score': 0.15,
            'knn_score': 0.10,
            'hybrid_score': 0.15,
            
            # Match Textual (20% do peso total)
            'exact_match': 0.10,
            'term_coverage': 0.10,
            
            # Qualidade do Texto (10% do peso total)
            'title_length': 0.02,
            'description_length': 0.02,
            'title_description_ratio': 0.03,
            'text_quality_score': 0.03,
            
            # Contexto (10% do peso total)
            'first_word_match': 0.04,
            'has_numbers': 0.01,
            'brand_match': 0.03,
            'category_match': 0.02,
            
            # Popularidade (20% do peso total)
            'popularity_score': 0.08,  # Normalizar de 0-100 para 0-1
            'quality_score': 0.06,
            'ctr': 0.04,
            'sales_count_normalized': 0.02,
        }
        
        self.model_version = "1.0.0-weights"
        logger.info(f"Modelo LTR inicializado (versão: {self.model_version})")
    
    def predict(self, features: InternalFeatureVector) -> float:
        """
        Calcula o score ML para um produto baseado em suas features.
        
        Args:
            features: FeatureVector com as 17 features do produto
            
        Returns:
            Score ML (quanto maior, melhor)
        """
        # Normalizar popularity_score de 0-100 para 0-1
        normalized_popularity = features.popularity_score / 100.0
        
        # Calcular score ponderado
        score = (
            # Relevância
            self.weights['bm25_score'] * features.bm25_score +
            self.weights['knn_score'] * features.knn_score +
            self.weights['hybrid_score'] * features.hybrid_score +
            
            # Match Textual
            self.weights['exact_match'] * features.exact_match +
            self.weights['term_coverage'] * features.term_coverage +
            
            # Qualidade do Texto
            self.weights['title_length'] * self._normalize_length(features.title_length) +
            self.weights['description_length'] * self._normalize_length(features.description_length) +
            self.weights['title_description_ratio'] * features.title_description_ratio +
            self.weights['text_quality_score'] * features.text_quality_score +
            
            # Contexto
            self.weights['first_word_match'] * features.first_word_match +
            self.weights['has_numbers'] * features.has_numbers +
            self.weights['brand_match'] * features.brand_match +
            self.weights['category_match'] * features.category_match +
            
            # Popularidade
            self.weights['popularity_score'] * normalized_popularity +
            self.weights['quality_score'] * features.quality_score +
            self.weights['ctr'] * features.ctr +
            self.weights['sales_count_normalized'] * features.sales_count_normalized
        )
        
        return score
    
    def _normalize_length(self, length: float) -> float:
        """
        Normaliza comprimento de texto para 0-1.
        Assume que comprimentos ideais estão entre 30-200 caracteres.
        """
        if length <= 0:
            return 0.0
        if length <= 30:
            return length / 30.0
        if length <= 200:
            return 1.0
        # Penaliza textos muito longos
        return max(0.0, 1.0 - (length - 200) / 500.0)
    
    def get_version(self) -> str:
        """Retorna a versão do modelo"""
        return self.model_version
    
    def rank(self, candidates: List[InternalFeatureVector], top_k: int = 20) -> List[tuple]:
        """
        Ranqueia candidatos e retorna os Top K.
        
        Args:
            candidates: Lista de FeatureVectors
            top_k: Número de produtos a retornar (padrão: 20)
            
        Returns:
            Lista de tuplas (product_id, score) ordenadas por score decrescente
        """
        # Calcular scores para todos os candidatos
        scored_candidates = [
            (features.product_id, self.predict(features))
            for features in candidates
        ]
        
        # Ordenar por score decrescente
        scored_candidates.sort(key=lambda x: x[1], reverse=True)
        
        # Retornar Top K
        return scored_candidates[:top_k]

