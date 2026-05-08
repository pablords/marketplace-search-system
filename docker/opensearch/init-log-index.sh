#!/bin/bash
# Script de inicialização: cria index template e ILM policy no OpenSearch
# Executado uma única vez quando o container é criado.

OPENSEARCH_URL="http://open-search:9200"

echo "Aguardando OpenSearch estar pronto..."
until curl -sf "$OPENSEARCH_URL/_cluster/health" > /dev/null 2>&1; do
  sleep 5
done
echo "OpenSearch disponível!"

# ── Configurações de Cluster: Watermarks e Desbloqueio ─────────────────────
echo "Configurando watermarks de disco e removendo bloqueios..."
curl -s -X PUT "$OPENSEARCH_URL/_cluster/settings" \
  -H 'Content-Type: application/json' \
  -d '{
    "persistent": {
      "cluster.routing.allocation.disk.watermark.low": "95%",
      "cluster.routing.allocation.disk.watermark.high": "97%",
      "cluster.routing.allocation.disk.watermark.flood_stage": "98%",
      "cluster.blocks.create_index": null
    }
  }'
echo ""

# ── ILM Policy: retenção de 7 dias, rollover por tamanho ──────────────────
echo "Criando ILM policy de logs..."
curl -s -X PUT "$OPENSEARCH_URL/_plugins/_ism/policies/marketplace-logs-policy" \
  -H 'Content-Type: application/json' \
  -d '{
    "policy": {
      "description": "Marketplace logs retention: 7 dias, rollover 5GB",
      "default_state": "hot",
      "states": [
        {
          "name": "hot",
          "actions": [],
          "transitions": [
            {
              "state_name": "delete",
              "conditions": { "min_index_age": "7d" }
            }
          ]
        },
        {
          "name": "delete",
          "actions": [{ "delete": {} }],
          "transitions": []
        }
      ]
    }
  }'
echo ""

# ── Index Template: mapeamentos dos campos de log ─────────────────────────
echo "Criando index template marketplace-logs..."
curl -s -X PUT "$OPENSEARCH_URL/_index_template/marketplace-logs-template" \
  -H 'Content-Type: application/json' \
  -d '{
    "index_patterns": ["marketplace-logs-*"],
    "priority": 1,
    "template": {
      "settings": {
        "number_of_shards": 1,
        "number_of_replicas": 0,
        "index.refresh_interval": "5s",
        "plugins.index_state_management.policy_id": "marketplace-logs-policy"
      },
      "mappings": {
        "properties": {
          "@timestamp":      { "type": "date" },
          "level":           { "type": "keyword" },
          "msg":             { "type": "text", "fields": { "keyword": { "type": "keyword", "ignore_above": 512 } } },
          "message":         { "type": "text", "fields": { "keyword": { "type": "keyword", "ignore_above": 512 } } },
          "logger":          { "type": "keyword" },
          "trace_id":        { "type": "keyword" },
          "span_id":         { "type": "keyword" },
          "request_id":      { "type": "keyword" },
          "method":          { "type": "keyword" },
          "path":            { "type": "keyword" },
          "status":          { "type": "integer" },
          "latency":         { "type": "float" },
          "container_name":  { "type": "keyword" },
          "container_id":    { "type": "keyword" },
          "environment":     { "type": "keyword" },
          "stack":           { "type": "keyword" },
          "caller":          { "type": "keyword" },
          "error":           { "type": "text" }
        }
      }
    }
  }'
echo ""
echo "Index Template criado com sucesso!"
