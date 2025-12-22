# Script de População de Produtos via API

Este script cria produtos de teste no sistema via API REST. Os produtos são salvos no PostgreSQL e automaticamente indexados no Elasticsearch via CDC (Debezium).

## 🔄 Fluxo

```
Script Python → API REST → PostgreSQL → Debezium (CDC) → Kafka → Consumer → Elasticsearch
```

## 📋 Pré-requisitos

1. **Infraestrutura rodando**:
```bash
docker compose up -d
```

2. **Debezium configurado**:
```bash
chmod +x docker/debezium/register-postgres-connector.sh
./docker/debezium/register-postgres-connector.sh
```

3. **API rodando**:
```bash
mvn spring-boot:run
# ou
java -jar bootstrap/target/bootstrap-1.0.0-SNAPSHOT.jar
```

4. **Dependências Python**:
```bash
pip install requests faker
```

## 🚀 Executar o Script

```bash
# Tornar executável
chmod +x scripts/populate_elasticsearch.py

# Executar
python3 scripts/populate_elasticsearch.py
```

## 📊 O que o script faz

1. ✅ Verifica se API e Elasticsearch estão rodando
2. 📦 Gera 100 produtos aleatórios com:
   - IDs únicos (formato MLB123456)
   - Categorias, marcas e vendedores variados
   - Preços, descrições e atributos realistas
   - Métricas de vendas, avaliações e estoque
3. 🔄 Cria cada produto via `POST /products`
4. ⏳ Aguarda o CDC processar (5 segundos)
5. 🔍 Verifica quantos produtos foram indexados no Elasticsearch
6. 📈 Mostra estatísticas de sucesso/falha

## 📝 Exemplo de Produto Gerado

```json
{
  "id": "MLB456789",
  "title": "Samsung Smartphone Galaxy A54",
  "description": "Smartphone top de linha...",
  "price": 1899.99,
  "currency": "BRL",
  "category": {
    "id": "cat_2",
    "name": "Smartphones",
    "path": "/eletronicos/smartphones"
  },
  "brand": {
    "id": "brand_1",
    "name": "Samsung",
    "description": "Marca líder em tecnologia"
  },
  "seller": {
    "id": "seller_1",
    "name": "TechStore Brasil",
    "type": "PROFESSIONAL",
    "status": "ACTIVE",
    "reputation": {
      "score": 4.8,
      "total_reviews": 1500,
      "cancellation_rate": 0.02,
      "delivery_performance": 0.98
    }
  },
  "attributes": ["Garantia 1 ano", "Bivolt"],
  "metrics": {
    "stockQuantity": 45,
    "totalViews": 2340,
    "totalSales": 87,
    "averageRating": 4.6,
    "totalReviews": 34,
    "conversionRate": 0.037
  },
  "status": {
    "active": true,
    "suspended": false,
    "hasStock": true
  }
}
```

## 🔍 Verificar Resultados

### Via PostgreSQL
```bash
docker compose exec postgres psql -U marketplace -d marketplace

# Ver produtos
SELECT id, title, price, status FROM products LIMIT 10;

# Contar produtos
SELECT COUNT(*) FROM products;
```

### Via Elasticsearch
```bash
# Contar produtos indexados
curl "http://localhost:9200/products/_count?pretty"

# Ver produtos
curl "http://localhost:9200/products/_search?size=10&pretty"
```

### Via Kibana
1. Acesse: http://localhost:5601
2. Discover → Selecione data view `products*`
3. Visualize os produtos em tempo real

### Via Kafka UI
1. Acesse: http://localhost:8081
2. Topics → `product-events`
3. Ver mensagens sendo processadas

## 🎛️ Configuração

Edite as constantes no início do script:

```python
# URLs
API_URL = "http://localhost:8080"
ELASTICSEARCH_URL = "http://localhost:9200"

# Quantidade de produtos
total_products = 100  # na função main()

# Delay entre requests
time.sleep(0.1)  # ajuste se necessário
```

## 🐛 Troubleshooting

### API não responde
```bash
# Verificar se a API está rodando
curl http://localhost:8080/actuator/health

# Ver logs
tail -f logs/application.log
```

### Produtos não aparecem no Elasticsearch
```bash
# Verificar status do Debezium
curl http://localhost:8083/connectors/marketplace-products-connector/status | jq

# Ver logs do Kafka Connect
docker compose logs -f kafka-connect

# Ver logs do consumer
docker compose logs -f <consumer-service-name>
```

### Muitas falhas ao criar produtos
1. Reduza o número de produtos
2. Aumente o delay entre requests
3. Verifique logs da API para erros de validação

## 📈 Performance

- **100 produtos**: ~15-20 segundos
- **Rate**: ~5-6 produtos/segundo
- **Delay CDC**: 2-5 segundos adicionais

## 🔄 Limpar Dados

```bash
# PostgreSQL
docker compose exec postgres psql -U marketplace -d marketplace -c "DELETE FROM products;"

# Elasticsearch
curl -X POST "http://localhost:9200/products/_delete_by_query?pretty" \
  -H 'Content-Type: application/json' \
  -d '{"query": {"match_all": {}}}'
```

## 🎯 Vantagens da Abordagem via API

✅ **Validação**: Usa as mesmas validações da aplicação real  
✅ **CDC Automático**: Debezium captura e indexa automaticamente  
✅ **Realista**: Simula fluxo real de criação de produtos  
✅ **Transacional**: Garante consistência entre PostgreSQL e Elasticsearch  
✅ **Auditável**: Logs completos do processo  
✅ **Testável**: Valida todo o pipeline end-to-end
