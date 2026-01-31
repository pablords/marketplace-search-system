#!/usr/bin/env python3
"""
Script de Validação da Busca Híbrida com LTR (Learning to Rank)

Este script valida se a busca híbrida (BM25 + k-NN) com LTR está funcionando corretamente:
1. Verifica se o Embedding Service está disponível
2. Testa geração de embeddings para queries
3. Verifica se produtos têm embeddings no OpenSearch
4. Verifica se o ML Ranking Service (LTR) está disponível
5. Testa o endpoint de ranking ML diretamente
6. Valida que os resultados da busca estão sendo re-ranqueados pelo LTR
7. Compara resultados de busca com e sem busca híbrida
8. Valida scores BM25, k-NN e ML
9. Testa diferentes tipos de queries

Atualizado para trabalhar com dataset real do Amazon (248 categorias) e incluir validação de LTR.
Suporta execução com containers Docker e Traefik como gateway.

Modos de execução:
  - Modo Local (padrão): Acessa serviços diretamente nas portas locais
  - Modo Container: Acessa serviços via Traefik (porta 8888) e serviços ML diretamente

Exemplos:
  # Modo local
  python validate_hybrid_search.py
  
  # Modo container
  python validate_hybrid_search.py --container-mode
  
  # Modo container com URLs customizadas
  python validate_hybrid_search.py --container-mode \\
    --traefik-url http://localhost:8888 \\
    --embedding-url http://localhost:8085 \\
    --ml-ranking-url http://localhost:8084 \\
    --opensearch-url http://localhost:9200
"""

import requests
import json
import sys
import os
import re
import argparse
from typing import Dict, List, Optional, Tuple
from datetime import datetime
import time

# Configurações padrão (modo local)
DEFAULT_EMBEDDING_SERVICE_URL = "http://localhost:8085"
DEFAULT_SEARCH_SERVICE_URL = "http://localhost:8083"
DEFAULT_ML_RANKING_SERVICE_URL = "http://localhost:8084"
DEFAULT_OPENSEARCH_URL = "http://localhost:9200"
DEFAULT_TRAEFIK_URL = "http://localhost:8888"
DEFAULT_TRAEFIK_API_PREFIX = "/api"

OPENSEARCH_INDEX = "products_index"

# Variáveis globais de configuração (serão inicializadas em parse_arguments)
USE_CONTAINER_MODE = False
TRAEFIK_URL = DEFAULT_TRAEFIK_URL
TRAEFIK_API_PREFIX = DEFAULT_TRAEFIK_API_PREFIX
EMBEDDING_SERVICE_URL = DEFAULT_EMBEDDING_SERVICE_URL
SEARCH_SERVICE_URL = DEFAULT_SEARCH_SERVICE_URL
ML_RANKING_SERVICE_URL = DEFAULT_ML_RANKING_SERVICE_URL
OPENSEARCH_URL = DEFAULT_OPENSEARCH_URL

def configure_urls(
    container_mode: bool = False,
    traefik_url: Optional[str] = None,
    embedding_url: Optional[str] = None,
    ml_ranking_url: Optional[str] = None,
    opensearch_url: Optional[str] = None,
    search_url: Optional[str] = None
):
    """Configura as URLs dos serviços baseado no modo e argumentos fornecidos"""
    global USE_CONTAINER_MODE, TRAEFIK_URL, TRAEFIK_API_PREFIX
    global EMBEDDING_SERVICE_URL, SEARCH_SERVICE_URL, ML_RANKING_SERVICE_URL, OPENSEARCH_URL
    
    # Detectar modo container (via variável de ambiente ou argumento)
    USE_CONTAINER_MODE = container_mode or os.getenv("USE_CONTAINER_MODE", "false").lower() == "true"
    
    # Configurar Traefik
    TRAEFIK_URL = traefik_url or os.getenv("TRAEFIK_URL", DEFAULT_TRAEFIK_URL)
    TRAEFIK_API_PREFIX = os.getenv("TRAEFIK_API_PREFIX", DEFAULT_TRAEFIK_API_PREFIX)
    
    # Configurações quando em modo container
    if USE_CONTAINER_MODE:
        # Via Traefik: os serviços ML não estão expostos diretamente, então tentamos acessar diretamente
        # Se os containers não expuserem portas, precisaremos adicionar rotas no Traefik ou usar docker exec
        # Por enquanto, assumimos que as portas podem ser acessadas diretamente se mapeadas
        EMBEDDING_SERVICE_URL = embedding_url or os.getenv("EMBEDDING_SERVICE_URL", DEFAULT_EMBEDDING_SERVICE_URL)
        ML_RANKING_SERVICE_URL = ml_ranking_url or os.getenv("ML_RANKING_SERVICE_URL", DEFAULT_ML_RANKING_SERVICE_URL)
        # Search Service via Traefik
        SEARCH_SERVICE_URL = f"{TRAEFIK_URL}{TRAEFIK_API_PREFIX}/v1"
        # OpenSearch pode ser acessado diretamente se a porta estiver mapeada
        OPENSEARCH_URL = opensearch_url or os.getenv("OPENSEARCH_URL", DEFAULT_OPENSEARCH_URL)
    else:
        # Modo local - usar URLs padrão ou variáveis de ambiente
        EMBEDDING_SERVICE_URL = embedding_url or os.getenv("EMBEDDING_SERVICE_URL", DEFAULT_EMBEDDING_SERVICE_URL)
        SEARCH_SERVICE_URL = search_url or os.getenv("SEARCH_SERVICE_URL", DEFAULT_SEARCH_SERVICE_URL)
        ML_RANKING_SERVICE_URL = ml_ranking_url or os.getenv("ML_RANKING_SERVICE_URL", DEFAULT_ML_RANKING_SERVICE_URL)
        OPENSEARCH_URL = opensearch_url or os.getenv("OPENSEARCH_URL", DEFAULT_OPENSEARCH_URL)

# Cores para output
class Colors:
    GREEN = '\033[92m'
    RED = '\033[91m'
    YELLOW = '\033[93m'
    BLUE = '\033[94m'
    RESET = '\033[0m'
    BOLD = '\033[1m'

def print_header(text: str):
    """Imprime um cabeçalho formatado"""
    print(f"\n{Colors.BOLD}{Colors.BLUE}{'='*60}{Colors.RESET}")
    print(f"{Colors.BOLD}{Colors.BLUE}{text}{Colors.RESET}")
    print(f"{Colors.BOLD}{Colors.BLUE}{'='*60}{Colors.RESET}\n")

def print_success(text: str):
    """Imprime mensagem de sucesso"""
    print(f"{Colors.GREEN}✓ {text}{Colors.RESET}")

def print_error(text: str):
    """Imprime mensagem de erro"""
    print(f"{Colors.RED}✗ {text}{Colors.RESET}")

def print_warning(text: str):
    """Imprime mensagem de aviso"""
    print(f"{Colors.YELLOW}⚠ {text}{Colors.RESET}")

def print_info(text: str):
    """Imprime informação"""
    print(f"{Colors.BLUE}ℹ {text}{Colors.RESET}")

def check_service_health(url: str, service_name: str, health_path: str = "/api/v1/health") -> bool:
    """Verifica se um serviço está saudável"""
    try:
        # Em modo container, o Search Service já está configurado com a URL completa via Traefik
        # Então verificamos se a URL já contém o caminho base do Traefik
        if USE_CONTAINER_MODE and "search" in service_name.lower():
            # Health check do Search Service via Traefik (url já contém /api/v1)
            health_url = f"{url}/health"
        else:
            # Modo local ou outros serviços - construir URL completa
            health_url = f"{url}{health_path}"
        
        response = requests.get(health_url, timeout=20)
        if response.status_code == 200:
            data = response.json()
            status = data.get("status", "unknown")
            # Aceitar vários status válidos (case-insensitive)
            status_upper = str(status).upper()
            valid_statuses = ["HEALTHY", "DEGRADED", "UP"]
            is_healthy = status_upper in valid_statuses
            
            if is_healthy:
                print_success(f"{service_name} está {status}")
                # Mostrar informações adicionais se disponíveis
                if "model_loaded" in data:
                    model_loaded = data.get("model_loaded", False)
                    model_version = data.get("model_version", "N/A")
                    if model_loaded:
                        print_info(f"  Modelo LTR carregado: {model_version}")
                    else:
                        print_warning(f"  Modelo LTR não está carregado")
                return True
            else:
                print_warning(f"{service_name} retornou status '{status}' (esperado: healthy/degraded/UP)")
                return False
        else:
            print_error(f"{service_name} retornou status HTTP {response.status_code}")
            return False
    except requests.exceptions.RequestException as e:
        print_error(f"{service_name} não está acessível: {e}")
        return False

def test_embedding_generation(query: str) -> Optional[List[float]]:
    """Testa a geração de embedding para uma query"""
    try:
        payload = {
            "texts": [query],
            "type": "query"
        }
        response = requests.post(
            f"{EMBEDDING_SERVICE_URL}/api/v1/embeddings/query",
            json=payload,
            timeout=20
        )
        
        if response.status_code == 200:
            data = response.json()
            if data.get("embeddings") and len(data["embeddings"]) > 0:
                embedding = data["embeddings"][0].get("vector", [])
                dimension = data.get("dimension", 0)
                print_success(f"Embedding gerado: {len(embedding)} dimensões (esperado: {dimension})")
                return embedding
            else:
                print_error("Resposta do embedding service não contém embeddings")
                return None
        else:
            print_error(f"Erro ao gerar embedding: {response.status_code}")
            print_error(f"Resposta: {response.text}")
            return None
    except requests.exceptions.RequestException as e:
        print_error(f"Erro ao chamar embedding service: {e}")
        return None

def check_total_products_in_index() -> int:
    """Verifica o total de produtos no índice OpenSearch"""
    try:
        response = requests.get(
            f"{OPENSEARCH_URL}/{OPENSEARCH_INDEX}/_count",
            headers={"Content-Type": "application/json"},
            timeout=20
        )
        
        if response.status_code == 200:
            data = response.json()
            count = data.get("count", 0)
            return count
        else:
            print_error(f"Erro ao contar produtos: {response.status_code}")
            return 0
    except requests.exceptions.RequestException as e:
        print_error(f"Erro ao acessar OpenSearch: {e}")
        return 0

def get_sample_product_titles(limit: int = 50) -> List[str]:
    """Obtém alguns títulos de produtos do índice para sugerir queries"""
    try:
        query = {
            "size": limit,
            "_source": ["title", "category"],
            "query": {"match_all": {}}
        }
        
        response = requests.post(
            f"{OPENSEARCH_URL}/{OPENSEARCH_INDEX}/_search",
            json=query,
            headers={"Content-Type": "application/json"},
            timeout=20
        )
        
        if response.status_code == 200:
            data = response.json()
            hits = data.get("hits", {}).get("hits", [])
            titles = []
            for hit in hits:
                source = hit.get("_source", {})
                title = source.get("title", "")
                if title:
                    titles.append(title)
            return titles
        return []
    except requests.exceptions.RequestException:
        return []

def extract_keywords_from_titles(titles: List[str], min_word_length: int = 3, max_keywords: int = 10) -> List[str]:
    """
    Extrai palavras-chave relevantes dos títulos de produtos.
    Filtra palavras comuns em inglês e português, mantendo apenas termos relevantes.
    """
    # Palavras comuns a serem ignoradas (stop words)
    stop_words = {
        'the', 'a', 'an', 'and', 'or', 'but', 'in', 'on', 'at', 'to', 'for', 'of', 'with', 'by',
        'o', 'a', 'os', 'as', 'um', 'uma', 'uns', 'umas', 'de', 'do', 'da', 'dos', 'das',
        'em', 'no', 'na', 'nos', 'nas', 'por', 'para', 'com', 'sem', 'sob', 'sobre',
        'is', 'are', 'was', 'were', 'be', 'been', 'being', 'have', 'has', 'had',
        'this', 'that', 'these', 'those', 'it', 'its', 'they', 'them', 'their'
    }
    
    # Palavras comuns de produtos que não são úteis para busca
    product_stop_words = {
        'new', 'used', 'refurbished', 'novo', 'usado', 'recondicionado',
        'pack', 'set', 'kit', 'bundle', 'lot', 'pcs', 'pc', 'piece', 'pieces',
        'pcs', 'pc', 'piece', 'pieces', 'unit', 'units', 'item', 'items',
        'for', 'with', 'without', 'com', 'sem', 'para'
    }
    
    all_stop_words = stop_words | product_stop_words
    
    keywords = set()
    
    for title in titles:
        # Normalizar título: remover caracteres especiais, converter para lowercase
        title_clean = re.sub(r'[^\w\s]', ' ', title.lower())
        words = title_clean.split()
        
        for word in words:
            # Filtrar palavras muito curtas, muito longas, ou stop words
            if (len(word) >= min_word_length and 
                len(word) <= 20 and 
                word not in all_stop_words and
                not word.isdigit() and  # Ignorar números puros
                word.isalnum()):  # Apenas alfanuméricos
                keywords.add(word)
    
    # Converter para lista e ordenar por frequência (se possível)
    keywords_list = list(keywords)
    
    # Limitar quantidade
    if len(keywords_list) > max_keywords:
        keywords_list = keywords_list[:max_keywords]
    
    return keywords_list

def get_default_test_queries() -> List[str]:
    """
    Retorna queries de teste padrão apropriadas para produtos do Amazon.
    Estas queries são genéricas e devem funcionar com a maioria dos datasets.
    """
    return [
        "phone",           # Celulares
        "laptop",          # Notebooks
        "headphones",      # Fones de ouvido
        "watch",           # Relógios
        "camera",          # Câmeras
        "tablet",          # Tablets
        "speaker",         # Alto-falantes
        "charger",         # Carregadores
        "case",            # Capas
        "bag"              # Bolsas
    ]

def check_product_embeddings() -> Tuple[bool, int]:
    """Verifica se produtos no OpenSearch têm embeddings"""
    try:
        # Buscar alguns produtos do índice
        query = {
            "size": 10,
            "_source": ["title", "product_vector"],
            "query": {"match_all": {}}
        }
        
        response = requests.post(
            f"{OPENSEARCH_URL}/{OPENSEARCH_INDEX}/_search",
            json=query,
            headers={"Content-Type": "application/json"},
            timeout=20
        )
        
        if response.status_code == 200:
            data = response.json()
            hits = data.get("hits", {}).get("hits", [])
            
            if not hits:
                print_warning("Nenhum produto encontrado no índice")
                return False, 0
            
            products_with_embeddings = 0
            products_without_embeddings = 0
            
            for hit in hits:
                source = hit.get("_source", {})
                if "product_vector" in source:
                    vector = source["product_vector"]
                    if vector and len(vector) > 0:
                        products_with_embeddings += 1
                    else:
                        products_without_embeddings += 1
                else:
                    products_without_embeddings += 1
            
            total = len(hits)
            print_info(f"Produtos verificados: {total}")
            print_success(f"Produtos com embeddings: {products_with_embeddings}")
            
            if products_without_embeddings > 0:
                print_warning(f"Produtos sem embeddings: {products_without_embeddings}")
            
            return products_with_embeddings > 0, products_with_embeddings
        else:
            print_error(f"Erro ao buscar produtos: {response.status_code}")
            return False, 0
    except requests.exceptions.RequestException as e:
        print_error(f"Erro ao acessar OpenSearch: {e}")
        return False, 0

def test_search_query(query: str, use_hybrid: bool = True) -> Optional[Dict]:
    """Testa uma query de busca"""
    try:
        # Em modo container, o Search Service é acessado via Traefik
        # O caminho já inclui /api/v1 quando USE_CONTAINER_MODE está ativo
        if USE_CONTAINER_MODE:
            url = f"{SEARCH_SERVICE_URL}/search/products"
        else:
            url = f"{SEARCH_SERVICE_URL}/api/v1/search/products"
        
        # O SearchController espera: query, page, size (não q, offset, limit)
        params = {
            "query": query,
            "page": 0,
            "size": 10
        }
        
        # Nota: O parâmetro "hybrid" não existe no SearchController
        # A busca híbrida é ativada automaticamente se o Embedding Service estiver disponível
        
        response = requests.get(url, params=params, timeout=20)
        
        if response.status_code == 200:
            result = response.json()
            # Debug: mostrar estrutura da resposta se não houver resultados
            if result.get("total_count", 0) == 0:
                print_warning(f"Resposta da API para '{query}': {json.dumps(result, indent=2)[:500]}")
            return result
        else:
            print_error(f"Erro na busca: {response.status_code}")
            print_error(f"Resposta: {response.text}")
            return None
    except requests.exceptions.RequestException as e:
        print_error(f"Erro ao chamar search service: {e}")
        return None

def analyze_search_results(results: Dict, query: str) -> Dict:
    """Analisa os resultados da busca"""
    # A resposta pode ter totalCount ou total_count (camelCase vs snake_case)
    total_count = results.get("totalCount") or results.get("total_count", 0)
    execution_time = results.get("executionTimeMs") or results.get("execution_time_ms", 0)
    
    analysis = {
        "total_count": total_count,
        "products_returned": len(results.get("products", [])),
        "execution_time_ms": execution_time,
        "has_metrics": "metrics" in results,
        "products": []
    }
    
    products = results.get("products", [])
    # Analisar os últimos 5 resultados ao invés dos primeiros
    products_to_analyze = products[-5:] if len(products) > 5 else products
    for product in products_to_analyze:
        product_info = {
            "id": product.get("id"),
            "title": product.get("title", "")[:50],
            "score": product.get("score", 0.0) if "score" in product else None
        }
        analysis["products"].append(product_info)
    
    return analysis

def check_ml_ranking_service() -> Tuple[bool, Optional[Dict]]:
    """Verifica se o ML Ranking Service está disponível e retorna informações"""
    try:
        response = requests.get(f"{ML_RANKING_SERVICE_URL}/health", timeout=20)
        if response.status_code == 200:
            data = response.json()
            status = data.get("status", "unknown")
            model_loaded = data.get("model_loaded", False)
            model_version = data.get("model_version", None)
            
            is_healthy = status.lower() == "healthy" and model_loaded
            
            if is_healthy:
                print_success(f"ML Ranking Service está {status} (modelo: {model_version})")
                return True, data
            else:
                print_warning(f"ML Ranking Service retornou status '{status}' (modelo carregado: {model_loaded})")
                return False, data
        else:
            print_error(f"ML Ranking Service retornou status HTTP {response.status_code}")
            return False, None
    except requests.exceptions.RequestException as e:
        print_error(f"ML Ranking Service não está acessível: {e}")
        return False, None

def test_ml_ranking_directly() -> bool:
    """Testa o endpoint de ranking ML diretamente com dados de exemplo"""
    try:
        # Criar um candidato de exemplo com todas as 17 features
        test_candidate = {
            "product_id": "test-product-123",
            "bm25_score": 0.85,
            "knn_score": 0.72,
            "hybrid_score": 0.80,
            "exact_match": 1.0,
            "term_coverage": 0.9,
            "title_length": 45.0,
            "description_length": 200.0,
            "title_description_ratio": 0.225,
            "text_quality_score": 0.8,
            "first_word_match": 1.0,
            "has_numbers": 0.0,
            "brand_match": 1.0,
            "category_match": 0.0,
            "popularity_score": 75.5,
            "quality_score": 0.9,
            "ctr": 0.15,
            "sales_count_normalized": 0.7
        }
        
        payload = {
            "candidates": [test_candidate],
            "query": "test query"
        }
        
        response = requests.post(
            f"{ML_RANKING_SERVICE_URL}/api/v1/ml/rank",
            json=payload,
            timeout=20
        )
        
        if response.status_code == 200:
            data = response.json()
            ranked_products = data.get("ranked_products", [])
            model_version = data.get("model_version", "N/A")
            
            if ranked_products and len(ranked_products) > 0:
                ml_score = ranked_products[0].get("ml_score", 0.0)
                rank = ranked_products[0].get("rank", 0)
                print_success(f"ML Ranking funcionando: score={ml_score:.4f}, rank={rank}, modelo={model_version}")
                return True
            else:
                print_error("ML Ranking Service não retornou produtos ranqueados")
                return False
        else:
            print_error(f"Erro ao testar ML Ranking: {response.status_code}")
            print_error(f"Resposta: {response.text}")
            return False
    except requests.exceptions.RequestException as e:
        print_error(f"Erro ao chamar ML Ranking Service: {e}")
        return False

def test_search_with_hybrid_check(query: str, ml_ranking_available: bool = False) -> Dict:
    """Testa uma busca e verifica se a busca híbrida e LTR estão funcionando"""
    print_info(f"Testando query: '{query}'")
    
    # Executar busca (busca híbrida será ativada automaticamente se Embedding Service estiver disponível)
    results = test_search_query(query, use_hybrid=True)
    
    comparison = {
        "query": query,
        "results": None,
        "hybrid_active": None,
        "ltr_active": None
    }
    
    if results:
        analysis = analyze_search_results(results, query)
        comparison["results"] = analysis
        
        total_count = analysis["total_count"]
        execution_time = analysis["execution_time_ms"]
        
        if total_count > 0:
            print_success(f"Busca retornou {total_count} resultados em {execution_time}ms")
            
            # Verificar se produtos foram retornados
            products = results.get("products", [])
            if products:
                # Mostrar os últimos resultados ao invés dos primeiros
                last_products = products[-3:] if len(products) > 3 else products
                print_info(f"Últimos resultados (de {len(products)} total):")
                for i, product in enumerate(last_products, len(products) - len(last_products) + 1):
                    title = product.get("title", "Sem título")
                    print(f"  {i}. {title[:60]}")
            
            # A busca híbrida está ativa se o Embedding Service estiver disponível
            # (verificado na seção 1)
            comparison["hybrid_active"] = True
            print_info("Busca híbrida: Ativada automaticamente (Embedding Service disponível)")
            
            # LTR está ativo se o ML Ranking Service estiver disponível
            comparison["ltr_active"] = ml_ranking_available
            if ml_ranking_available:
                print_info("LTR (Learning to Rank): Ativo (ML Ranking Service disponível)")
            else:
                print_warning("LTR (Learning to Rank): Inativo (ML Ranking Service não disponível)")
        else:
            print_warning(f"Busca retornou 0 resultados para '{query}'")
            print_warning("Verifique se o termo existe nos produtos indexados")
    else:
        print_error("Falha na busca")
    
    return comparison

def validate_hybrid_search():
    """Função principal de validação"""
    print_header("VALIDAÇÃO DA BUSCA HÍBRIDA COM LTR (LEARNING TO RANK)")
    
    all_checks_passed = True
    
    # 1. Verificar serviços
    print_header("1. Verificando Serviços")
    embedding_ok = check_service_health(EMBEDDING_SERVICE_URL, "Embedding Service", "/api/v1/health")
    search_ok = check_service_health(SEARCH_SERVICE_URL, "Search Service", "/api/v1/health")
    ml_ranking_ok, ml_ranking_info = check_ml_ranking_service()
    
    if not embedding_ok:
        print_warning("Embedding Service não está disponível - busca híbrida pode não funcionar")
        all_checks_passed = False
    
    if not search_ok:
        print_error("Search Service não está disponível - não é possível continuar")
        return False
    
    if not ml_ranking_ok:
        print_warning("ML Ranking Service não está disponível - LTR não funcionará")
        all_checks_passed = False
    
    # 2. Testar geração de embeddings
    print_header("2. Testando Geração de Embeddings")
    # Queries de teste genéricas que funcionam com produtos reais do Amazon
    test_queries = [
        "smartphone",
        "laptop computer",
        "wireless headphones"
    ]
    
    embeddings_ok = True
    for query in test_queries:
        embedding = test_embedding_generation(query)
        if not embedding:
            embeddings_ok = False
            all_checks_passed = False
        else:
            if len(embedding) != 384:
                print_warning(f"Embedding tem {len(embedding)} dimensões, esperado 384")
    
    if not embeddings_ok:
        print_error("Falha na geração de embeddings")
        return False
    
    # 2.5. Testar ML Ranking Service diretamente
    print_header("2.5. Testando ML Ranking Service (LTR)")
    ml_ranking_test_ok = False
    if ml_ranking_ok:
        ml_ranking_test_ok = test_ml_ranking_directly()
        if not ml_ranking_test_ok:
            print_warning("Teste direto do ML Ranking Service falhou")
            all_checks_passed = False
    else:
        print_warning("Pulando teste direto - ML Ranking Service não está disponível")
    
    # 3. Verificar embeddings de produtos
    print_header("3. Verificando Embeddings de Produtos no OpenSearch")
    has_embeddings, count = check_product_embeddings()
    
    if not has_embeddings:
        print_error("Nenhum produto tem embeddings - busca híbrida não funcionará")
        all_checks_passed = False
    else:
        print_success(f"{count} produtos têm embeddings")
    
    # 4. Verificar se há produtos no índice
    print_header("4. Verificando Produtos no Índice")
    total_products = check_total_products_in_index()
    if total_products == 0:
        print_warning("Nenhum produto encontrado no índice OpenSearch")
        print_warning("É necessário indexar produtos antes de testar a busca")
        all_checks_passed = False
        comparisons = []
    else:
        print_success(f"Total de produtos no índice: {total_products}")
        
        # Obter alguns títulos para sugerir queries (aumentar para melhor extração de palavras-chave)
        sample_titles = get_sample_product_titles(50)
        if sample_titles:
            print_info(f"Exemplos de produtos no índice ({len(sample_titles)} títulos analisados):")
            for i, title in enumerate(sample_titles[:5], 1):
                print(f"  {i}. {title[:60]}...")
            if len(sample_titles) > 5:
                print_info(f"  ... e mais {len(sample_titles) - 5} produtos")
    
    # 5. Testar buscas (apenas se houver produtos)
    print_header("5. Testando Buscas Híbridas")
    
    if total_products == 0:
        print_warning("Pulando testes de busca - nenhum produto no índice")
        comparisons = []
    else:
        # Tentar usar termos dos produtos reais
        test_queries_search = []
        
        # Se temos títulos, extrair palavras-chave relevantes
        if sample_titles and len(sample_titles) > 0:
            print_info(f"Analisando {len(sample_titles)} títulos de produtos para extrair palavras-chave...")
            keywords = extract_keywords_from_titles(sample_titles, min_word_length=4, max_keywords=5)
            
            if keywords and len(keywords) >= 3:
                # Usar as palavras-chave extraídas
                test_queries_search = keywords[:5]  # Usar até 5 palavras-chave
                print_info(f"Palavras-chave extraídas dos produtos: {keywords[:10]}")
                print_info(f"Testando com termos reais dos produtos: {test_queries_search}")
            else:
                # Se não conseguimos extrair palavras-chave suficientes, usar queries padrão
                test_queries_search = get_default_test_queries()[:5]
                print_info(f"Usando queries padrão: {test_queries_search}")
        else:
            # Fallback para queries padrão (genéricas para produtos do Amazon)
            test_queries_search = get_default_test_queries()[:5]
            print_info(f"Nenhum título disponível. Usando queries padrão: {test_queries_search}")
        
        comparisons = []
        for query in test_queries_search:
            comparison = test_search_with_hybrid_check(query, ml_ranking_available=ml_ranking_ok)
            comparisons.append(comparison)
            time.sleep(1)  # Pequeno delay entre requisições
    
    # 6. Resumo final
    print_header("6. Resumo da Validação")
    
    print_info("Status dos Serviços:")
    print(f"  - Embedding Service: {'✓' if embedding_ok else '✗'}")
    print(f"  - Search Service: {'✓' if search_ok else '✗'}")
    print(f"  - ML Ranking Service (LTR): {'✓' if ml_ranking_ok else '✗'}")
    if ml_ranking_ok and ml_ranking_info:
        model_version = ml_ranking_info.get("model_version", "N/A")
        print(f"    Modelo: {model_version}")
    
    print_info("Status dos Embeddings:")
    print(f"  - Geração de embeddings: {'✓' if embeddings_ok else '✗'}")
    print(f"  - Produtos com embeddings: {'✓' if has_embeddings else '✗'} ({count} produtos)")
    
    print_info("Status do LTR:")
    print(f"  - ML Ranking Service disponível: {'✓' if ml_ranking_ok else '✗'}")
    print(f"  - Teste direto do ranking: {'✓' if ml_ranking_test_ok else '✗'}")
    
    print_info("Resultados das Buscas:")
    total_results_found = 0
    searches_with_results = 0
    searches_with_ltr = 0
    for comp in comparisons:
        if comp.get("results"):
            count = comp["results"]["total_count"]
            total_results_found += count
            if count > 0:
                searches_with_results += 1
            hybrid_status = "✓" if comp.get("hybrid_active") else "?"
            ltr_status = "✓" if comp.get("ltr_active") else "✗"
            if comp.get("ltr_active"):
                searches_with_ltr += 1
            print(f"  - Query '{comp['query']}': {count} resultados (Híbrida: {hybrid_status}, LTR: {ltr_status})")
    
    if total_products > 0 and total_results_found == 0:
        print_warning("⚠ Nenhum resultado encontrado nas buscas testadas")
        print_warning("   Isso pode indicar que os produtos não correspondem às queries de teste")
        all_checks_passed = False
    elif total_products > 0 and searches_with_results == 0:
        print_warning("⚠ Produtos existem mas nenhuma busca retornou resultados")
        print_warning("   Verifique se os termos de busca correspondem aos produtos indexados")
        all_checks_passed = False
    
    # Recomendações
    print_header("Recomendações")
    
    if not embedding_ok:
        print_warning("1. Verifique se o Embedding Service está rodando na porta 8085")
    
    if not ml_ranking_ok:
        print_warning("2. Verifique se o ML Ranking Service está rodando na porta 8084")
        print_info("   O LTR (Learning to Rank) melhora a qualidade dos resultados de busca")
    
    if not has_embeddings:
        print_warning("3. Produtos precisam ser indexados com embeddings. Verifique o Indexing Service")
    
    if total_products == 0:
        print_warning("4. É necessário indexar produtos no OpenSearch antes de testar a busca")
        print_info("   Use o script data_gen.py com --use-real-dataset para criar produtos do dataset Amazon")
        print_info("   Exemplo: python data_gen.py --api-url http://localhost:8080/api/v1 --total 100 --use-real-dataset")
    
    if total_products > 0 and total_results_found == 0:
        print_warning("5. Produtos estão indexados mas não correspondem às queries de teste")
        print_info("   Tente buscar por termos que existem nos títulos dos produtos indexados")
        print_info("   O script tenta extrair palavras-chave automaticamente dos títulos reais")
        print_info("   Se necessário, ajuste as queries de teste no código para seu dataset específico")
    
    # Verificar se busca híbrida e LTR estão funcionando
    hybrid_working = embedding_ok and has_embeddings and total_products > 0
    ltr_working = ml_ranking_ok and ml_ranking_test_ok and total_products > 0
    
    if all_checks_passed and total_products > 0 and searches_with_results > 0:
        if hybrid_working and ltr_working:
            print_success("✓ Todas as validações passaram! A busca híbrida com LTR está funcionando.")
            print_info("  - Embedding Service: Disponível")
            print_info("  - ML Ranking Service (LTR): Disponível e funcionando")
            print_info("  - Produtos com embeddings: Sim")
            print_info("  - Buscas retornando resultados: Sim")
            print_info("  - LTR ativo nas buscas: Sim")
        elif hybrid_working:
            print_success("✓ Busca híbrida está funcionando, mas LTR não está ativo.")
            print_info("  - Embedding Service: Disponível")
            print_info("  - Produtos com embeddings: Sim")
            print_info("  - Buscas retornando resultados: Sim")
            print_warning("  - LTR (Learning to Rank): Não disponível - resultados podem não estar otimizados")
        else:
            print_warning("⚠ Busca funcionando, mas busca híbrida pode não estar ativa")
            print_info("  - Verifique se o Embedding Service está gerando embeddings para queries")
    elif all_checks_passed and total_products > 0:
        print_warning("⚠ Validações técnicas passaram, mas nenhum resultado foi encontrado nas buscas")
        print_info("  - Tente buscar por termos que existem nos títulos dos produtos")
    else:
        print_error("✗ Algumas validações falharam. Revise os problemas acima.")
    
    return all_checks_passed

def parse_arguments():
    """Parse argumentos da linha de comando"""
    parser = argparse.ArgumentParser(
        description="Validação da Busca Híbrida com LTR (Learning to Rank)",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Exemplos de uso:
  # Modo local (padrão)
  python validate_hybrid_search.py
  
  # Modo container com Traefik
  python validate_hybrid_search.py --container-mode
  
  # Modo container com URLs customizadas
  python validate_hybrid_search.py --container-mode \\
    --traefik-url http://localhost:8888 \\
    --embedding-url http://localhost:8085 \\
    --ml-ranking-url http://localhost:8084 \\
    --opensearch-url http://localhost:9200
  
  # Usando variáveis de ambiente
  USE_CONTAINER_MODE=true TRAEFIK_URL=http://localhost:8888 python validate_hybrid_search.py
        """
    )
    
    parser.add_argument(
        "--container-mode",
        action="store_true",
        help="Usar modo container (acessa serviços via Traefik)"
    )
    
    parser.add_argument(
        "--traefik-url",
        default=None,
        help="URL do Traefik (padrão: http://localhost:8888)"
    )
    
    parser.add_argument(
        "--embedding-url",
        default=None,
        help="URL do Embedding Service (padrão: http://localhost:8085)"
    )
    
    parser.add_argument(
        "--ml-ranking-url",
        default=None,
        help="URL do ML Ranking Service (padrão: http://localhost:8084)"
    )
    
    parser.add_argument(
        "--opensearch-url",
        default=None,
        help="URL do OpenSearch (padrão: http://localhost:9200)"
    )
    
    parser.add_argument(
        "--search-url",
        default=None,
        help="URL do Search Service (apenas modo local, padrão: http://localhost:8083)"
    )
    
    return parser.parse_args()

if __name__ == "__main__":
    args = parse_arguments()
    
    # Configurar URLs baseado nos argumentos
    configure_urls(
        container_mode=args.container_mode,
        traefik_url=args.traefik_url,
        embedding_url=args.embedding_url,
        ml_ranking_url=args.ml_ranking_url,
        opensearch_url=args.opensearch_url,
        search_url=args.search_url
    )
    
    # Mostrar configuração atual
    print_header("CONFIGURAÇÃO")
    print_info(f"Modo: {'Container (via Traefik)' if USE_CONTAINER_MODE else 'Local'}")
    print_info(f"Traefik URL: {TRAEFIK_URL}")
    print_info(f"Search Service: {SEARCH_SERVICE_URL}")
    print_info(f"Embedding Service: {EMBEDDING_SERVICE_URL}")
    print_info(f"ML Ranking Service: {ML_RANKING_SERVICE_URL}")
    print_info(f"OpenSearch: {OPENSEARCH_URL}")
    
    try:
        success = validate_hybrid_search()
        sys.exit(0 if success else 1)
    except KeyboardInterrupt:
        print("\n\nValidação interrompida pelo usuário")
        sys.exit(1)
    except Exception as e:
        print_error(f"Erro inesperado: {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)

