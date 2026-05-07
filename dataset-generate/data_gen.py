#!/usr/bin/env python3
"""
Script refatorado para geração de Dataset de E-commerce.
Garante consistência entre Categoria, Produto e Marca.
Suporta integração com datasets reais do Kaggle mantendo geração de métricas.
"""

import json
import random
import re
import hashlib
from faker import Faker
import requests
import time
from datetime import datetime, timedelta
import os
import yaml
from typing import Optional, List, Dict, Tuple
import pandas as pd
from concurrent.futures import ThreadPoolExecutor, as_completed
from threading import Lock

# Importar módulos de integração com dataset real
try:
    from dataset_loader import DatasetLoader
    from data_mapper import DataMapper
    DATASET_MODULES_AVAILABLE = True
except ImportError:
    DATASET_MODULES_AVAILABLE = False
    print("⚠️  Módulos dataset_loader e data_mapper não encontrados. Usando apenas geração fake.")



# Configuração do Faker
fake = Faker('pt_BR')

# ==========================================
# 1. DADOS MESTRES (Infraestrutura)
# ==========================================

SELLERS = [
    {"id": "TechStore", "name": "TechStore Brasil", "type": "PROFESSIONAL", "status": "ACTIVE", 
     "reputation": {"score": 4.9, "total_reviews": 2000, "cancellation_rate": 0.01, "delivery_performance": 0.99}},
    {"id": "ModaBrasil", "name": "Moda Brasil Online", "type": "PROFESSIONAL", "status": "ACTIVE",
     "reputation": {"score": 4.5, "total_reviews": 1200, "cancellation_rate": 0.03, "delivery_performance": 0.97}},
    {"id": "SportBr", "name": "Sport Center Brasil", "type": "PROFESSIONAL", "status": "ACTIVE",
     "reputation": {"score": 4.7, "total_reviews": 900, "cancellation_rate": 0.02, "delivery_performance": 0.98}},
    {"id": "CasaDecor", "name": "Casa & Decor Online", "type": "PROFESSIONAL", "status": "ACTIVE",
     "reputation": {"score": 4.6, "total_reviews": 1100, "cancellation_rate": 0.03, "delivery_performance": 0.97}},
    {"id": "BelezaBr", "name": "Beleza e Saúde Brasil", "type": "INDIVIDUAL", "status": "ACTIVE",
     "reputation": {"score": 4.3, "total_reviews": 700, "cancellation_rate": 0.05, "delivery_performance": 0.95}},
    {"id": "BebeFeliz", "name": "Bebê Feliz Shop", "type": "INDIVIDUAL", "status": "ACTIVE",
     "reputation": {"score": 4.8, "total_reviews": 500, "cancellation_rate": 0.02, "delivery_performance": 0.98}},
    {"id": "NutriShop", "name": "Nutri Shop Brasil", "type": "INDIVIDUAL", "status": "ACTIVE",
     "reputation": {"score": 4.2, "total_reviews": 400, "cancellation_rate": 0.05, "delivery_performance": 0.95}},
    {"id": "PetAmigo", "name": "Pet Amigo Shop", "type": "INDIVIDUAL", "status": "ACTIVE",
     "reputation": {"score": 4.6, "total_reviews": 600, "cancellation_rate": 0.02, "delivery_performance": 0.98}},
    {"id": "AutoShop", "name": "Auto Shop Online", "type": "PROFESSIONAL", "status": "ACTIVE",
     "reputation": {"score": 4.5, "total_reviews": 600, "cancellation_rate": 0.02, "delivery_performance": 0.98}},
    {"id": "CulturaBr", "name": "Cultura Brasil", "type": "INDIVIDUAL", "status": "ACTIVE",
     "reputation": {"score": 4.6, "total_reviews": 350, "cancellation_rate": 0.03, "delivery_performance": 0.97}},
    {"id": "FerroPro", "name": "Ferramentas Pro", "type": "PROFESSIONAL", "status": "ACTIVE",
     "reputation": {"score": 4.7, "total_reviews": 450, "cancellation_rate": 0.01, "delivery_performance": 0.99}},
    {"id": "Marketplace", "name": "Marketplace Brasil", "type": "PROFESSIONAL", "status": "ACTIVE",
     "reputation": {"score": 4.5, "total_reviews": 1000, "cancellation_rate": 0.02, "delivery_performance": 0.98}},
]

BRANDS_DB = {
    "Apple": {"id": "Apple", "name": "Apple", "description": "Inovação e design"},
    "Samsung": {"id": "Samsung", "name": "Samsung", "description": "Líder em Android"},
    "Dell": {"id": "Dell", "name": "Dell", "description": "Soluções corporativas"},
    "Nike": {"id": "Nike", "name": "Nike", "description": "Just do it"},
    "Adidas": {"id": "Adidas", "name": "Adidas", "description": "Performance esportiva"},
    # "brand_herman": {"id": "brand_herman", "name": "Herman Miller", "description": "Ergonomia premium"},
    # "brand_tokstok": {"id": "brand_tokstok", "name": "Tok&Stok", "description": "Design acessível"},
    # "brand_generic": {"id": "brand_generic", "name": "Genérica", "description": "Custo benefício"},
    # "brand_lg": {"id": "brand_lg", "name": "LG", "description": "Life's Good"},
    # "brand_sony": {"id": "brand_sony", "name": "Sony", "description": "Make Believe"},
}

# Cache para categorias carregadas (mantido para compatibilidade se data_mapper não estiver disponível)
_CATEGORIES_DB_CACHE = None

def get_categories_db(cache_dir: str = "./dataset-generate/data/cache") -> List[Dict]:
    """
    Retorna as categorias (carregadas do dataset ou padrão).
    Tenta usar a função do data_mapper se disponível para garantir consistência.
    """
    if DATASET_MODULES_AVAILABLE:
        try:
            from data_mapper import get_categories_db as get_mapper_categories
            return get_mapper_categories()
        except ImportError:
            pass
            
    # Fallback se data_mapper não estiver disponível ou falhar
    global _CATEGORIES_DB_CACHE
    if _CATEGORIES_DB_CACHE is not None:
        return _CATEGORIES_DB_CACHE

    # --- Tentativa 1: extrair do CSV principal (Amazon Brazil) ---
    brazil_csv = os.path.join(cache_dir, "amazon_products.csv")
    if os.path.exists(brazil_csv):
        try:
            # Ler apenas a coluna categoryName para economizar memória
            df = pd.read_csv(brazil_csv, usecols=["categoryName"], dtype=str)
            unique_names = df["categoryName"].dropna().unique()

            categories = []
            for name in unique_names:
                name = name.strip()
                if not name:
                    continue
                # ID determinístico via MD5
                cat_id = str(int(hashlib.md5(name.encode('utf-8')).hexdigest(), 16) % 100000)
                path_slug = re.sub(r'[^\w\s-]', '', name.lower())
                path_slug = re.sub(r'[-\s]+', '-', path_slug).strip('-')
                categories.append({
                    "id": cat_id,
                    "name": name,
                    "path": f"/{path_slug}",
                    "parent_id": None
                })

            if categories:
                print(f"✅ Carregadas {len(categories)} categorias do dataset Amazon Brazil")
                _CATEGORIES_DB_CACHE = categories
                return categories
        except Exception as e:
            print(f"⚠️  Erro ao extrair categorias do CSV Brazil: {e}")

    # Fallback final
    return [
        {"id": "1", "name": "Electronics", "path": "/electronics", "parent_id": None},
        {"id": "2", "name": "Computers", "path": "/computers", "parent_id": None},
    ]

# Carregar categorias do dataset dinamicamente
CATEGORIES_DB = get_categories_db(cache_dir="./dataset-generate/data/cache")

# ==========================================
# 2. REGRAS DE CATÁLOGO (Taxonomia)
# ==========================================
# Aqui definimos o que pode ser gerado dentro de cada categoria
CATALOG_RULES = {
    "Celulares": {
        "products": ["iPhone 13", "iPhone 14 Pro", "Galaxy S23", "Galaxy A54", "Redmi Note 12", "Moto G200"],
        "brands": ["Apple", "Samsung", "Dell", "Nike"],
        "metrics_seed": {"pop_min": 1000, "pop_max": 9000, "qual_min": 3.8, "qual_max": 4.9},
        "attrs_func": lambda: [
            f"Memória: {random.choice(['128GB', '256GB', '512GB'])}",
            f"Cor: {random.choice(['Preto', 'Branco', 'Dourado', 'Grafite'])}",
            f"Conectividade: {random.choice(['4G', '5G'])}",
            f"Tela: {random.choice(['6.1', '6.7'])} polegadas"
        ]
    },
    "Notebooks": {
        "products": ["MacBook Air M2", "MacBook Pro", "Dell XPS 13", "Dell Inspiron", "Samsung Galaxy Book", "Lenovo ThinkPad"],
        "brands": ["Dell", "Nike", "Apple"],
        "metrics_seed": {"pop_min": 500, "pop_max": 5000, "qual_min": 4.0, "qual_max": 4.9},
        "attrs_func": lambda: [
            f"Processador: {random.choice(['Intel i5', 'Intel i7', 'M2', 'M3'])}",
            f"RAM: {random.choice(['8GB', '16GB', '32GB'])}",
            f"SSD: {random.choice(['256GB', '512GB', '1TB'])}"
        ]
    },
    "TV e Áudio": {
        "products": ["Smart TV 4K", "TV OLED 55", "Soundbar", "Home Theater", "Smart TV 65"],
        "brands": ["Dell", "Nike", "Apple"],
        "metrics_seed": {"pop_min": 300, "pop_max": 4000, "qual_min": 4.2, "qual_max": 4.8},
        "attrs_func": lambda: [
            f"Resolução: {random.choice(['4K', '8K', 'Full HD'])}",
            f"Polegadas: {random.choice(['43', '50', '55', '65', '75'])}",
            f"Voltagem: {random.choice(['110v', '220v', 'Bivolt'])}"
        ]
    },
    "Móveis": {
        "products": ["Cadeira Gamer", "Mesa de Escritório", "Sofá 3 Lugares", "Estante de Livros", "Cama Box Casal"],
        "brands": ["Dell", "Nike", "Apple"],
        "metrics_seed": {"pop_min": 100, "pop_max": 2000, "qual_min": 3.5, "qual_max": 4.7},
        "attrs_func": lambda: [
            f"Material: {random.choice(['Madeira Maciça', 'MDF', 'Aço', 'Couro Sintético'])}",
            f"Cor: {random.choice(['Preto', 'Branco', 'Marrom', 'Cinza'])}",
            "Necessita montagem: Sim"
        ]
    },
    "Decoração": {
        "products": ["Luminária de Mesa", "Quadro Decorativo", "Tapete Sala", "Vaso de Cerâmica", "Espelho Redondo"],
        "brands": ["Dell", "Nike", "Apple"],
        "metrics_seed": {"pop_min": 50, "pop_max": 1500, "qual_min": 3.9, "qual_max": 4.6},
        "attrs_func": lambda: [
            f"Estilo: {random.choice(['Industrial', 'Clássico', 'Moderno', 'Rústico'])}",
            f"Dimensões: {random.randint(20, 100)}x{random.randint(20, 100)}cm"
        ]
    },
    "Vestuário": {
        "products": ["Camiseta Básica", "Tênis de Corrida", "Calça Jeans", "Jaqueta Corta-Vento", "Moletom"],
        "brands": ["Dell", "Nike", "Apple"],
        "metrics_seed": {"pop_min": 800, "pop_max": 8000, "qual_min": 3.0, "qual_max": 4.5},
        "attrs_func": lambda: [
            f"Tamanho: {random.choice(['P', 'M', 'G', 'GG'])}",
            f"Gênero: {random.choice(['Masculino', 'Feminino', 'Unissex'])}",
            f"Material: {random.choice(['Algodão', 'Poliéster', 'Elastano'])}"
        ]
    }
}

# ==========================================
# 3. FUNÇÕES GERADORAS
# ==========================================

def gnerate_date(data_inicio, data_fim):
    """
    Gera uma data aleatória entre data_inicio e data_fim.
    """
    # Calcula a diferença total de dias entre as datas
    diferenca = data_fim - data_inicio
    dias_aleatorios = random.randrange(diferenca.days)
    
    # Adiciona um número aleatório de dias à data de início
    data_aleatoria = data_inicio + timedelta(days=dias_aleatorios)
    return data_aleatoria

def generate_quality_metrics(rule_metrics):
    """Gera métricas de popularidade consistentes com a categoria"""
    popularity = random.randint(rule_metrics["pop_min"], rule_metrics["pop_max"])
    quality = round(random.uniform(rule_metrics["qual_min"], rule_metrics["qual_max"]), 1)
    total_views = popularity * random.randint(5, 15)
    total_sales = int(popularity * (quality / 5.0) * random.uniform(0.5, 1.5))
    total_reviews = int(total_sales * random.uniform(0.05, 0.15))
    
    # Correlacionar average_rating com a qualidade do produto
    # Produtos de alta qualidade (4.5-5.0) devem ter ratings altos (4.0-5.0)
    # Produtos de baixa qualidade (3.0-3.5) devem ter ratings mais baixos (2.5-4.0)
    quality_ratio = (quality - rule_metrics["qual_min"]) / (rule_metrics["qual_max"] - rule_metrics["qual_min"])
    rating_min = 2.5 + (quality_ratio * 1.5)  # Entre 2.5 e 4.0
    rating_max = 3.5 + (quality_ratio * 1.5)   # Entre 3.5 e 5.0
    average_rating = round(random.uniform(rating_min, rating_max), 2)
    average_rating = min(5.0, max(0.0, average_rating))  # Garantir entre 0 e 5
    
    last_sale = gnerate_date(datetime(2025, 1, 1), datetime(2025, 12, 31)).isoformat() + "Z"
    last_view = gnerate_date(datetime(2025, 1, 1), datetime(2025, 12, 31)).isoformat() + "Z"

    
    # Cálculo de CTR correlacionado com a qualidade
    # Um produto 5 estrelas tem mais chance de clique que um de 3 estrelas
    base_ctr = 0.03 + (quality - 3.0) * 0.04 
    ctr = base_ctr * random.uniform(0.8, 1.2) # Variação de 20%
    ctr = round(min(0.25, max(0.01, ctr)), 4)
    
    return {
        "popularity": popularity,
        "quality": quality,
        "ctr": ctr,
        "total_views": total_views,
        "total_sales": total_sales,
        "total_reviews": total_reviews,
        "average_rating": average_rating,
        "stock_quantity": 0,
        "last_sale": last_sale,
        "last_view": last_view
    }

def enrich_real_product(real_product_data: dict, category_id: Optional[str] = None) -> dict:
    """
    Enriquece um produto real do dataset com métricas geradas.
    Combina dados reais (title, price, description, etc) com métricas sintéticas.
    
    Esta função:
    1. Usa dados reais do dataset (title, price, description, etc)
    2. Gera métricas usando generate_quality_metrics()
    3. Atribui vendedor aleatório dos SELLERS existentes
    4. Garante formato compatível com API (todos os campos obrigatórios)
    
    Args:
        real_product_data: Dicionário com dados do produto real (vindo do DataMapper)
        category_id: ID da categoria (opcional, será extraído do real_product_data se não fornecido)
        
    Returns:
        Dicionário completo do produto enriquecido com métricas, pronto para envio à API
        
    Raises:
        ValueError: Se real_product_data for None ou inválido
    """
    # Validação inicial
    if not real_product_data or not isinstance(real_product_data, dict):
        raise ValueError("real_product_data deve ser um dicionário não vazio")
    
    try:
        # 1. Usar a categoria real do produto (vinda do data_mapper via categoryName)
        real_category = real_product_data.get("category", {})
        real_category_name = real_category.get("name", "") if isinstance(real_category, dict) else ""

        # 2. Tentar encontrar um metrics_seed compatível em CATALOG_RULES
        #    Faz match parcial por nome (ex: "Smartphones" → "Celulares")
        #    Se não encontrar, usa seed genérico — mas NUNCA substitui a categoria real.
        _RULES_ALIAS = {
            "smartphone": "Celulares",
            "celular": "Celulares",
            "phone": "Celulares",
            "notebook": "Notebooks",
            "laptop": "Notebooks",
            "computador": "Notebooks",
            "tv": "TV e Áudio",
            "televisão": "TV e Áudio",
            "televisao": "TV e Áudio",
            "áudio": "TV e Áudio",
            "audio": "TV e Áudio",
            "móvel": "Móveis",
            "movel": "Móveis",
            "furniture": "Móveis",
            "decoração": "Decoração",
            "decor": "Decoração",
            "roupa": "Vestuário",
            "vestuario": "Vestuário",
            "clothing": "Vestuário",
            "fashion": "Vestuário",
        }

        matched_rule_key = None
        name_lower = real_category_name.lower()
        for alias, rule_key in _RULES_ALIAS.items():
            if alias in name_lower:
                matched_rule_key = rule_key
                break
        # Fallback direto por nome exato
        if matched_rule_key is None and real_category_name in CATALOG_RULES:
            matched_rule_key = real_category_name

        if matched_rule_key:
            metrics_seed = CATALOG_RULES[matched_rule_key].get(
                "metrics_seed", {"pop_min": 100, "pop_max": 5000, "qual_min": 3.5, "qual_max": 4.8}
            )
        else:
            # Seed genérico — aplica para qualquer categoria do Amazon Brazil
            metrics_seed = {"pop_min": 100, "pop_max": 5000, "qual_min": 3.5, "qual_max": 4.8}

        
        # 3. Gerar métricas de qualidade usando a função existente
        try:
            quality_metrics = generate_quality_metrics(metrics_seed)
        except Exception as e:
            # Fallback para métricas padrão se houver erro
            print(f"⚠️  Erro ao gerar métricas de qualidade: {e}. Usando valores padrão.")
            quality_metrics = {
                "popularity": 1000,
                "quality": 4.0,
                "ctr": 0.05,
                "total_views": 5000,
                "total_sales": 200,
                "total_reviews": 30,
                "average_rating": 4.0,
                "stock_quantity": 0,
                "last_sale": datetime.now().isoformat() + "Z",
                "last_view": datetime.now().isoformat() + "Z"
            }
        
        # 4. Atribuir vendedor aleatório dos SELLERS existentes
        # (substitui o seller que pode ter vindo do mapper para garantir consistência)
        if not SELLERS:
            raise ValueError("Lista de vendedores (SELLERS) está vazia")
        
        seller = random.choice(SELLERS).copy()
        
        # Ajustar reviews do seller baseado na qualidade do produto
        # Produtos de alta qualidade tendem a ter mais reviews positivas
        try:
            base_total_reviews = int(seller["reputation"]["total_reviews"] * random.uniform(0.8, 1.2))
            quality_factor = quality_metrics.get("quality", 4.0) / 5.0
            total_reviews = max(0, int(base_total_reviews * (0.5 + quality_factor * 0.5)))
            
            # Distribuir reviews proporcionalmente baseado na qualidade
            positive_ratio = 0.5 + (quality_factor * 0.3)  # Entre 0.5 e 0.8
            neutral_ratio = 0.3 - (quality_factor * 0.15)  # Entre 0.15 e 0.3
            
            positive_reviews = max(0, int(total_reviews * positive_ratio))
            neutral_reviews = max(0, int(total_reviews * neutral_ratio))
            negative_reviews = max(0, total_reviews - positive_reviews - neutral_reviews)
            
            seller["reputation"] = {
                **seller.get("reputation", {}),
                "total_reviews": total_reviews,
                "positive_reviews": positive_reviews,
                "neutral_reviews": neutral_reviews,
                "negative_reviews": negative_reviews,
            }
        except (KeyError, TypeError, ValueError) as e:
            print(f"⚠️  Erro ao ajustar reviews do seller: {e}. Usando valores padrão.")
            # Manter seller original se houver erro
        
        # 5. Atualizar quantidade disponível nas métricas
        try:
            available_quantity = real_product_data.get("available_quantity")
            if available_quantity is None or not isinstance(available_quantity, (int, float)):
                available_quantity = random.randint(0, 500)
            else:
                available_quantity = max(0, int(available_quantity))
        except (ValueError, TypeError):
            available_quantity = random.randint(0, 500)
        
        quality_metrics["stock_quantity"] = available_quantity
        
        # 6. Garantir que o produto tenha todos os campos obrigatórios da API
        # Campos obrigatórios: id, title, price, currency, category, brand, seller
        enriched_product = {
            **real_product_data,  # Preserva todos os dados reais
            "seller": seller,  # Substitui seller para garantir consistência com métricas
            "metrics": quality_metrics,  # Adiciona métricas geradas
            "available_quantity": available_quantity,  # Garante que está presente
        }
        
        # Validar campos obrigatórios
        if not enriched_product.get("id"):
            # Fallback: gerar ID genérico se não houver ID do dataset
            enriched_product["id"] = f"PROD{int(datetime.now().timestamp() * 1000)}"
        
        if not enriched_product.get("title"):
            enriched_product["title"] = "Produto sem título"
        
        if not enriched_product.get("price") or enriched_product.get("price", 0) <= 0:
            enriched_product["price"] = round(random.uniform(50.0, 5000.0), 2)
        
        # 7. Garantir campos opcionais com valores padrão se necessário
        if enriched_product.get("description") is None or enriched_product.get("description") == "":
            enriched_product["description"] = fake.text(max_nb_chars=300)
        
        # Garantir que currency esteja presente
        if not enriched_product.get("currency"):
            enriched_product["currency"] = "BRL"
        
        # Garantir que is_active esteja presente
        if enriched_product.get("is_active") is None:
            enriched_product["is_active"] = True
        
        # Garantir que category seja um dict válido
        if not isinstance(enriched_product.get("category"), dict):
            category = CATEGORIES_DB[0]
            enriched_product["category"] = {
                "id": category["id"],
                "name": category["name"],
                "path": category["path"],
                "parent_id": category.get("parent_id")
            }
        
        # Garantir que brand seja um dict válido
        if not isinstance(enriched_product.get("brand"), dict):
            brand = BRANDS_DB["Apple"]
            enriched_product["brand"] = {
                "id": brand["id"],
                "name": brand["name"],
                "description": brand.get("description", "")
            }
        
        # Garantir que images seja uma lista (mesmo que vazia)
        if not isinstance(enriched_product.get("images"), list):
            category = enriched_product.get("category", {})
            cat_id = category.get("id", "Celulares") if isinstance(category, dict) else "Celulares"
            enriched_product["images"] = [f"https://marketplace.com/img/{cat_id}_{random.randint(1,9)}.jpg" 
                                         for _ in range(3)]
        
        # Garantir que attributes seja uma lista
        if not isinstance(enriched_product.get("attributes"), list):
            enriched_product["attributes"] = ["Produto original"]
        
        # Garantir que tags seja uma lista
        if not isinstance(enriched_product.get("tags"), list):
            title = enriched_product.get("title", "")
            if title:
                # Extrair algumas palavras do título como tags
                words = [w.lower() for w in title.split() if len(w) > 3][:4]
                enriched_product["tags"] = words if words else ["produto"]
            else:
                enriched_product["tags"] = ["produto"]
        
        # Garantir que condition esteja presente
        if not enriched_product.get("condition"):
            enriched_product["condition"] = "NEW"
        
        return enriched_product
        
    except Exception as e:
        # Se houver qualquer erro crítico, relançar com contexto
        error_msg = f"Erro ao enriquecer produto: {e}"
        print(f"❌ {error_msg}")
        raise ValueError(error_msg) from e


def analyze_product_source(product: dict) -> str:
    """
    Analisa um produto e determina se parece ser real ou fake baseado em heurísticas.
    
    Indicadores de dados fake:
    - Títulos com padrões como " - Novo", " - Promoção", " - Edição Limitada"
    - Títulos muito genéricos como "Mesa de Escritório Apple"
    - Descrições com texto Lorem Ipsum do Faker
    
    Indicadores de dados reais:
    - Títulos variados e específicos
    - Descrições detalhadas e únicas
    - Preços variados e realistas
    
    Args:
        product: Dicionário com dados do produto
        
    Returns:
        "REAL" ou "FAKE" baseado em heurísticas
    """
    if not product or not isinstance(product, dict):
        return "FAKE"
    
    title = str(product.get("title", "")).strip()
    description = str(product.get("description", "")).strip()
    
    # Padrões que indicam dados fake
    fake_patterns = [
        " - Novo",
        " - Promoção",
        " - Edição Limitada",
        " - Original",
        "Mesa de Escritório",
        "Cadeira Gamer",
        "Camiseta Básica",
    ]
    
    # Verificar padrões de título fake
    for pattern in fake_patterns:
        if pattern in title:
            return "FAKE"
    
    # Verificar se título é muito genérico (menos de 3 palavras ou muito curto)
    title_words = title.split()
    if len(title_words) < 3 or len(title) < 15:
        # Pode ser fake, mas não é definitivo
        pass
    
    # Verificar descrição - Faker gera texto específico em português
    # Textos do Faker geralmente têm padrões como "Praesentium", "Blanditiis", etc.
    faker_keywords = [
        "praesentium",
        "blanditiis",
        "necessitatibus",
        "reprehenderit",
        "voluptate",
        "cupiditate",
    ]
    
    description_lower = description.lower()
    for keyword in faker_keywords:
        if keyword in description_lower:
            return "FAKE"
    
    # Se passou pelas verificações, provavelmente é real
    # Mas se não tem descrição ou título muito curto, pode ser fake
    if not description or len(description) < 50:
        # Sem descrição detalhada, mas pode ser real se título for bom
        if len(title) > 20 and len(title_words) >= 4:
            return "REAL"
        return "FAKE"
    
    # Título e descrição parecem realistas
    return "REAL"


def check_dataset_cache(config: dict) -> Tuple[bool, str]:
    """
    Verifica se o dataset está no cache e retorna informações.
    
    Args:
        config: Dicionário com configurações do dataset
        
    Returns:
        (exists: bool, cache_path: str)
    """
    if not config or "dataset" not in config:
        return False, ""
    
    dataset_config = config.get("dataset", {})
    cache_dir = dataset_config.get("cache_dir", "./data/cache")
    dataset_name = dataset_config.get("name", "")
    
    if not dataset_name:
        return False, ""
    
    # Extrair nome do dataset (última parte após /)
    dataset_slug = dataset_name.split('/')[-1] if '/' in dataset_name else dataset_name
    
    # Verificar se existe no cache
    cache_path = os.path.join(cache_dir, dataset_slug)
    
    if os.path.exists(cache_path):
        # Verificar se tem arquivos dentro
        if os.path.isdir(cache_path):
            files = [f for f in os.listdir(cache_path) if f.endswith(('.csv', '.json'))]
            if files:
                return True, os.path.join(cache_path, files[0])
        elif os.path.isfile(cache_path):
            return True, cache_path
    
    return False, cache_path


def validate_dataset_usage(dataset_df: Optional[pd.DataFrame], 
                          mapper: Optional[DataMapper],
                          config: Optional[dict] = None) -> dict:
    """
    Valida se o dataset real está sendo usado e retorna estatísticas.
    
    Args:
        dataset_df: DataFrame do pandas com o dataset
        mapper: Instância do DataMapper
        config: Configurações do dataset (opcional)
        
    Returns:
        dict com informações de validação:
        - is_using_real_dataset: bool
        - dataset_size: int
        - sample_titles: List[str]
        - validation_checks: dict
        - cache_info: dict
    """
    result = {
        "is_using_real_dataset": False,
        "dataset_size": 0,
        "sample_titles": [],
        "validation_checks": {},
        "cache_info": {},
        "columns": [],
    }
    
    # Verificar se dataset foi carregado
    if dataset_df is None or dataset_df.empty:
        result["validation_checks"]["dataset_loaded"] = False
        result["validation_checks"]["error"] = "Dataset não carregado ou vazio"
        return result
    
    result["is_using_real_dataset"] = True
    result["dataset_size"] = len(dataset_df)
    result["columns"] = list(dataset_df.columns)
    
    # Verificar cache
    if config:
        cache_exists, cache_path = check_dataset_cache(config)
        result["cache_info"] = {
            "exists": cache_exists,
            "path": cache_path,
        }
    
    # Verificar mapper
    result["validation_checks"]["mapper_initialized"] = mapper is not None
    
    # Extrair amostra de títulos
    if "title" in dataset_df.columns:
        # Pegar até 5 títulos únicos como amostra
        titles = dataset_df["title"].dropna().unique()[:5]
        result["sample_titles"] = [str(t) for t in titles]
    else:
        result["validation_checks"]["has_title_column"] = False
    
    # Verificar se tem colunas obrigatórias
    required_cols = ["title", "price"]
    missing_cols = [col for col in required_cols if col not in dataset_df.columns]
    result["validation_checks"]["has_required_columns"] = len(missing_cols) == 0
    if missing_cols:
        result["validation_checks"]["missing_columns"] = missing_cols
    
    # Verificar variação de preços (indicador de dados reais)
    if "price" in dataset_df.columns:
        try:
            prices = pd.to_numeric(dataset_df["price"], errors='coerce').dropna()
            if len(prices) > 0:
                price_std = prices.std()
                price_mean = prices.mean()
                result["validation_checks"]["price_variation"] = {
                    "std": float(price_std),
                    "mean": float(price_mean),
                    "has_variation": price_std > 0 and price_std / price_mean > 0.1,  # Pelo menos 10% de variação
                }
        except Exception as e:
            result["validation_checks"]["price_validation_error"] = str(e)
    
    # Verificar se títulos parecem realistas (não seguem padrões fake)
    if "title" in dataset_df.columns:
        titles_sample = dataset_df["title"].dropna().head(20)
        fake_count = 0
        for title in titles_sample:
            # Criar produto mock para análise
            mock_product = {"title": str(title), "description": ""}
            if analyze_product_source(mock_product) == "FAKE":
                fake_count += 1
        
        result["validation_checks"]["titles_analysis"] = {
            "sample_size": len(titles_sample),
            "fake_detected": fake_count,
            "real_detected": len(titles_sample) - fake_count,
            "fake_ratio": fake_count / len(titles_sample) if len(titles_sample) > 0 else 0,
        }
    
    result["validation_checks"]["dataset_loaded"] = True
    
    return result


def generate_product_payload():
    """Gera um único produto validado pelas regras de catálogo (modo fake)"""
    
    # 1. Escolher uma categoria alvo que tenha regras definidas
    target_cat_id = random.choice(list(CATALOG_RULES.keys()))
    rule = CATALOG_RULES[target_cat_id]
    
    # 2. Recuperar o objeto categoria completo do DB
    category_obj = next((c for c in CATEGORIES_DB if c["id"] == target_cat_id), None)
    if category_obj is None:
        raise ValueError(f"Categoria {target_cat_id} não encontrada em CATEGORIES_DB. "
                        f"Categorias disponíveis: {[c['id'] for c in CATEGORIES_DB]}")
    
    # 3. Escolher Produto e Marca compatíveis
    product_name_base = random.choice(rule["products"])
    brand_id = random.choice(rule["brands"])
    brand_obj = BRANDS_DB[brand_id]
    
    # 4. Compor Título (Variação para parecer real)
    if brand_obj["name"] in product_name_base:
        title = product_name_base # Ex: "MacBook Air" já implica Apple
    else:
        # 50% chance de "Marca + Produto" ou "Produto + Marca"
        if random.random() > 0.5:
            title = f"{product_name_base} {brand_obj['name']}"
        else:
            title = f"{brand_obj['name']} {product_name_base}"
            
    # Adicionar sufixo aleatório para unicidade (Ex: " - Preto", " - 2023")
    suffix = random.choice(["", " - Edição Limitada", " - Novo", " - Promoção", " Original"])
    title += suffix

    # 5. Gerar Atributos Específicos
    attributes = rule["attrs_func"]()
    # Adicionar atributos comuns
    attributes.append("Produto original")
    if "used" not in suffix.lower():
        attributes.append("Garantia de fábrica")

    # 6. Preço
    price = round(random.uniform(50.0, 5000.0), 2)
    
    # 7. Métricas de Qualidade (LTR Features)
    quality_metrics = generate_quality_metrics(rule["metrics_seed"])
    
    # 8. Vendedor - Gerar reviews de forma consistente
    seller = random.choice(SELLERS)
    
    # Primeiro definir o total de reviews baseado na qualidade do produto e reputação do seller
    # Sellers com melhor reputação tendem a ter mais reviews
    base_total_reviews = int(seller["reputation"]["total_reviews"] * random.uniform(0.8, 1.2))
    # Ajustar baseado na qualidade do produto (produtos melhores geram mais reviews)
    quality_factor = quality_metrics["quality"] / 5.0
    total_reviews = max(0, int(base_total_reviews * (0.5 + quality_factor * 0.5)))
    
    # Distribuir proporcionalmente baseado na qualidade do produto
    # Produtos de alta qualidade: ~80% positivo, ~15% neutro, ~5% negativo
    # Produtos de baixa qualidade: ~50% positivo, ~30% neutro, ~20% negativo
    positive_ratio = 0.5 + (quality_factor * 0.3)  # Entre 0.5 e 0.8
    neutral_ratio = 0.3 - (quality_factor * 0.15)    # Entre 0.15 e 0.3
    
    # Garantir que a soma seja exatamente total_reviews
    positive_reviews = int(total_reviews * positive_ratio)
    neutral_reviews = int(total_reviews * neutral_ratio)
    # Calcular negative_reviews como o restante para garantir soma exata
    negative_reviews = total_reviews - positive_reviews - neutral_reviews
    
    # Garantir valores não negativos
    positive_reviews = max(0, positive_reviews)
    neutral_reviews = max(0, neutral_reviews)
    negative_reviews = max(0, negative_reviews)
    
    seller = {
        **seller,
        "reputation": {
            **seller["reputation"],
            "total_reviews": total_reviews,
            "positive_reviews": positive_reviews,
            "neutral_reviews": neutral_reviews,
            "negative_reviews": negative_reviews,
        }
    }

    available_quantity = random.randint(0, 500)
    quality_metrics["stock_quantity"] = available_quantity
    
    # Montagem do JSON Final
    product = {
        "id": f"PROD{int(datetime.now().timestamp() * 1000)}",
        "title": title,
        "description": fake.text(max_nb_chars=300),
        "price": price,
        "currency": "BRL",
        "available_quantity": available_quantity, # 0 permite testar lógica de out_of_stock
        "condition": random.choice(["NEW", "NEW", "NEW", "USED"]), # Peso maior para NEW
        "is_active": True,
        
        "category": {
            "id": category_obj["id"],
            "name": category_obj["name"],
            "path": category_obj["path"],
            "parent_id": category_obj.get("parent_id")
        },
        
        "brand": {
            "id": brand_obj["id"],
            "name": brand_obj["name"],
            "description": brand_obj["description"]
        },
        
        "seller": seller,
        
        "images": [f"https://marketplace.com/img/{target_cat_id}_{random.randint(1,9)}.jpg" for _ in range(3)],
        "attributes": attributes,
        "tags": [fake.word() for _ in range(4)],
        
        # Objeto de qualidade (Fundamental para seu LTR)
        "metrics": quality_metrics
    }
    
    return product

def create_dataset_file(total_products=100):
    dataset = []
    
    print(f"🔄 Gerando {total_products} produtos consistentes...")
    
    for _ in range(total_products):
        dataset.append(generate_product_payload())
        
    output_file = "data/products.json"
    
    # Garantir diretório (opcional, simples aqui)
    import os
    os.makedirs("data", exist_ok=True)
    
    with open(output_file, 'w', encoding='utf-8') as f:
        json.dump(dataset, f, ensure_ascii=False, indent=2)
        
    print(f"✅ Sucesso! Arquivo salvo em: {output_file}")
    
    # Preview de um item para validação visual
    print("\n🔎 Exemplo de Item Gerado:")
    print(json.dumps(dataset[0], ensure_ascii=False, indent=2))


def load_dataset_config(config_path: str = "scripts/dataset_config.yaml") -> dict:
    """
    Carrega configuração do dataset do arquivo YAML.
    
    Args:
        config_path: Caminho para o arquivo de configuração
        
    Returns:
        Dicionário com configurações ou None se não encontrar
    """
    try:
        if os.path.exists(config_path):
            with open(config_path, 'r', encoding='utf-8') as f:
                return yaml.safe_load(f)
        else:
            # Tentar caminho relativo
            alt_path = os.path.join(os.path.dirname(__file__), "dataset_config.yaml")
            if os.path.exists(alt_path):
                with open(alt_path, 'r', encoding='utf-8') as f:
                    return yaml.safe_load(f)
    except Exception as e:
        print(f"⚠️  Erro ao carregar configuração: {e}")
    return None


def generate_products_json(output_file: str, total_products=10, dataset_df: Optional[pd.DataFrame] = None, 
                           use_real_dataset: bool = False, config_path: Optional[str] = None,
                           force_fake: bool = False, validate_dataset: bool = False):
    """
    Gera uma amostra de produtos em formato JSON para validação, sem chamar a API.
    
    Args:
        output_file: Caminho do arquivo JSON de saída
        total_products: Número total de produtos a gerar
        dataset_df: DataFrame do pandas com produtos reais (opcional)
        use_real_dataset: Se True, tenta carregar dataset real automaticamente
        config_path: Caminho para arquivo de configuração (opcional)
        force_fake: Se True, força uso de geração fake mesmo se dataset estiver disponível
        validate_dataset: Se True, força validação detalhada do dataset
    """
    # Inicializar mapper se usar dataset real
    mapper = None
    dataset_loaded = False
    error_messages = []
    config = None
    
    # Log inicial de diagnóstico
    print("\n" + "="*60)
    print("🔍 DIAGNÓSTICO DE CARREGAMENTO DE DATASET")
    print("="*60)
    print(f"📋 force_fake: {force_fake}")
    print(f"📋 use_real_dataset: {use_real_dataset}")
    print(f"📋 dataset_df fornecido: {dataset_df is not None}")
    print(f"📋 DATASET_MODULES_AVAILABLE: {DATASET_MODULES_AVAILABLE}")
    print("="*60 + "\n")
    
    # Se force_fake estiver ativo, pular carregamento de dataset
    if not force_fake and (use_real_dataset or dataset_df is not None) and DATASET_MODULES_AVAILABLE:
        try:
            print("📂 Carregando configuração do dataset...")
            config = load_dataset_config(config_path) if config_path else load_dataset_config()
            
            if config is None:
                print("⚠️  Configuração não encontrada. Tentando usar valores padrão...")
            else:
                print("✅ Configuração carregada com sucesso")
                if "dataset" in config:
                    print(f"   Dataset configurado: {config['dataset'].get('name', 'N/A')}")
            
            category_mapping = None
            brand_mapping = None
            
            if config and "mapping" in config:
                category_mapping = config["mapping"].get("category_mapping", {})
                brand_mapping = config["mapping"].get("brand_mapping", {})
                print(f"✅ Mapeamentos carregados: {len(category_mapping)} categorias, {len(brand_mapping)} marcas")
            
            print("🔧 Inicializando DataMapper...")
            mapper = DataMapper(category_mapping=category_mapping, brand_mapping=brand_mapping)
            print("✅ DataMapper inicializado com sucesso")
            
            # Carregar dataset se não foi fornecido
            if dataset_df is None and use_real_dataset:
                try:
                    loader = DatasetLoader()
                    if config and "dataset" in config:
                        dataset_name = config["dataset"].get("name")
                        cache_dir = config["dataset"].get("cache_dir", "./data/cache")
                        
                        if dataset_name:
                            print(f"📥 Tentando carregar dataset: {dataset_name}")
                            print(f"📁 Diretório de cache: {cache_dir}")
                            
                            # Tentar carregar do cache primeiro
                            dataset_slug = dataset_name.split('/')[-1] if '/' in dataset_name else dataset_name
                            dataset_path = os.path.join(cache_dir, dataset_slug)
                            
                            # Também verificar se há arquivos CSV/JSON diretamente no cache_dir
                            cache_path_found = None
                            if os.path.exists(dataset_path):
                                cache_path_found = dataset_path
                            else:
                                # Verificar se há arquivos CSV/JSON diretamente no cache_dir
                                import glob
                                csv_files = glob.glob(os.path.join(cache_dir, "*.csv"))
                                json_files = glob.glob(os.path.join(cache_dir, "*.json"))
                                
                                if csv_files:
                                    cache_path_found = csv_files[0]  # Usar o primeiro CSV encontrado
                                    print(f"🔍 Arquivo CSV encontrado no cache: {cache_path_found}")
                                elif json_files:
                                    cache_path_found = json_files[0]  # Usar o primeiro JSON encontrado
                                    print(f"🔍 Arquivo JSON encontrado no cache: {cache_path_found}")
                            
                            if cache_path_found and os.path.exists(cache_path_found):
                                print(f"✅ Dataset encontrado no cache: {cache_path_found}")
                                try:
                                    dataset_df = loader.load_dataset(cache_path_found)
                                    print(f"✅ Dataset carregado: {len(dataset_df)} registros")
                                    loader.validate_dataset(dataset_df)
                                    print("✅ Dataset validado com sucesso")
                                    dataset_loaded = True
                                except Exception as e:
                                    error_msg = f"Erro ao carregar dataset do cache: {e}"
                                    error_messages.append(error_msg)
                                    print(f"❌ {error_msg}")
                                    import traceback
                                    traceback.print_exc()
                            else:
                                print(f"⚠️  Dataset não encontrado no cache: {dataset_path}")
                                # Tentar baixar do Kaggle
                                print(f"📥 Tentando baixar dataset do Kaggle...")
                                try:
                                    dataset_df = loader.download_and_load(dataset_name, validate=True)
                                    print(f"✅ Dataset baixado e carregado: {len(dataset_df)} registros")
                                    dataset_loaded = True
                                except ImportError as ie:
                                    error_msg = f"Pacote kaggle não instalado: {ie}"
                                    error_messages.append(error_msg)
                                    print(f"❌ {error_msg}")
                                    print("💡 Instale com: pip install kaggle")
                                    print("💡 Configure credenciais: https://github.com/Kaggle/kaggle-api#api-credentials")
                                except FileNotFoundError as fnf:
                                    error_msg = f"Dataset não encontrado: {fnf}"
                                    error_messages.append(error_msg)
                                    print(f"❌ {error_msg}")
                                except Exception as e:
                                    error_msg = f"Erro ao baixar dataset do Kaggle: {e}"
                                    error_messages.append(error_msg)
                                    print(f"❌ {error_msg}")
                                    import traceback
                                    traceback.print_exc()
                            
                            if dataset_loaded and dataset_df is not None:
                                max_products = config["dataset"].get("max_products", total_products)
                                if len(dataset_df) > max_products:
                                    print(f"📊 Limitando dataset de {len(dataset_df)} para {max_products} produtos")
                                    dataset_df = dataset_df.head(max_products)
                                    print(f"✅ Dataset limitado: {len(dataset_df)} produtos")
                        else:
                            error_msg = "Nome do dataset não configurado no arquivo de configuração"
                            error_messages.append(error_msg)
                            print(f"⚠️  {error_msg}")
                    else:
                        error_msg = "Configuração de dataset não encontrada no arquivo de configuração"
                        error_messages.append(error_msg)
                        print(f"⚠️  {error_msg}")
                except Exception as e:
                    error_msg = f"Erro ao inicializar loader: {e}"
                    error_messages.append(error_msg)
                    print(f"❌ {error_msg}")
                    import traceback
                    traceback.print_exc()
                    dataset_df = None
                    mapper = None
        except Exception as e:
            error_msg = f"Erro ao carregar configuração ou inicializar mapper: {e}"
            error_messages.append(error_msg)
            print(f"❌ {error_msg}")
            import traceback
            traceback.print_exc()
            dataset_df = None
            mapper = None
    elif not DATASET_MODULES_AVAILABLE:
        print("⚠️  Módulos de dataset não disponíveis (dataset_loader, data_mapper)")
        print("💡 Certifique-se de que os módulos estão instalados e acessíveis")
    elif force_fake:
        print("🔄 Modo fake forçado - pulando carregamento de dataset")
    elif not use_real_dataset:
        print("⚠️  use_real_dataset está False - não tentando carregar dataset")
    
    # Determinar modo de operação
    use_real = not force_fake and dataset_df is not None and mapper is not None and not dataset_df.empty
    
    # Log de diagnóstico final
    print("\n" + "="*60)
    print("📊 RESULTADO DO CARREGAMENTO")
    print("="*60)
    print(f"✅ Dataset carregado: {dataset_df is not None and not dataset_df.empty if dataset_df is not None else False}")
    if dataset_df is not None:
        print(f"📦 Tamanho do dataset: {len(dataset_df)} registros")
    print(f"✅ Mapper inicializado: {mapper is not None}")
    print(f"✅ Usando dataset real: {use_real}")
    if error_messages:
        print(f"⚠️  Erros encontrados: {len(error_messages)}")
        for i, err in enumerate(error_messages, 1):
            print(f"   {i}. {err}")
    print("="*60 + "\n")
    
    # Validação do dataset se solicitado ou se usando dataset real
    validation_result = None
    if (validate_dataset or use_real) and dataset_df is not None:
        print("\n" + "="*60)
        print("📊 VALIDAÇÃO DE DATASET")
        print("="*60)
        print("🔍 Verificando dataset...\n")
        
        validation_result = validate_dataset_usage(dataset_df, mapper, config)
        
        if validation_result["is_using_real_dataset"]:
            print("✅ Dataset carregado com sucesso!")
            if validation_result["cache_info"].get("exists"):
                print(f"📦 Arquivo: {validation_result['cache_info']['path']}")
            print(f"📊 Total de registros: {validation_result['dataset_size']}")
            print(f"📋 Colunas encontradas: {validation_result['columns']}")
            
            if validation_result["sample_titles"]:
                print("\n🔍 Amostra de produtos reais:")
                for i, title in enumerate(validation_result["sample_titles"][:5], 1):
                    print(f"   {i}. \"{title}\"")
            
            # Verificar validações
            checks = validation_result["validation_checks"]
            print("\n✅ Validação: Dataset real confirmado")
            if checks.get("has_required_columns"):
                print("   - Colunas obrigatórias presentes ✓")
            if checks.get("price_variation", {}).get("has_variation"):
                print("   - Preços variados e realistas ✓")
            titles_analysis = checks.get("titles_analysis", {})
            if titles_analysis.get("fake_ratio", 1.0) < 0.3:
                print("   - Títulos variados e específicos ✓")
            if checks.get("dataset_loaded"):
                print("   - Dataset carregado corretamente ✓")
        else:
            print("❌ Dataset não está sendo usado ou está inválido")
            if checks := validation_result.get("validation_checks"):
                if "error" in checks:
                    print(f"   Erro: {checks['error']}")
        
        print("="*60 + "\n")
    
    if use_real:
        print(f"✅ Usando dataset real com {len(dataset_df)} produtos")
        # Limitar ao número solicitado
        if len(dataset_df) > total_products:
            dataset_df = dataset_df.head(total_products)
        total_products = len(dataset_df)
    else:
        if force_fake:
            print("🔄 Usando geração fake de produtos (forçado)")
        elif not DATASET_MODULES_AVAILABLE:
            print("🔄 Usando geração fake de produtos (módulos de dataset não disponíveis)")
        elif error_messages:
            print("🔄 Usando geração fake de produtos (erros ao carregar dataset real)")
            if len(error_messages) > 0:
                print(f"   Erros encontrados: {len(error_messages)}")
        else:
            print("🔄 Usando geração fake de produtos")
    
    print("\n" + "="*60)
    print("📝 Gerando produtos para arquivo JSON...")
    print("="*60 + "\n")
    
    products_generated = []
    products_failed = 0
    products_fallback = 0
    products_real_count = 0
    products_fake_count = 0
    
    try:
        for index in range(total_products):
            product_payload = None
            use_fallback = False
            
            try:
                if use_real:
                    # Usar produto real do dataset
                    try:
                        row = dataset_df.iloc[index]
                        product_base = mapper.map_to_product_dto(row)
                        product_payload = enrich_real_product(product_base)
                    except (IndexError, KeyError, ValueError) as e:
                        error_msg = f"Erro ao processar produto real {index + 1}: {e}"
                        print(f"⚠️  {error_msg}")
                        use_fallback = True
                    except Exception as e:
                        error_msg = f"Erro ao processar produto real {index + 1}: {e}"
                        print(f"⚠️  {error_msg}")
                        use_fallback = True
                    
                    if use_fallback:
                        print("🔄 Usando geração fake como fallback para este produto")
                        product_payload = generate_product_payload()
                        products_fallback += 1
                else:
                    # Usar geração fake
                    product_payload = generate_product_payload()
                
                # Validar payload antes de adicionar
                if not product_payload:
                    raise ValueError("Payload do produto é None")
                
                if not product_payload.get("id"):
                    raise ValueError("Produto sem ID")
                
                if not product_payload.get("title"):
                    raise ValueError("Produto sem título")
                
                # Determinar origem do produto para logging
                product_source = analyze_product_source(product_payload)
                if use_real and not use_fallback:
                    source_label = "[REAL]"
                    products_real_count += 1
                else:
                    source_label = "[FAKE]"
                    products_fake_count += 1
                
                title = product_payload.get('title', 'Sem título')[:60]  # Limitar tamanho
                price = product_payload.get('price', 0)
                category_name = product_payload.get('category', {}).get('name', 'N/A')
                
                print(f"{source_label} Produto {index + 1}/{total_products}: {product_payload['id']}")
                print(f"       Título: \"{title}\"")
                print(f"       Preço: R$ {price:,.2f}")
                print(f"       Categoria: {category_name}")
                
                products_generated.append(product_payload)
                print(f"       ✅ Produto gerado com sucesso\n")
                    
            except Exception as e:
                error_msg = f"Erro inesperado ao processar produto {index + 1}: {e}"
                print(f"❌ {error_msg}")
                products_failed += 1
                # Continuar com próximo produto mesmo em caso de erro
                continue
        
        # Garantir diretório de saída
        os.makedirs(os.path.dirname(output_file) if os.path.dirname(output_file) else ".", exist_ok=True)
        
        # Salvar arquivo JSON
        with open(output_file, 'w', encoding='utf-8') as f:
            json.dump(products_generated, f, ensure_ascii=False, indent=2)
        
        print("\n" + "="*60)
        print("📊 RESUMO FINAL")
        print("="*60)
        print(f"✅ Produtos gerados: {len(products_generated)}")
        print(f"❌ Produtos com falha: {products_failed}")
        if products_fallback > 0:
            print(f"🔄 Produtos com fallback: {products_fallback}")
        print(f"📦 Total processado: {total_products}")
        print(f"💾 Arquivo salvo em: {output_file}")
        
        # Estatísticas de origem dos dados
        if use_real or products_real_count > 0 or products_fake_count > 0:
            print("\n📊 ORIGEM DOS DADOS:")
            if products_real_count > 0:
                real_percentage = (products_real_count / total_products) * 100 if total_products > 0 else 0
                print(f"✅ Dataset real (Amazon): {products_real_count} ({real_percentage:.1f}%)")
            if products_fake_count > 0:
                fake_percentage = (products_fake_count / total_products) * 100 if total_products > 0 else 0
                print(f"🔄 Geração fake: {products_fake_count} ({fake_percentage:.1f}%)")
            
            # Validação final
            if use_real and products_real_count > 0:
                success_rate = (products_real_count / total_products) * 100 if total_products > 0 else 0
                print(f"\n🔍 VALIDAÇÃO:")
                if success_rate >= 90:
                    print(f"✅ Dataset real confirmado em uso")
                    print(f"📈 Taxa de uso do dataset: {success_rate:.1f}%")
                elif success_rate >= 50:
                    print(f"⚠️  Dataset real parcialmente em uso")
                    print(f"📈 Taxa de uso do dataset: {success_rate:.1f}%")
                else:
                    print(f"❌ Dataset real não está sendo usado adequadamente")
                    print(f"📈 Taxa de uso do dataset: {success_rate:.1f}%")
        
        print("="*60)
        
        # Preview de um item para validação visual
        if products_generated:
            print("\n🔎 Exemplo de Item Gerado:")
            print(json.dumps(products_generated[0], ensure_ascii=False, indent=2))
                
    except KeyboardInterrupt:
        print("\n⚠️  Execução interrompida pelo usuário")
        print(f"📊 Produtos gerados até o momento: {len(products_generated)}")
        # Salvar produtos gerados até o momento
        if products_generated:
            with open(output_file, 'w', encoding='utf-8') as f:
                json.dump(products_generated, f, ensure_ascii=False, indent=2)
            print(f"💾 Produtos salvos em: {output_file}")
    except Exception as e:
        error_msg = f"Erro crítico na execução: {e}"
        print(f"❌ {error_msg}")
        print(f"📊 Produtos gerados antes do erro: {len(products_generated)}")
        # Salvar produtos gerados até o momento
        if products_generated:
            with open(output_file, 'w', encoding='utf-8') as f:
                json.dump(products_generated, f, ensure_ascii=False, indent=2)
            print(f"💾 Produtos salvos em: {output_file}")
        raise


def _process_single_product(index: int, api_url: str, headers: dict, 
                            dataset_df: Optional[pd.DataFrame], mapper: Optional[DataMapper],
                            use_real: bool, total_products: int) -> Dict:
    """
    Processa um único produto de forma thread-safe.
    
    Args:
        index: Índice do produto a processar
        api_url: URL base da API
        headers: Headers HTTP para requisições
        dataset_df: DataFrame com produtos reais (opcional)
        mapper: Instância do DataMapper (opcional)
        use_real: Se True, usa dataset real
        total_products: Total de produtos a processar (para logging)
        
    Returns:
        Dicionário com resultado do processamento:
        {
            "index": int,
            "success": bool,
            "product_id": str,
            "source": str,  # "REAL" ou "FAKE"
            "error": str (opcional),
            "use_fallback": bool
        }
    """
    result = {
        "index": index,
        "success": False,
        "product_id": None,
        "source": "FAKE",
        "error": None,
        "use_fallback": False
    }
    
    try:
        product_payload = None
        use_fallback = False
        
        if use_real and dataset_df is not None and mapper is not None:
            # Usar produto real do dataset
            try:
                row = dataset_df.iloc[index]
                product_base = mapper.map_to_product_dto(row)
                product_payload = enrich_real_product(product_base)
                result["source"] = "REAL"
            except (IndexError, KeyError, ValueError) as e:
                error_msg = f"Índice {index} fora do range ou erro de validação: {e}"
                use_fallback = True
                result["use_fallback"] = True
            except Exception as e:
                error_msg = f"Erro ao processar produto real {index + 1}: {e}"
                use_fallback = True
                result["use_fallback"] = True
            
            if use_fallback:
                product_payload = generate_product_payload()
                result["source"] = "FAKE"
        else:
            # Usar geração fake
            product_payload = generate_product_payload()
            result["source"] = "FAKE"
        
        # Validar payload
        if not product_payload:
            raise ValueError("Payload do produto é None")
        
        if not product_payload.get("id"):
            raise ValueError("Produto sem ID")
        
        if not product_payload.get("title"):
            raise ValueError("Produto sem título")
        
        result["product_id"] = product_payload.get("id")
        title = product_payload.get('title', 'Sem título')[:60]
        price = product_payload.get('price', 0)
        category_name = product_payload.get('category', {}).get('name', 'N/A')
        
        # Enviar para API
        try:
            response = requests.post(f"{api_url}/products", headers=headers, json=product_payload, timeout=30)
            if response.status_code == 201:
                result["success"] = True
            else:
                result["error"] = f"HTTP {response.status_code}: {response.text[:200]}"
        except requests.exceptions.Timeout:
            result["error"] = f"Timeout ao criar produto {product_payload['id']}"
        except requests.exceptions.ConnectionError:
            result["error"] = f"Erro de conexão ao criar produto {product_payload['id']}"
        except requests.exceptions.RequestException as re:
            result["error"] = f"Erro na requisição: {re}"
        
    except Exception as e:
        result["error"] = f"Erro inesperado: {e}"
    
    return result


def create_products_on_demand(api_url, total_products=10, dataset_df: Optional[pd.DataFrame] = None, 
                              use_real_dataset: bool = False, config_path: Optional[str] = None,
                              force_fake: bool = False, validate_dataset: bool = False,
                              concurrent_workers: int = 1):
    """
    Cria produtos via API, usando dataset real se disponível ou geração fake.
    
    Args:
        api_url: URL base da API
        total_products: Número total de produtos a criar
        dataset_df: DataFrame do pandas com produtos reais (opcional)
        use_real_dataset: Se True, tenta carregar dataset real automaticamente
        config_path: Caminho para arquivo de configuração (opcional)
        force_fake: Se True, força uso de geração fake mesmo se dataset estiver disponível
        validate_dataset: Se True, força validação detalhada do dataset
        concurrent_workers: Número de workers concorrentes (padrão: 1 = sequencial)
    """
    # Inicializar mapper se usar dataset real
    mapper = None
    dataset_loaded = False
    error_messages = []
    config = None
    
    # Se force_fake estiver ativo, pular carregamento de dataset
    if not force_fake and (use_real_dataset or dataset_df is not None) and DATASET_MODULES_AVAILABLE:
        try:
            config = load_dataset_config(config_path) if config_path else load_dataset_config()
            category_mapping = None
            brand_mapping = None
            
            if config and "mapping" in config:
                category_mapping = config["mapping"].get("category_mapping", {})
                brand_mapping = config["mapping"].get("brand_mapping", {})
            
            mapper = DataMapper(category_mapping=category_mapping, brand_mapping=brand_mapping)
            
            # Carregar dataset se não foi fornecido
            if dataset_df is None and use_real_dataset:
                try:
                    loader = DatasetLoader()
                    if config and "dataset" in config:
                        dataset_name = config["dataset"].get("name")
                        cache_dir = config["dataset"].get("cache_dir", "./data/cache")
                        
                        if dataset_name:
                            print(f"📥 Tentando carregar dataset do Kaggle: {dataset_name}")
                            try:
                                # Tentar carregar do cache primeiro
                                dataset_path = os.path.join(cache_dir, dataset_name.split('/')[-1])
                                if os.path.exists(dataset_path):
                                    print(f"📂 Dataset encontrado no cache: {dataset_path}")
                                    dataset_df = loader.load_dataset(dataset_path)
                                    loader.validate_dataset(dataset_df)
                                    dataset_loaded = True
                                else:
                                    # Tentar baixar do Kaggle (com fallback público)
                                    print(f"📥 Baixando dataset do Kaggle...")
                                    try:
                                        dataset_df = loader.download_and_load(dataset_name, validate=True)
                                        dataset_loaded = True
                                    except Exception as e:
                                        # Se o fallback público baixou o arquivo, tentar carregar do zip
                                        public_zip = os.path.join(cache_dir, f"{dataset_name.split('/')[-1]}.zip")
                                        if os.path.exists(public_zip) and os.path.getsize(public_zip) > 0:
                                            print(f"📦 Dataset zip baixado via fallback público: {public_zip}")
                                            # Tentar extrair e carregar CSV/JSON do zip
                                            import zipfile
                                            with zipfile.ZipFile(public_zip, 'r') as zip_ref:
                                                zip_ref.extractall(cache_dir)
                                            # Procurar CSV/JSON extraído
                                            import glob
                                            csv_files = glob.glob(os.path.join(cache_dir, '*.csv'))
                                            json_files = glob.glob(os.path.join(cache_dir, '*.json'))
                                            if csv_files:
                                                dataset_df = loader.load_dataset(csv_files[0])
                                                loader.validate_dataset(dataset_df)
                                                dataset_loaded = True
                                            elif json_files:
                                                dataset_df = loader.load_dataset(json_files[0])
                                                loader.validate_dataset(dataset_df)
                                                dataset_loaded = True
                                            else:
                                                raise Exception(f"Nenhum CSV/JSON encontrado após extrair {public_zip}")
                                        else:
                                            raise e
                                
                                max_products = config["dataset"].get("max_products", total_products)
                                if len(dataset_df) > max_products:
                                    dataset_df = dataset_df.head(max_products)
                                    print(f"📊 Limitando a {max_products} produtos do dataset")
                            except ImportError as ie:
                                error_msg = f"Pacote kaggle não instalado: {ie}"
                                error_messages.append(error_msg)
                                print(f"⚠️  {error_msg}")
                                print("💡 Instale com: pip install kaggle")
                            except FileNotFoundError as fnf:
                                error_msg = f"Dataset não encontrado no cache: {fnf}"
                                error_messages.append(error_msg)
                                print(f"⚠️  {error_msg}")
                            except Exception as e:
                                error_msg = f"Erro ao carregar dataset do Kaggle: {e}"
                                error_messages.append(error_msg)
                                print(f"⚠️  {error_msg}")
                except Exception as e:
                    error_msg = f"Erro ao inicializar loader: {e}"
                    error_messages.append(error_msg)
                    print(f"⚠️  {error_msg}")
                    dataset_df = None
                    mapper = None
        except Exception as e:
            error_msg = f"Erro ao carregar configuração ou inicializar mapper: {e}"
            error_messages.append(error_msg)
            print(f"⚠️  {error_msg}")
            dataset_df = None
            mapper = None
    
    # Determinar modo de operação
    use_real = not force_fake and dataset_df is not None and mapper is not None and not dataset_df.empty
    
    # Validação do dataset se solicitado ou se usando dataset real
    validation_result = None
    if (validate_dataset or use_real) and dataset_df is not None:
        print("\n" + "="*60)
        print("📊 VALIDAÇÃO DE DATASET")
        print("="*60)
        print("🔍 Verificando dataset...\n")
        
        validation_result = validate_dataset_usage(dataset_df, mapper, config)
        
        if validation_result["is_using_real_dataset"]:
            print("✅ Dataset carregado com sucesso!")
            if validation_result["cache_info"].get("exists"):
                print(f"📦 Arquivo: {validation_result['cache_info']['path']}")
            print(f"📊 Total de registros: {validation_result['dataset_size']}")
            print(f"📋 Colunas encontradas: {validation_result['columns']}")
            
            if validation_result["sample_titles"]:
                print("\n🔍 Amostra de produtos reais:")
                for i, title in enumerate(validation_result["sample_titles"][:5], 1):
                    print(f"   {i}. \"{title}\"")
            
            # Verificar validações
            checks = validation_result["validation_checks"]
            print("\n✅ Validação: Dataset real confirmado")
            if checks.get("has_required_columns"):
                print("   - Colunas obrigatórias presentes ✓")
            if checks.get("price_variation", {}).get("has_variation"):
                print("   - Preços variados e realistas ✓")
            titles_analysis = checks.get("titles_analysis", {})
            if titles_analysis.get("fake_ratio", 1.0) < 0.3:
                print("   - Títulos variados e específicos ✓")
            if checks.get("dataset_loaded"):
                print("   - Dataset carregado corretamente ✓")
        else:
            print("❌ Dataset não está sendo usado ou está inválido")
            if checks := validation_result.get("validation_checks"):
                if "error" in checks:
                    print(f"   Erro: {checks['error']}")
        
        print("="*60 + "\n")
    
    if use_real:
        print(f"✅ Usando dataset real com {len(dataset_df)} produtos")
        # Limitar ao número solicitado
        if len(dataset_df) > total_products:
            dataset_df = dataset_df.head(total_products)
        total_products = len(dataset_df)
    else:
        if force_fake:
            print("🔄 Usando geração fake de produtos (forçado)")
        elif not DATASET_MODULES_AVAILABLE:
            print("🔄 Usando geração fake de produtos (módulos de dataset não disponíveis)")
        elif error_messages:
            print("🔄 Usando geração fake de produtos (erros ao carregar dataset real)")
            if len(error_messages) > 0:
                print(f"   Erros encontrados: {len(error_messages)}")
        else:
            print("🔄 Usando geração fake de produtos")
    
    if use_real:
        print("\n" + "="*60)
        print("🚀 Iniciando criação de produtos...")
        print("="*60 + "\n")
    
    # Informar modo de processamento
    if concurrent_workers > 1:
        print(f"⚡ Modo concorrente: {concurrent_workers} workers\n")
    else:
        print("🔄 Modo sequencial\n")

    products_created = 0
    products_failed = 0
    products_fallback = 0
    products_real_count = 0
    products_fake_count = 0
    
    headers = {
        "Content-Type": "application/json" 
    }
    
    start_time = time.time()
    
    try:
        if concurrent_workers > 1:
            # Processamento concorrente
            with ThreadPoolExecutor(max_workers=concurrent_workers) as executor:
                # Submeter todas as tarefas
                future_to_index = {
                    executor.submit(_process_single_product, index, api_url, headers, 
                                   dataset_df, mapper, use_real, total_products): index
                    for index in range(total_products)
                }
                
                # Processar resultados conforme completam
                completed = 0
                for future in as_completed(future_to_index):
                    completed += 1
                    index = future_to_index[future]
                    
                    try:
                        result = future.result()
                        
                        # Atualizar contadores
                        if result["success"]:
                            products_created += 1
                        else:
                            products_failed += 1
                        
                        if result["source"] == "REAL":
                            products_real_count += 1
                        else:
                            products_fake_count += 1
                        
                        if result["use_fallback"]:
                            products_fallback += 1
                        
                        # Log do resultado
                        source_label = f"[{result['source']}]"
                        status_icon = "✅" if result["success"] else "❌"
                        
                        print(f"{source_label} Produto {index + 1}/{total_products}: {result['product_id']} {status_icon}")
                        if result["error"]:
                            print(f"       Erro: {result['error']}")
                        print()  # Linha em branco
                        
                        # Progresso
                        if completed % 10 == 0 or completed == total_products:
                            elapsed = time.time() - start_time
                            rate = completed / elapsed if elapsed > 0 else 0
                            remaining = (total_products - completed) / rate if rate > 0 else 0
                            print(f"📊 Progresso: {completed}/{total_products} ({completed*100//total_products}%) | "
                                  f"Taxa: {rate:.1f} produtos/s | "
                                  f"Tempo restante: {remaining:.1f}s\n")
                    
                    except Exception as e:
                        print(f"❌ Erro ao processar resultado do produto {index + 1}: {e}\n")
                        products_failed += 1
        else:
            # Processamento sequencial (código original)
            for index in range(total_products):
                result = _process_single_product(index, api_url, headers, 
                                                 dataset_df, mapper, use_real, total_products)
                
                # Atualizar contadores
                if result["success"]:
                    products_created += 1
                else:
                    products_failed += 1
                
                if result["source"] == "REAL":
                    products_real_count += 1
                else:
                    products_fake_count += 1
                
                if result["use_fallback"]:
                    products_fallback += 1
                
                # Log do resultado
                source_label = f"[{result['source']}]"
                status_icon = "✅" if result["success"] else "❌"
                
                print(f"{source_label} Produto {index + 1}/{total_products}: {result['product_id']} {status_icon}")
                if result["error"]:
                    print(f"       Erro: {result['error']}")
                print()  # Linha em branco
                
                # Pequena pausa para evitar sobrecarga no servidor (apenas no modo sequencial)
                if index < total_products - 1:
                    time.sleep(0.1)
        
        # Resumo final
        print("\n" + "="*60)
        print("📊 RESUMO FINAL")
        print("="*60)
        print(f"✅ Produtos criados: {products_created}")
        print(f"❌ Produtos com falha: {products_failed}")
        if products_fallback > 0:
            print(f"🔄 Produtos com fallback: {products_fallback}")
        print(f"📦 Total processado: {total_products}")
        
        # Estatísticas de origem dos dados
        if use_real or products_real_count > 0 or products_fake_count > 0:
            print("\n📊 ORIGEM DOS DADOS:")
            if products_real_count > 0:
                real_percentage = (products_real_count / total_products) * 100 if total_products > 0 else 0
                print(f"✅ Dataset real (Amazon): {products_real_count} ({real_percentage:.1f}%)")
            if products_fake_count > 0:
                fake_percentage = (products_fake_count / total_products) * 100 if total_products > 0 else 0
                print(f"🔄 Geração fake: {products_fake_count} ({fake_percentage:.1f}%)")
            
            # Validação final
            if use_real and products_real_count > 0:
                success_rate = (products_real_count / total_products) * 100 if total_products > 0 else 0
                print(f"\n🔍 VALIDAÇÃO:")
                if success_rate >= 90:
                    print(f"✅ Dataset real confirmado em uso")
                    print(f"📈 Taxa de uso do dataset: {success_rate:.1f}%")
                elif success_rate >= 50:
                    print(f"⚠️  Dataset real parcialmente em uso")
                    print(f"📈 Taxa de uso do dataset: {success_rate:.1f}%")
                else:
                    print(f"❌ Dataset real não está sendo usado adequadamente")
                    print(f"📈 Taxa de uso do dataset: {success_rate:.1f}%")
        
        print("="*60)
                
    except KeyboardInterrupt:
        print("\n⚠️  Execução interrompida pelo usuário")
        print(f"📊 Produtos criados até o momento: {products_created}")
    except Exception as e:
        error_msg = f"Erro crítico na execução: {e}"
        print(f"❌ {error_msg}")
        print(f"📊 Produtos criados antes do erro: {products_created}")
        raise


def main():
    """
    Função principal. Por padrão tenta usar dataset real, com fallback para geração fake.
    Suporta os seguintes modos:
    1. Dataset real (padrão): Tenta carregar dataset real do Kaggle ou arquivo local
    2. Geração fake: Se --force-fake for usado ou se dataset real não estiver disponível
    3. Modo JSON: Se --output-json for usado, gera arquivo JSON sem chamar a API
    4. Modo API: Se --output-json não for usado, chama a API para criar produtos
    """
    import argparse
    
    parser = argparse.ArgumentParser(description="Gerador de produtos para API do marketplace")
    parser.add_argument("--api-url", default="http://localhost:8888/api/v1", 
                       help="URL base da API (usado apenas no modo API)")
    parser.add_argument("--total", type=int, default=100, 
                       help="Número total de produtos a criar")
    parser.add_argument("--use-real-dataset", action="store_true", default=True,
                       help="Tentar usar dataset real do Kaggle (padrão: True)")
    parser.add_argument("--force-fake", action="store_true",
                       help="Forçar uso de geração fake mesmo se dataset estiver disponível")
    parser.add_argument("--config", type=str, default=None,
                       help="Caminho para arquivo de configuração YAML")
    parser.add_argument("--dataset-file", type=str, default=None,
                       help="Caminho para arquivo CSV/JSON local (alternativa ao Kaggle)")
    parser.add_argument("--validate-dataset", action="store_true",
                       help="Forçar validação detalhada do dataset")
    parser.add_argument("--output-json", type=str, default=None,
                       help="Caminho do arquivo JSON de saída. Se fornecido, gera JSON sem chamar a API")
    parser.add_argument("--concurrent-workers", type=int, default=1,
                       help="Número de workers concorrentes para criar produtos (padrão: 1 = sequencial)")
    
    args = parser.parse_args()
    
    # Se force-fake estiver ativo, desabilitar uso de dataset real
    use_real_dataset = args.use_real_dataset and not args.force_fake
    
    # Se fornecido arquivo local, carregar diretamente
    dataset_df = None
    if args.dataset_file and DATASET_MODULES_AVAILABLE and not args.force_fake:
        try:
            print(f"📂 Tentando carregar dataset local: {args.dataset_file}")
            loader = DatasetLoader()
            dataset_df = loader.load_dataset(args.dataset_file)
            loader.validate_dataset(dataset_df)
            print(f"✅ Dataset local carregado: {len(dataset_df)} produtos")
        except FileNotFoundError as fnf:
            print(f"⚠️  Arquivo não encontrado: {fnf}")
            print("🔄 Usando geração fake como fallback")
            dataset_df = None
        except ValueError as ve:
            print(f"⚠️  Erro de validação no dataset: {ve}")
            print("🔄 Usando geração fake como fallback")
            dataset_df = None
        except Exception as e:
            print(f"⚠️  Erro ao carregar dataset local: {e}")
            print("🔄 Usando geração fake como fallback")
            dataset_df = None
    
    # Se não foi fornecido arquivo local e use_real_dataset está ativo, tentar carregar do Kaggle
    if dataset_df is None and use_real_dataset and not args.force_fake:
        # As funções tentarão carregar automaticamente
        pass
    
    # Determinar modo de operação
    if args.output_json:
        # Modo JSON: gerar arquivo JSON sem chamar API
        print("="*60)
        print("📝 MODO: Geração de JSON para Validação")
        print("="*60)
        print(f"💾 Arquivo de saída: {args.output_json}")
        print("="*60 + "\n")
        
        try:
            generate_products_json(
                output_file=args.output_json,
                total_products=args.total,
                dataset_df=dataset_df,
                use_real_dataset=use_real_dataset,
                config_path=args.config,
                force_fake=args.force_fake,
                validate_dataset=args.validate_dataset
            )
        except KeyboardInterrupt:
            print("\n⚠️  Execução interrompida pelo usuário")
        except Exception as e:
            print(f"\n❌ Erro crítico na execução: {e}")
            import traceback
            traceback.print_exc()
            raise
    else:
        # Modo API: chamar API para criar produtos
        print("="*60)
        print("🚀 MODO: Criação de Produtos via API")
        print("="*60)
        print(f"🌐 URL da API: {args.api_url}")
        print("="*60 + "\n")
        
        try:
            create_products_on_demand(
                api_url=args.api_url,
                total_products=args.total,
                dataset_df=dataset_df,
                use_real_dataset=use_real_dataset,
                config_path=args.config,
                force_fake=args.force_fake,
                validate_dataset=args.validate_dataset,
                concurrent_workers=args.concurrent_workers
            )
        except KeyboardInterrupt:
            print("\n⚠️  Execução interrompida pelo usuário")
        except Exception as e:
            print(f"\n❌ Erro crítico na execução: {e}")
            import traceback
            traceback.print_exc()
            raise


if __name__ == "__main__":
    main()