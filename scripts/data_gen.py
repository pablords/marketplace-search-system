#!/usr/bin/env python3
"""
Script refatorado para geração de Dataset de E-commerce.
Garante consistência entre Categoria, Produto e Marca.
"""

import json
import random
from faker import Faker
import requests
import time
from datetime import datetime, timedelta



# Configuração do Faker
fake = Faker('pt_BR')

# ==========================================
# 1. DADOS MESTRES (Infraestrutura)
# ==========================================

SELLERS = [
    {"id": "seller_1", "name": "TechStore Brasil", "type": "PROFESSIONAL", "status": "ACTIVE", 
     "reputation": {"score": 4.8, "total_reviews": 1500, "cancellation_rate": 0.02, "delivery_performance": 0.98}},
    {"id": "seller_2", "name": "Casa & Decoração Ltda", "type": "PROFESSIONAL", "status": "ACTIVE",
     "reputation": {"score": 4.5, "total_reviews": 1200, "cancellation_rate": 0.03, "delivery_performance": 0.95}},
    {"id": "seller_3", "name": "Fashion Store", "type": "INDIVIDUAL", "status": "ACTIVE",
     "reputation": {"score": 4.2, "total_reviews": 800, "cancellation_rate": 0.05, "delivery_performance": 0.92}},
    {"id": "seller_4", "name": "Gamer Pro", "type": "PROFESSIONAL", "status": "ACTIVE",
     "reputation": {"score": 4.9, "total_reviews": 2000, "cancellation_rate": 0.01, "delivery_performance": 0.99}},
]

BRANDS_DB = {
    "brand_apple": {"id": "brand_apple", "name": "Apple", "description": "Inovação e design"},
    "brand_samsung": {"id": "brand_samsung", "name": "Samsung", "description": "Líder em Android"},
    "brand_dell": {"id": "brand_dell", "name": "Dell", "description": "Soluções corporativas"},
    "brand_nike": {"id": "brand_nike", "name": "Nike", "description": "Just do it"},
    "brand_adidas": {"id": "brand_adidas", "name": "Adidas", "description": "Performance esportiva"},
    "brand_herman": {"id": "brand_herman", "name": "Herman Miller", "description": "Ergonomia premium"},
    "brand_tokstok": {"id": "brand_tokstok", "name": "Tok&Stok", "description": "Design acessível"},
    "brand_generic": {"id": "brand_generic", "name": "Genérica", "description": "Custo benefício"},
    "brand_lg": {"id": "brand_lg", "name": "LG", "description": "Life's Good"},
    "brand_sony": {"id": "brand_sony", "name": "Sony", "description": "Make Believe"},
}

CATEGORIES_DB = [
    {"id": "CAT001", "name": "Smartphones", "path": "/eletronicos/smartphones", "parent_id": "CAT001"},
    {"id": "CAT002", "name": "Notebooks", "path": "/eletronicos/notebooks", "parent_id": "CAT001"},
    {"id": "CAT003", "name": "TV e Áudio", "path": "/eletronicos/tv-audio", "parent_id": "CAT001"},
    {"id": "CAT004", "name": "Móveis", "path": "/casa/moveis", "parent_id": "CAT004"},
    {"id": "CAT005", "name": "Decoração", "path": "/casa/decoracao", "parent_id": "CAT004"},
    {"id": "CAT006", "name": "Roupas e Calçados", "path": "/moda/roupas", "parent_id": "CAT004"},
]

# ==========================================
# 2. REGRAS DE CATÁLOGO (Taxonomia)
# ==========================================
# Aqui definimos o que pode ser gerado dentro de cada categoria
CATALOG_RULES = {
    "CAT001": {
        "products": ["iPhone 13", "iPhone 14 Pro", "Galaxy S23", "Galaxy A54", "Redmi Note 12", "Moto G200"],
        "brands": ["brand_apple", "brand_samsung", "brand_lg", "brand_sony"],
        "metrics_seed": {"pop_min": 1000, "pop_max": 9000, "qual_min": 3.8, "qual_max": 4.9},
        "attrs_func": lambda: [
            f"Memória: {random.choice(['128GB', '256GB', '512GB'])}",
            f"Cor: {random.choice(['Preto', 'Branco', 'Dourado', 'Grafite'])}",
            f"Conectividade: {random.choice(['4G', '5G'])}",
            f"Tela: {random.choice(['6.1', '6.7'])} polegadas"
        ]
    },
    "CAT002": {
        "products": ["MacBook Air M2", "MacBook Pro", "Dell XPS 13", "Dell Inspiron", "Samsung Galaxy Book", "Lenovo ThinkPad"],
        "brands": ["brand_apple", "brand_dell", "brand_samsung"],
        "metrics_seed": {"pop_min": 500, "pop_max": 5000, "qual_min": 4.0, "qual_max": 4.9},
        "attrs_func": lambda: [
            f"Processador: {random.choice(['Intel i5', 'Intel i7', 'M2', 'M3'])}",
            f"RAM: {random.choice(['8GB', '16GB', '32GB'])}",
            f"SSD: {random.choice(['256GB', '512GB', '1TB'])}"
        ]
    },
    "CAT003": {
        "products": ["Smart TV 4K", "TV OLED 55", "Soundbar", "Home Theater", "Smart TV 65"],
        "brands": ["brand_samsung", "brand_lg", "brand_sony"],
        "metrics_seed": {"pop_min": 300, "pop_max": 4000, "qual_min": 4.2, "qual_max": 4.8},
        "attrs_func": lambda: [
            f"Resolução: {random.choice(['4K', '8K', 'Full HD'])}",
            f"Polegadas: {random.choice(['43', '50', '55', '65', '75'])}",
            f"Voltagem: {random.choice(['110v', '220v', 'Bivolt'])}"
        ]
    },
    "CAT004": {
        "products": ["Cadeira Gamer", "Mesa de Escritório", "Sofá 3 Lugares", "Estante de Livros", "Cama Box Casal"],
        "brands": ["brand_herman", "brand_tokstok", "brand_generic"],
        "metrics_seed": {"pop_min": 100, "pop_max": 2000, "qual_min": 3.5, "qual_max": 4.7},
        "attrs_func": lambda: [
            f"Material: {random.choice(['Madeira Maciça', 'MDF', 'Aço', 'Couro Sintético'])}",
            f"Cor: {random.choice(['Preto', 'Branco', 'Marrom', 'Cinza'])}",
            "Necessita montagem: Sim"
        ]
    },
    "CAT005": {
        "products": ["Luminária de Mesa", "Quadro Decorativo", "Tapete Sala", "Vaso de Cerâmica", "Espelho Redondo"],
        "brands": ["brand_tokstok", "brand_generic"],
        "metrics_seed": {"pop_min": 50, "pop_max": 1500, "qual_min": 3.9, "qual_max": 4.6},
        "attrs_func": lambda: [
            f"Estilo: {random.choice(['Industrial', 'Clássico', 'Moderno', 'Rústico'])}",
            f"Dimensões: {random.randint(20, 100)}x{random.randint(20, 100)}cm"
        ]
    },
    "CAT006": {
        "products": ["Camiseta Básica", "Tênis de Corrida", "Calça Jeans", "Jaqueta Corta-Vento", "Moletom"],
        "brands": ["brand_nike", "brand_adidas", "brand_generic"],
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
    average_rating = round(quality + random.uniform(-0.5, 0.5), 2)
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

def generate_product_payload():
    """Gera um único produto validado pelas regras de catálogo"""
    
    # 1. Escolher uma categoria alvo que tenha regras definidas
    target_cat_id = random.choice(list(CATALOG_RULES.keys()))
    rule = CATALOG_RULES[target_cat_id]
    
    # 2. Recuperar o objeto categoria completo do DB
    category_obj = next(c for c in CATEGORIES_DB if c["id"] == target_cat_id)
    
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
    
    # 8. Vendedor
    seller = random.choice(SELLERS)
    positive_reviews = random.randint(int(quality_metrics["quality"] / 5.0 * 500), 600)
    neutral_reviews = random.randint(int((1.0 - quality_metrics["quality"] / 5.0) * 300), 400)
    negative_reviews = random.randint(int((1.0 - quality_metrics["quality"] / 5.0) * 200), 300)
    total_reviews = positive_reviews + neutral_reviews + negative_reviews
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
        "id": f"MLB{random.randint(1000000, 9999999)}",
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


def create_products_on_demand(api_url, total_products=10):
    headers = {
        "Content-Type": "application/json" 
    }

    try:
        for index in range(total_products):
            product_payload = generate_product_payload()
            print(f"Enviando produto {index + 1}/{total_products}: {product_payload['id']} available_quantity={product_payload['available_quantity']}")
            response = requests.post(f"{api_url}/products", headers=headers, json=product_payload)
            if response.status_code == 201:
                print(f"Produto criado com sucesso: {product_payload['id']}")
            else:
                print(f"Erro ao criar produto: {response.status_code} - {response.text}")
            time.sleep(index * 0.5)  # Pequena pausa para evitar sobrecarga no servidor
    except Exception as e:
        print(f"Erro na requisição: {e}")


def main():
    create_dataset_file(total_products=10)
    # create_products_on_demand(api_url="http://localhost:8080/api/v1", total_products=1)

if __name__ == "__main__":
    main()