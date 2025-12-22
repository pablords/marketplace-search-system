# Sistema de Busca - Regras de Negócio e Scoring# Documentação - Sistema de Busca de Marketplace



## Índice## 📚 Índice



1. [Algoritmo de Relevância](#algoritmo-de-relevância)- [Visão Geral](#visão-geral)

2. [Fórmulas de Cálculo](#fórmulas-de-cálculo)- [Arquitetura](#arquitetura)

3. [Business Boost e Re-ranking](#business-boost-e-re-ranking)- [Documentação Técnica](#documentação-técnica)

4. [Casos de Uso](#casos-de-uso)- [Regras de Negócio](#regras-de-negócio)

- [Guias de Desenvolvimento](#guias-de-desenvolvimento)

---- [API Reference](#api-reference)

- [Deployment](#deployment)

## Algoritmo de Relevância

---

### Fórmula Principal

## Visão Geral

O score de relevância de um produto é calculado através da combinação de múltiplos fatores:

Sistema de busca inteligente para marketplace construído com arquitetura hexagonal, que combina:

```

Relevance Score = (Text Score × 0.40) + - **Elasticsearch** para busca textual de alta performance

                  (Popularity Score × 0.25) + - **Regras de Domínio** para re-ranking personalizado baseado em métricas de negócio

                  (Seller Score × 0.20) + - **CDC (Change Data Capture)** via Debezium/Kafka para sincronização em tempo real

                  (Personalization Score × 0.15) -- **Cache Distribuído** (Redis) para otimização de latência

                  Freshness Penalty- **Event-Driven Architecture** para processamento assíncrono

```

### Stack Tecnológica

### Pesos dos Componentes

| Camada | Tecnologia | Versão |

| Componente | Peso | Justificativa ||--------|-----------|--------|

|------------|------|---------------|| **Backend** | Java + Spring Boot | 17 / 3.2.0 |

| **Text Score** | 40% | A relevância textual é o fator mais importante - o produto deve corresponder ao que o usuário está buscando || **Busca** | Elasticsearch | 8.x |

| **Popularity Score** | 25% | Produtos populares tendem a ter melhor qualidade e maior satisfação || **Mensageria** | Kafka + Debezium | 2.13 / 2.6 |

| **Seller Score** | 20% | Vendedores confiáveis oferecem melhor experiência de compra || **Cache** | Redis | 7.x |

| **Personalization Score** | 15% | Personalização melhora a experiência mas não deve dominar os resultados || **Banco de Dados** | PostgreSQL | 15.x |

| **Freshness Penalty** | Variável | Penaliza produtos desatualizados sem impactar novos produtos || **Build** | Maven | 3.9+ |



---### Características Principais



## Fórmulas de Cálculo✅ **Busca Inteligente** - Combina relevância textual com métricas de negócio  

✅ **Tempo Real** - Indexação automática via CDC (< 1s de lag)  

### 1. Text Score (40%)✅ **Alta Performance** - P95 < 200ms com cache hit rate > 80%  

✅ **Personalização** - Ranking adaptado ao histórico do usuário  

**Origem:** Elasticsearch BM25 (Best Matching 25)✅ **Escalável** - Arquitetura modular pronta para microserviços  
# Sistema de Busca - Regras de Negócio e Scoring

## Índice

1. [Algoritmo de Relevância](#algoritmo-de-relevância)
2. [Fórmulas de Cálculo](#fórmulas-de-cálculo)
3. [Business Boost e Re-ranking](#business-boost-e-re-ranking)
4. [Casos de Uso](#casos-de-uso)
5. [Fórmulas Resumidas](#fórmulas-resumidas)

---

## Algoritmo de Relevância

### Fórmula Principal

O score de relevância de um produto é calculado através da combinação de múltiplos fatores:

```
Relevance Score = (Text Score × 0.40) + 
          (Popularity Score × 0.25) + 
          (Seller Score × 0.20) + 
          (Personalization Score × 0.15) -
          Freshness Penalty
```

### Pesos dos Componentes

| Componente | Peso | Justificativa |
|------------|------|---------------|
| Text Score | 40% | Relevância textual é o fator mais importante: precisa corresponder à intenção da busca |
| Popularity Score | 25% | Métrica de engajamento e confiança social (views, cliques, vendas) |
| Seller Score | 20% | Confiabilidade do vendedor impacta a qualidade percebida |
| Personalization Score | 15% | Melhora conversão, mas não pode dominar o ranking geral |
| Freshness Penalty | Variável | Evita exibir produtos desatualizados ou abandonados |

---

## Fórmulas de Cálculo

### 1. Text Score (40%)

Baseado no score BM25 do Elasticsearch considerando campos ponderados:

| Campo | Peso |
|-------|------|
| name | 3.0 |
| category | 2.0 |
| brand | 1.5 |
| description | 1.0 |

```
BM25 = IDF × (f(qi, D) × (k1 + 1)) / (f(qi, D) + k1 × (1 - b + b × |D| / avgdl))
Parâmetros típicos: k1 = 1.2, b = 0.75
```

Implementação simplificada:
```java
      },
```

### 2. Popularity Score (25%)

```java

             Math.log1p(clickCount) * 0.3 +
             Math.log1p(salesCount) * 0.4;
popularityScore = Math.min(popularityScore * 10, 100.0); // normalização 0-100
```

| Métrica | Peso Interno | Justificativa |
|---------|--------------|---------------|
| viewCount | 0.3 | Indicador inicial de interesse |
| clickCount | 0.3 | Confirma relevância após impressão |
| salesCount | 0.4 | Conversão efetiva (maior importância) |

Uso de `log1p` para reduzir viés de produtos extremamente populares.

### 3. Seller Score (20%)

```java
Score Normalizado (0-100) = min(227.2 / 3, 100) = 75.7      "seller": {
           (Math.log1p(sellerSalesCount) * 5) * 0.4;
sellerScore = Math.min(sellerScore, 100.0);
```

| Componente | Peso Interno | Observação |
|------------|--------------|------------|
| sellerRating | 0.6 | Converte estrelas (0-5) em escala 0-100 |
| sellerSalesCount | 0.4 | Experiência histórica do vendedor |

Níveis de reputação (aplicado como multiplicador final opcional):

| Nível | Vendas | Rating mínimo | Multiplicador |
|-------|--------|---------------|---------------|
| PLATINUM | > 10000 | ≥ 4.8 | ×1.15 |
| GOLD | > 5000 | ≥ 4.5 | ×1.10 |
| SILVER | > 1000 | ≥ 4.0 | ×1.05 |
| BRONZE | > 100 | ≥ 3.5 | ×1.02 |
| NEW | ≤ 100 | ≥ 3.0 | ×1.00 |

### 4. Personalization Score (15%)

```java

if (userPurchasedInCategory) personalizationScore += 40.0;
if (userViewedSimilarProducts) personalizationScore += 30.0;
if (priceMatchesUserRange) personalizationScore += 20.0;
double distanceKm = calculateDistance(userLocation, sellerLocation);
if (distanceKm < 50) personalizationScore += 10.0;
else if (distanceKm < 200) personalizationScore += 5.0;
```

| Fator | Pontos | Explicação |
|-------|--------|------------|
| Categoria de interesse | 40 | Relevância histórica forte |
| Produtos similares vistos | 30 | Interesse recente contextual |
| Faixa de preço compatível | 20 | Probabilidade de conversão |
| Proximidade logística | 10 / 5 | Menor tempo/frete |

Máximo: 100 pontos (interno), depois ponderado por 0.15.

### 5. Freshness Penalty

```java
long days = ChronoUnit.DAYS.between(lastUpdated, now);
double freshnessPenalty = 0.0;
if (days > 365) freshnessPenalty = 20.0;
else if (days > 180) freshnessPenalty = 10.0;
else if (days > 90) freshnessPenalty = 5.0;
```

| Dias sem atualização | Penalidade |
|----------------------|------------|
| 0 - 90 | 0 |
| 91 - 180 | 5 |
| 181 - 365 | 10 |
| > 365 | 20 |

---

## Business Boost e Re-ranking

Após calcular o `Relevance Score`, aplicam-se multiplicadores de negócio:

```java
```        "id": "seller_001",
if (product.isPremium()) businessBoost *= 1.5;
if (product.hasFreeShipping()) businessBoost *= 1.3;
if (product.hasActiveDiscount()) businessBoost *= 1.2;
if (product.getStock() > 50) businessBoost *= 1.1;
if (product.getRating() >= 4.5 && product.getReviewCount() > 100) businessBoost *= 1.15;
// Penalidades
if (product.getStock() < 5) businessBoost *= 0.6;
if (product.getRating() < 3.0) businessBoost *= 0.8;
if (!product.isActive()) businessBoost *= 0.1;
double finalScore = relevanceScore * businessBoost;
```

| Condição | Multiplicador | Impacto |
|----------|---------------|---------|
| Premium | 1.5× | Destaque comercial prioritário |
| Frete grátis | 1.3× | Aumento de conversão esperado |
| Desconto ativo | 1.2× | Estimula compra imediata |
| Estoque alto (>50) | 1.1× | Capacidade de atender demanda |
| Avaliação alta (≥4.5 & >100 reviews) | 1.15× | Validação social forte |
| Estoque crítico (<5) | 0.6× | Risco de ruptura iminente |
| Avaliação baixa (<3.0) | 0.8× | Qualidade percebida baixa |
| Inativo | 0.1× | Virtualmente removido do ranking |

Limite prático: pode-se normalizar o `finalScore` para faixa 0-100 após boosts.

### Exemplo de Cálculo

```
Relevance Score base = 84.44
Multiplicadores: Premium(1.5) × Frete(1.3) × Desconto(1.2) × Avaliação(1.15)
Boost total = 1.5 × 1.3 × 1.2 × 1.15 = 2.691
Final Score bruto = 84.44 × 2.691 = 227.2
Final Score normalizado (div /3 limite) ≈ 75.7
```

---

## Casos de Uso

### Caso 1: Busca Genérica ("notebook")
Prioriza popularidade e boosts comerciais (premium, frete, desconto).

### Caso 2: Busca Específica ("notebook dell inspiron 15 i7")
Text Score domina. Seller Score diferencia vendedores.

### Caso 3: Usuário Recorrente ("mouse gamer")
Personalization ganha relevância: categoria, similares e faixa de preço.

### Caso 4: Produto Novo vs Popular
Produto popular supera novo mesmo com Text Score levemente inferior devido a alta popularidade + reputação.

### Caso 5: Filtros Aplicados (smartphone + faixa de preço + frete grátis)
Reduz conjunto antes de calcular scores, evitando custo desnecessário.

---

## Fórmulas Resumidas

| Componente | Fórmula | Faixa |
|------------|---------|-------|
| Text Score | BM25 ponderado por campo | 0-100 |
| Popularity | log1p(views)*0.3 + log1p(clicks)*0.3 + log1p(sales)*0.4 (×10) | 0-100 |
| Seller | (rating/5*100)*0.6 + (log1p(sales)*5)*0.4 | 0-100 |
| Personalization | Soma pontos (40+30+20+10/5) | 0-100 interno |
| Freshness Penalty | 0 / 5 / 10 / 20 | 0-20 |
| Relevance | (Text×0.40)+(Popularity×0.25)+(Seller×0.20)+(Personalization×0.15)-Penalty | ≈0-100 |
| Business Boost | Produto/Seller multiplicadores | ≈0.1-~3× |
| Final Score | Relevance × Boost (normalizar opcional) | 0-100 |

---

**Documentação gerada em:** 02/11/2025  
**Versão:** 1.0.0

        "name": "TechStore",

---        "reputation": {

          "score": 4.8,

## Casos de Uso          "totalReviews": 1523

        }

### Caso 1: Busca Genérica      },

      "images": ["url1", "url2"],

**Cenário:** Usuário busca "notebook"      "condition": "NEW",

      "availableQuantity": 10

**Fatores Dominantes:**    }

- Text Score (40%): Match amplo, muitos resultados  ],

- Popularity Score (25%): Produtos mais vendidos aparecem primeiro  "totalCount": 156,

- Business Boost: Premium + Frete Grátis dominam  "pageSize": 20,

  "pageNumber": 0,

**Resultado Esperado:**  "totalPages": 8,

1. Notebooks premium com frete grátis e alta popularidade  "hasNextPage": true,

2. Notebooks bem avaliados de vendedores platinum  "executionTimeMs": 45

3. Notebooks com desconto ativo}

4. Notebooks básicos/sem destaque```



### Caso 2: Busca Específica#### 💡 Sugestões de Busca



**Cenário:** Usuário busca "notebook dell inspiron 15 i7 16gb"```http

GET /search/suggestions?term=noteb&limit=10

**Fatores Dominantes:**```

- Text Score (40%): Match exato, poucos resultados

- Seller Score (20%): Vendedor confiável é crucial para produto específico**Resposta:**

- Personalization: Menos relevante (busca específica)```json

{

**Resultado Esperado:**  "suggestions": [

1. Produto exato de vendedor platinum    "notebook dell",

2. Produto exato de vendedor gold    "notebook gamer",

3. Produtos similares (i5 ou 8GB) de vendedores confiáveis    "notebook lenovo"

  ]

### Caso 3: Usuário Recorrente}

```

**Cenário:** Usuário que já comprou eletrônicos busca "mouse gamer"

#### ➕ Criar Produto (Admin/Command API)

**Fatores Dominantes:**

- Personalization Score (15%): Peso maior por histórico```http

- Text Score (40%): Match com preferências conhecidasPOST /products

- Proximity: Vendedores próximos ganham boostContent-Type: application/json



**Resultado Esperado:**{

1. Mouse gamer na faixa de preço histórica do usuário  "id": "MLB999888",

2. Marcas que o usuário já comprou  "title": "Notebook Gamer Asus ROG",

3. Vendedores próximos com frete rápido  "description": "Intel i7, 16GB RAM, RTX 3060",

  "price": 5999.99,

### Caso 4: Produto Novo vs Produto Popular  "currency": "BRL",

  "availableQuantity": 10,

**Cenário:** Dois produtos similares, um novo (sem vendas) e um popular  "condition": "NEW",

  "category": {

```    "id": "eletronicos",

Produto A (Novo):    "name": "Eletrônicos"

- Text Score: 90 (match perfeito)  },

- Popularity: 5 (sem histórico)  "brand": {

- Seller: 70 (vendedor silver)    "id": "asus",

- Personalization: 50    "name": "Asus"

- Relevance = 90×0.4 + 5×0.25 + 70×0.2 + 50×0.15 = 59.75  },

- Business Boost: 1.5 (premium) × 1.3 (frete) = 1.95  "seller": {

- Final: 59.75 × 1.95 = 116.5    "id": "seller_premium_001",

    "name": "Premium Store"

Produto B (Popular):  }

- Text Score: 85 (match bom)}

- Popularity: 85 (muitas vendas)```

- Seller: 95 (vendedor platinum)

- Personalization: 70**Resposta:**

- Relevance = 85×0.4 + 85×0.25 + 95×0.2 + 70×0.15 = 84.75```http

- Business Boost: 1.5 × 1.3 × 1.15 = 2.24HTTP/1.1 201 Created

- Final: 84.75 × 2.24 = 189.8Location: /products/MLB999888

``````



**Resultado:** Produto popular vence apesar do text score menor.📄 **API completa**: [API.md](./API.md) *(a criar)*



### Caso 5: Filtros Aplicados---



**Cenário:** Usuário busca "smartphone" com filtros:## Deployment

- Preço: R$ 1500 - R$ 2500

- Frete grátis: Sim### Docker Build

- Avaliação mínima: 4.0

```bash

**Processamento:**# Build da imagem

1. **Query Elasticsearch**: Busca textual "smartphone"docker build -t marketplace-search:latest .

2. **Filtros**: Aplica filtros de preço, frete e avaliação

3. **Scoring**: Calcula relevance score apenas para produtos filtrados# Executar container

4. **Business Boost**: Aplica multiplicadoresdocker run -p 8080:8080 \

5. **Re-ranking**: Ordena por score final  -e SPRING_PROFILES_ACTIVE=production \

  -e POSTGRES_HOST=postgres.prod.com \

**Resultado:** Apenas smartphones que atendem TODOS os critérios, ordenados por relevância + boost.  marketplace-search:latest

```

---

### Kubernetes

## Fórmulas Resumidas

```yaml

### Score de Relevância# deployment.yaml

```apiVersion: apps/v1

Relevance = (Text × 0.40) + (Popularity × 0.25) + (Seller × 0.20) + (Personalization × 0.15) - Freshness Penaltykind: Deployment

```metadata:

  name: search-api

### Text Scorespec:

```  replicas: 3

Elasticsearch BM25 (0-100)  selector:

```    matchLabels:

      app: search-api

### Popularity Score  template:

```    metadata:

log1p(views)×0.3 + log1p(clicks)×0.3 + log1p(sales)×0.4      labels:

Normalizado × 10, max 100        app: search-api

```    spec:

      containers:

### Seller Score      - name: search-api

```        image: marketplace-search:1.0.0

(rating/5 × 100)×0.6 + (log1p(salesCount) × 5)×0.4        ports:

Max 100        - containerPort: 8080

```        env:

        - name: SPRING_PROFILES_ACTIVE

### Personalization Score          value: "production"

```        resources:

Categoria(40) + Similares(30) + PreçoMatch(20) + Proximidade(10)          requests:

Max 100            memory: "512Mi"

```            cpu: "500m"

          limits:

### Business Boost            memory: "2Gi"

```            cpu: "2000m"

Premium(1.5×) × FreteGrátis(1.3×) × Desconto(1.2×) × EstoqueAlto(1.1×) × AvaliaçãoAlta(1.15×)```

÷ EstoqueCrítico(0.6×) × AvaliaçãoBaixa(0.8×) × Inativo(0.1×)

```### Métricas de Produção



### Score Final| Métrica | Target | Crítico |

```|---------|--------|---------|

Final Score = Relevance Score × Business Boost| **Latência P95** | < 200ms | > 500ms |

```| **Cache Hit Rate** | > 80% | < 50% |

| **Indexing Lag** | < 1s | > 10s |

---| **Error Rate** | < 0.1% | > 1% |

| **Availability** | > 99.9% | < 99% |

**Documentação gerada em:** 02/11/2025  

**Versão:** 1.0.0### Monitoramento


- **Prometheus**: Métricas de aplicação (`/actuator/prometheus`)
- **Grafana**: Dashboards de latência, throughput e erros
- **ELK Stack**: Logs centralizados
- **Jaeger**: Distributed tracing

---

## Troubleshooting

### Problema: Elasticsearch não responde

```bash
# Verificar saúde do cluster
curl http://localhost:9200/_cluster/health?pretty

# Verificar índices
curl http://localhost:9200/_cat/indices?v

# Recriar índice (se necessário)
curl -X DELETE http://localhost:9200/products-v1
curl -X PUT http://localhost:9200/products-v1 -H 'Content-Type: application/json' -d @index-mapping.json
```

### Problema: Kafka consumer lag alto

```bash
# Verificar consumer group
kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
  --group search-indexing --describe

# Reset offset (cuidado!)
kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
  --group search-indexing --reset-offsets --to-latest --execute --all-topics
```

### Problema: Cache não invalida

```bash
# Conectar ao Redis
redis-cli -h localhost -p 6379 -a 1q2w3e4r

# Verificar keys
KEYS search:*

# Flush cache (cuidado!)
FLUSHDB
```

---

## Contribuindo

### Workflow de Desenvolvimento

1. **Criar branch** a partir de `main`:
   ```bash
   git checkout -b feature/nova-funcionalidade
   ```

2. **Desenvolver** seguindo convenções:
   - Código: Clean Code, SOLID principles
   - Commits: Conventional Commits (`feat:`, `fix:`, `docs:`)
   - Testes: Cobertura > 80%

3. **Testar localmente**:
   ```bash
   mvn clean verify
   ```

4. **Pull Request**:
   - Título claro e descritivo
   - Descrição com contexto e motivação
   - Screenshots/exemplos se aplicável
   - Reviewers: @pablords

### Code Style

- **Java**: Google Java Style Guide
- **Formatação**: `mvn spotless:apply`
- **Análise estática**: SonarQube / Checkstyle

### Commits Convencionais

```
feat: adiciona filtro por cor de produto
fix: corrige cálculo de score para vendedores premium
docs: atualiza README com exemplos de API
refactor: extrai lógica de cache para service dedicado
test: adiciona testes de integração para busca
```

---

## Licença

Este projeto está sob licença MIT. Veja [LICENSE](../LICENSE) para mais detalhes.

---

## Contato

- **Time**: Marketplace Search Team
- **Email**: search-team@marketplace.com
- **Slack**: #marketplace-search
- **Wiki**: [Confluence](https://wiki.marketplace.com/search)

---

## Changelog

### v1.0.0 (2024-11-02)
- ✨ Sistema de busca com Elasticsearch
- ✨ Indexação via CDC (Debezium + Kafka)
- ✨ Re-ranking com regras de negócio
- ✨ Cache distribuído (Redis)
- ✨ Personalização baseada em histórico
- 📚 Documentação completa

---

**Última atualização:** 02/11/2024  
**Versão:** 1.0.0  
**Status:** ✅ Em Produção
