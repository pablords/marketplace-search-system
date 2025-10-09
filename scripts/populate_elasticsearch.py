#!/usr/bin/env python3
"""
Script para popular o Elasticsearch com 100 produtos de teste
"""

import json
import random
import requests
from datetime import datetime, timedelta
from faker import Faker
import uuid

# Configuração do Elasticsearch
ELASTICSEARCH_URL = "http://localhost:9200"
INDEX_NAME = "marketplace-products-dev"

# Configuração do Faker para gerar dados em português
fake = Faker('pt_BR')

# Dados base para geração
CATEGORIES = [
    {"id": "cat_1", "name": "Eletrônicos", "path": "/eletronicos"},
    {"id": "cat_2", "name": "Smartphones", "path": "/eletronicos/smartphones", "parent_id": "cat_1"},
    {"id": "cat_3", "name": "Notebooks", "path": "/eletronicos/notebooks", "parent_id": "cat_1"},
    {"id": "cat_4", "name": "Casa e Decoração", "path": "/casa-decoracao"},
    {"id": "cat_5", "name": "Móveis", "path": "/casa-decoracao/moveis", "parent_id": "cat_4"},
    {"id": "cat_6", "name": "Roupas", "path": "/roupas"},
    {"id": "cat_7", "name": "Camisetas", "path": "/roupas/camisetas", "parent_id": "cat_6"},
    {"id": "cat_8", "name": "Livros", "path": "/livros"},
    {"id": "cat_9", "name": "Esportes", "path": "/esportes"},
    {"id": "cat_10", "name": "Calçados Esportivos", "path": "/esportes/calcados", "parent_id": "cat_9"}
]

BRANDS = [
    {"id": "brand_1", "name": "Samsung"},
    {"id": "brand_2", "name": "Apple"},
    {"id": "brand_3", "name": "Dell"},
    {"id": "brand_4", "name": "Nike"},
    {"id": "brand_5", "name": "Adidas"},
    {"id": "brand_6", "name": "Sony"},
    {"id": "brand_7", "name": "LG"},
    {"id": "brand_8", "name": "Xiaomi"},
    {"id": "brand_9", "name": "Lenovo"},
    {"id": "brand_10", "name": "HP"}
]

SELLERS = [
    {"id": "seller_1", "name": "TechStore Brasil", "reputation_score": 4.8},
    {"id": "seller_2", "name": "Casa & Decoração Ltda", "reputation_score": 4.5},
    {"id": "seller_3", "name": "Fashion Store", "reputation_score": 4.2},
    {"id": "seller_4", "name": "Livraria Digital", "reputation_score": 4.9},
    {"id": "seller_5", "name": "Sport Center", "reputation_score": 4.6},
    {"id": "seller_6", "name": "Mega Electronics", "reputation_score": 4.7},
    {"id": "seller_7", "name": "Style Fashion", "reputation_score": 4.3},
    {"id": "seller_8", "name": "Home Decor", "reputation_score": 4.4},
    {"id": "seller_9", "name": "Tech Paradise", "reputation_score": 4.8},
    {"id": "seller_10", "name": "Sports World", "reputation_score": 4.5}
]

PRODUCT_NAMES = [
    "Smartphone Galaxy A54", "iPhone 15 Pro", "Notebook Dell Inspiron", "Camiseta Básica",
    "Tênis de Corrida", "Livro de Programação", "Mesa de Escritório", "Cadeira Gamer",
    "Fone de Ouvido Bluetooth", "Smart TV 55 polegadas", "Tablet Android", "Relógio Smartwatch",
    "Câmera Digital", "Mouse Gamer", "Teclado Mecânico", "Monitor 4K", "Impressora Multifuncional",
    "Roteador Wi-Fi", "Caixa de Som Portátil", "Powerbank 20000mAh", "Cabo USB-C", "Capinha para Celular",
    "Película de Vidro", "Carregador Sem Fio", "Suporte para Notebook", "Webcam HD", "Microfone USB",
    "Headset Gamer", "Console de Videogame", "Jogo para PC", "SSD 1TB", "Memória RAM 16GB",
    "Placa de Vídeo", "Processador Intel", "Cooler para CPU", "Fonte de Alimentação", "Gabinete Gamer",
    "HD Externo", "Pen Drive 64GB", "Cartão de Memória", "Adaptador HDMI", "Hub USB", "Dock Station",
    "Mesa Digitalizadora", "Projetor Portátil", "Ar Condicionado", "Ventilador de Mesa", "Umidificador",
    "Purificador de Ar", "Cafeteira Elétrica", "Liquidificador", "Microondas", "Geladeira Duplex",
    "Fogão 4 Bocas", "Lava-Roupas", "Aspirador de Pó", "Ferro de Passar", "Secador de Cabelo",
    "Chapinha para Cabelo", "Barbeador Elétrico", "Escova de Dentes Elétrica", "Balança Digital",
    "Termômetro Digital", "Medidor de Pressão", "Oxímetro", "Nebulizador", "Massageador Elétrico",
    "Colchão Ortopédico", "Travesseiro Viscoelástico", "Jogo de Cama", "Toalha de Banho", "Roupão",
    "Chinelo", "Sandália", "Bota", "Sapato Social", "Tênis Casual", "Mochila", "Bolsa Feminina",
    "Carteira Masculina", "Óculos de Sol", "Relógio de Pulso", "Colar", "Pulseira", "Anel",
    "Brinco", "Perfume", "Maquiagem", "Creme Hidratante", "Protetor Solar", "Shampoo", "Condicionador",
    "Sabonete", "Desodorante", "Pasta de Dente", "Escova de Cabelo", "Espelho", "Organizador",
    "Cesta de Roupas", "Prateleira", "Quadro Decorativo", "Vaso de Plantas", "Luminária", "Abajur"
]

PRODUCT_STATUSES = ["ACTIVE", "INACTIVE", "OUT_OF_STOCK"]

def generate_price_range(price):
    """Gera faixa de preço baseada no valor"""
    if price < 50:
        return "0-50"
    elif price < 100:
        return "50-100"
    elif price < 500:
        return "100-500"
    elif price < 1000:
        return "500-1000"
    else:
        return "1000+"

def generate_product():
    """Gera um produto aleatório"""
    product_id = str(uuid.uuid4())
    category = random.choice(CATEGORIES)
    brand = random.choice(BRANDS)
    seller = random.choice(SELLERS)
    
    price = round(random.uniform(29.99, 2999.99), 2)
    name = random.choice(PRODUCT_NAMES)
    
    # Adiciona variação ao nome baseado na marca
    if category["name"] in ["Smartphones", "Notebooks", "Smart TV"]:
        title = f"{brand['name']} {name}"
    else:
        title = f"{name} {brand['name']}"
    
    # Gera descrição
    description = fake.text(max_nb_chars=200)
    
    # Gera imagens (URLs fictícias)
    num_images = random.randint(1, 5)
    images = [f"https://marketplace.com/images/{product_id}_{i}.jpg" for i in range(num_images)]
    
    # Gera atributos baseados na categoria
    attributes = set()
    if "Eletrônicos" in category["path"]:
        attributes.add("Garantia 1 ano")
        attributes.add("Bivolt")
    if "Roupas" in category["path"]:
        attributes.add(f"Tamanho {random.choice(['P', 'M', 'G', 'GG'])}")
        attributes.add(f"Cor {random.choice(['Preto', 'Branco', 'Azul', 'Vermelho', 'Verde'])}")
    if "Esportes" in category["path"]:
        attributes.add(f"Tamanho {random.randint(35, 44)}")
        attributes.add("Material sintético")
    
    # Gera tags
    tags = {fake.word() for _ in range(random.randint(2, 5))}
    
    # Gera métricas
    views = random.randint(10, 5000)
    sales = random.randint(0, 200)
    rating = round(random.uniform(3.0, 5.0), 1)
    
    # Gera datas
    created_date = fake.date_time_between(start_date='-2y', end_date='now')
    updated_date = fake.date_time_between(start_date=created_date, end_date='now')
    
    popularity_score = round(random.uniform(0.1, 1.0), 2)
    
    product = {
        "id": product_id,
        "title": title,
        "description": description,
        "price": price,
        "currency": "BRL",
        "category": {
            "id": category["id"],
            "name": category["name"],
            "path": category["path"],
            "parent_id": category.get("parent_id")
        },
        "brand": {
            "id": brand["id"],
            "name": brand["name"]
        },
        "seller": {
            "id": seller["id"],
            "name": seller["name"],
            "reputation_score": seller["reputation_score"]
        },
        "images": images,
        "attributes": list(attributes),
        "tags": list(tags),
        "metrics": {
            "views": views,
            "sales": sales,
            "rating": rating,
            "stock_quantity": random.randint(0, 100),
            "conversion_rate": round(random.uniform(0.01, 0.15), 3)
        },
        "status": {
            "value": random.choice(PRODUCT_STATUSES),
            "reason": "Sistema automático"
        },
        "created_at": created_date.isoformat() + "Z",
        "updated_at": updated_date.isoformat() + "Z",
        "searchable_text": f"{title} {description} {brand['name']} {category['name']}",
        "price_range": generate_price_range(price),
        "popularity_score": popularity_score
    }
    
    return product

def create_index():
    """Cria o índice no Elasticsearch com mapping adequado"""
    mapping = {
        "mappings": {
            "properties": {
                "id": {"type": "keyword"},
                "title": {"type": "text", "analyzer": "standard"},
                "description": {"type": "text", "analyzer": "standard"},
                "price": {"type": "double"},
                "currency": {"type": "keyword"},
                "category": {
                    "properties": {
                        "id": {"type": "keyword"},
                        "name": {"type": "text"},
                        "path": {"type": "keyword"},
                        "parent_id": {"type": "keyword"}
                    }
                },
                "brand": {
                    "properties": {
                        "id": {"type": "keyword"},
                        "name": {"type": "text"}
                    }
                },
                "seller": {
                    "properties": {
                        "id": {"type": "keyword"},
                        "name": {"type": "text"},
                        "reputation_score": {"type": "double"}
                    }
                },
                "images": {"type": "keyword"},
                "attributes": {"type": "keyword"},
                "tags": {"type": "keyword"},
                "metrics": {
                    "properties": {
                        "views": {"type": "integer"},
                        "sales": {"type": "integer"},
                        "rating": {"type": "double"},
                        "stock_quantity": {"type": "integer"},
                        "conversion_rate": {"type": "double"}
                    }
                },
                "status": {
                    "properties": {
                        "value": {"type": "keyword"},
                        "reason": {"type": "text"}
                    }
                },
                "created_at": {"type": "date"},
                "updated_at": {"type": "date"},
                "searchable_text": {"type": "text", "analyzer": "standard"},
                "price_range": {"type": "keyword"},
                "popularity_score": {"type": "double"}
            }
        }
    }
    
    # Deleta o índice se já existir
    requests.delete(f"{ELASTICSEARCH_URL}/{INDEX_NAME}")
    
    # Cria o novo índice
    response = requests.put(f"{ELASTICSEARCH_URL}/{INDEX_NAME}", json=mapping)
    if response.status_code not in [200, 201]:
        print(f"Erro ao criar índice: {response.text}")
        return False
    
    print(f"Índice '{INDEX_NAME}' criado com sucesso!")
    return True

def bulk_index_products(products):
    """Indexa produtos em lote no Elasticsearch"""
    bulk_data = []
    
    for product in products:
        # Ação de indexação
        action = {"index": {"_index": INDEX_NAME, "_id": product["id"]}}
        bulk_data.append(json.dumps(action))
        bulk_data.append(json.dumps(product))
    
    bulk_body = "\n".join(bulk_data) + "\n"
    
    headers = {"Content-Type": "application/x-ndjson"}
    response = requests.post(f"{ELASTICSEARCH_URL}/_bulk", data=bulk_body, headers=headers)
    
    if response.status_code == 200:
        result = response.json()
        if result.get("errors"):
            print("Alguns produtos falharam na indexação:")
            for item in result["items"]:
                if "error" in item.get("index", {}):
                    print(f"Erro: {item['index']['error']}")
        else:
            print(f"Todos os {len(products)} produtos foram indexados com sucesso!")
    else:
        print(f"Erro na indexação em lote: {response.text}")

def main():
    """Função principal"""
    print("🚀 Iniciando geração de produtos para teste...")
    
    # Verifica se o Elasticsearch está rodando
    try:
        response = requests.get(f"{ELASTICSEARCH_URL}/_cluster/health")
        if response.status_code != 200:
            print("❌ Elasticsearch não está acessível!")
            return
        print("✅ Elasticsearch está rodando!")
    except requests.exceptions.ConnectionError:
        print("❌ Não foi possível conectar ao Elasticsearch. Verifique se está rodando em localhost:9200")
        return
    
    # Cria o índice
    if not create_index():
        return
    
    # Gera produtos
    print("📦 Gerando 100 produtos...")
    products = []
    for i in range(100):
        product = generate_product()
        products.append(product)
        if (i + 1) % 10 == 0:
            print(f"   Gerados {i + 1}/100 produtos...")
    
    # Indexa produtos em lotes de 10
    print("📝 Indexando produtos no Elasticsearch...")
    batch_size = 10
    for i in range(0, len(products), batch_size):
        batch = products[i:i + batch_size]
        bulk_index_products(batch)
        print(f"   Indexados {min(i + batch_size, len(products))}/100 produtos...")
    
    # Verifica a indexação
    print("🔍 Verificando indexação...")
    response = requests.get(f"{ELASTICSEARCH_URL}/{INDEX_NAME}/_count")
    if response.status_code == 200:
        count = response.json()["count"]
        print(f"✅ Total de produtos indexados: {count}")
    
    print("🎉 Script finalizado com sucesso!")
    print(f"🌐 Você pode verificar os dados em: {ELASTICSEARCH_URL}/{INDEX_NAME}/_search")

if __name__ == "__main__":
    main()