# Kafka CDC Consumer - Implementação Completa

## 📋 Visão Geral

Foi implementado um **Consumer do Kafka** que processa automaticamente eventos de **Change Data Capture (CDC)** vindos do Debezium, sincronizando produtos do PostgreSQL para o Elasticsearch de forma assíncrona.

## 🏗️ Arquitetura

```
PostgreSQL → Debezium → Kafka → Consumer → Elasticsearch
```

### Fluxo Completo:
1. **Aplicação cria/atualiza/deleta produto** → Salva no PostgreSQL
2. **Debezium captura mudança** → WAL (Write-Ahead Log) do PostgreSQL
3. **Debezium publica no Kafka** → Tópico `catalog-db.public.products`
4. **Consumer processa evento** → `ProductCdcConsumer`
5. **Produto indexado no Elasticsearch** → Via `IndexProductUseCase`

## 📦 Componentes Implementados

### 1️⃣ DTOs (Data Transfer Objects)

#### `DebeziumEventDTO`
- **Localização**: `infrastructure/kafka/dto/DebeziumEventDTO.java`
- **Propósito**: Representa estrutura do evento CDC do Debezium
- **Estrutura**:
  ```json
  {
    "before": {...},  // estado anterior (null em INSERT)
    "after": {...},   // estado atual (null em DELETE)
    "op": "c|u|d|r",  // create, update, delete, read
    "ts_ms": 123456,  // timestamp
    "source": {...}   // info da origem
  }
  ```
- **Métodos auxiliares**:
  - `isCreate()` - Verifica operação de criação
  - `isUpdate()` - Verifica operação de atualização
  - `isDelete()` - Verifica operação de deleção

#### `ProductPayloadDTO`
- **Localização**: `infrastructure/kafka/dto/ProductPayloadDTO.java`
- **Propósito**: Representa estrutura da linha do PostgreSQL
- **Campos**: Todos os campos da tabela `products` (40+ campos)
- **JSONB**: `attributes`, `images`, `tags` (representados como `JsonNode`)

### 2️⃣ Mappers

#### `DebeziumProductMapper`
- **Localização**: `infrastructure/kafka/mappers/DebeziumProductMapper.java`
- **Propósito**: Converte `ProductPayloadDTO` → `ProductDTO`
- **Responsabilidades**:
  - Mapear campos flat do PostgreSQL para objetos aninhados
  - Converter JSONB (`JsonNode`) para `List<String>` e `Set<String>`
  - Converter tipos (Double → BigDecimal)
  - Montar objetos `CategoryDTO`, `BrandDTO`, `SellerDTO`, `SellerReputationDTO`

### 3️⃣ Consumer

#### `ProductCdcConsumer`
- **Localização**: `infrastructure/kafka/consumers/ProductCdcConsumer.java`
- **Propósito**: Consumir eventos do Kafka e sincronizar com Elasticsearch
- **Configuração**:
  - **Tópico**: `catalog-db.public.products` (configurável via `kafka.topics.product-events`)
  - **Group ID**: `marketplace-search-indexer-service`
  - **Acknowledge Mode**: `MANUAL_IMMEDIATE` (controle manual do offset)
  - **Concurrency**: 3 threads

#### Fluxo de Processamento:
```java
@KafkaListener(topics = "${kafka.topics.product-events}", ...)
public void consumeProductEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
    1. Parse envelope Debezium
    2. Identifica tipo de operação (CREATE/UPDATE/DELETE)
    3. Processa baseado na operação:
       - CREATE/UPDATE → indexProductUseCase.execute()
       - DELETE → deleteProductUseCase.execute()
    4. Confirma processamento (ack.acknowledge())
}
```

#### Tratamento de Erros:
- **Erro de parse**: Não confirma mensagem (será reprocessada)
- **Erro de processamento**: Confirma mensagem para não travar a fila
- **TODO**: Implementar Dead Letter Queue (DLQ) para mensagens com erro persistente

### 4️⃣ Use Cases

#### `DeleteProductUseCase`
- **Localização**: `application/usecases/DeleteProductUseCase.java`
- **Propósito**: Remove produto do índice Elasticsearch
- **Fluxo**:
  1. Verifica se produto existe
  2. Remove do índice via `ProductIndexRepository`
  3. Publica evento `ProductDeletedEvent`

#### `ProductDeletedEvent`
- **Localização**: `domain/events/ProductDeletedEvent.java`
- **Propósito**: Evento de domínio para deleção de produto

## ⚙️ Configuração

### `application-development.yml`

```yaml
# Kafka topics
kafka:
  topics:
    product-events: catalog-db.public.products
  consumer:
    group-id: marketplace-search-indexer-service

# Spring Kafka
spring:
  kafka:
    bootstrap-servers: localhost:9092
    consumer:
      group-id: marketplace-search-indexer-service
      auto-offset-reset: earliest
      enable-auto-commit: false
      max-poll-records: 100
      properties:
        session.timeout.ms: 30000
        max.poll.interval.ms: 300000
    producer:
      client-id: marketplace-search-producer
      acks: all
      retries: 3
```

### Logging

```yaml
logging:
  level:
    com.marketplace.search: DEBUG
    org.springframework.kafka: INFO
    org.apache.kafka: WARN
    com.marketplace.search.infrastructure.kafka: DEBUG
```

## 📊 Dependências Maven

### `infrastructure/pom.xml`

```xml
<dependencies>
    <!-- Application module -->
    <dependency>
        <groupId>com.marketplace.search</groupId>
        <artifactId>application</artifactId>
    </dependency>
    
    <!-- Kafka -->
    <dependency>
        <groupId>org.springframework.kafka</groupId>
        <artifactId>spring-kafka</artifactId>
    </dependency>
</dependencies>
```

## 🎯 Benefícios da Implementação

### ✅ Desacoplamento
- API não depende da disponibilidade do Elasticsearch
- Criação de produto é rápida (só persiste no PostgreSQL)
- Elasticsearch pode ficar offline temporariamente

### ✅ Confiabilidade
- **Garantia de entrega**: Kafka persiste mensagens
- **Reprocessamento**: Possível reprocessar eventos (replay)
- **At-least-once delivery**: Mensagens não são perdidas

### ✅ Escalabilidade
- **Consumer Group**: Múltiplas instâncias processam em paralelo
- **Particionamento**: Kafka distribui carga entre consumers
- **Concorrência**: 3 threads por instância

### ✅ Auditoria
- **Event Sourcing**: Kafka mantém histórico de mudanças
- **Rastreabilidade**: Todos os eventos são logados
- **Debugging**: Fácil reprocessar eventos para debug

### ✅ Flexibilidade
- **Múltiplos Consumidores**: Outros sistemas podem reagir aos mesmos eventos
- **Transformações**: Fácil adicionar novos consumidores (analytics, cache, notificações)

## 🚀 Como Testar

### 1. Iniciar Infraestrutura

```bash
cd /Users/pablosantos/estudos/search-system
docker compose up -d
```

### 2. Registrar Connector Debezium

```bash
chmod +x docker/debezium/register-postgres-connector.sh
./docker/debezium/register-postgres-connector.sh
```

### 3. Verificar Connector

```bash
curl http://localhost:8083/connectors/marketplace-products-connector/status | jq
```

### 4. Iniciar Aplicação

```bash
mvn spring-boot:run -pl bootstrap
```

### 5. Criar Produto via API

```bash
# Via Python script
python3 scripts/populate_elasticsearch.py

# Ou via cURL
curl -X POST http://localhost:8080/api/v1/search/products/MLB123/index \
  -H "Content-Type: application/json" \
  -d '{...product data...}'
```

### 6. Verificar Logs do Consumer

Você verá logs como:
```
INFO  c.m.s.i.k.c.ProductCdcConsumer - Received CDC event from topic: catalog-db.public.products
INFO  c.m.s.i.k.c.ProductCdcConsumer - Processing CREATE for product: MLB123
INFO  c.m.s.a.u.IndexProductUseCase - Indexing product: MLB123
INFO  c.m.s.i.k.c.ProductCdcConsumer - Product create indexed successfully: MLB123
INFO  c.m.s.i.k.c.ProductCdcConsumer - CDC event processed successfully
```

### 7. Verificar Produto no Elasticsearch

```bash
curl http://localhost:9200/products/_search?q=MLB123 | jq
```

### 8. Monitorar Kafka

- **Kafka UI**: http://localhost:8081
- **Tópicos**: `catalog-db.public.products`
- **Consumer Groups**: `marketplace-search-indexer-service`

## 📈 Métricas e Monitoramento

### Kafka Consumer Metrics (via JMX)

- `records-consumed-rate`: Taxa de mensagens processadas/s
- `records-lag`: Mensagens pendentes de processamento
- `commit-latency-avg`: Latência média do commit
- `fetch-latency-avg`: Latência média do fetch

### Logs Importantes

```log
# Evento recebido
Received CDC event from topic: catalog-db.public.products, partition: 0, offset: 123

# Processamento
Processing CREATE for product: MLB123
Product create indexed successfully in Elasticsearch: MLB123

# Confirmação
CDC event processed successfully: operation=c, productId=MLB123
```

## 🔧 Troubleshooting

### Consumer não recebe mensagens

1. Verificar se Debezium está rodando:
   ```bash
   curl http://localhost:8083/connectors/marketplace-products-connector/status
   ```

2. Verificar tópico no Kafka:
   ```bash
   docker compose exec kafka kafka-topics --list --bootstrap-server localhost:9092
   ```

3. Verificar mensagens no tópico:
   - Acesse http://localhost:8081 (Kafka UI)
   - Navegue até o tópico `catalog-db.public.products`

### Mensagens processadas mas produto não aparece no Elasticsearch

1. Verificar logs do IndexProductUseCase
2. Verificar conectividade com Elasticsearch:
   ```bash
   curl http://localhost:9200/_cluster/health
   ```

### Mensagens sendo reprocessadas infinitamente

- Implementar Dead Letter Queue (DLQ)
- Adicionar retry policy com backoff exponencial
- Logar mensagens problemáticas para análise manual

## 🎯 Próximos Passos

- [ ] Implementar Dead Letter Queue (DLQ) para mensagens com erro
- [ ] Adicionar retry policy com backoff exponencial
- [ ] Implementar métricas customizadas (Micrometer)
- [ ] Adicionar circuit breaker para Elasticsearch
- [ ] Implementar batch processing para melhor performance
- [ ] Adicionar testes de integração com Testcontainers
- [ ] Implementar idempotência no consumer
- [ ] Adicionar health check do consumer

## 📚 Referências

- [Debezium PostgreSQL Connector](https://debezium.io/documentation/reference/stable/connectors/postgresql.html)
- [Spring Kafka Documentation](https://docs.spring.io/spring-kafka/reference/)
- [Kafka Consumer Configuration](https://kafka.apache.org/documentation/#consumerconfigs)
- [Event-Driven Architecture](https://martinfowler.com/articles/201701-event-driven.html)
