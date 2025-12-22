# ML Ranking Service

Serviço de re-ranking com Machine Learning para produtos do marketplace.

## Descrição

Este serviço recebe candidatos de produtos (até 400) com suas 17 features extraídas e retorna os Top 20 ranqueados por score de Machine Learning.

## Tecnologias

- **Python 3.11**
- **FastAPI** - Framework web assíncrono
- **Pydantic** - Validação de dados
- **Uvicorn** - ASGI server

## Estrutura

```
ml-ranking-service/
├── main.py                 # Aplicação FastAPI principal
├── models/
│   └── ltr_model.py       # Modelo Learning to Rank
├── services/
│   └── ranking_service.py # Serviço de ranking
├── requirements.txt       # Dependências Python
├── Dockerfile            # Container Docker
└── README.md            # Este arquivo
```

## Features

O modelo utiliza 17 features agrupadas em:

1. **Relevância** (3 features):
   - `bm25_score`: Score BM25 normalizado (0-1)
   - `knn_score`: Score k-NN normalizado (0-1)
   - `hybrid_score`: Score híbrido (BM25 + k-NN)

2. **Match Textual** (2 features):
   - `exact_match`: Match exato (0 ou 1)
   - `term_coverage`: Cobertura de termos (0-1)

3. **Qualidade do Texto** (4 features):
   - `title_length`: Comprimento do título
   - `description_length`: Comprimento da descrição
   - `title_description_ratio`: Ratio título/descrição (0-1)
   - `text_quality_score`: Score de qualidade do texto (0-1)

4. **Contexto** (4 features):
   - `first_word_match`: Match da primeira palavra (0 ou 1)
   - `has_numbers`: Contém números (0 ou 1)
   - `brand_match`: Match de marca (0 ou 1)
   - `category_match`: Match de categoria (0 ou 1)

5. **Popularidade** (4 features):
   - `popularity_score`: Score de popularidade (0-100)
   - `quality_score`: Score de qualidade (0-1)
   - `ctr`: Click-through rate (0-1)
   - `sales_count_normalized`: Vendas normalizadas (0-1)

## API

### POST /api/v1/ml/rank

Re-ranqueia produtos candidatos usando modelo ML.

**Request:**
```json
{
  "candidates": [
    {
      "product_id": "prod-123",
      "bm25_score": 0.85,
      "knn_score": 0.72,
      "hybrid_score": 0.80,
      "exact_match": 1.0,
      "term_coverage": 0.9,
      "title_length": 45.0,
      "description_length": 200.0,
      "title_description_ratio": 0.225,
      "text_quality_score": 0.8,
      "first_word_match": 1.0,
      "has_numbers": 0.0,
      "brand_match": 1.0,
      "category_match": 0.0,
      "popularity_score": 75.5,
      "quality_score": 0.9,
      "ctr": 0.15,
      "sales_count_normalized": 0.7
    }
  ],
  "query": "smartphone samsung"
}
```

**Response:**
```json
{
  "ranked_products": [
    {
      "product_id": "prod-123",
      "ml_score": 0.8234,
      "rank": 1
    }
  ],
  "total_candidates": 1,
  "model_version": "1.0.0-weights"
}
```

### GET /health

Health check do serviço.

**Response:**
```json
{
  "status": "healthy",
  "service": "ml-ranking-service",
  "version": "1.0.0"
}
```

## Executar Localmente

```bash
# Instalar dependências
pip install -r requirements.txt

# Executar servidor
python main.py
# ou
uvicorn main:app --host 0.0.0.0 --port 8084 --reload
```

O serviço estará disponível em: http://localhost:8084

## Docker

```bash
# Build da imagem
docker build -t ml-ranking-service:latest .

# Executar container
docker run -p 8084:8084 ml-ranking-service:latest
```

## Modelo

Atualmente utiliza um modelo baseado em pesos fixos. Futuramente será substituído por modelo treinado (XGBoost, LightGBM) usando dados históricos do Feature Store.

### Pesos Atuais

- Relevância: 40%
- Match Textual: 20%
- Qualidade do Texto: 10%
- Contexto: 10%
- Popularidade: 20%

## Logs

O serviço utiliza logging padrão do Python. Logs incluem:
- Requisições recebidas
- Número de candidatos processados
- Erros e exceções
- Versão do modelo utilizado

