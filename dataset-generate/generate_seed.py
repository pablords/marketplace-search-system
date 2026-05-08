#!/usr/bin/env python3
"""
Gera seed.sql a partir do dataset Amazon Brazil.
Extrai brands dos títulos dos produtos e gera sellers por cluster de categorias.

Uso:
    python3 generate_seed.py

Pré-requisito:
    ./data/cache/amz_br_total_products_data_processed.csv
"""

import hashlib
import re
import os
import sys
import pandas as pd

# ─────────────────────────────────────────────
# Paths
# ─────────────────────────────────────────────
BRAZIL_CSV   = "./dataset-generate/data/cache/amazon_products.csv"
OUTPUT_SQL   = "./catalog-service/bootstrap/src/main/resources/data/seed.sql"

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
NON_BRAND_WORDS = _PORTUGUESE_STOPWORDS | _PRODUCT_GENERIC_WORDS


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
    # Rejeitar palavras que terminam com sufixos de adjetivos/substantivos PT-BR
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


def deterministic_id(name: str) -> str:
    return str(int(hashlib.md5(name.encode("utf-8")).hexdigest(), 16) % 100000)


def slug(name: str) -> str:
    s = re.sub(r"[^\w\s-]", "", name.lower())
    return re.sub(r"[-\s]+", "-", s).strip("-")


def esc(value: str) -> str:
    return value.replace("'", "''")


# ─────────────────────────────────────────────
# 1. Categorias
# ─────────────────────────────────────────────
def load_categories(csv_path: str) -> list[dict]:
    print("📂 Extraindo categorias...")
    df = pd.read_csv(csv_path, usecols=["categoryName"], dtype=str)
    unique_names = sorted(df["categoryName"].dropna().unique())
    cats, seen = [], {}
    for name in unique_names:
        name = name.strip()
        if not name:
            continue
        cat_id = deterministic_id(name)
        if cat_id in seen and seen[cat_id] != name:
            cat_id = str(int(cat_id) + 1)
        seen[cat_id] = name
        cats.append({"id": cat_id, "name": name, "path": f"/{slug(name)}"})
    print(f"  ✅ {len(cats)} categorias")
    return cats


# ─────────────────────────────────────────────
# 2. Brands — extraídas dos títulos (pipeline 4 camadas)
# ─────────────────────────────────────────────
def extract_brand_from_title(title: str) -> str | None:
    """
    Percorre as primeiras palavras do título e retorna a primeira que
    pareça um nome de marca.

    Pipeline de validação (4 camadas):
      1. Stopwords PT-BR (preposições, artigos, conectivos)
      2. Palavras genéricas de produto (NON_BRAND_WORDS)
      3. Heurística de sufixos (rejeita adjetivos/substantivos comuns)
      4. Normalização case → UPPER (determinístico)
    """
    if not isinstance(title, str):
        return None
    for word in title.strip().split()[:4]:
        clean = re.sub(r"[^\w]", "", word).strip()
        if len(clean) < 2 or clean[0].isdigit():
            continue
        # Camada 1+2: stopwords + palavras genéricas
        if clean.lower() in NON_BRAND_WORDS:
            continue
        # Camada 3: heurística de sufixos
        if not _is_likely_brand(clean):
            continue
        # Camada 4 (parcial): normalização case determinística
        return clean.upper()
    return None


def load_brands(csv_path: str, min_count: int = 5, max_brands: int = 1000) -> list[dict]:
    print("📂 Extraindo marcas dos títulos (pipeline 4 camadas)...")
    df = pd.read_csv(csv_path, usecols=["title"], dtype=str)
    extracted = df["title"].apply(extract_brand_from_title).dropna()
    raw_counts = extracted.value_counts()

    # Camada 4: agrupar por stem para eliminar variantes morfológicas
    # Ex: "CARRAPATO" e "CARRAPATOS" → stem "carrapato" → contagem somada
    from collections import defaultdict
    stem_groups: dict[str, dict[str, int]] = defaultdict(dict)
    for name, count in raw_counts.items():
        stem = _simple_stem(name)
        stem_groups[stem][name] = count

    # Para cada grupo de stem, usar o nome mais frequente como canônico
    brands = []
    for stem, variants in stem_groups.items():
        total_count = sum(variants.values())
        if total_count < min_count:
            continue
        # Nome canônico = variante com mais ocorrências
        canonical = max(variants, key=variants.get)
        brands.append({
            "id":          canonical,
            "name":        canonical,
            "description": f"Marca com {total_count} produtos no catálogo",
            "_count":      total_count,
        })

    # Ordenar por contagem e limitar
    brands.sort(key=lambda b: b["_count"], reverse=True)
    brands = brands[:max_brands]

    # Remover campo auxiliar _count
    for b in brands:
        del b["_count"]

    # Garantir fallback "OUTROS" para produtos sem marca identificada
    if not any(b["id"] == "OUTROS" for b in brands):
        brands.append({
            "id":          "OUTROS",
            "name":        "OUTROS",
            "description": "Marca não identificada",
        })

    print(f"  ✅ {len(brands)} marcas (min {min_count} ocorrências)")
    return brands


# ─────────────────────────────────────────────
# 3. Sellers — gerados por cluster de categorias
# ─────────────────────────────────────────────

# Cada entrada: (regex_de_categorias, id, nome, tipo, score, total, pos, neu, neg, cancel, delivery)
SELLER_TEMPLATES = [
    (r"eletrôn|eletr[oô]n|inform[aá]t|comput|notebook|celular|smartphone|tv\b|[áa]udio|c[âa]mera|game|console|smartwatch|impressora|monitor",
     "TechStore",  "TechStore Brasil",        "PROFESSIONAL", 4.9, 2000, 1950,  40, 10, 0.01, 0.99),
    (r"moda|roupa|cal[çc]ado|sapato|bolsa|j[oó]ia|rel[oó]gio|fashion|vestu[áa]rio|lingerie|pijama",
     "ModaBrasil", "Moda Brasil Online",      "PROFESSIONAL", 4.5, 1200, 1100,  80, 20, 0.03, 0.97),
    (r"esporte|lazer|fitness|outdoor|aventura|bicicleta|ciclismo|nata[çc][aã]o|futebol|corrida|academia",
     "SportBr",    "Sport Center Brasil",     "PROFESSIONAL", 4.7,  900,  820,  60, 20, 0.02, 0.98),
    (r"casa|cozinha|cama|m[oó]vel|decora[çc]|ilumina|jardim|limpeza|organiza[çc]",
     "CasaDecor",  "Casa & Decor Online",     "PROFESSIONAL", 4.6, 1100, 1020,  60, 20, 0.03, 0.97),
    (r"beleza|sa[úu]de|cuidado|perfume|cosm[eé]t|medicament|farm[aá]cia|higiene",
     "BelezaBr",   "Beleza e Saúde Brasil",   "INDIVIDUAL",   4.3,  700,  620,  60, 20, 0.05, 0.95),
    (r"beb[êe]|infant|brinquedo|crian[çc]a|escolar",
     "BebeFeliz",  "Bebê Feliz Shop",         "INDIVIDUAL",   4.8,  500,  465,  25, 10, 0.02, 0.98),
    (r"aliment|bebida|mercearia|gourmet|suplemento|nutri[çc]",
     "NutriShop",  "Nutri Shop Brasil",       "INDIVIDUAL",   4.2,  400,  350,  35, 15, 0.05, 0.95),
    (r"pet\b|animal|veterin|canino|felino|aqu[áa]rio|\brave\b",
     "PetAmigo",   "Pet Amigo Shop",          "INDIVIDUAL",   4.6,  600,  560,  30, 10, 0.02, 0.98),
    (r"automotiv|carro|moto\b|pe[çc]a auto|pneu|[oó]leo lubrificante",
     "AutoShop",   "Auto Shop Online",        "PROFESSIONAL", 4.5,  600,  555,  35, 10, 0.02, 0.98),
    (r"livro|papelaria|escrit[oó]rio|m[uú]sica|instrumento|arte|artesanat",
     "CulturaBr",  "Cultura Brasil",          "INDIVIDUAL",   4.6,  350,  320,  22,  8, 0.03, 0.97),
    (r"ferramenta|constru[çc]|industrial|material|el[eé]tric|hidr[aá]ulic",
     "FerroPro",   "Ferramentas Pro",         "PROFESSIONAL", 4.7,  450,  415,  25, 10, 0.01, 0.99),
]

# Seller genérico que sempre estará presente (para categorias não mapeadas)
DEFAULT_SELLER = ("Marketplace", "Marketplace Brasil", "PROFESSIONAL", "ACTIVE",
                  4.5, 1000, 920, 60, 20, 0.02, 0.98)


def generate_sellers(categories: list[dict]) -> list[tuple]:
    print("📂 Gerando sellers por cluster de categorias...")
    category_text = " ".join(c["name"].lower() for c in categories)
    sellers = []
    for (pattern, sid, name, stype, score, total, pos, neu, neg, cancel, delivery) in SELLER_TEMPLATES:
        if re.search(pattern, category_text, re.IGNORECASE):
            sellers.append((sid, name, stype, "ACTIVE", score, total, pos, neu, neg, cancel, delivery))
    sellers.append(DEFAULT_SELLER)
    print(f"  ✅ {len(sellers)} sellers")
    return sellers


# ─────────────────────────────────────────────
# 4. Gerar SQL
# ─────────────────────────────────────────────
def generate_sql(categories, brands, sellers) -> str:
    lines = [
        "-- ============================================================",
        "-- seed.sql — Gerado automaticamente por generate_seed.py",
        "-- Dataset: Amazon Brazil Products 2023 (asaniczka)",
        f"-- Categorias: {len(categories)} | Marcas: {len(brands)} | Sellers: {len(sellers)}",
        "-- ============================================================",
        "",
        "TRUNCATE sellers, brands, categories CASCADE;",
        "",
        f"-- 1. Categorias ({len(categories)})",
        "INSERT INTO categories (id, name, path, parent_id) VALUES",
    ]
    lines.append(",\n".join(
        f"('{esc(c['id'])}', '{esc(c['name'])}', '{esc(c['path'])}', NULL)"
        for c in categories
    ) + ";")

    lines += [
        "",
        f"-- 2. Marcas ({len(brands)})",
        "INSERT INTO brands (id, name, description) VALUES",
    ]
    lines.append(",\n".join(
        f"('{esc(b['id'])}', '{esc(b['name'])}', '{esc(b['description'])}')"
        for b in brands
    ) + ";")

    lines += [
        "",
        f"-- 3. Sellers ({len(sellers)})",
        "-- total_reviews = positive + neutral + negative",
        "INSERT INTO sellers (id, name, type, status, score, total_reviews, "
        "positive_reviews, neutral_reviews, negative_reviews, cancellation_rate, delivery_performance) VALUES",
    ]
    lines.append(",\n".join(
        f"('{esc(s[0])}', '{esc(s[1])}', '{s[2]}', '{s[3]}', "
        f"{s[4]}, {s[5]}, {s[6]}, {s[7]}, {s[8]}, {s[9]}, {s[10]})"
        for s in sellers
    ) + ";")

    return "\n".join(lines) + "\n"


# ─────────────────────────────────────────────
# Main
# ─────────────────────────────────────────────
def main():
    if not os.path.exists(BRAZIL_CSV):
        print(f"❌ CSV não encontrado: {BRAZIL_CSV}")
        print("   Coloque amazon_products.csv em ./data/cache/")
        sys.exit(1)

    categories = load_categories(BRAZIL_CSV)
    brands     = load_brands(BRAZIL_CSV)
    sellers    = generate_sellers(categories)
    sql        = generate_sql(categories, brands, sellers)

    out = os.path.abspath(OUTPUT_SQL)
    os.makedirs(os.path.dirname(out), exist_ok=True)
    with open(out, "w", encoding="utf-8") as f:
        f.write(sql)

    print(f"\n✅ seed.sql gerado: {out}")
    print("\nPróximos passos:")
    print("  docker compose down && docker compose up catalog-db -d")


if __name__ == "__main__":
    main()
