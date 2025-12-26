# ML Embedding Service

Serviço de geração de embeddings para produtos e queries do marketplace.

## Descrição

Este serviço gera embeddings vetoriais (representações numéricas) de textos usando modelos de Machine Learning. Os embeddings são utilizados para:

- **Indexação**: Gerar vetores para títulos/descrições de produtos no OpenSearch
- **Busca**: Gerar vetores para queries de busca para realizar busca semântica (k-NN)

## Tecnologias

- **Python 3.11**
- **FastAPI** - Framework web assíncrono
- **sentence-transformers** - Biblioteca para modelos de embedding
- **PyTorch** - Backend para modelos de ML
- **Pydantic** - Validação de dados
- **Uvicorn** - ASGI server
- **Redis 7** - Cache de embeddings para melhor performance

## Modelo de Embedding

- **Modelo**: `sentence-transformers/all-MiniLM-L6-v2`
- **Dimensão**: 384
- **Normalização**: Embeddings são normalizados (L2 norm)

## Estrutura

```
embedding-service/
├── main.py                    # Aplicação FastAPI principal
├── models/
│   └── embedding_model.py     # Modelo de embedding
├── services/
│   └── embedding_service.py   # Serviço de embedding
├── requirements.txt           # Dependências Python
├── Dockerfile                # Container Docker
└── README.md                 # Este arquivo
```

## API

### POST /api/v1/embeddings/generate

Gera embeddings para uma lista de textos (produtos ou queries).

**Request:**
```json
{
  "texts": ["produto 1", "produto 2", ...],
  "type": "product" | "query"
}
```

**Response:**
```json
{
  "embeddings": [
    {
      "text": "produto 1",
      "vector": [0.1, 0.2, ...]
    },
    {
      "text": "produto 2",
      "vector": [0.3, 0.4, ...]
    }
  ],
  "model_version": "all-MiniLM-L6-v2",
  "dimension": 384
}
```

### POST /api/v1/embeddings/query

Alias para `/generate` com `type="query"`. Gera embedding para uma query de busca.

**Request:**
```json
{
  "texts": ["notebook gamer"],
  "type": "query"
}
```

**Response:**
```json
{
  "embeddings": [
    {
      "text": "notebook gamer",
      "vector": [0.1, 0.2, ...]
    }
  ],
  "model_version": "all-MiniLM-L6-v2",
  "dimension": 384
}
```

### GET /health

Health check endpoint com verificação de Redis.

**Response:**
```json
{
  "status": "healthy",
  "service": "embedding-service",
  "version": "1.0.0",
  "model_loaded": true,
  "redis_connected": true
}
```

## Uso

### Executar localmente

```bash
# Instalar dependências
pip install -r requirements.txt

# Executar servidor
python main.py
```

O serviço estará disponível em `http://localhost:8085`

### Executar com Docker

```bash
# Build da imagem
docker build -t embedding-service .

# Executar container
docker run -p 8085:8085 embedding-service
```

## Integração

### Indexing Service

O indexing-service chama este serviço para gerar embeddings de produtos durante a indexação:

```java
POST http://embedding-service:8085/api/v1/embeddings/generate
{
  "texts": ["Título do produto"],
  "type": "product"
}
```

### Search Service

O search-service chama este serviço para gerar embeddings de queries durante a busca:

```java
POST http://embedding-service:8085/api/v1/embeddings/query
{
  "texts": ["query do usuário"],
  "type": "query"
}
```

## Performance

- **Batch Processing**: Suporta processamento em lote (até 100 textos por request)
- **Cache de Modelo**: Modelo é carregado uma vez na inicialização
- **Cache Redis**: Embeddings gerados são cacheados no Redis para evitar recálculo
- **Normalização**: Embeddings são normalizados para melhor performance em busca k-NN
- **TTL Configurável**: TTL do cache Redis pode ser configurado via variáveis de ambiente

## Limitações

- **Batch Size**: Máximo de 100 textos por request (configurável)
- **Memória**: Modelo consome ~80MB de RAM
- **Primeira Requisição**: Pode ser mais lenta devido ao carregamento do modelo

## Configuração

### Variáveis de Ambiente

```bash
# Redis
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=
REDIS_DB=0
REDIS_TTL_SECONDS=3600  # TTL padrão: 1 hora

# Servidor
PORT=8085
LOG_LEVEL=INFO
```

## Monitoramento

- Health check disponível em `/health` (verifica Redis)
- Logs estruturados com nível INFO
- Métricas podem ser adicionadas via Prometheus (futuro)

