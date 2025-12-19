# Marketplace Search System

Sistema de busca para marketplace com arquitetura de microserviços e API Gateway.

## 🏗️ Arquitetura

O projeto segue **Arquitetura de Microserviços** com API Gateway para roteamento:

```
search-system/
├── api-gateway/         # 🚪 API Gateway - Roteamento de requisições
├── catalog-service/      # 📦 Catalog Service - CRUD de produtos
├── indexing-service/     # 🔍 Indexing Service - Indexação via Kafka
└── search-service/       # 🔎 Search Service - Busca de produtos
```

### Componentes

- **API Gateway** (porta 8080): Roteia requisições para os microserviços
- **Catalog Service** (porta 8081): Gerencia produtos no PostgreSQL
- **Indexing Service** (porta 8082): Indexa produtos no OpenSearch via Kafka CDC
- **Search Service** (porta 8083): Realiza buscas no OpenSearch

## 🚀 Tecnologias

- **Java 17** + **Spring Boot 3.2.0**
- **OpenSearch 8.11.3** - Motor de busca principal
- **Apache Kafka 3.6.1** - Eventos e CDC em tempo real
- **Redis 7** - Cache de alta performance
- **Maven** - Gerenciamento de dependências
- **Docker Compose** - Ambiente de desenvolvimento

## 📦 Funcionalidades

### ✅ Implementado
- [x] **Busca de Produtos** - Busca textual, filtros, ordenação
- [x] **Indexação Automática** - Eventos CDC via Kafka
- [x] **Cache Inteligente** - Redis para consultas frequentes
- [x] **Métricas & Observabilidade** - Micrometer + Prometheus
- [x] **Arquitetura Hexagonal** - Preparada para microserviços

### 🔄 Em Desenvolvimento
- [ ] **API REST** - Controllers e documentação OpenAPI
- [ ] **Consumidores Kafka** - Processamento de eventos
- [ ] **Testes** - Unit, Integration e Performance

### 📋 Roadmap
- [ ] **Analytics** - Métricas de busca e comportamento
- [ ] **Recomendações** - ML para sugestões personalizadas
- [ ] **A/B Testing** - Experimentação de relevância

## 🛠️ Setup do Ambiente

### Pré-requisitos
- Java 17+
- Maven 3.8+
- Docker & Docker Compose

### 1. Clonar e Configurar

```bash
git clone <repository-url>
cd search-system
```

### 2. Subir Infraestrutura

```bash
# Subir todos os serviços (PostgreSQL, Redis, OpenSearch, Kafka, etc.)
docker-compose up -d

# Verificar se os serviços estão rodando
docker-compose ps
```

### 3. Compilar e Executar

```bash
# Compilar o projeto
mvn clean compile

# Executar o API Gateway
mvn spring-boot:run -pl api-gateway/bootstrap

# Executar os microserviços (em terminais separados)
mvn spring-boot:run -pl catalog-service/bootstrap
mvn spring-boot:run -pl indexing-service/bootstrap
mvn spring-boot:run -pl search-service/bootstrap

# Ou executar os JARs
mvn clean package
java -jar api-gateway/bootstrap/target/bootstrap-*.jar
```

### 4. Verificar Health

```bash
# Health check do API Gateway
curl http://localhost:8080/api/v1/actuator/health

# Health check dos microserviços
curl http://localhost:8081/api/v1/actuator/health  # Catalog Service
curl http://localhost:8082/api/v1/actuator/health  # Indexing Service
curl http://localhost:8083/api/v1/actuator/health  # Search Service

# Métricas Prometheus
curl http://localhost:8080/api/v1/actuator/prometheus
```

## 🔧 Configuração

### Profiles

- **development** - Ambiente local com logs verbosos
- **production** - Configuração para produção

### Variáveis de Ambiente

```bash
# Database
DATABASE_URL=jdbc:postgresql://localhost:5432/marketplace_search
DATABASE_USERNAME=marketplace
DATABASE_PASSWORD=marketplace123

# Redis
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=

# OpenSearch
ELASTICSEARCH_HOST=localhost
ELASTICSEARCH_PORT=9200
ELASTICSEARCH_USERNAME=
ELASTICSEARCH_PASSWORD=

# Kafka
KAFKA_BOOTSTRAP_SERVERS=localhost:9092
```

## 📊 Monitoramento

### Acessar Dashboards

- **Aplicação**: http://localhost:8080/api/v1/actuator
- **OpenSearch**: http://localhost:9200
- **Kibana**: http://localhost:5601
- **Kafka UI**: http://localhost:8081
- **Prometheus**: http://localhost:9090
- **Grafana**: http://localhost:3000 (admin/admin)

### Métricas Principais

- **Latência de Busca** - Tempo de resposta das queries
- **Throughput** - Requests por segundo
- **Taxa de Cache Hit** - Eficiência do Redis
- **Indexação** - Volume de produtos indexados
- **Saúde dos Serviços** - Status dos componentes

## 🔍 API de Busca

### Endpoints Principais (via API Gateway)

```http
# Criar produto
POST /api/v1/products
Content-Type: application/json
{
  "id": "prod-123",
  "title": "Smartphone Samsung",
  "price": 999.99,
  ...
}

# Buscar produtos
GET /api/v1/search/products?query=smartphone&categoryId=electronics&page=0&size=20

# Obter sugestões
GET /api/v1/search/suggestions?term=smart&limit=10

# Health check
GET /api/v1/health
```

### Filtros Disponíveis

- **Texto**: `q=termo de busca`
- **Categoria**: `category=electronics`
- **Preço**: `minPrice=100&maxPrice=500`
- **Marca**: `brand=apple`
- **Avaliação**: `minRating=4.0`
- **Frete Grátis**: `freeShipping=true`

### Ordenação

- **Relevância**: `sort=relevance` (padrão)
- **Preço**: `sort=price_asc|price_desc`
- **Data**: `sort=newest|oldest`
- **Popularidade**: `sort=best_sellers|best_rated`

## 🏗️ Arquitetura Detalhada

### API Gateway
- Roteia requisições para os microserviços
- Circuit breaker e retry para resiliência
- Health checks dos serviços downstream
- Documentação OpenAPI/Swagger

### Catalog Service
- Gerencia produtos no PostgreSQL
- Expõe API REST para CRUD de produtos
- Publica eventos CDC via Debezium/Kafka

### Indexing Service
- Consome eventos CDC do Kafka
- Indexa produtos no OpenSearch
- Processamento assíncrono e escalável

### Search Service
- Realiza buscas no OpenSearch
- Cache Redis para consultas frequentes
- Sugestões de busca e autocomplete

## 🧪 Testes

```bash
# Unit Tests
mvn test

# Integration Tests
mvn test -Dtest="*IntegrationTest"

# Performance Tests
mvn test -Dtest="*PerformanceTest"
```

## 📈 Performance

### Benchmarks Esperados
- **Busca Simples**: < 50ms (P95)
- **Busca Complexa**: < 200ms (P95)  
- **Indexação**: > 10k produtos/min
- **Throughput**: > 1000 RPS

### Otimizações
- **Cache Redis** - TTL inteligente por tipo de consulta
- **OpenSearch** - Índices otimizados e queries eficientes
- **Kafka** - Batching para indexação em massa
- **Connection Pooling** - Configurações ajustadas para alta carga

## 🚀 Deploy

### Docker
```bash
# Build da imagem do API Gateway
docker build -t api-gateway:latest .

# Run do container
docker run -p 8080:8080 api-gateway:latest

# Ou usar docker-compose para subir todos os serviços
docker-compose up -d
```

### Kubernetes
```bash
# Apply dos manifestos
kubectl apply -f k8s/

# Verificar pods
kubectl get pods -l app=marketplace-search
```

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

## 🔄 Fluxo de Dados

```mermaid
sequenceDiagram
    participant Client
    participant APIGateway
    participant CatalogService
    participant SearchService
    participant Kafka
    participant IndexingService
    participant OpenSearch

    Client->>APIGateway: POST /api/v1/products
    APIGateway->>CatalogService: POST /api/v1/products (HTTP)
    CatalogService->>CatalogService: Salva no PostgreSQL
    CatalogService-->>APIGateway: 201 Created
    APIGateway-->>Client: 201 Created
    
    Note over CatalogService,Kafka: Debezium CDC captura mudança
    CatalogService->>Kafka: Publica evento CDC
    Kafka->>IndexingService: Consome evento
    IndexingService->>OpenSearch: Indexa produto

    Client->>APIGateway: GET /api/v1/search/products?q=smartphone
    APIGateway->>SearchService: GET /api/v1/search/products?q=smartphone (HTTP)
    SearchService->>OpenSearch: Busca produtos
    OpenSearch-->>SearchService: Retorna resultados
    SearchService-->>APIGateway: Retorna resultados
    APIGateway-->>Client: Retorna resultados
```


```mermaid

graph TD
    subgraph "Fontes de Dados"
        DB["Banco de Dados<br>(Ex: PostgreSQL)"]
    end

    subgraph "Plataforma de Dados"
        CDC[Debezium CDC]
        KAFKA["Apache Kafka<br>Tópico: product-events"]
    end

    subgraph "Backend de Indexação"
        IDX_SVC["Indexing Service<br>(Microserviço Spring Boot)"]
    end

    subgraph "Core da Busca"
        ELASTIC[OpenSearch Cluster]
    end

    subgraph "Cache & Features"
        REDIS["Redis Cluster<br>Cache de Features"]
        FS["Feature Store<br>Offline & Online"]
    end
    
    subgraph "Plataforma de Machine Learning"
        ML_MODEL["ML Ranking Service<br>(Modelo de ML servido via API)"]
    end

    subgraph "Backend de Busca (API)"
        API_GW[API Gateway]
        SEARCH_API["Search API<br>(Microserviço Spring Boot)"]
    end

    USER[Usuário]

    %% FLUXO DE INDEXAÇÃO (ESCRITA)
    DB --"1. Captura de Mudanças (CDC)"--> CDC
    CDC --"2. Publica Eventos"--> KAFKA
    KAFKA --"3. Consome Eventos"--> IDX_SVC
    IDX_SVC --"4. Enriquece e Formata"--> FS
    IDX_SVC --"5. Indexa Documento"--> ELASTIC

    %% FLUXO DE BUSCA (LEITURA)
    USER --"1. GET /search?q=celular"--> API_GW
    API_GW --"2. Roteia Requisição"--> SEARCH_API
    SEARCH_API --"3a. Fase 1: Busca de Candidatos<br>(Top 400)"--> ELASTIC
    ELASTIC --"3b. Retorna Candidatos"--> SEARCH_API
    SEARCH_API --"4a. Fase 2: Busca Features<br>dos candidatos"--> REDIS
    SEARCH_API --"4b. Busca Features<br>dos candidatos"--> FS
    REDIS --"4c. Retorna Features em Cache"--> SEARCH_API
    FS --"4d. Retorna Features"--> SEARCH_API
    SEARCH_API --"5a. Envia Candidatos + Features<br>para Re-ranquear"--> ML_MODEL
    ML_MODEL --"5b. Retorna Scores de ML"--> SEARCH_API
    SEARCH_API --"6. Ordena pelo Score de ML<br>e retorna Top 20"--> API_GW
    API_GW --"7. Responde ao Usuário"--> USER

```
