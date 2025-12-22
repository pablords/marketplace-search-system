#!/bin/bash
# Script para executar o ML Embedding Service

cd "$(dirname "$0")"
cd ..

# Ativar ambiente virtual
source .venv/bin/activate

# Verificar se uvicorn está instalado
if ! python -m pip show uvicorn > /dev/null 2>&1; then
    echo "Instalando dependências..."
    python -m pip install -r ml-embedding-service/requirements.txt
fi

# Executar o serviço
cd ml-embedding-service
python -m uvicorn main:app --host 0.0.0.0 --port 8085 --reload

