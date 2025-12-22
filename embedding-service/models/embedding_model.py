"""
Embedding Model
Modelo de embedding usando sentence-transformers (all-MiniLM-L6-v2)
"""

from typing import List, Optional
import numpy as np
import logging
from collections import OrderedDict

logger = logging.getLogger(__name__)

try:
    from sentence_transformers import SentenceTransformer
    SENTENCE_TRANSFORMERS_AVAILABLE = True
except ImportError:
    SENTENCE_TRANSFORMERS_AVAILABLE = False
    logger.warning(
        "sentence-transformers não está instalado. "
        "Instale com: pip install sentence-transformers"
    )


class EmbeddingModel:
    """
    Modelo de embedding usando sentence-transformers.
    
    Utiliza o modelo 'sentence-transformers/all-MiniLM-L6-v2' que gera
    embeddings de 384 dimensões.
    """
    
    MODEL_NAME = "sentence-transformers/all-MiniLM-L6-v2"
    DIMENSION = 384
    MODEL_VERSION = "all-MiniLM-L6-v2"
    DEFAULT_CACHE_SIZE = 1000
    
    def __init__(self, cache_size: int = DEFAULT_CACHE_SIZE):
        """
        Inicializa o modelo de embedding.
        
        Args:
            cache_size: Tamanho do cache LRU (padrão: 1000)
        """
        self.model = None
        self._loaded = False
        self.cache_size = cache_size
        self.cache: OrderedDict[str, np.ndarray] = OrderedDict()
        
        if SENTENCE_TRANSFORMERS_AVAILABLE:
            try:
                logger.info(f"Carregando modelo de embedding: {self.MODEL_NAME}")
                self.model = SentenceTransformer(self.MODEL_NAME)
                self._loaded = True
                logger.info(
                    f"Modelo carregado com sucesso "
                    f"(dimensão: {self.DIMENSION}, versão: {self.MODEL_VERSION})"
                )
            except Exception as e:
                logger.error(f"Erro ao carregar modelo de embedding: {str(e)}", exc_info=True)
                self._loaded = False
        else:
            logger.error("sentence-transformers não está disponível")
            self._loaded = False
    
    def embed(self, text: str) -> np.ndarray:
        """
        Gera embedding para um único texto.
        Usa cache LRU para textos repetidos.
        
        Args:
            text: Texto para gerar embedding
            
        Returns:
            Array numpy com embedding (384 dimensões)
        """
        if not self._loaded or self.model is None:
            raise RuntimeError("Modelo de embedding não está carregado")
        
        if not text or not text.strip():
            # Retornar vetor zero para texto vazio
            return np.zeros(self.DIMENSION)
        
        # Verificar cache
        text_key = text.strip().lower()
        if text_key in self.cache:
            # Mover para o final (mais recente)
            embedding = self.cache.pop(text_key)
            self.cache[text_key] = embedding
            logger.debug(f"Cache hit para texto: {text_key[:50]}...")
            return embedding.copy()
        
        try:
            # Gerar embedding
            embedding = self.model.encode(text, normalize_embeddings=True)
            
            # Adicionar ao cache
            self._add_to_cache(text_key, embedding)
            
            return embedding
        except Exception as e:
            logger.error(f"Erro ao gerar embedding: {str(e)}", exc_info=True)
            raise
    
    def _add_to_cache(self, key: str, embedding: np.ndarray):
        """
        Adiciona embedding ao cache LRU.
        
        Args:
            key: Chave do cache (texto normalizado)
            embedding: Vetor de embedding
        """
        # Se já existe, remover para atualizar posição
        if key in self.cache:
            self.cache.pop(key)
        
        # Adicionar ao final
        self.cache[key] = embedding.copy()
        
        # Se cache excedeu tamanho, remover o mais antigo (primeiro item)
        if len(self.cache) > self.cache_size:
            self.cache.popitem(last=False)  # Remove o primeiro (mais antigo)
            logger.debug(f"Cache LRU: removido item mais antigo (tamanho: {len(self.cache)})")
    
    def embed_batch(self, texts: List[str]) -> List[np.ndarray]:
        """
        Gera embeddings para uma lista de textos (processamento em lote).
        Usa cache para textos repetidos.
        
        Args:
            texts: Lista de textos para gerar embeddings
            
        Returns:
            Lista de arrays numpy com embeddings
        """
        if not self._loaded or self.model is None:
            raise RuntimeError("Modelo de embedding não está carregado")
        
        if not texts:
            return []
        
        # Preparar resultado e identificar textos que precisam ser gerados
        result = []
        texts_to_encode = []
        encode_indices = []  # Índices dos textos que precisam ser gerados
        
        for idx, text in enumerate(texts):
            if not text or not text.strip():
                # Texto vazio - adicionar vetor zero
                result.append(np.zeros(self.DIMENSION))
                continue
            
            text_key = text.strip().lower()
            
            # Verificar cache
            if text_key in self.cache:
                # Cache hit - mover para o final (mais recente)
                embedding = self.cache.pop(text_key)
                self.cache[text_key] = embedding
                result.append(embedding.copy())
                logger.debug(f"Cache hit (batch) para texto: {text_key[:50]}...")
            else:
                # Cache miss - precisa gerar
                result.append(None)  # Placeholder
                texts_to_encode.append(text)
                encode_indices.append(idx)
        
        # Gerar embeddings para textos não encontrados no cache
        if texts_to_encode:
            try:
                embeddings = self.model.encode(
                    texts_to_encode,
                    normalize_embeddings=True,
                    show_progress_bar=False
                )
                
                # Preencher resultados e adicionar ao cache
                for encode_idx, original_idx in enumerate(encode_indices):
                    text = texts[original_idx]
                    text_key = text.strip().lower()
                    embedding = embeddings[encode_idx]
                    
                    # Adicionar ao cache
                    self._add_to_cache(text_key, embedding)
                    
                    # Preencher resultado
                    result[original_idx] = embedding
                    
            except Exception as e:
                logger.error(f"Erro ao gerar embeddings em batch: {str(e)}", exc_info=True)
                raise
        
        return result
    
    def get_version(self) -> str:
        """Retorna a versão do modelo"""
        return self.MODEL_VERSION
    
    def get_dimension(self) -> int:
        """Retorna a dimensão dos vetores de embedding"""
        return self.DIMENSION
    
    def is_loaded(self) -> bool:
        """Verifica se o modelo está carregado"""
        return self._loaded
    
    def get_cache_size(self) -> int:
        """Retorna o tamanho atual do cache"""
        return len(self.cache)
    
    def clear_cache(self):
        """Limpa o cache de embeddings"""
        self.cache.clear()
        logger.info("Cache de embeddings limpo")

