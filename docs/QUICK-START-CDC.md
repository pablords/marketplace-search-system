# Guia Rápido - CDC com Debezium

## Arquitetura

```
API REST → PostgreSQL → Debezium (CDC) → Kafka → Consumer → Elasticsearch → Kibana
```

## Iniciar o Sistema

### 1. Subir infraestrutura

```bash
# Subir todos os serviços
docker compose up -d

# Aguardar inicialização (30-60 segundos)
docker compose ps
```

### 2. Registrar conector Debezium

```bash
# Dar permissão ao script
chmod +x docker/debezium/register-postgres-connector.sh

# Registrar conector (aguardar ~30s após docker compose up)
./docker/debezium/register-postgres-connector.sh

# Verificar status
curl http://localhost:8083/connectors/marketplace-products-connector/status
```

## Testar o CDC

### 1. Inserir produto no PostgreSQL

```bash
# Conectar ao PostgreSQL
docker compose exec postgres psql -U marketplace -d marketplace

# Inserir produto
INSERT INTO products (
    id, title, description, price, currency, available_quantity,
    condition, status, category_id, category_name
) VALUES (
    'TEST001',
    'Produto Teste CDC',
    'Testando Change Data Capture',
    199.99,
    'BRL',
    50,
    'NEW',
    'ACTIVE',
    'CAT001',
    'Eletrônicos'
);

# Sair
\q
```

### 2. Verificar no Elasticsearch

```bash
# Via curl
curl "http://localhost:9200/products/_search?pretty"

# Ver todos os produtos
curl "http://localhost:9200/products/_search?size=100&pretty"
```

### 3. Ver no Kibana

1. Acesse: http://localhost:5601
2. Menu → Management → Stack Management → Data Views
3. Criar Data View:
   - Index pattern: `products*`
   - Timestamp field: `@timestamp` ou deixe vazio
   - Save
4. Menu → Analytics → Discover
5. Visualize os produtos indexados em tempo real!

## Monitoramento

### Kafka UI
- URL: http://localhost:8081
- Ver tópicos e mensagens

### Kafka Connect
```bash
# Status do conector
curl http://localhost:8083/connectors/marketplace-products-connector/status | jq

# Logs
docker compose logs -f kafka-connect
```

### PostgreSQL
```bash
# Ver produtos
docker compose exec postgres psql -U marketplace -d marketplace -c "SELECT id, title, price FROM products;"

# Ver slots de replicação
docker compose exec postgres psql -U marketplace -d marketplace -c "SELECT * FROM pg_replication_slots;"
```

## Endpoints da API

### Criar produto via API REST

```bash
curl -X POST http://localhost:8080/products \
  -H "Content-Type: application/json" \
  -d '{
    "id": "MLB123",
    "title": "Smartphone Samsung Galaxy S23",
    "description": "Smartphone top de linha",
    "price": 3499.99,
    "currency": "BRL",
    "availableQuantity": 50,
    "category": {
      "id": "CAT001",
      "name": "Eletrônicos",
      "path": "Eletrônicos > Celulares"
    },
    "brand": {
      "id": "BRAND001",
      "name": "Samsung"
    },
    "seller": {
      "id": "SELLER001",
      "name": "TechStore",
      "type": "PROFESSIONAL"
    }
  }'
```

## Portas dos Serviços

- **PostgreSQL**: 5432
- **Elasticsearch**: 9200
- **Kibana**: 5601
- **Kafka**: 9092
- **Kafka UI**: 8081
- **Kafka Connect**: 8083
- **API**: 8080

## Troubleshooting

### Conector não aparece
```bash
# Ver logs
docker compose logs -f kafka-connect

# Recriar conector
curl -X DELETE http://localhost:8083/connectors/marketplace-products-connector
./docker/debezium/register-postgres-connector.sh
```

### Eventos não chegam no Kafka
```bash
# Verificar tópico
docker compose exec kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic product-events \
  --from-beginning
```

### Elasticsearch não indexa
```bash
# Ver logs do consumer
docker compose logs -f <consumer-service-name>

# Health do Elasticsearch
curl http://localhost:9200/_cluster/health?pretty
```

## Parar o Sistema

```bash
# Parar todos os serviços
docker compose down

# Parar e remover volumes (CUIDADO: apaga dados)
docker compose down -v
```
