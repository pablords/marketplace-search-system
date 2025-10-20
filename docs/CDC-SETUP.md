# CDC com Debezium - Marketplace Search System

Este documento explica como configurar e usar o Change Data Capture (CDC) com Debezium para sincronizar automaticamente dados do PostgreSQL para o Elasticsearch via Kafka.

## Arquitetura

```
PostgreSQL → Debezium (Kafka Connect) → Kafka → Consumer → Elasticsearch
```

1. **PostgreSQL**: Banco de dados primário onde os produtos são armazenados
2. **Debezium**: Captura mudanças no PostgreSQL via Write-Ahead Log (WAL)
3. **Kafka**: Distribui eventos de mudança
4. **Consumer**: Processa eventos e indexa no Elasticsearch
5. **Elasticsearch**: Índice de busca atualizado em tempo real

## Configuração

### 1. Iniciar os serviços

```bash
# Subir toda a infraestrutura
docker compose up -d

# Verificar se todos os serviços estão rodando
docker compose ps
```

### 2. Registrar o conector Debezium

Aguarde cerca de 30 segundos para o Kafka Connect iniciar completamente, depois execute:

```bash
# Dar permissão de execução ao script
chmod +x docker/debezium/register-postgres-connector.sh

# Registrar o conector
./docker/debezium/register-postgres-connector.sh
```

### 3. Verificar o conector

```bash
# Listar conectores
curl http://localhost:8083/connectors

# Ver status do conector
curl http://localhost:8083/connectors/marketplace-products-connector/status
```

## Como funciona

### Fluxo de dados

1. **INSERT/UPDATE/DELETE no PostgreSQL**: Qualquer mudança na tabela `products`
2. **Debezium captura**: Lê o WAL do PostgreSQL
3. **Publica no Kafka**: Envia evento para o tópico `product-events`
4. **Consumer processa**: Indexa automaticamente no Elasticsearch
5. **Kibana visualiza**: Dados disponíveis em tempo real

### Estrutura da tabela products

```sql
CREATE TABLE products (
    id VARCHAR(255) PRIMARY KEY,
    title VARCHAR(500) NOT NULL,
    description TEXT,
    price DECIMAL(10, 2) NOT NULL,
    currency VARCHAR(10) NOT NULL DEFAULT 'BRL',
    available_quantity INTEGER NOT NULL DEFAULT 0,
    -- ... outros campos
    attributes JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

## Testando o CDC

### 1. Conectar ao PostgreSQL

```bash
docker compose exec postgres psql -U marketplace -d marketplace
```

### 2. Inserir um produto

```sql
INSERT INTO products (
    id, title, description, price, currency, available_quantity, 
    condition, status, category_id, category_name
) VALUES (
    'MLB999', 
    'Produto de Teste CDC', 
    'Este produto testa o Change Data Capture',
    99.99, 
    'BRL', 
    10,
    'NEW', 
    'ACTIVE', 
    'CAT001', 
    'Eletrônicos'
);
```

### 3. Atualizar um produto

```sql
UPDATE products 
SET price = 89.99, available_quantity = 5 
WHERE id = 'MLB999';
```

### 4. Deletar um produto

```sql
DELETE FROM products WHERE id = 'MLB999';
```

### 5. Verificar no Kafka

```bash
# Ver mensagens no tópico product-events
docker compose exec kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic product-events \
  --from-beginning
```

### 6. Verificar no Elasticsearch

```bash
# Via curl
curl "http://localhost:9200/products/_search?pretty"

# Ou via Kibana Dev Tools
GET products/_search
{
  "query": {
    "match_all": {}
  }
}
```

## Monitoramento

### Kafka UI
- URL: http://localhost:8081
- Visualize tópicos, mensagens e consumidores

### Kafka Connect API
```bash
# Status do conector
curl http://localhost:8083/connectors/marketplace-products-connector/status | jq

# Configuração do conector
curl http://localhost:8083/connectors/marketplace-products-connector/config | jq

# Tarefas do conector
curl http://localhost:8083/connectors/marketplace-products-connector/tasks | jq
```

### Kibana
- URL: http://localhost:5601
- Criar Data View para `products*`
- Visualizar dados em tempo real no Discover

## Troubleshooting

### Conector não inicia

```bash
# Ver logs do Kafka Connect
docker compose logs -f kafka-connect

# Reiniciar o conector
curl -X POST http://localhost:8083/connectors/marketplace-products-connector/restart
```

### Eventos não aparecem no Kafka

1. Verificar se WAL está habilitado:
```sql
SHOW wal_level; -- deve retornar 'logical'
```

2. Verificar replicação da tabela:
```sql
SELECT relreplident FROM pg_class WHERE relname = 'products';
-- 'f' = FULL replica identity
```

3. Verificar slot de replicação:
```sql
SELECT * FROM pg_replication_slots;
```

### Dados não indexam no Elasticsearch

1. Verificar logs do consumer:
```bash
docker compose logs -f <nome-do-consumer>
```

2. Verificar health do Elasticsearch:
```bash
curl http://localhost:9200/_cluster/health?pretty
```

## Integração com a aplicação Spring Boot

### application.yml

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/marketplace
    username: marketplace
    password: marketplace123
    driver-class-name: org.postgresql.Driver
  
  jpa:
    hibernate:
      ddl-auto: none  # Schema gerenciado por migrations
    show-sql: true
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        format_sql: true
```

### Dependências Maven

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <scope>runtime</scope>
</dependency>
```

## Vantagens do CDC

1. **Sincronização automática**: Dados sempre atualizados no Elasticsearch
2. **Baixa latência**: Mudanças capturadas em tempo real
3. **Zero impacto**: Não afeta performance do banco de dados
4. **Confiável**: Garante que nenhuma mudança seja perdida
5. **Event-driven**: Permite reagir a mudanças de dados em tempo real
6. **Histórico**: Pode capturar todas as operações (INSERT, UPDATE, DELETE)

## Próximos passos

- [ ] Adicionar dead letter queue para eventos com falha
- [ ] Implementar transformações customizadas no Kafka Connect
- [ ] Configurar schema registry para validação de eventos
- [ ] Adicionar alertas para falhas no conector
- [ ] Implementar backup incremental baseado em CDC
