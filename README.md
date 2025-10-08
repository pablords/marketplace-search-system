# Marketplace Search System

Sistema de busca para marketplace seguindo arquitetura hexagonal, preparado para microserviços e alta escala.

## 🏗️ Arquitetura

O projeto segue **Arquitetura Hexagonal (Ports & Adapters)** organizada em módulos Maven:

```
search-system/
├── domain/          # 🔵 Domínio - Entities, Value Objects, Repositories
├── application/     # 🟡 Aplicação - Use Cases, DTOs, Mappers
├── infrastructure/  # 🟢 Infraestrutura - Elasticsearch, Kafka, Redis
├── interfaces/      # 🟠 Interfaces - REST API, Consumers
└── bootstrap/       # ⚫ Bootstrap - Configuração e inicialização
```

## 🚀 Tecnologias

- **Java 17** + **Spring Boot 3.2.0**
- **Elasticsearch 8.11.3** - Motor de busca principal
- **Apache Kafka 3.6.1** - Eventos e CDC em tempo real
- **Redis 7** - Cache de alta performance
- **PostgreSQL** - Armazenamento relacional
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
# Subir todos os serviços (PostgreSQL, Redis, Elasticsearch, Kafka, etc.)
docker-compose up -d

# Verificar se os serviços estão rodando
docker-compose ps
```

### 3. Compilar e Executar

```bash
# Compilar o projeto
mvn clean compile

# Executar a aplicação
mvn spring-boot:run -pl bootstrap

# Ou executar o JAR
mvn clean package
java -jar bootstrap/target/search-system-bootstrap-1.0.0-SNAPSHOT.jar
```

### 4. Verificar Health

```bash
# Health check da aplicação
curl http://localhost:8080/api/v1/actuator/health

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

# Elasticsearch
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
- **Elasticsearch**: http://localhost:9200
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

### Endpoints Principais

```http
GET /api/v1/search/products?q=smartphone&category=electronics&page=0&size=20
GET /api/v1/search/products/{id}
GET /api/v1/search/suggestions?q=smart
GET /api/v1/search/popular?category=electronics
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

### Domain Layer
```java
// Entidades de domínio
Product, Category, Brand, Seller

// Value Objects
ProductId, ProductInfo, SearchQuery, SearchResult

// Repositories (interfaces)
ProductSearchRepository, ProductIndexRepository

// Domain Services
SearchDomainService, RelevanceCalculator
```

### Application Layer
```java
// Use Cases
SearchProductsUseCase, IndexProductUseCase

// DTOs
ProductDTO, SearchRequestDTO, SearchResultDTO

// Event Handlers
ProductEventHandler (Kafka integration)
```

### Infrastructure Layer
```java
// Elasticsearch
ElasticsearchProductSearchRepository
ProductDocument, ElasticsearchQueryBuilder

// Cache
RedisCacheRepository

// Events
KafkaEventPublisher
```

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
- **Elasticsearch** - Índices otimizados e queries eficientes
- **Kafka** - Batching para indexação em massa
- **Connection Pooling** - Configurações ajustadas para alta carga

## 🚀 Deploy

### Docker
```bash
# Build da imagem
docker build -t marketplace-search:latest .

# Run do container
docker run -p 8080:8080 marketplace-search:latest
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