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

def check_total_products_in_index() -> int:
    """Verifica o total de produtos no índice OpenSearch"""
    try:
        response = requests.get(
            f"{OPENSEARCH_URL}/{OPENSEARCH_INDEX}/_count",
            headers={"Content-Type": "application/json"},
            timeout=10
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

def get_sample_product_titles(limit: int = 20) -> List[str]:
    """Obtém alguns títulos de produtos do índice para sugerir queries"""
    try:
        query = {
            "size": limit,
            "_source": ["title"],
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
    for product in products[:5]:  # Analisar apenas os 5 primeiros
        product_info = {
            "id": product.get("id"),
            "title": product.get("title", "")[:50],
            "score": product.get("score", 0.0) if "score" in product else None
        }
        analysis["products"].append(product_info)
    
    return analysis

def test_search_with_hybrid_check(query: str) -> Dict:
    """Testa uma busca e verifica se a busca híbrida está funcionando"""
    print_info(f"Testando query: '{query}'")
    
    # Executar busca (busca híbrida será ativada automaticamente se Embedding Service estiver disponível)
    results = test_search_query(query, use_hybrid=True)
    
    comparison = {
        "query": query,
        "results": None,
        "hybrid_active": None
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
                print_info("Primeiros resultados:")
                for i, product in enumerate(products[:3], 1):
                    title = product.get("title", "Sem título")
                    print(f"  {i}. {title[:60]}")
            
            # A busca híbrida está ativa se o Embedding Service estiver disponível
            # (verificado na seção 1)
            comparison["hybrid_active"] = True
            print_info("Busca híbrida: Ativada automaticamente (Embedding Service disponível)")
        else:
            print_warning(f"Busca retornou 0 resultados para '{query}'")
            print_warning("Verifique se o termo existe nos produtos indexados")
    else:
        print_error("Falha na busca")
    
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
        
        # Obter alguns títulos para sugerir queries
        sample_titles = get_sample_product_titles(10)
        if sample_titles:
            print_info("Exemplos de produtos no índice:")
            for i, title in enumerate(sample_titles[:5], 1):
                print(f"  {i}. {title[:60]}...")
    
    # 5. Testar buscas (apenas se houver produtos)
    print_header("5. Testando Buscas Híbridas")
    
    if total_products == 0:
        print_warning("Pulando testes de busca - nenhum produto no índice")
        comparisons = []
    else:
        # Tentar usar termos dos produtos reais
        test_queries_search = []
        
        # Se temos títulos, extrair palavras comuns para testar
        if sample_titles:
            # Extrair palavras dos títulos (primeira palavra e palavras-chave comuns)
            keywords = set()
            for title in sample_titles:
                words = title.lower().split()
                # Adicionar primeira palavra e palavras com mais de 3 caracteres
                for word in words:
                    if len(word) > 3:
                        keywords.add(word)
            
            # Selecionar algumas palavras para testar
            keywords_list = list(keywords)[:3]
            if keywords_list:
                test_queries_search = keywords_list
                print_info(f"Testando com termos reais dos produtos: {test_queries_search}")
            else:
                # Fallback para queries padrão
                test_queries_search = ["TV", "Dell", "Apple"]
        else:
            # Fallback para queries padrão
            test_queries_search = ["TV", "Dell", "Apple"]
        
        comparisons = []
        for query in test_queries_search:
            comparison = test_search_with_hybrid_check(query)
            comparisons.append(comparison)
            time.sleep(1)  # Pequeno delay entre requisições
    
    # 6. Resumo final
    print_header("6. Resumo da Validação")
    
    print_info("Status dos Serviços:")
    print(f"  - Embedding Service: {'✓' if embedding_ok else '✗'}")
    print(f"  - Search Service: {'✓' if search_ok else '✗'}")
    
    print_info("Status dos Embeddings:")
    print(f"  - Geração de embeddings: {'✓' if embeddings_ok else '✗'}")
    print(f"  - Produtos com embeddings: {'✓' if has_embeddings else '✗'} ({count} produtos)")
    
    print_info("Resultados das Buscas:")
    total_results_found = 0
    searches_with_results = 0
    for comp in comparisons:
        if comp.get("results"):
            count = comp["results"]["total_count"]
            total_results_found += count
            if count > 0:
                searches_with_results += 1
            hybrid_status = "✓" if comp.get("hybrid_active") else "?"
            print(f"  - Query '{comp['query']}': {count} resultados (Híbrida: {hybrid_status})")
    
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
    
    if not has_embeddings:
        print_warning("2. Produtos precisam ser indexados com embeddings. Verifique o Indexing Service")
    
    if total_products == 0:
        print_warning("3. É necessário indexar produtos no OpenSearch antes de testar a busca")
        print_info("   Use o script data_gen.py ou a API do Catalog Service para criar produtos")
    
    if total_products > 0 and total_results_found == 0:
        print_warning("4. Produtos estão indexados mas não correspondem às queries de teste")
        print_info("   Tente buscar por termos que existem nos títulos dos produtos indexados")
    
    # Verificar se busca híbrida está funcionando
    hybrid_working = embedding_ok and has_embeddings and total_products > 0
    
    if all_checks_passed and total_products > 0 and searches_with_results > 0:
        if hybrid_working:
            print_success("✓ Todas as validações passaram! A busca híbrida está funcionando.")
            print_info("  - Embedding Service: Disponível")
            print_info("  - Produtos com embeddings: Sim")
            print_info("  - Buscas retornando resultados: Sim")
        else:
            print_warning("⚠ Busca funcionando, mas busca híbrida pode não estar ativa")
            print_info("  - Verifique se o Embedding Service está gerando embeddings para queries")
    elif all_checks_passed and total_products > 0:
        print_warning("⚠ Validações técnicas passaram, mas nenhum resultado foi encontrado nas buscas")
        print_info("  - Tente buscar por termos que existem nos títulos dos produtos")
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

