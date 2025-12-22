#!/bin/bash
# Script para executar o ML Ranking Service

cd "$(dirname "$0")"
cd ..

# Ativar ambiente virtual
source .venv/bin/activate

# Verificar se uvicorn está instalado
if ! python -m pip show uvicorn > /dev/null 2>&1; then
    echo "Instalando dependências..."
    python -m pip install -r ml-ranking-service/requirements.txt
fi

# Executar o serviço
cd ml-ranking-service
python -m uvicorn main:app --host 0.0.0.0 --port 8084 --reload

