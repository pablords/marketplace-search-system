# Buscar por texto
curl -X GET "http://localhost:9200/products/_search?pretty" \
  -H 'Content-Type: application/json' \
  -d '{
    "query": {
      "match": {
        "title": "smartphone"
      }
    }
  }'


curl -X POST http://localhost:9200/products/_search \
  -H 'Content-Type: application/json' \

  -d '{
    "query": {
      "term": {
        "category_name.keyword": "Eletrônicos"
      }
    },
    "size": 10
  }'


POST http://localhost:9200/products/_search
Content-Type: application/json

{
  "query": {
    "range": {
      "price": {
        "gte": 100,
        "lte": 500
      }
    }
  },
  "sort": [
    {
      "price": {
        "order": "asc"
      }
    }
  ]
}


POST http://localhost:9200/products/_search
Content-Type: application/json

{
  "query": {
    "bool": {
      "must": [
        {
          "match": {
            "title": "Samsung"
          }
        }
      ],
      "filter": [
        {
          "range": {
            "price": {
              "gte": 200
            }
          }
        },
        {
          "term": {
            "category_name.keyword": "Eletrônicos"
          }
        }
      ]
    }
  }
}