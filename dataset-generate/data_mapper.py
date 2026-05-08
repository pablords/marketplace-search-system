#!/usr/bin/env python3
"""
Módulo de mapeamento de dados do dataset Kaggle para formato da API.
Responsável por transformar dados brutos do dataset em DTOs compatíveis com a API.
"""

import random
import re
import hashlib
from typing import Dict, List, Optional, Set
from datetime import datetime
import pandas as pd
import os

# Cache para categorias carregadas
_CATEGORIES_DB_CACHE = None

def load_categories_from_dataset(cache_dir: str = "./dataset-generate/data/cache") -> List[Dict]:
    """
    Carrega categorias do novo dataset Amazon Brazil.

    O dataset Amazon Brazil não possui um arquivo separado de categorias —
    as categorias estão embutidas na coluna 'categoryName' do CSV principal.
    Esta função lê esse CSV, extrai os nomes únicos de categoria e gera IDs
    determinísticos (hash) para manter consistência entre execuções.

    Como fallback, ainda tenta o arquivo amazon_categories.csv legado.

    Args:
        cache_dir: Diretório onde está o arquivo de produtos

    Returns:
        Lista de dicionários com categorias no formato:
        [{"id": "123", "name": "Smartphones", "path": "/smartphones", "parent_id": None}, ...]
    """
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
                # ID determinístico via MD5 (hash() do Python não é estável entre execuções)
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
                _CATEGORIES_DB_CACHE = categories
                return categories
        except Exception as e:
            # Não logar erro se for FileNotFoundError, apenas se for algo inesperado
            if not isinstance(e, FileNotFoundError):
                print(f"⚠️  Erro ao extrair categorias do CSV Brazil: {e}")

    # --- Tentativa 2: arquivo legado amazon_categories.csv ---
    categories_file = os.path.join(cache_dir, "amazon_categories.csv")
    if os.path.exists(categories_file):
        try:
            df = pd.read_csv(categories_file)
            categories = []

            for _, row in df.iterrows():
                category_id = str(row.get("id", ""))
                category_name = str(row.get("category_name", "")).strip()

                if not category_name:
                    continue

                path_slug = re.sub(r'[^\w\s-]', '', category_name.lower())
                path_slug = re.sub(r'[-\s]+', '-', path_slug)
                path = f"/{path_slug}"

                categories.append({
                    "id": category_id,
                    "name": category_name,
                    "path": path,
                    "parent_id": None
                })

            _CATEGORIES_DB_CACHE = categories
            return categories
        except Exception as e:
            print(f"⚠️  Erro ao carregar categorias do dataset legado: {e}")

    # --- Fallback final ---
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

# ─────────────────────────────────────────────
# Camada 1: Stopwords PT-BR (preposições, artigos, pronomes, conectivos)
# ─────────────────────────────────────────────
_PORTUGUESE_STOPWORDS = {
    # Preposições
    "a", "ao", "aos", "à", "às", "ante", "até", "após", "com", "contra",
    "de", "do", "da", "dos", "das", "desde", "em", "entre", "no", "na",
    "nos", "nas", "para", "per", "perante", "por", "sem", "sob", "sobre",
    # Artigos
    "o", "os", "um", "uma", "uns", "umas", "as",
    # Pronomes demonstrativos / outros
    "este", "esta", "esse", "essa", "aquele", "aquela", "isto", "isso",
    # Conjunções / conectivos
    "ou", "que", "se", "não", "nao", "mais", "muito", "como", "mas", "já",
    "ja", "e", "nem",
}

# ─────────────────────────────────────────────
# Palavras genéricas (tipos de produto, não marcas)
# ─────────────────────────────────────────────
_PRODUCT_GENERIC_WORDS = {
    "ração","racao","raçao","kit","suplemento","brinquedo","comedouro","tapete",
    "shampoo","coleira","cama","antipulgas","combo","arranhador","bebedouro",
    "pet","petisco","escova","biscoito","bola","guia","cercado","caixa",
    "vermifugo","vermífugo","mordedor","capa","osso","bolsa","peitoral",
    "conjunto","bifinho","smart","grade","fonte","smartphone","notebook",
    "tablet","cadeira","mesa","sofa","sofá","tenis","tênis","camiseta",
    "calca","calça","vestido","meia","camera","câmera","tv","geladeira",
    "fogao","fogão","liquidificador","fritadeira","aspirador","ferro","micro",
    "colchao","colchão","travesseiro","cobertor","lencol","lençol","panela",
    "frigideira","faca","garfo","prato","copo","livro","agenda","caderno",
    "caneta","lapis","lápis","sabonete","creme","perfume","condicionador",
    "hidratante","vitamina","proteina","whey","creatina","cabo","carregador",
    "case","pelicula","película","oleo","óleo","filtro","pneu","bateria",
    "raquete","capacete","luva","poltrona","armario","armário","luminaria",
    "luminária","lampada","lâmpada","relogio","relógio","oculos","óculos",
    "mochila","carteira","novo","original","premium","profissional","pro",
    "pack","mini","maxi","ultra","super","mega","turbo","set","par",
    "carro","moto","bike","bicicleta","dog","cat","gato","cachorro",
    "baby","bebe","bebê","infantil","novo","nova","grande","pequeno",
    "duplo","triplo",
    "linha","serie","série","edição","edicao","especial","exclusivo",
    "ave","peixe","hamster","coelho","alimento",
    "comida","petfood","seco","umido","úmido","adulto","filhote","senior",
    "castrado","light","natural","organico","orgânico","vegano","integral",
    # Palavras genéricas detectadas na análise de dados
    "areia","areias","higiênico","higienico","úmida","umida",
    "carrapato","carrapatos","fórmula","formula","gatos","refil",
    "vitamínico","vitaminico","tira","led",
    "alimentador","alimentar","anti","banho","banheira",
}

# União de todas as palavras proibidas
_NON_BRAND_WORDS = _PORTUGUESE_STOPWORDS | _PRODUCT_GENERIC_WORDS


# ─────────────────────────────────────────────
# Camada 3: Heurísticas de validação de brand
# ─────────────────────────────────────────────
_REJECT_SUFFIXES = (
    "ico", "ica", "ido", "ida", "oso", "osa",
    "ção", "são", "cao", "sao",
    "mento", "ário", "ária", "ario", "aria",
    "ável", "ível", "avel", "ivel",
    "ente", "ante",
)

def _is_likely_brand(clean_word: str) -> bool:
    """
    Retorna True se a palavra parece um nome de marca.
    Filtra adjetivos/substantivos comuns em PT-BR pelos sufixos.
    """
    if len(clean_word) < 2 or len(clean_word) > 20:
        return False
    lower = clean_word.lower()
    for suffix in _REJECT_SUFFIXES:
        if lower.endswith(suffix) and len(lower) > len(suffix) + 2:
            return False
    return True


# ─────────────────────────────────────────────
# Camada 4: Stemming caseiro (plural/gênero)
# ─────────────────────────────────────────────
def _simple_stem(word: str) -> str:
    """
    Remove sufixos comuns de plural/gênero PT-BR.
    Objetivo: agrupar variantes morfológicas (ex: Carrapato/Carrapatos).
    """
    w = word.lower()
    if w.endswith("ões"):   return w[:-3] + "ão"
    if w.endswith("ães"):   return w[:-3] + "ão"
    if w.endswith("ais"):   return w[:-2] + "al"
    if w.endswith("éis"):   return w[:-3] + "el"
    if w.endswith("is") and len(w) > 3: return w[:-2] + "l"
    if w.endswith("s") and not w.endswith("ss") and len(w) > 2:
        return w[:-1]
    return w


def _extract_brand(title: str) -> Optional[str]:
    """
    Extrai marca do título usando pipeline de 4 camadas.
    Retorna nome normalizado em UPPER ou None.
    """
    if not isinstance(title, str):
        return None
    for word in title.strip().split()[:4]:
        clean = re.sub(r"[^\w]", "", word).strip()
        if len(clean) < 2 or clean[0].isdigit():
            continue
        if clean.lower() in _NON_BRAND_WORDS:
            continue
        if not _is_likely_brand(clean):
            continue
        return clean.upper()
    return None


def load_brands_from_dataset(cache_dir: str = "./dataset-generate/data/cache", min_count: int = 5, max_brands: int = 1000) -> Set[str]:
    """
    Extrai marcas dos títulos dos produtos usando pipeline de 4 camadas
    (stopwords, genéricas, heurística de sufixos, stemming para dedup).
    """
    brazil_csv = os.path.join(cache_dir, "amazon_products.csv")
    if not os.path.exists(brazil_csv):
        return {"OUTROS"}

    try:
        df = pd.read_csv(brazil_csv, usecols=["title"], dtype=str)
        extracted = df["title"].apply(_extract_brand).dropna()
        raw_counts = extracted.value_counts()

        # Agrupar por stem para eliminar variantes morfológicas
        from collections import defaultdict
        stem_groups: dict[str, dict[str, int]] = defaultdict(dict)
        for name, count in raw_counts.items():
            stem = _simple_stem(name)
            stem_groups[stem][name] = count

        # Para cada grupo, calcular total e nome canônico
        brand_candidates = []
        for stem, variants in stem_groups.items():
            total_count = sum(variants.values())
            if total_count < min_count:
                continue
            canonical = max(variants, key=variants.get)
            brand_candidates.append((canonical, total_count))

        # Ordenar por contagem (maior primeiro) — DEVE ser consistente com generate_seed.py
        brand_candidates.sort(key=lambda x: x[1], reverse=True)
        top_brands = {name for name, _ in brand_candidates[:max_brands]}

        top_brands.add("OUTROS")
        return top_brands
    except Exception as e:
        print(f"⚠️  Erro ao carregar marcas: {e}")
        return {"OUTROS"}

# Carregar marcas do dataset dinamicamente
BRANDS_DB = load_brands_from_dataset()

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
        
        # Último fallback: usar ID '77927' (TV, Áudio e Cinema em Casa) que existe no seed.sql
        # ou a primeira categoria se CATEGORIES_DB estiver populado
        fallback_id = "77927" 
        fallback_name = "TV, Áudio e Cinema em Casa"
        
        if categories:
            fallback_id = categories[0]["id"]
            fallback_name = categories[0]["name"]
            
        return {
            "id": fallback_id,
            "name": fallback_name,
            "path": f"/{fallback_id}",
            "parent_id": None
        }
    
    def normalize_brand(self, brand_name: str, title: str = "") -> Dict[str, str]:
        """
        Normaliza a marca para BrandDTO.

        Prioridade:
        1. brand_name explícito (campo 'brand' do CSV) → normalizado para UPPER
        2. Pipeline de 4 camadas no título (stopwords, genéricas, sufixos, UPPER)
        3. Fallback para 'OUTROS' (sempre presente no seed.sql)
        """
        # Tenta usar brand_name explícito
        if brand_name and not pd.isna(brand_name):
            name = str(brand_name).strip().upper()
            if name:
                return {"id": name, "name": name, "description": ""}

        # Extrai usando pipeline de 4 camadas
        if title:
            extracted = _extract_brand(str(title))
            if extracted and extracted in BRANDS_DB:
                return {"id": extracted, "name": extracted, "description": ""}

        # Fallback garantido
        return {"id": "OUTROS", "name": "OUTROS", "description": ""}
    
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
        title = str(row.get("title", "")).strip()[:1000] if not pd.isna(row.get("title")) else "Produto sem título"
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
        
        # Categoria — novo dataset usa 'categoryName' (texto); fallback para category_id (legado)
        category_id = None
        category_name = None

        if "categoryName" in row and not pd.isna(row.get("categoryName")):
            # Amazon Brazil: campo de texto em Português
            category_name = str(row["categoryName"]).strip()
        elif "category_id" in row and not pd.isna(row.get("category_id")):
            # Amazon US legado: ID numérico
            category_id = str(row["category_id"]).strip()
        elif "category" in row and not pd.isna(row.get("category")):
            category_name = str(row["category"]).strip()
        else:
            category_name = str(title).lower()
        
        category = self.normalize_category(category_id=category_id, category_name=category_name)
        
        # Marca - tentar extrair do título ou usar fallback
        brand_name = ""
        if "brand" in row and not pd.isna(row.get("brand")):
            brand_name = str(row["brand"]).strip()

        brand = self.normalize_brand(brand_name, title=title)
        
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

