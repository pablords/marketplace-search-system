#!/usr/bin/env python3
"""
Script para popular o banco de dados com produtos de teste via API REST.
Os produtos serão salvos no PostgreSQL e automaticamente indexados no Elasticsearch via CDC (Debezium).
"""

import json
import random
import requests
from datetime import datetime, timedelta
from faker import Faker
import uuid
import time

# Configuração da API
API_URL = "http://localhost:8080/api/v1"
PRODUCTS_ENDPOINT = f"{API_URL}/products"

# Configuração do Elasticsearch (para verificação)
ELASTICSEARCH_URL = "https://elasticsearch.pablolab.online:9200"
INDEX_NAME = "products"

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
    {"id": "brand_1", "name": "Samsung", "description": "Marca líder em tecnologia"},
    {"id": "brand_2", "name": "Apple", "description": "Inovação e design"},
    {"id": "brand_3", "name": "Dell", "description": "Computadores de qualidade"},
    {"id": "brand_4", "name": "Nike", "description": "Just do it"},
    {"id": "brand_5", "name": "Adidas", "description": "Impossible is nothing"},
    {"id": "brand_6", "name": "Sony", "description": "Entretenimento e tecnologia"},
    {"id": "brand_7", "name": "LG", "description": "Life's good"},
    {"id": "brand_8", "name": "Xiaomi", "description": "Tecnologia acessível"},
    {"id": "brand_9", "name": "Lenovo", "description": "For those who do"},
    {"id": "brand_10", "name": "HP", "description": "Keep reinventing"}
]

SELLERS = [
    {"id": "seller_1", "name": "TechStore Brasil", "type": "PROFESSIONAL", "status": "ACTIVE", 
     "reputation": {"score": 4.8, "total_reviews": 1500, "cancellation_rate": 0.02, "delivery_performance": 0.98}},
    {"id": "seller_2", "name": "Casa & Decoração Ltda", "type": "PROFESSIONAL", "status": "ACTIVE",
     "reputation": {"score": 4.5, "total_reviews": 1200, "cancellation_rate": 0.03, "delivery_performance": 0.95}},
    {"id": "seller_3", "name": "Fashion Store", "type": "INDIVIDUAL", "status": "ACTIVE",
     "reputation": {"score": 4.2, "total_reviews": 800, "cancellation_rate": 0.05, "delivery_performance": 0.92}},
    {"id": "seller_4", "name": "Livraria Digital", "type": "PROFESSIONAL", "status": "ACTIVE",
     "reputation": {"score": 4.9, "total_reviews": 2000, "cancellation_rate": 0.01, "delivery_performance": 0.99}},
    {"id": "seller_5", "name": "Sport Center", "type": "PROFESSIONAL", "status": "ACTIVE",
     "reputation": {"score": 4.6, "total_reviews": 1100, "cancellation_rate": 0.02, "delivery_performance": 0.96}}
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
    """Gera um produto aleatório no formato da API"""
    product_id = f"MLB{random.randint(100000, 999999)}"
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
    attributes = []
    if "Eletrônicos" in category["path"]:
        attributes.extend([
            "Garantia de 1 ano",
            "Bivolt",
            f"Cor: {random.choice(['Preto', 'Branco', 'Prata', 'Azul', 'Vermelho'])}",
            f"Modelo: {fake.bothify('??-###')}"
        ])
        if "Smartphones" in category["name"]:
            attributes.extend([
                f"Memória: {random.choice(['64GB', '128GB', '256GB', '512GB'])}",
                f"RAM: {random.choice(['4GB', '6GB', '8GB', '12GB'])}",
                f"Câmera: {random.choice(['12MP', '48MP', '64MP', '108MP'])}",
                f"Tela: {random.choice(['5.5 pol', '6.1 pol', '6.4 pol', '6.7 pol'])}"
            ])
        elif "Notebooks" in category["name"]:
            attributes.extend([
                f"Processador: {random.choice(['Intel i5', 'Intel i7', 'AMD Ryzen 5', 'AMD Ryzen 7'])}",
                f"RAM: {random.choice(['8GB', '16GB', '32GB'])}",
                f"Armazenamento: {random.choice(['256GB SSD', '512GB SSD', '1TB SSD', '1TB HDD'])}",
                f"Tela: {random.choice(['14 pol', '15.6 pol', '17.3 pol'])}"
            ])
    elif "Roupas" in category["path"]:
        attributes.extend([
            f"Tamanho: {random.choice(['PP', 'P', 'M', 'G', 'GG', 'XGG'])}",
            f"Cor: {random.choice(['Preto', 'Branco', 'Azul', 'Vermelho', 'Verde', 'Rosa', 'Amarelo'])}",
            f"Material: {random.choice(['Algodão', 'Poliéster', 'Viscose', 'Linho', 'Jeans'])}",
            f"Gênero: {random.choice(['Masculino', 'Feminino', 'Unissex'])}"
        ])
    elif "Esportes" in category["path"]:
        attributes.extend([
            f"Tamanho: {random.randint(35, 44)}",
            f"Material: {random.choice(['Sintético', 'Couro', 'Tecido', 'Mesh'])}",
            f"Cor: {random.choice(['Preto', 'Branco', 'Azul', 'Vermelho', 'Verde'])}",
            f"Tipo: {random.choice(['Corrida', 'Casual', 'Futebol', 'Basquete', 'Caminhada'])}"
        ])
    elif "Casa" in category["path"]:
        attributes.extend([
            f"Material: {random.choice(['Madeira', 'Metal', 'Plástico', 'Vidro', 'Cerâmica'])}",
            f"Cor: {random.choice(['Branco', 'Preto', 'Marrom', 'Bege', 'Cinza'])}",
            f"Dimensões: {random.randint(10, 200)}x{random.randint(10, 200)}cm"
        ])
    else:
        # Atributos genéricos
        attributes.extend([
            f"Cor: {random.choice(['Preto', 'Branco', 'Azul', 'Vermelho', 'Verde'])}",
            f"Material: {random.choice(['Plástico', 'Metal', 'Madeira', 'Tecido'])}",
            "Produto nacional"
        ])
    
    # Gera tags
    tags = [fake.word() for _ in range(random.randint(2, 5))]
    
    # Gera métricas
    stock_quantity = random.randint(5, 100)
    total_reviews = random.randint(10, 500)
    
    # Calcula reviews de forma que a soma seja exata
    positive_percentage = random.uniform(0.6, 0.9)
    negative_percentage = random.uniform(0.05, 0.15)
    neutral_percentage = 1.0 - positive_percentage - negative_percentage
    
    positive_reviews = int(total_reviews * positive_percentage)
    negative_reviews = int(total_reviews * negative_percentage)
    neutral_reviews = total_reviews - positive_reviews - negative_reviews
    
    # Garante que a soma seja exata (ajusta o neutral se necessário)
    if positive_reviews + negative_reviews + neutral_reviews != total_reviews:
        neutral_reviews = total_reviews - positive_reviews - negative_reviews
    
    # Monta o payload no formato esperado pela API (ProductDTO)
    product = {
        "id": product_id,
        "title": title,
        "description": description,
        "price": price,
        "currency": "BRL",
        "stock_quantity": stock_quantity,  # Campo correto do ProductDTO
        "condition": random.choice(["NEW", "USED", "REFURBISHED"]),
        "is_active": random.choice([True, False]),  # Campo correto do ProductDTO
        
        # Categoria (como objeto CategoryDTO)
        "category": {
            "id": category["id"],
            "name": category["name"],
            "path": category["path"],
            "parent_id": category.get("parent_id")
        },
        
        # Marca (como objeto BrandDTO)
        "brand": {
            "id": brand["id"],
            "name": brand["name"],
            "description": brand["description"]
        },
        
        # Vendedor (como objeto SellerDTO)
        "seller": {
            "id": seller["id"],
            "name": seller["name"],  # Campo correto é 'name', não 'nickname'
            "type": seller["type"],
            "status": seller["status"],
            "reputation": {
                "score": seller["reputation"]["score"],
                "total_reviews": total_reviews,
                "positive_reviews": positive_reviews,
                "neutral_reviews": neutral_reviews,
                "negative_reviews": negative_reviews,
                "cancellation_rate": seller["reputation"]["cancellation_rate"],
                "delivery_performance": seller["reputation"]["delivery_performance"]
            }
        },
        
        # Outros campos
        "images": images,
        "attributes": attributes,  # Set<String> no backend
        "tags": tags              # Set<String> no backend
    }
    
    return product

def create_product_via_api(product):
    """Cria um produto via API REST usando o ProductCommandController endpoint /products"""
    try:
        headers = {"Content-Type": "application/json"}
        response = requests.post(PRODUCTS_ENDPOINT, json=product, headers=headers, timeout=10)
        
        if response.status_code in [201, 200]:
            return True, None
        else:
            return False, f"Status {response.status_code}: {response.text}"
    except requests.exceptions.RequestException as e:
        return False, str(e)

def main():
    """Função principal"""
    print("🚀 Iniciando criação de produtos via API REST...")
    print(f"📍 API URL: {API_URL}")
    print(f"📍 Elasticsearch URL: {ELASTICSEARCH_URL}")
    print()
    
    # Verifica se a API está rodando
    try:
        response = requests.get(f"{API_URL}/actuator/health", timeout=3)
        if response.status_code == 200:
            print("✅ API está rodando!")
        else:
            print("⚠️  API respondeu mas pode não estar saudável")
    except requests.exceptions.ConnectionError:
        print("❌ Não foi possível conectar à API. Verifique se está rodando em localhost:8080")
        return
    except requests.exceptions.Timeout:
        print("⚠️  API demorou para responder, mas vamos tentar continuar...")
    
    # Verifica se o Elasticsearch está rodando
    try:
        response = requests.get(f"{ELASTICSEARCH_URL}/_cluster/health", timeout=3)
        if response.status_code == 200:
            print("✅ Elasticsearch está rodando!")
        else:
            print("⚠️  Elasticsearch respondeu mas pode não estar saudável")
    except requests.exceptions.ConnectionError:
        print(f"❌ Elasticsearch não está acessível em {ELASTICSEARCH_URL}")
        print("⚠️  Os produtos serão criados mas não poderão ser verificados")
    
    print()
    print("📦 Gerando e criando produtos...")
    print("=" * 60)
    
    # Gera e cria produtos
    total_products = 10
    created = 0
    failed = 0
    
    for i in range(total_products):
        product = generate_product()
        
        success, error = create_product_via_api(product)
        
        if success:
            created += 1
            if (i + 1) % 10 == 0:
                print(f"✅ [{i + 1}/{total_products}] Produtos criados: {created} | Falhas: {failed}")
        else:
            failed += 1
            print(f"❌ Erro ao criar produto {product['id']}: {error}")
        
        # Pequeno delay para não sobrecarregar a API
        time.sleep(0.1)
    
    print("=" * 60)
    print()
    print(f"📊 Resumo:")
    print(f"   ✅ Produtos criados com sucesso: {created}")
    print(f"   ❌ Produtos com falha: {failed}")
    print(f"   📈 Taxa de sucesso: {(created/total_products)*100:.1f}%")
    print()
    
    # Aguarda alguns segundos para o Debezium processar
    print("⏳ Aguardando 5 segundos para o CDC (Debezium) processar...")
    time.sleep(5)
    
    # Verifica a indexação no Elasticsearch
    try:
        print("🔍 Verificando indexação no Elasticsearch...")
        response = requests.get(f"{ELASTICSEARCH_URL}/{INDEX_NAME}/_count", timeout=3)
        if response.status_code == 200:
            count = response.json()["count"]
            print(f"✅ Total de produtos indexados no Elasticsearch: {count}")
            
            if count < created:
                print(f"⚠️  Alguns produtos ainda podem estar sendo processados pelo CDC")
                print(f"   Aguarde alguns segundos e verifique novamente")
        else:
            print(f"⚠️  Não foi possível verificar a contagem no Elasticsearch")
    except:
        print("⚠️  Não foi possível verificar o Elasticsearch")
    
    print()
    print("🎉 Script finalizado!")
    print()
    print("🌐 URLs úteis:")
    print(f"   - API Products: {PRODUCTS_ENDPOINT}")
    print(f"   - Swagger UI: {API_URL}/swagger-ui/index.html")
    print(f"   - Elasticsearch Search: {ELASTICSEARCH_URL}/{INDEX_NAME}/_search")
    print(f"   - Kibana: http://localhost:5601")
    print(f"   - Kafka UI: http://localhost:8081")
    print(f"   - Kafka Connect: http://localhost:8083/connectors")

if __name__ == "__main__":
    main()