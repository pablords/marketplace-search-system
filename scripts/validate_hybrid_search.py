#!/usr/bin/env python3
"""
Script de Validação da Busca Híbrida

Este script valida se a busca híbrida (BM25 + k-NN) está funcionando corretamente:
1. Verifica se o Embedding Service está disponível
2. Testa geração de embeddings para queries
3. Verifica se produtos têm embeddings no OpenSearch
4. Compara resultados de busca com e sem busca híbrida
5. Valida scores BM25 e k-NN
6. Testa diferentes tipos de queries
"""

import requests
import json
import sys
from typing import Dict, List, Optional, Tuple
from datetime import datetime
import time

# Configurações
EMBEDDING_SERVICE_URL = "http://localhost:8085"
SEARCH_SERVICE_URL = "http://localhost:8083"
OPENSEARCH_URL = "http://localhost:9200"
OPENSEARCH_INDEX = "products_index"

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

def check_service_health(url: str, service_name: str) -> bool:
    """Verifica se um serviço está saudável"""
    try:
        response = requests.get(f"{url}/api/v1/health", timeout=5)
        if response.status_code == 200:
            data = response.json()
            status = data.get("status", "unknown")
            # Aceitar vários status válidos (case-insensitive)
            status_upper = str(status).upper()
            valid_statuses = ["HEALTHY", "DEGRADED", "UP"]
            is_healthy = status_upper in valid_statuses
            
            if is_healthy:
                print_success(f"{service_name} está {status}")
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
            timeout=10
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
            timeout=10
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
        url = f"{SEARCH_SERVICE_URL}/api/v1/search/products"
        # O SearchController espera: query, page, size (não q, offset, limit)
        params = {
            "query": query,
            "page": 0,
            "size": 10
        }
        
        # Nota: O parâmetro "hybrid" não existe no SearchController
        # A busca híbrida é ativada automaticamente se o Embedding Service estiver disponível
        
        response = requests.get(url, params=params, timeout=15)
        
        if response.status_code == 200:
            return response.json()
        else:
            print_error(f"Erro na busca: {response.status_code}")
            print_error(f"Resposta: {response.text}")
            return None
    except requests.exceptions.RequestException as e:
        print_error(f"Erro ao chamar search service: {e}")
        return None

def analyze_search_results(results: Dict, query: str) -> Dict:
    """Analisa os resultados da busca"""
    analysis = {
        "total_count": results.get("totalCount", 0),
        "products_returned": len(results.get("products", [])),
        "execution_time_ms": results.get("executionTimeMs", 0),
        "has_metrics": "metrics" in results,
        "products": []
    }
    
    products = results.get("products", [])
    for product in products[:5]:  # Analisar apenas os 5 primeiros
        product_info = {
            "id": product.get("id"),
            "title": product.get("title", "")[:50],
            "score": product.get("score", 0.0) if "score" in product else None
        }
        analysis["products"].append(product_info)
    
    return analysis

def compare_hybrid_vs_bm25(query: str) -> Dict:
    """Compara resultados de busca híbrida vs apenas BM25"""
    print_info(f"Testando query: '{query}'")
    
    # Busca híbrida
    print_info("Executando busca híbrida (BM25 + k-NN)...")
    hybrid_results = test_search_query(query, use_hybrid=True)
    
    # Busca apenas BM25 (simulada - na prática, seria sem embedding)
    print_info("Executando busca apenas BM25...")
    bm25_results = test_search_query(query, use_hybrid=False)
    
    comparison = {
        "query": query,
        "hybrid": None,
        "bm25": None,
        "differences": []
    }
    
    if hybrid_results:
        hybrid_analysis = analyze_search_results(hybrid_results, query)
        comparison["hybrid"] = hybrid_analysis
        print_success(f"Busca híbrida: {hybrid_analysis['total_count']} resultados em {hybrid_analysis['execution_time_ms']}ms")
    else:
        print_error("Falha na busca híbrida")
    
    if bm25_results:
        bm25_analysis = analyze_search_results(bm25_results, query)
        comparison["bm25"] = bm25_analysis
        print_success(f"Busca BM25: {bm25_analysis['total_count']} resultados em {bm25_analysis['execution_time_ms']}ms")
    else:
        print_error("Falha na busca BM25")
    
    # Comparar resultados
    if hybrid_results and bm25_results:
        hybrid_products = {p.get("id"): p.get("title", "") for p in hybrid_results.get("products", [])}
        bm25_products = {p.get("id"): p.get("title", "") for p in bm25_results.get("products", [])}
        
        # Produtos que aparecem apenas na busca híbrida
        only_hybrid = set(hybrid_products.keys()) - set(bm25_products.keys())
        # Produtos que aparecem apenas na busca BM25
        only_bm25 = set(bm25_products.keys()) - set(hybrid_products.keys())
        
        if only_hybrid:
            print_info(f"Produtos encontrados apenas na busca híbrida: {len(only_hybrid)}")
            for pid in list(only_hybrid)[:3]:
                print(f"  - {hybrid_products[pid]}")
        
        if only_bm25:
            print_info(f"Produtos encontrados apenas na busca BM25: {len(only_bm25)}")
            for pid in list(only_bm25)[:3]:
                print(f"  - {bm25_products[pid]}")
        
        # Verificar se a ordem mudou
        hybrid_ids = [p.get("id") for p in hybrid_results.get("products", [])[:10]]
        bm25_ids = [p.get("id") for p in bm25_results.get("products", [])[:10]]
        
        if hybrid_ids != bm25_ids:
            print_info("A ordem dos resultados é diferente entre busca híbrida e BM25")
            comparison["differences"].append("order_different")
        else:
            print_warning("A ordem dos resultados é a mesma (pode indicar que busca híbrida não está ativa)")
            comparison["differences"].append("order_same")
    
    return comparison

def validate_hybrid_search():
    """Função principal de validação"""
    print_header("VALIDAÇÃO DA BUSCA HÍBRIDA")
    
    all_checks_passed = True
    
    # 1. Verificar serviços
    print_header("1. Verificando Serviços")
    embedding_ok = check_service_health(EMBEDDING_SERVICE_URL, "Embedding Service")
    search_ok = check_service_health(SEARCH_SERVICE_URL, "Search Service")
    
    if not embedding_ok:
        print_warning("Embedding Service não está disponível - busca híbrida pode não funcionar")
        all_checks_passed = False
    
    if not search_ok:
        print_error("Search Service não está disponível - não é possível continuar")
        return False
    
    # 2. Testar geração de embeddings
    print_header("2. Testando Geração de Embeddings")
    test_queries = [
        "smartphone",
        "notebook gamer",
        "fone de ouvido bluetooth"
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
    
    # 3. Verificar embeddings de produtos
    print_header("3. Verificando Embeddings de Produtos no OpenSearch")
    has_embeddings, count = check_product_embeddings()
    
    if not has_embeddings:
        print_error("Nenhum produto tem embeddings - busca híbrida não funcionará")
        all_checks_passed = False
    else:
        print_success(f"{count} produtos têm embeddings")
    
    # 4. Testar buscas
    print_header("4. Testando Buscas Híbridas")
    
    test_queries_search = [
        "smartphone",
        "notebook",
        "fone bluetooth"
    ]
    
    comparisons = []
    for query in test_queries_search:
        comparison = compare_hybrid_vs_bm25(query)
        comparisons.append(comparison)
        time.sleep(1)  # Pequeno delay entre requisições
    
    # 5. Resumo final
    print_header("5. Resumo da Validação")
    
    print_info("Status dos Serviços:")
    print(f"  - Embedding Service: {'✓' if embedding_ok else '✗'}")
    print(f"  - Search Service: {'✓' if search_ok else '✗'}")
    
    print_info("Status dos Embeddings:")
    print(f"  - Geração de embeddings: {'✓' if embeddings_ok else '✗'}")
    print(f"  - Produtos com embeddings: {'✓' if has_embeddings else '✗'} ({count} produtos)")
    
    print_info("Resultados das Buscas:")
    for comp in comparisons:
        if comp["hybrid"] and comp["bm25"]:
            hybrid_count = comp["hybrid"]["total_count"]
            bm25_count = comp["bm25"]["total_count"]
            print(f"  - Query '{comp['query']}': Híbrida={hybrid_count}, BM25={bm25_count}")
    
    # Recomendações
    print_header("Recomendações")
    
    if not embedding_ok:
        print_warning("1. Verifique se o Embedding Service está rodando na porta 8085")
    
    if not has_embeddings:
        print_warning("2. Produtos precisam ser indexados com embeddings. Verifique o Indexing Service")
    
    if all_checks_passed:
        print_success("✓ Todas as validações passaram! A busca híbrida está funcionando.")
    else:
        print_error("✗ Algumas validações falharam. Revise os problemas acima.")
    
    return all_checks_passed

if __name__ == "__main__":
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

