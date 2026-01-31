# Guia de Validação da Busca Híbrida

Este documento explica como validar se a busca híbrida (BM25 + k-NN) está funcionando corretamente no sistema.

## O que é Busca Híbrida?

A busca híbrida combina duas técnicas de busca:

1. **BM25**: Busca textual tradicional baseada em frequência de termos
2. **k-NN (k-Nearest Neighbors)**: Busca semântica usando embeddings vetoriais

A combinação dessas duas técnicas permite encontrar produtos tanto por correspondência exata de termos quanto por similaridade semântica.

## Componentes Necessários

Para a busca híbrida funcionar, os seguintes componentes devem estar operacionais:

1. **ML Embedding Service** (porta 8085): Gera embeddings para queries e produtos
2. **Search Service** (porta 8083): Executa as buscas
3. **OpenSearch** (porta 9200): Armazena produtos com embeddings no campo `product_vector`

## Validação Automática

### Executar o Script de Validação

```bash
# Certifique-se de que os serviços estão rodando
docker-compose up -d

# Execute o script de validação
python3 scripts/validate_hybrid_search.py
```

O script verifica:
- ✅ Disponibilidade dos serviços
- ✅ Geração de embeddings para queries
- ✅ Presença de embeddings nos produtos indexados
- ✅ Comparação entre busca híbrida e busca apenas BM25
- ✅ Diferenças nos resultados

## Validação Manual

### 1. Verificar Embedding Service

```bash
# Health check
curl http://localhost:8085/health

# Testar geração de embedding
curl -X POST http://localhost:8085/api/v1/embeddings/query \
  -H "Content-Type: application/json" \
  -d '{
    "texts": ["smartphone"],
    "type": "query"
  }'
```

**Resultado esperado:**
- Status: `healthy`
- Embedding com 384 dimensões
- Modelo: `all-MiniLM-L6-v2`

### 2. Verificar Produtos com Embeddings no OpenSearch

```bash
# Buscar produtos e verificar campo product_vector
curl -X POST http://localhost:9200/products_index/_search \
  -H "Content-Type: application/json" \
  -d '{
    "size": 5,
    "_source": ["title", "product_vector"],
    "query": {"match_all": {}}
  }'
```

**Resultado esperado:**
- Produtos devem ter o campo `product_vector`
- Vetor deve ter 384 dimensões
- Valores devem ser números entre -1 e 1 (normalizados)

### 3. Testar Busca Híbrida

```bash
# Busca via Search Service
curl "http://localhost:8083/api/v1/search/products?q=smartphone&limit=10"
```

**Verificar nos logs:**
- Se o embedding da query foi gerado
- Se a query híbrida foi construída (BM25 + k-NN)
- Scores de relevância retornados

### 4. Comparar Resultados

Execute duas buscas:

**Busca apenas BM25 (sem embedding):**
```bash
curl "http://localhost:8083/api/v1/search/products?q=smartphone&limit=10"
```

**Busca híbrida (com embedding):**
```bash
# Se houver parâmetro para forçar busca híbrida
curl "http://localhost:8083/api/v1/search/products?q=smartphone&limit=10&hybrid=true"
```

**Comparar:**
- Número de resultados
- Ordem dos produtos
- Produtos que aparecem em uma busca mas não na outra

## Indicadores de Funcionamento Correto

### ✅ Busca Híbrida Funcionando

1. **Embedding Service disponível:**
   - Health check retorna `healthy`
   - Embeddings são gerados com 384 dimensões

2. **Produtos têm embeddings:**
   - Campo `product_vector` presente nos documentos
   - Vetores normalizados (valores entre -1 e 1)

3. **Queries usam embeddings:**
   - Logs mostram geração de embedding para queries
   - Query do OpenSearch contém tanto BM25 quanto k-NN

4. **Resultados diferentes:**
   - Busca híbrida retorna resultados diferentes de busca apenas BM25
   - Produtos semanticamente similares aparecem mesmo sem termos exatos

### ❌ Problemas Comuns

1. **Embedding Service não disponível:**
   - Verificar se o serviço está rodando: `docker ps | grep embedding`
   - Verificar logs: `docker logs ml-embedding-service`

2. **Produtos sem embeddings:**
   - Verificar se o Indexing Service está processando eventos
   - Verificar logs do Indexing Service
   - Reindexar produtos se necessário

3. **Busca híbrida não ativa:**
   - Verificar se o código está gerando embedding da query
   - Verificar se o embedding está sendo passado para `buildQuery()`
   - Verificar configuração do `EmbeddingClient`

## Verificação no Código

### 1. Verificar se EmbeddingClient está configurado

Arquivo: `search-service/infrastructure/src/main/java/.../embedding/EmbeddingClient.java`

```java
// Deve estar habilitado
@Value("${ml.embedding.enabled:true}")
private boolean enabled;
```

### 2. Verificar se embedding é usado na busca

Arquivo: `search-service/infrastructure/.../repositories/OpenSearchProductSearchRepository.java`

O método `searchCandidatesWithScores` deve:
1. Gerar embedding da query usando `EmbeddingClient.generateQueryEmbedding()`
2. Passar o embedding para `queryBuilder.buildQuery(query, userContext, Optional.of(embedding))`

### 3. Verificar construção da query híbrida

Arquivo: `search-service/infrastructure/.../queries/OpenSearchQueryBuilder.java`

O método `buildQuery` com embedding deve:
1. Criar query BM25 com `buildTextQuery()`
2. Criar query k-NN com `buildKnnQuery(embedding)`
3. Combinar ambas usando `should()` no `BoolQuery`

## Métricas de Validação

### Métricas Esperadas

1. **Taxa de sucesso de embeddings:**
   - > 95% das queries devem gerar embeddings com sucesso

2. **Cobertura de embeddings:**
   - > 90% dos produtos devem ter embeddings

3. **Diferença nos resultados:**
   - Busca híbrida deve retornar pelo menos 10% de produtos diferentes da busca BM25

4. **Performance:**
   - Busca híbrida deve completar em < 500ms (P95)

## Troubleshooting

### Problema: Embeddings não são gerados

**Solução:**
1. Verificar se Embedding Service está rodando
2. Verificar logs do Embedding Service
3. Verificar configuração de URL do serviço

### Problema: Produtos não têm embeddings

**Solução:**
1. Verificar se Indexing Service está consumindo eventos do Kafka
2. Verificar se Embedding Service está acessível pelo Indexing Service
3. Reindexar produtos manualmente se necessário

### Problema: Busca híbrida retorna mesmos resultados que BM25

**Solução:**
1. Verificar se embedding da query está sendo gerado
2. Verificar se embedding está sendo passado para `buildQuery()`
3. Verificar logs do OpenSearch para confirmar query híbrida
4. Verificar se produtos têm embeddings válidos

## Próximos Passos

Após validar que a busca híbrida está funcionando:

1. **Ajustar pesos:** Balancear BM25 e k-NN para melhor relevância
2. **Monitorar métricas:** Acompanhar performance e qualidade dos resultados
3. **Otimizar:** Ajustar parâmetros k-NN (k, boost) conforme necessário
4. **Testar queries reais:** Validar com queries de usuários reais

## Referências

- [OpenSearch k-NN Documentation](https://opensearch.org/docs/latest/search-plugins/knn/index/)
- [BM25 Algorithm](https://en.wikipedia.org/wiki/Okapi_BM25)
- [Sentence Transformers](https://www.sbert.net/)

