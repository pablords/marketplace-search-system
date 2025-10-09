#!/bin/bash

echo "🔧 Configurando ambiente para popular Elasticsearch..."

# Verifica se Python3 está instalado
if ! command -v python3 &> /dev/null; then
    echo "❌ Python3 não está instalado!"
    exit 1
fi

# Cria ambiente virtual se não existir
if [ ! -d "venv" ]; then
    echo "📦 Criando ambiente virtual Python..."
    python3 -m venv venv
fi

# Ativa ambiente virtual
echo "🚀 Ativando ambiente virtual..."
source venv/bin/activate

# Instala dependências
echo "📚 Instalando dependências..."
pip install -r requirements.txt

# Executa o script
echo "🎯 Executando script de população..."
python3 populate_elasticsearch.py

echo "✅ Processo finalizado!"