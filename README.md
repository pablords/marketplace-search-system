# Marketplace Search System

Sistema de busca inteligente para marketplace com arquitetura de microserviços, Machine Learning e indexação em tempo real via CDC.

## 🏗️ Arquitetura

O projeto segue **Arquitetura de Microserviços** com Traefik como gateway único e API Gateway interno para roteamento:

```
marketplace-search-system/
├── traefik/              # 🌐 Traefik Gateway - Gateway único exposto (porta 80/443)
├── api-gateway/          # 🚪 API Gateway - Roteamento interno de requisições
├── catalog-service/      # 📦 Catalog Service - CRUD de produtos
├── indexing-service/     # 🔍 Indexing Service - Indexação via Kafka CDC
├── search-service/       # 🔎 Search Service - Busca com ML ranking
├── ml-ranking-service/   # 🤖 ML Ranking Service - Re-ranking com ML
└── ml-embedding-service/ # 🧠 ML Embedding Service - Geração de embeddings
```

### Componentes

| Serviço | Porta Interna | Acesso Público | Descrição |
|---------|---------------|----------------|-----------|
| **Traefik Gateway** | 8888/443 | ✅ `http://localhost` | Gateway único exposto, cache de borda para search |
| **API Gateway** | 8080 | ❌ Via Traefik | Roteia requisições para os microserviços |
| **Catalog Service** | 8081 | ❌ Via API Gateway | Gerencia produtos no PostgreSQL |
| **Indexing Service** | 8082 | ❌ Privado | Indexa produtos no OpenSearch via Kafka CDC |
| **Search Service** | 8083 | ❌ Via API Gateway | Realiza buscas no OpenSearch com ML ranking |
| **ML Ranking Service** | 8084 | ❌ Privado | Re-ranqueia produtos usando Machine Learning |
| **ML Embedding Service** | 8085 | ❌ Privado | Gera embeddings vetoriais para busca semântica |
| **Grafana** | 3000 | ✅ Via Traefik `/grafana` | Visualização de métricas |
| **OpenSearch Dashboards** | 5601 | ✅ Via Traefik `/opensearch-dashboards` | Dashboard do OpenSearch |

## 🚀 Tecnologias

### Backend
- **Go 1.23+** - API Gateway (Gin Framework)
- **Java 17** + **Spring Boot 3.2.0** - Microserviços Java
- **Maven** - Gerenciamento de dependências Java
- **Arquitetura Hexagonal** - Separação de responsabilidades

### Busca e Indexação
- **OpenSearch 3.x** - Motor de busca principal
- **Apache Kafka 3.6.1** - Eventos e CDC em tempo real
- **Debezium 2.6.0** - Change Data Capture (CDC)

### Machine Learning
- **Python 3.11** - Serviços ML
- **FastAPI** - Framework web assíncrono
- **sentence-transformers** - Modelos de embedding
- **PyTorch** - Backend para modelos de ML

### Infraestrutura
- **Traefik v2.10** - Gateway reverso, load balancer e cache HTTP de borda
- **PostgreSQL 15** - Banco de dados transacional
- **Redis 7** - Cache de alta performance e Feature Store
- **Docker Compose** - Ambiente de desenvolvimento
- **Prometheus** - Coleta e armazenamento de métricas
- **Grafana** - Visualização de métricas e dashboards
- **Jaeger** - Tracing distribuído para observabilidade

## 📦 Funcionalidades

### ✅ Implementado
- [x] **Busca de Produtos** - Busca textual com filtros, ordenação e paginação
- [x] **Busca Híbrida** - Combinação de BM25 (textual) + k-NN (semântica) em paralelo
- [x] **Busca em 2 Fases** - Top 400 candidatos + ML ranking para Top 20
- [x] **Indexação Automática** - Eventos CDC via Kafka (Debezium)
- [x] **Indexação Assíncrona** - Processamento paralelo com ThreadPool
- [x] **Deduplicação de Eventos** - Prevenção de processamento duplicado via Redis
- [x] **Idempotência na Criação** - Verificação de produtos duplicados antes de criar
- [x] **Cache Inteligente** - Redis para consultas frequentes
- [x] **Cache nos Serviços ML** - Redis para embeddings e features nos serviços ML
- [x] **Cache de Borda** - Cache HTTP no Traefik para rotas de search (TTL configurável)
- [x] **Gateway Único** - Traefik como único ponto de entrada exposto
- [x] **Privatização de Serviços** - Todos os serviços privados exceto Traefik, Grafana e OpenSearch Dashboards
- [x] **Tracing Distribuído** - Jaeger para rastreamento de requisições entre serviços
- [x] **Dashboards de Métricas** - Dashboards do Grafana para monitoramento de casos de uso
- [x] **ML Ranking** - Re-ranking com 17 features usando modelo ML
- [x] **Embeddings Vetoriais** - Busca semântica com k-NN
- [x] **Feature Store** - Cache de features ML no Redis
- [x] **Cache Híbrido L1/L2 de Features** - Caffeine (L1 em memória) + Redis (L2) no Feature Store para otimização do ML Ranking
- [x] **Carga/Escrita de Features em Lote** - Pipelining Redis para diminuir a latência de rede no Feature Store
- [x] **Backpressure no Consumidor Kafka** - Controle com ThreadPool `CallerRunsPolicy` e sincronização via `join()` para balancear consumo
- [x] **Debezium Async Register** - Inicialização assíncrona com mecanismo de retries para registro do conector Debezium
- [x] **Logs Estruturados em JSON** - Logback padronizado sem console ANSI para ingestão otimizada no Fluent Bit
- [x] **Inicialização de Índices** - Criação automática de índices k-NN no OpenSearch
- [x] **Métricas & Observabilidade** - Micrometer + Prometheus
- [x] **Arquitetura Hexagonal** - Preparada para microserviços
- [x] **API Gateway** - Roteamento centralizado com OpenAPI
- [x] **Centralização de Logs** - Fluent Bit + OpenSearch + Grafana (Visualização unificada)

### 🔄 Em Desenvolvimento
- [ ] **Dead Letter Queue** - Tratamento de erros persistentes
- [ ] **Circuit Breaker** - Resiliência para serviços downstream (parcialmente implementado no API Gateway)
- [ ] **Retry Automático** - Retry com backoff exponencial (parcialmente implementado no API Gateway)

### 📋 Roadmap
- [ ] **Analytics** - Métricas de busca e comportamento
- [ ] **A/B Testing** - Experimentação de relevância
- [ ] **Personalização Avançada** - Baseada em histórico do usuário
- [ ] **Modelo ML Treinado** - Substituir pesos fixos por modelo treinado

## 🛠️ Setup do Ambiente

### Pré-requisitos
- Go 1.23+ (para API Gateway)
- Java 17+ (para microserviços Java)
- Maven 3.8+
- Docker & Docker Compose
- Python 3.11+ (para serviços ML)

### 1. Clonar e Configurar

```bash
git clone <repository-url>
cd marketplace-search-system
```

### 2. Subir Infraestrutura

```bash
# Subir todos os serviços (Traefik, PostgreSQL, Redis, OpenSearch, Kafka, etc.)
docker-compose up -d

# Verificar se os serviços estão rodando
docker-compose ps

# Ver logs do Traefik
docker-compose logs -f traefik
```

### 3. Compilar e Executar

```bash
# Compilar e executar o API Gateway (Go)
cd api-gateway
go run cmd/gateway/main.go

# Ou compilar o binário
go build -o gateway cmd/gateway/main.go
./gateway

# Executar os microserviços Java (em terminais separados)
mvn spring-boot:run -pl catalog-service/bootstrap
mvn spring-boot:run -pl indexing-service/bootstrap
mvn spring-boot:run -pl search-service/bootstrap

# Executar serviços ML (em terminais separados)
cd ml-ranking-service && python main.py
cd ml-embedding-service && python main.py

# Ou executar os JARs dos microserviços Java
mvn clean package
java -jar catalog-service/bootstrap/target/bootstrap-*.jar
java -jar indexing-service/bootstrap/target/bootstrap-*.jar
java -jar search-service/bootstrap/target/bootstrap-*.jar
```

### 4. Verificar Health

```bash
# Health check via Traefik (gateway único)
curl http://localhost/api/v1/health

# Health check direto dos microserviços (apenas interno)
curl http://localhost:8081/api/v1/actuator/health  # Catalog Service
curl http://localhost:8082/api/v1/actuator/health  # Indexing Service
curl http://localhost:8083/api/v1/actuator/health  # Search Service
curl http://localhost:8084/health                   # ML Ranking Service
curl http://localhost:8085/health                  # ML Embedding Service

# Métricas Prometheus (via Traefik)
curl http://localhost/api/v1/metrics
```

## 🔧 Configuração

### Profiles

- **development** - Ambiente local com logs verbosos
- **production** - Configuração para produção

### Variáveis de Ambiente

```bash
# Database
DATABASE_URL=jdbc:postgresql://localhost:5432/marketplace
DATABASE_USERNAME=catalog
DATABASE_PASSWORD=catalog123

# Redis
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=

# OpenSearch
OPENSEARCH_HOST=localhost
OPENSEARCH_PORT=9200
OPENSEARCH_USERNAME=
OPENSEARCH_PASSWORD=

# Kafka
KAFKA_BOOTSTRAP_SERVERS=localhost:9092

# ML Services
ML_RANKING_SERVICE_URL=http://localhost:8084
EMBEDDING_SERVICE_URL=http://localhost:8085

# Kafka Deduplication (Indexing Service)
KAFKA_DEDUPLICATION_TTL_HOURS=168  # 7 dias (padrão)

# Feature Store Cache (Search Service)
ML_FEATURE_STORE_TTL=3600             # TTL no L2 (Redis) - 1 hora (padrão)
ML_FEATURE_STORE_L1_TTL_SECONDS=300   # TTL no L1 (Caffeine) - 5 minutos (padrão)
ML_FEATURE_STORE_L1_MAX_SIZE=10000    # Tamanho máximo do cache L1 (Caffeine) - 10000 itens (padrão)

# Debezium Connector Configuration (Catalog Service)
DEBEZIUM_DB_NAME=catalog              # Nome do banco de dados exclusivo para registro do Debezium

# Traefik Cache (Cache de Borda)
CACHE_SEARCH_TTL_SECONDS=300  # 5 minutos (padrão) - TTL do cache HTTP para rotas de search

# OpenTelemetry Tracing
OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector:4318
OTEL_SERVICE_NAME=search-service  # Ajustar por serviço
OTEL_METRICS_EXPORTER=none
OTEL_LOGS_EXPORTER=none
OTEL_HTTP_SERVER_EXCLUDED_PATHS=/actuator/prometheus,/actuator/health,/api/v1/actuator/prometheus,/api/v1/actuator/health
OTEL_JAVAAGENT_EXCLUDED_URLS=.*\/actuator\/.*
OTEL_JAVAAGENT_DISABLED_INSTRUMENTATIONS=redis,jedis,lettuce
OTEL_INSTRUMENTATION_METHODS_INCLUDE=com.marketplace.search..*[*]
```

## 📊 Monitoramento

### Acessar Dashboards

**Via Traefik (Gateway Único):**
- **API Gateway**: http://localhost/api/v1
- **Swagger UI**: http://localhost/api/v1/swagger-ui.html
- **Grafana**: http://localhost/grafana (admin/admin)
- **OpenSearch Dashboards**: http://localhost/opensearch-dashboards
- **Jaeger UI**: http://localhost/jaeger
- **Traefik Dashboard**: http://localhost:8080 (apenas interno)

**Serviços Privados (sem acesso público):**
- **OpenSearch**: http://localhost:9200 (apenas interno)
- **Kafka UI**: http://localhost:9091 (apenas interno)
- **Prometheus**: http://localhost:9090 (apenas interno)
- **Debezium UI**: http://localhost:9094 (apenas interno)

### Métricas Principais

- **Latência de Busca** - Tempo de resposta das queries (P95 < 200ms)
- **Throughput** - Requests por segundo
- **Taxa de Cache Hit** - Eficiência do Redis (> 80%)
- **Indexação** - Volume de produtos indexados
- **Consumer Lag** - Lag do Kafka consumer (deve ser ~0)
- **Saúde dos Serviços** - Status dos componentes

### Dashboards do Grafana

Os seguintes dashboards estão disponíveis no Grafana:

1. **Marketplace Search System - Overview**
   - Visão geral do sistema
   - Taxa de requisições (RPS)
   - Taxa de erros
   - Tempo de resposta (P95, P99)
   - Saúde dos serviços

2. **Marketplace Search - Search Performance**
   - Performance de buscas
   - Taxa de requisições de busca
   - Latência de busca (P50, P95, P99)
   - Taxa de cache hit
   - Contagem de resultados
   - Tempo de query no OpenSearch
   - Tempo de ML ranking

3. **Marketplace Search - Indexing Performance**
   - Performance de indexação
   - Lag do consumidor Kafka
   - Taxa de eventos processados
   - Taxa de sucesso de indexação
   - Duração de indexação
   - Tempo de geração de embeddings
   - Taxa de deduplicação

4. **Marketplace Search - API Performance**
   - Performance da API Gateway
   - Taxa de requisições por método
   - Tempo de resposta da API Gateway
   - Status do Circuit Breaker
   - Latência dos serviços downstream
   - Tentativas de retry
   - Taxa de cache hit do Traefik
5. **Marketplace Search - Centralized Logs**
   - Stream de logs em tempo real de todos os containers
   - Busca textual via Lucene
   - Filtros por nível de log (INFO, WARN, ERROR)
   - Correlação direta entre Logs, Traces (Trace ID) e Jaeger

### Tracing com Jaeger

O Jaeger está configurado para rastrear requisições distribuídas entre os serviços:

- **Acesso**: http://localhost/jaeger
- **Funcionalidades**:
  - Visualização de traces completos
  - Análise de latência por serviço
  - Identificação de gargalos
  - Rastreamento de requisições entre serviços

### Alertas do Prometheus

Alertas configurados para:
- Alta taxa de erros nos serviços
- Alta latência (P95 > threshold)
- Baixa taxa de cache hit
- Alto lag do consumidor Kafka
- Serviços indisponíveis
- Alto uso de memória/CPU

## 🔍 API de Busca

### Endpoints Principais (via API Gateway)

#### Criar Produto
```http
POST /api/v1/products
Content-Type: application/json
{
  "id": "MLB123456",
  "title": "Smartphone Samsung Galaxy",
  "description": "Smartphone com 128GB de armazenamento",
  "price": 999.99,
  "currency": "BRL",
  "availableQuantity": 10,
  "condition": "NEW",
  "status": "ACTIVE",
  "category": {
    "id": "eletronicos",
    "name": "Eletrônicos"
  },
  "brand": {
    "id": "samsung",
    "name": "Samsung"
  },
  "seller": {
    "id": "seller_001",
    "name": "TechStore"
  }
}
```

#### Buscar Produtos (com Cache de Borda)
```http
GET /api/v1/search/products?query=smartphone&categoryId=eletronicos&page=0&size=20&sortBy=relevance
```
**Nota**: As rotas de search (`/api/v1/search/*`) têm cache HTTP de borda no Traefik com TTL configurável (padrão: 5 minutos). O cache é baseado na URL completa incluindo query parameters.

#### Health Check
```http
GET /api/v1/health
```

### Filtros Disponíveis

- **Texto**: `query=termo de busca`
- **Categoria**: `categoryId=electronics`
- **Preço**: `minPrice=100&maxPrice=500`
- **Marca**: `brand=samsung`
- **Condição**: `condition=NEW`
- **Vendedor**: `sellerId=seller_001`

### Ordenação

- **Relevância**: `sortBy=relevance` (padrão)
- **Preço**: `sortBy=price&sortDirection=asc|desc`
- **Data**: `sortBy=date&sortDirection=desc`

## 🏗️ Arquitetura Detalhada

### Fluxo de Busca em 2 Fases

O sistema implementa uma busca em 2 fases para otimizar performance e relevância:

**Fase 1: Busca de Candidatos (Top 400)**
- Busca híbrida no OpenSearch: BM25 (textual) + k-NN (semântica) executadas em paralelo
- Embedding da query gerado pelo ML Embedding Service
- Combinação de scores BM25 e k-NN para relevância híbrida
- Retorna até 400 candidatos com scores de relevância
- Filtros aplicados nesta fase

**Fase 2: ML Ranking (Top 20)**
- Extração de 17 features dos candidatos
- Chamada ao ML Ranking Service para re-ranquear
- Retorno dos Top 20 produtos mais relevantes

### Fluxo de Indexação CDC

1. **Criação/Atualização de Produto** → Catalog Service verifica idempotência e salva no PostgreSQL
2. **Debezium captura mudança** → Lê WAL do PostgreSQL
3. **Kafka recebe evento** → Publica no tópico `catalog-db.public.products`
4. **Indexing Service consome** → Verifica deduplicação via Redis (evita reprocessamento)
5. **Geração de Embedding** → Chama ML Embedding Service (com cache Redis)
6. **Indexação no OpenSearch** → Produto indexado com vetor de embedding (k-NN)
7. **Cache de Features** → Features ML armazenadas no Redis

### Traefik Gateway

- **Gateway único exposto** - Único ponto de entrada público (porta 80/443)
- **Cache HTTP de borda** - Cache para rotas de search com TTL configurável
- **Load balancing** - Distribuição de carga entre instâncias
- **Roteamento dinâmico** - Configuração via labels Docker
- **Dashboard administrativo** - Interface web para monitoramento (porta 8080, apenas interno)

### API Gateway

- Implementado em **Go** com Gin Framework
- Roteia requisições para os microserviços (acessível apenas via Traefik)
- Health checks dos serviços downstream
- Documentação OpenAPI/Swagger
- Tratamento de erros e timeouts
- Circuit Breaker e Retry para resiliência

### Catalog Service

- Gerencia produtos no PostgreSQL
- Expõe API REST para CRUD de produtos
- **Idempotência**: Verifica se produto já existe antes de criar (evita duplicação)
- Publica eventos CDC via Debezium/Kafka
- Validações de negócio

### Indexing Service

- Consome eventos CDC do Kafka
- **Deduplicação de Eventos**: Usa Redis para evitar reprocessamento de eventos duplicados
- Processamento assíncrono com ThreadPool
- Indexa produtos no OpenSearch com vetores de embedding (k-NN)
- **Inicialização de Índices**: Cria automaticamente índices k-NN no OpenSearch
- Gera embeddings via ML Embedding Service (com cache Redis)
- Calcula e cacheia features ML no Redis

### Search Service

- Realiza buscas híbridas no OpenSearch (BM25 + k-NN em paralelo)
- Gera embeddings de queries via ML Embedding Service
- Cache Redis para consultas frequentes
- Integração com ML Ranking Service
- Extração de features para ML
- Filtros, ordenação e paginação

### ML Ranking Service

- Re-ranqueia produtos usando 17 features
- **Cache Redis**: Cacheia features e resultados de ranking
- Modelo baseado em pesos fixos (futuro: modelo treinado)
- API REST para ranking de candidatos
- Health check com verificação de Redis

### ML Embedding Service

- Gera embeddings vetoriais para produtos e queries
- **Cache Redis**: Cacheia embeddings gerados para melhor performance
- Modelo: `sentence-transformers/all-MiniLM-L6-v2`
- Dimensão: 384
- Normalização L2
- Health check com verificação de Redis

## 📈 Performance

### Benchmarks Esperados

- **Busca Simples**: < 50ms (P95)
- **Busca Complexa**: < 200ms (P95)
- **Indexação**: > 10k produtos/min
- **Throughput**: > 1000 RPS
- **Cache Hit Rate**: > 80%

### Observabilidade

O sistema possui observabilidade completa com métricas, traces e logs:

**Métricas (Prometheus + Grafana)**
- Métricas de aplicação (Micrometer)
- Métricas de infraestrutura (CPU, memória, rede)
- Dashboards pré-configurados para casos de uso
- Alertas configurados para eventos críticos

**Tracing (Jaeger)**
- Rastreamento distribuído de requisições
- Visualização de spans entre serviços
- Análise de latência por componente
- Identificação de gargalos

**Logs (Fluent Bit + OpenSearch)**
- Logs estruturados (JSON) em todos os serviços
- Agregação centralizada via Fluent Bit (agente de coleta)
- Armazenamento e indexação no OpenSearch para alta performance de busca
- Retenção automática de 7 dias via ILM Policy
- Mascaramento de dados sensíveis (PII) no API Gateway
- Injeção de Trace ID e Span ID para correlação logs-traces

### Otimizações

- **Cache de Borda (Traefik)** - Cache HTTP para rotas de search, reduzindo carga nos serviços backend
- **Cache Redis** - TTL inteligente por tipo de consulta
- **OpenSearch** - Índices otimizados e queries eficientes
- **Kafka** - Processamento assíncrono para indexação
- **ThreadPool** - Processamento paralelo de eventos
- **Connection Pooling** - Configurações ajustadas para alta carga

## 🧪 Testes

```bash
# Unit Tests
mvn test
```

## 🚀 Deploy

### Docker

```bash
# Build da imagem do API Gateway (Go)
cd api-gateway
docker build -t api-gateway:latest .

# Run do container
docker run -p 8080:8080 api-gateway:latest

# Ou usar docker-compose para subir todos os serviços
docker-compose up -d
```


## 🔄 Fluxo de Dados

### Fluxo Completo: Criação de Produto

```mermaid
sequenceDiagram
    participant Client
    participant APIGateway
    participant CatalogService
    participant PostgreSQL
    participant Debezium
    participant Kafka
    participant IndexingService
    participant EmbeddingService
    participant OpenSearch
    participant MLRankingService
    participant Redis

    Client->>APIGateway: POST /api/v1/products
    APIGateway->>CatalogService: POST /api/v1/products (HTTP)
    CatalogService->>PostgreSQL: Verifica se produto existe (idempotência)
    alt Produto já existe
        PostgreSQL-->>CatalogService: Produto existe
        CatalogService-->>APIGateway: 409 Conflict
        APIGateway-->>Client: 409 Conflict
    else Produto não existe
        CatalogService->>PostgreSQL: INSERT produto
        PostgreSQL-->>CatalogService: 201 Created
        CatalogService-->>APIGateway: 201 Created
        APIGateway-->>Client: 201 Created
    end
    
    Note over PostgreSQL,Kafka: Debezium CDC captura mudança
    PostgreSQL->>Debezium: WAL Event
    Debezium->>Kafka: Publica evento CDC
    Kafka->>IndexingService: Consome evento
    IndexingService->>Redis: Verifica deduplicação
    alt Evento duplicado
        Redis-->>IndexingService: Evento já processado
        IndexingService->>Kafka: Acknowledge (ignora)
    else Evento novo
        Redis-->>IndexingService: Evento novo
        IndexingService->>EmbeddingService: Gera embedding
        EmbeddingService->>Redis: Verifica cache de embedding
        alt Cache Hit
            Redis-->>EmbeddingService: Retorna embedding cacheado
        else Cache Miss
            EmbeddingService->>EmbeddingService: Gera embedding (modelo ML)
            EmbeddingService->>Redis: Cacheia embedding
        end
        EmbeddingService-->>IndexingService: Retorna embedding
        IndexingService->>OpenSearch: Indexa produto (com vetor k-NN)
        IndexingService->>Redis: Cacheia features ML
        IndexingService->>Redis: Marca evento como processado
    end
```


### Fluxo Completo: Busca
``` mermaid
sequenceDiagram
    participant Client
    participant APIGateway
    participant SearchService
    participant EmbeddingService
    participant OpenSearch
    participant MLRankingService
    participant Redis

    Client->>APIGateway: GET /api/v1/search/products?q=smartphone
    APIGateway->>SearchService: GET /api/v1/search/products?q=smartphone
    SearchService->>Redis: Verifica cache de resultado
    alt Cache Hit
        Redis-->>SearchService: Retorna resultado cacheado
    else Cache Miss
        SearchService->>EmbeddingService: Gera embedding da query
        EmbeddingService->>Redis: Verifica cache de embedding
        alt Embedding cacheado
            Redis-->>EmbeddingService: Retorna embedding
        else Gera novo embedding
            EmbeddingService->>EmbeddingService: Gera embedding (modelo ML)
            EmbeddingService->>Redis: Cacheia embedding
        end
        EmbeddingService-->>SearchService: Retorna embedding
        Note over SearchService,OpenSearch: Fase 1: Busca Híbrida (BM25 + k-NN em paralelo)
        SearchService->>OpenSearch: Busca BM25 (textual)
        SearchService->>OpenSearch: Busca k-NN (semântica)
        OpenSearch-->>SearchService: Retorna candidatos + scores híbridos
        SearchService->>Redis: Busca features dos candidatos
        Redis-->>SearchService: Retorna features
        SearchService->>MLRankingService: Re-ranqueia com ML
        MLRankingService->>Redis: Verifica cache de ranking
        alt Ranking cacheado
            Redis-->>MLRankingService: Retorna ranking cacheado
        else Calcula novo ranking
            MLRankingService->>MLRankingService: Calcula ranking ML
            MLRankingService->>Redis: Cacheia ranking
        end
        MLRankingService-->>SearchService: Retorna Top 20 ranqueados
        SearchService->>Redis: Cacheia resultado completo
    end
    SearchService-->>APIGateway: Retorna resultados
    APIGateway-->>Client: Retorna resultados
``` 


### Arquitetura Geral

```mermaid
graph TD
    subgraph ClientLayer["Client Layer"]
        CLIENT["Cliente/App"]
    end

    subgraph APIGateway["API Gateway"]
        GATEWAY["API Gateway Go<br/>Porta 8080"]
    end

    subgraph MicroservicosJava["Microservicos Java"]
        CATALOG["Catalog Service<br/>Porta 8081"]
        INDEXING["Indexing Service<br/>Porta 8082"]
        SEARCH["Search Service<br/>Porta 8083"]
    end

    subgraph ServicosML["Servicos ML"]
        ML_RANKING["ML Ranking Service<br/>Porta 8084"]
        ML_EMBEDDING["ML Embedding Service<br/>Porta 8085"]
    end

    subgraph Infraestrutura["Infraestrutura"]
        POSTGRES[("PostgreSQL<br/>Banco de Dados")]
        REDIS[("Redis<br/>Cache + Features")]
        OPENSEARCH[("OpenSearch<br/>Motor de Busca")]
        KAFKA["Kafka<br/>Eventos CDC"]
        DEBEZIUM["Debezium<br/>CDC Connector"]
    end

    CLIENT --> TRAEFIK["Traefik Gateway<br/>Porta 80/443<br/>Cache de Borda"]
    TRAEFIK --> GATEWAY
    GATEWAY --> CATALOG
    GATEWAY --> SEARCH
    
    CATALOG --> POSTGRES
    POSTGRES --> DEBEZIUM
    DEBEZIUM --> KAFKA
    KAFKA --> INDEXING
    
    INDEXING --> ML_EMBEDDING
    INDEXING --> OPENSEARCH
    INDEXING --> REDIS
    
    SEARCH --> OPENSEARCH
    SEARCH --> REDIS
    SEARCH --> ML_RANKING
    
    ML_RANKING --> REDIS
```

## 📝 Script de População de Produtos

Este script cria produtos de teste no sistema via API REST. Os produtos são salvos no PostgreSQL e automaticamente indexados no OpenSearch via CDC (Debezium).

### Fluxo

```
Script Python → API REST → PostgreSQL → Debezium (CDC) → Kafka → Consumer → OpenSearch
```

### Executar

```bash
# Navegar para o diretório do script
cd dataset-generate

# Instalar dependências
pip install -r requirements.txt

# Executar script
python3 data_gen.py
```

## 📚 Documentação Adicional

- [Arquitetura Detalhada](docs/ARCHITECTURE.md)

## 🤝 Contribuição

1. Fork o projeto
2. Crie uma branch para sua feature (`git checkout -b feature/nova-funcionalidade`)
3. Commit suas mudanças (`git commit -am 'Adiciona nova funcionalidade'`)
4. Push para a branch (`git push origin feature/nova-funcionalidade`)
5. Abra um Pull Request

## 📄 Licença

Este projeto está licenciado sob a MIT License - veja o arquivo [LICENSE](LICENSE) para detalhes.

## 📞 Suporte

- **Issues**: [GitHub Issues](link-para-issues)
- **Documentação**: [Wiki](link-para-wiki)
- **Slack**: [#marketplace-search](link-para-slack)
