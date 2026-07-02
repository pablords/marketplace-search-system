#!/bin/bash

if [ ! -d ".venv" ]; then
    python3 -m venv .venv
fi

source .venv/bin/activate

# Definição das etapas de Ramp-up: total_produtos trabalhadores_concorrentes sleep_time_seconds
stages=(
    "1000 5 10"    # Etapa 1: warm-up leve
    "5000 15 15"   # Etapa 2: aumento leve
    "10000 30 20"  # Etapa 3: carga média
    "25000 50 30"  # Etapa 4: carga alta
    "59000 75 0"   # Etapa 5: carga pico (soma total: 100.000 produtos)
)

echo "🚀 Iniciando teste de criação de produtos com Ramp-up..."

for i in "${!stages[@]}"; do
    stage=(${stages[$i]})
    total=${stage[0]}
    workers=${stage[1]}
    sleep_time=${stage[2]}
    
    echo "============================================="
    echo "🔥 [Etapa $((i+1))/${#stages[@]}] total: $total | workers: $workers"
    echo "============================================="
    
    python dataset-generate/data_gen.py \
        --dataset-file ./dataset-generate/data/cache/amazon_products.csv \
        --total "$total" \
        --concurrent-workers "$workers" \
        --api-url http://api.lab.com.br/api/v1
        
    if [ "$sleep_time" -gt 0 ]; then
        echo "💤 Aguardando ${sleep_time}s antes da próxima etapa..."
        sleep "$sleep_time"
    fi
done

echo "✅ Teste de criação de produtos com Ramp-up concluído com sucesso!"