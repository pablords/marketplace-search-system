#!/usr/bin/env python3
"""
Módulo de mapeamento de dados do dataset Kaggle para formato da API.
Responsável por transformar dados brutos do dataset em DTOs compatíveis com a API.
"""

import random
import re
from typing import Dict, List, Optional, Set
from datetime import datetime
import pandas as pd
import os

# Cache para categorias carregadas
_CATEGORIES_DB_CACHE = None

def load_categories_from_dataset(cache_dir: str = "./data/cache") -> List[Dict]:
    """
    Carrega categorias do arquivo amazon_categories.csv.
    
    Args:
        cache_dir: Diretório onde está o arquivo de categorias
        
    Returns:
        Lista de dicionários com categorias no formato:
        [{"id": "1", "name": "Category Name", "path": "/category-name", "parent_id": None}, ...]
    """
    global _CATEGORIES_DB_CACHE
    
    # Retornar cache se já foi carregado
    if _CATEGORIES_DB_CACHE is not None:
        return _CATEGORIES_DB_CACHE
    
    categories_file = os.path.join(cache_dir, "amazon_categories.csv")
    categories = []
    
    if os.path.exists(categories_file):
        try:
            df = pd.read_csv(categories_file)
            
            for _, row in df.iterrows():
                category_id = str(row.get("id", ""))
                category_name = str(row.get("category_name", "")).strip()
                
                if not category_name:
                    continue
                
                # Gerar path baseado no nome da categoria (slug)
                # Converter para lowercase, remover caracteres especiais, substituir espaços por hífens
                path_slug = re.sub(r'[^\w\s-]', '', category_name.lower())
                path_slug = re.sub(r'[-\s]+', '-', path_slug)
                path = f"/{path_slug}"
                
                categories.append({
                    "id": category_id,
                    "name": category_name,
                    "path": path,
                    "parent_id": None  # Não temos informação de hierarquia no CSV
                })
            
            _CATEGORIES_DB_CACHE = categories
            return categories
        except Exception as e:
            print(f"⚠️  Erro ao carregar categorias do dataset: {e}")
            # Fallback para categorias padrão
            return get_default_categories()
    else:
        # Fallback para categorias padrão
        return get_default_categories()

def get_default_categories() -> List[Dict]:
    """
    Retorna categorias padrão como fallback.
    
    Returns:
        Lista de categorias padrão
    """
    return [
        {"id": "1", "name": "Electronics", "path": "/electronics", "parent_id": None},
        {"id": "2", "name": "Computers", "path": "/computers", "parent_id": None},
        {"id": "3", "name": "Home & Kitchen", "path": "/home-kitchen", "parent_id": None},
        {"id": "4", "name": "Clothing", "path": "/clothing", "parent_id": None},
    ]

def get_categories_db() -> List[Dict]:
    """
    Retorna as categorias (carregadas do dataset ou padrão).
    
    Returns:
        Lista de categorias
    """
    return load_categories_from_dataset()

# Dados mestres (compartilhados com data_gen.py)
# Carregar categorias do dataset dinamicamente
CATEGORIES_DB = get_categories_db()

BRANDS_DB = {
    "Apple": {"id": "Apple", "name": "Apple", "description": "Inovação e design"},
    "Samsung": {"id": "Samsung", "name": "Samsung", "description": "Líder em Android"},
    "Dell": {"id": "Dell", "name": "Dell", "description": "Soluções corporativas"},
    "Nike": {"id": "Nike", "name": "Nike", "description": "Just do it"},
    "Adidas": {"id": "Adidas", "name": "Adidas", "description": "Performance esportiva"},
}

SELLERS = [
    {"id": "TechStore", "name": "TechStore Brasil", "type": "PROFESSIONAL", "status": "ACTIVE", 
     "reputation": {"score": 4.8, "total_reviews": 1500, "cancellation_rate": 0.02, "delivery_performance": 0.98}},
    {"id": "InfoShop", "name": "Casa & Decoração Ltda", "type": "PROFESSIONAL", "status": "ACTIVE",
     "reputation": {"score": 4.5, "total_reviews": 1200, "cancellation_rate": 0.03, "delivery_performance": 0.95}},
    {"id": "SportCenter", "name": "Fashion Store", "type": "INDIVIDUAL", "status": "ACTIVE",
     "reputation": {"score": 4.2, "total_reviews": 800, "cancellation_rate": 0.05, "delivery_performance": 0.92}},
    {"id": "Sport", "name": "Gamer Pro", "type": "PROFESSIONAL", "status": "ACTIVE",
     "reputation": {"score": 4.9, "total_reviews": 2000, "cancellation_rate": 0.01, "delivery_performance": 0.99}},
]


class DataMapper:
    """
    Classe responsável por mapear dados do dataset Kaggle para o formato da API.
    """
    
    def __init__(self, category_mapping: Optional[Dict[str, str]] = None, 
                 brand_mapping: Optional[Dict[str, str]] = None):
        """
        Inicializa o mapeador com mapeamentos customizados opcionais.
        
        Args:
            category_mapping: Dicionário mapeando nomes de categorias do dataset para IDs de categorias
            brand_mapping: Dicionário mapeando nomes de marcas do dataset para IDs de marcas
        """
        self.category_mapping = category_mapping or {}
        self.brand_mapping = brand_mapping or {}
    
    def normalize_category(self, category_id: Optional[str] = None, category_name: Optional[str] = None) -> Dict[str, str]:
        """
        Normaliza a categoria do dataset para CategoryDTO.
        
        Args:
            category_id: ID numérico da categoria do dataset (prioridade)
            category_name: Nome da categoria do dataset (fallback)
            
        Returns:
            Dicionário representando CategoryDTO com id, name, path e parent_id
        """
        # Recarregar categorias para garantir que está atualizado
        categories = get_categories_db()
        
        # Prioridade 1: Buscar por category_id (ID numérico do dataset)
        if category_id is not None and not pd.isna(category_id):
            category_id_str = str(category_id).strip()
            category = next((c for c in categories if c["id"] == category_id_str), None)
            if category:
                return {
                    "id": category["id"],
                    "name": category["name"],
                    "path": category["path"],
                    "parent_id": category.get("parent_id")
                }
        
        # Prioridade 2: Verificar mapeamento customizado
        if category_name and category_name in self.category_mapping:
            mapped_id = self.category_mapping[category_name]
            category = next((c for c in categories if c["id"] == mapped_id), None)
            if category:
                return {
                    "id": category["id"],
                    "name": category["name"],
                    "path": category["path"],
                    "parent_id": category.get("parent_id")
                }
        
        # Prioridade 3: Buscar por nome (case-insensitive, parcial)
        if category_name and not pd.isna(category_name):
            category_name_str = str(category_name).strip()
            category_name_lower = category_name_str.lower()
            
            for cat in categories:
                if cat["name"].lower() == category_name_lower or category_name_lower in cat["name"].lower():
                    return {
                        "id": cat["id"],
                        "name": cat["name"],
                        "path": cat["path"],
                        "parent_id": cat.get("parent_id")
                    }
        
        # Fallback: usar primeira categoria disponível
        if categories:
            category = categories[0]
            return {
                "id": category["id"],
                "name": category["name"],
                "path": category["path"],
                "parent_id": category.get("parent_id")
            }
        
        # Último fallback: categoria genérica
        return {
            "id": "1",
            "name": "General",
            "path": "/general",
            "parent_id": None
        }
    
    def normalize_brand(self, brand_name: str) -> Dict[str, str]:
        """
        Normaliza o nome da marca do dataset para BrandDTO.
        
        Args:
            brand_name: Nome da marca do dataset (pode ser vazio ou None)
            
        Returns:
            Dicionário representando BrandDTO com id, name e description
        """
        if not brand_name or pd.isna(brand_name):
            # Fallback: marca genérica
            brand = BRANDS_DB["Apple"]
            return {
                "id": brand["id"],
                "name": brand["name"],
                "description": brand.get("description", "")
            }
        
        brand_name = str(brand_name).strip()
        
        # Verificar mapeamento customizado primeiro
        if brand_name in self.brand_mapping:
            brand_id = self.brand_mapping[brand_name]
            brand = BRANDS_DB.get(brand_id)
            if brand:
                return {
                    "id": brand["id"],
                    "name": brand["name"],
                    "description": brand.get("description", "")
                }
        
        # Tentar match por nome (case-insensitive, exato ou parcial)
        brand_name_lower = brand_name.lower()
        for brand_id, brand in BRANDS_DB.items():
            if brand["name"].lower() == brand_name_lower or brand_name_lower in brand["name"].lower():
                return {
                    "id": brand["id"],
                    "name": brand["name"],
                    "description": brand.get("description", "")
                }
        
        # Se não encontrar, criar uma nova marca dinamicamente (ou usar fallback)
        # Por enquanto, usar fallback para manter consistência
        brand = BRANDS_DB["Apple"]
        return {
            "id": brand["id"],
            "name": brand["name"],
            "description": brand.get("description", "")
        }
    
    def parse_images(self, images_field) -> List[str]:
        """
        Extrai lista de URLs de imagens do campo do dataset.
        
        Args:
            images_field: Pode ser string (JSON, lista separada por vírgula, URL única) ou lista
            
        Returns:
            Lista de URLs de imagens
        """
        if pd.isna(images_field) or not images_field:
            return []
        
        # Se já é uma lista
        if isinstance(images_field, list):
            return [str(img) for img in images_field if img and str(img).strip()]
        
        # Se é string, tentar parsear
        images_str = str(images_field).strip()
        
        # Tentar parsear como JSON
        try:
            import json
            parsed = json.loads(images_str)
            if isinstance(parsed, list):
                return [str(img) for img in parsed if img]
        except:
            pass
        
        # Tentar split por vírgula
        if ',' in images_str:
            images = [img.strip() for img in images_str.split(',') if img.strip()]
            return images
        
        # Se é uma única URL
        if images_str.startswith('http'):
            return [images_str]
        
        return []
    
    def parse_attributes(self, attributes_field, description: str = "") -> Set[str]:
        """
        Extrai atributos do campo do dataset ou gera baseado na descrição.
        
        Args:
            attributes_field: Campo de atributos do dataset
            description: Descrição do produto para extrair atributos
            
        Returns:
            Set de strings com atributos
        """
        attributes = set()
        
        # Tentar extrair do campo attributes
        if attributes_field and not pd.isna(attributes_field):
            if isinstance(attributes_field, list):
                attributes.update(str(attr) for attr in attributes_field if attr)
            elif isinstance(attributes_field, str):
                # Tentar parsear JSON
                try:
                    import json
                    parsed = json.loads(attributes_field)
                    if isinstance(parsed, list):
                        attributes.update(str(attr) for attr in parsed if attr)
                    elif isinstance(parsed, dict):
                        # Se for dict, converter para lista de strings
                        attributes.update(f"{k}: {v}" for k, v in parsed.items() if v)
                except:
                    # Se não for JSON, tratar como string separada por vírgula
                    if ',' in attributes_field:
                        attributes.update(attr.strip() for attr in attributes_field.split(',') if attr.strip())
                    else:
                        attributes.add(attributes_field.strip())
        
        # Se não houver atributos, tentar extrair da descrição
        if not attributes and description:
            # Extrair palavras-chave comuns
            keywords = re.findall(r'\b\d+\s*(GB|MB|TB|GHz|MHz|kg|g|cm|m|pol|polegadas|w|watts)\b', 
                                description, re.IGNORECASE)
            if keywords:
                attributes.update(keywords)
        
        # Garantir pelo menos um atributo
        if not attributes:
            attributes.add("Produto original")
        
        return attributes
    
    def parse_tags(self, tags_field, title: str = "", category_name: str = "") -> Set[str]:
        """
        Extrai tags do campo do dataset ou gera baseado no título e categoria.
        
        Args:
            tags_field: Campo de tags do dataset
            title: Título do produto
            category_name: Nome da categoria
            
        Returns:
            Set de strings com tags
        """
        tags = set()
        
        # Tentar extrair do campo tags
        if tags_field and not pd.isna(tags_field):
            if isinstance(tags_field, list):
                tags.update(str(tag) for tag in tags_field if tag)
            elif isinstance(tags_field, str):
                # Tentar parsear JSON
                try:
                    import json
                    parsed = json.loads(tags_field)
                    if isinstance(parsed, list):
                        tags.update(str(tag) for tag in parsed if tag)
                except:
                    # Se não for JSON, tratar como string separada por vírgula
                    if ',' in tags_field:
                        tags.update(tag.strip() for tag in tags_field.split(',') if tag.strip())
                    else:
                        tags.add(tags_field.strip())
        
        # Se não houver tags, gerar baseado no título e categoria
        if not tags:
            if title:
                # Extrair palavras significativas do título (mais de 3 caracteres)
                words = re.findall(r'\b\w{4,}\b', title.lower())
                tags.update(words[:4])  # Limitar a 4 palavras
            
            if category_name:
                tags.add(category_name.lower())
        
        # Garantir pelo menos uma tag
        if not tags:
            tags.add("produto")
        
        return tags
    
    def parse_condition(self, condition_field) -> str:
        """
        Normaliza o campo de condição do produto.
        
        Args:
            condition_field: Campo de condição do dataset
            
        Returns:
            String com condição normalizada (NEW, USED, REFURBISHED, etc)
        """
        if pd.isna(condition_field) or not condition_field:
            return "NEW"
        
        condition_str = str(condition_field).strip().upper()
        
        # Mapeamento de condições comuns
        condition_mapping = {
            "NEW": "NEW",
            "NOVO": "NEW",
            "USED": "USED",
            "USADO": "USED",
            "REFURBISHED": "REFURBISHED",
            "REMANUFACTURED": "REFURBISHED",
            "REMOVIDO": "REFURBISHED",
        }
        
        for key, value in condition_mapping.items():
            if key in condition_str:
                return value
        
        return "NEW"  # Default
    
    def map_to_product_dto(self, row: pd.Series) -> Dict:
        """
        Mapeia uma linha do dataset (pandas Series) para o formato ProductDTO da API.
        
        Args:
            row: Linha do DataFrame do pandas com dados do produto
            
        Returns:
            Dicionário representando ProductDTO completo
        """
        # Campos obrigatórios básicos
        title = str(row.get("title", "")).strip() if not pd.isna(row.get("title")) else "Produto sem título"
        if not title:
            title = "Produto sem título"
        
        # Descrição - tentar múltiplos campos ou gerar do título
        description = ""
        for desc_field in ["description", "desc", "details", "productDescription"]:
            if desc_field in row and not pd.isna(row[desc_field]):
                description = str(row[desc_field]).strip()
                if description:
                    break
        
        # Se não tiver descrição, gerar uma básica do título
        if not description:
            description = f"Produto {title}. Qualidade garantida."
        
        # Preço - tentar múltiplos campos comuns
        price = None
        for price_field in ["price", "preco", "cost", "amount", "value", "listPrice"]:
            if price_field in row and not pd.isna(row[price_field]):
                try:
                    price_val = row[price_field]
                    if isinstance(price_val, str):
                        # Remover símbolos de moeda e espaços
                        price_val = re.sub(r'[^\d.,]', '', price_val)
                        price_val = price_val.replace(',', '.')
                    price = float(price_val)
                    if price > 0:
                        break
                except (ValueError, TypeError):
                    continue
        
        if price is None or price <= 0:
            price = round(random.uniform(50.0, 5000.0), 2)  # Fallback
        
        # Categoria - usar category_id do dataset se disponível
        category_id = None
        category_name = None
        
        if "category_id" in row and not pd.isna(row.get("category_id")):
            category_id = str(row["category_id"]).strip()
        elif "category" in row and not pd.isna(row.get("category")):
            category_name = str(row["category"]).strip()
        else:
            # Fallback: extrair categoria do título (não ideal, mas funciona)
            category_name = str(title).lower()
        
        category = self.normalize_category(category_id=category_id, category_name=category_name)
        
        # Marca - tentar extrair do título ou usar fallback
        brand_name = ""
        if "brand" in row and not pd.isna(row.get("brand")):
            brand_name = str(row["brand"]).strip()
        else:
            # Tentar extrair marca do título (palavras comuns de marcas)
            title_lower = title.lower()
            brand_keywords = ["apple", "samsung", "dell", "nike", "adidas", "sony", "lg", "hp", "lenovo"]
            for keyword in brand_keywords:
                if keyword in title_lower:
                    brand_name = keyword.capitalize()
                    break
        
        brand = self.normalize_brand(brand_name)
        
        # Vendedor (aleatório dos disponíveis)
        seller = random.choice(SELLERS).copy()
        
        # Imagens - tentar imgUrl, images ou gerar placeholder
        images = []
        if "imgUrl" in row and not pd.isna(row.get("imgUrl")):
            img_url = str(row["imgUrl"]).strip()
            if img_url and img_url.startswith('http'):
                images = [img_url]
        elif "images" in row:
            images = self.parse_images(row.get("images", ""))
        
        if not images:
            # Gerar URLs placeholder se não houver imagens
            images = [f"https://marketplace.com/img/{category['id']}_{random.randint(1,9)}.jpg" 
                     for _ in range(3)]
        
        # Atributos
        attributes = self.parse_attributes(row.get("attributes", ""), description)
        
        # Tags
        tags = self.parse_tags(row.get("tags", ""), title, category_name)
        
        # Quantidade disponível
        available_quantity = None
        for qty_field in ["available_quantity", "stock", "quantity", "qty", "inventory"]:
            if qty_field in row and not pd.isna(row[qty_field]):
                try:
                    available_quantity = int(row[qty_field])
                    if available_quantity >= 0:
                        break
                except (ValueError, TypeError):
                    continue
        
        if available_quantity is None:
            available_quantity = random.randint(0, 500)
        
        # Condição
        condition = self.parse_condition(row.get("condition", ""))
        
        # ID do produto - usar do dataset se disponível (asin, id, product_id)
        product_id = None
        for id_field in ["asin", "id", "product_id", "productId"]:
            if id_field in row and not pd.isna(row.get(id_field)):
                product_id = str(row[id_field]).strip()
                if product_id:
                    break
        
        # Se não encontrar ID no dataset, gerar um fallback (não MLB)
        if not product_id:
            product_id = f"PROD{int(datetime.now().timestamp() * 1000)}"
        
        # Montar ProductDTO
        product_dto = {
            "id": product_id,
            "title": title,
            "description": description if description else None,
            "price": round(price, 2),
            "currency": "BRL",
            "category": category,
            "brand": brand,
            "seller": seller,
            "images": images,
            "attributes": list(attributes),
            "tags": list(tags),
            "available_quantity": available_quantity,
            "condition": condition,
            "is_active": True,
            # metrics será adicionado pelo enricher
        }
        
        return product_dto

