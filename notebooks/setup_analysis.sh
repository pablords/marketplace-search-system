#!/bin/bash

# Script para configurar e executar o ambiente de análise de dados

echo "🔧 Configurando ambiente de análise de dados do Marketplace..."

# Verificar se o Python está instalado
if ! command -v python3 &> /dev/null; then
    echo "❌ Python3 não encontrado. Por favor, instale o Python 3.8+ primeiro."
    exit 1
fi

# Criar ambiente virtual se não existir
if [ ! -d "venv" ]; then
    echo "📦 Criando ambiente virtual..."
    python3 -m venv venv
fi

# Ativar ambiente virtual
echo "🔄 Ativando ambiente virtual..."
source venv/bin/activate

# Instalar dependências
echo "📚 Instalando dependências..."
pip install --upgrade pip
pip install -r requirements.txt

echo "✅ Ambiente configurado com sucesso!"
echo ""
echo "🚀 Para iniciar a análise:"
echo "1. Certifique-se que o Elasticsearch está rodando (docker compose up elasticsearch)"
echo "2. Execute: jupyter notebook product_data_analysis.ipynb"
echo "3. Ou use: jupyter lab"
echo ""
echo "💡 Para ativar o ambiente virtual manualmente:"
echo "   source venv/bin/activate"
echo ""
echo "🔗 URLs úteis:"
echo "   • Jupyter: http://localhost:8888"
echo "   • Elasticsearch: http://localhost:9200"
echo "   • Kibana: http://localhost:5601"

# Perguntar se quer iniciar o Jupyter automaticamente
read -p "🤔 Deseja iniciar o Jupyter Notebook agora? (y/n): " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    echo "🚀 Iniciando Jupyter Notebook..."
    jupyter notebook product_data_analysis.ipynb
fi