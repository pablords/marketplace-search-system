"""
Embedding Service
Serviço que orquestra a geração de embeddings usando o modelo de embedding
"""

from typing import List, Literal
from models.embedding_model import EmbeddingModel
import logging

logger = logging.getLogger(__name__)


class EmbeddingService:
    """
    Serviço de embedding que utiliza o modelo de embedding para gerar vetores.
    """
    
    def __init__(self):
        """Inicializa o serviço com o modelo de embedding"""
        self.model = EmbeddingModel()
        logger.info("EmbeddingService inicializado")
    
    def generate_embeddings(
        self,
        texts: List[str],
        embedding_type: Literal["product", "query"] = "product"
    ) -> List:
        """
        Gera embeddings para uma lista de textos.
        
        Args:
            texts: Lista de textos para gerar embeddings
            embedding_type: Tipo de embedding ('product' ou 'query')
            
        Returns:
            Lista de arrays numpy com embeddings (cada um com 384 dimensões)
        """
        if not texts:
            return []
        
        # Gerar embeddings usando o modelo
        embeddings = self.model.embed_batch(texts)
        
        logger.debug(
            f"Gerados {len(embeddings)} embeddings "
            f"(tipo: {embedding_type}, dimensão: {self.get_dimension()})"
        )
        
        return embeddings
    
    def get_model_version(self) -> str:
        """Retorna a versão do modelo utilizado"""
        return self.model.get_version()
    
    def get_dimension(self) -> int:
        """Retorna a dimensão dos vetores de embedding"""
        return self.model.get_dimension()
    
    def is_model_loaded(self) -> bool:
        """Verifica se o modelo está carregado"""
        return self.model.is_loaded()

