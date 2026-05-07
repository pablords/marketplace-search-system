#!/bin/bash

# Configurações
OS_URL="http://localhost:9200"
FREE_THRESHOLD=15  # Porcentagem mínima de espaço livre (15%)
LOG_FILE="/tmp/opensearch_cleanup.log"

echo "[$(date)] Iniciando verificação de disco do OpenSearch..." >> $LOG_FILE

# 1. Verificar espaço livre médio nos nós do cluster
# Retorna a porcentagem de espaço livre (ex: 5.4)
FREE_PCT=$(curl -s "$OS_URL/_nodes/stats/fs" | python3 -c "
import sys, json
data = json.load(sys.stdin)
total = 0
available = 0
for node in data['nodes'].values():
    for disk in node['fs']['data']:
        total += disk['total_in_bytes']
        available += disk['available_in_bytes']
if total == 0: print(100)
else: print(round((available / total) * 100, 2))
")

echo "[$(date)] Espaço livre atual: $FREE_PCT%" >> $LOG_FILE

# 2. Se o espaço livre for menor que o threshold, iniciar limpeza
if (( $(echo "$FREE_PCT < $FREE_THRESHOLD" | bc -l) )); then
    echo "[$(date)] ⚠️ DISCO CRÍTICO ($FREE_PCT% < $FREE_THRESHOLD%). Iniciando limpeza de emergência..." >> $LOG_FILE
    
    # Listar índices de logs e jaeger, ordenados pelo mais antigo
    OLD_INDICES=$(curl -s "$OS_URL/_cat/indices?h=index,creation.date&s=creation.date" | grep -E "logs-|jaeger-" | awk '{print $1}')
    
    if [ -z "$OLD_INDICES" ]; then
        echo "[$(date)] Nenhum índice de log encontrado para apagar." >> $LOG_FILE
    else
        # Pegar apenas o primeiro (mais antigo)
        INDEX_TO_DELETE=$(echo "$OLD_INDICES" | head -n 1)
        
        echo "[$(date)] Deletando índice mais antigo: $INDEX_TO_DELETE" >> $LOG_FILE
        DELETE_RES=$(curl -s -X DELETE "$OS_URL/$INDEX_TO_DELETE")
        echo "[$(date)] Resultado: $DELETE_RES" >> $LOG_FILE
        
        # Desbloquear o cluster caso tenha caído no flood-stage ou tenha bloqueio manual
        curl -s -X PUT "$OS_URL/_all/_settings" -H 'Content-Type: application/json' -d'{"index.blocks.read_only_allow_delete": null}' >> $LOG_FILE
        curl -s -X PUT "$OS_URL/_cluster/settings" -H 'Content-Type: application/json' -d'{"persistent": {"cluster.blocks.create_index": null}}' >> $LOG_FILE
    fi
else
    echo "[$(date)] Espaço em disco OK." >> $LOG_FILE
fi
