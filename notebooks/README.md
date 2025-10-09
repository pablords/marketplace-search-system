# 📊 Análise de Dados de Produtos - Marketplace Search System

Este notebook fornece ferramentas completas para visualizar e analisar os dados de produtos do sistema de busca do marketplace usando Python e pandas.

## 🚀 Como Usar

### 1. Configuração Rápida
```bash
cd notebooks/
./setup_analysis.sh
```

### 2. Configuração Manual
```bash
# Criar ambiente virtual
python3 -m venv venv
source venv/bin/activate

# Instalar dependências
pip install -r requirements.txt

# Iniciar Jupyter
jupyter notebook product_data_analysis.ipynb
```

## 📋 Funcionalidades

### 🔍 Carregamento de Dados
- ✅ Conecta automaticamente ao Elasticsearch
- ✅ Carrega dados de produtos em tempo real
- ✅ Fallback para dados de exemplo se Elasticsearch não estiver disponível
- ✅ Conversão automática de tipos de dados

### 📊 Visualizações
- **Básicas**: Histogramas, gráficos de barras, boxplots
- **Avançadas**: Matriz de correlação, análise temporal, scatter plots
- **Interativas**: Gráficos Plotly com zoom, filtros e tooltips

### 🔧 Filtros e Análises
- ✅ Filtros por preço, categoria, marca, vendedor
- ✅ Filtros por estoque e reputação
- ✅ Análise de produtos ativos/inativos
- ✅ Ferramentas de análise personalizada

### 📤 Exportação
- ✅ Exportar dados filtrados em CSV
- ✅ Relatórios estatísticos em TXT
- ✅ Planilhas Excel com múltiplas abas
- ✅ Relatórios automáticos com timestamp

## 📈 Exemplos de Análises

### Produtos de Luxo
```python
luxury_products = filter_products(df_products, min_price=500)
analyze_filtered_data(luxury_products, "Produtos de Luxo")
```

### Produtos com Boa Reputação
```python
quality_products = filter_products(
    df_products, 
    min_stock=10, 
    min_reputation=4.0
)
```

### Análise por Categoria
```python
category_products = filter_products(
    df_products, 
    categories=['Eletrônicos', 'Roupas']
)
```

## 🛠️ Estrutura do Notebook

1. **Importação de Bibliotecas** - Pandas, Matplotlib, Seaborn, Plotly
2. **Carregamento de Dados** - Elasticsearch + dados de exemplo
3. **Exploração e Limpeza** - Análise exploratória dos dados
4. **Visualizações Básicas** - Gráficos de distribuição
5. **Analytics Avançados** - Correlações e tendências
6. **Filtros Interativos** - Exploração dinâmica
7. **Gráficos Interativos** - Visualizações Plotly
8. **Exportação** - Relatórios e dados

## 🔗 Dependências

### Principais
- `pandas` - Manipulação de dados
- `matplotlib` - Visualização básica
- `seaborn` - Visualização estatística
- `plotly` - Gráficos interativos
- `requests` - Conexão com Elasticsearch

### Opcionais
- `xlsxwriter` - Exportação para Excel
- `scikit-learn` - Análises avançadas
- `jupyter` - Interface de notebook

## ⚙️ Configuração do Elasticsearch

O notebook se conecta automaticamente ao Elasticsearch em:
- **URL**: `http://localhost:9200`
- **Índice**: `marketplace-products-dev`

Certifique-se de que o Elasticsearch está rodando:
```bash
docker compose up elasticsearch
```

## 💡 Dicas de Uso

### Performance
- Use filtros para reduzir o dataset antes de análises pesadas
- Para datasets grandes (>10k produtos), considere usar `size` menor no carregamento
- Gráficos interativos podem ser lentos com muitos pontos

### Personalização
- Modifique a função `filter_products()` para suas necessidades
- Ajuste cores e estilos nos gráficos matplotlib/seaborn
- Adicione novas métricas na função `analyze_filtered_data()`

### Troubleshooting
- Se Elasticsearch não conectar, dados de exemplo serão usados
- Para problemas com Plotly, execute `pip install plotly --upgrade`
- Para exportar Excel, instale: `pip install xlsxwriter`

## 📚 Recursos Úteis

- [Pandas Documentation](https://pandas.pydata.org/docs/)
- [Plotly Documentation](https://plotly.com/python/)
- [Seaborn Gallery](https://seaborn.pydata.org/examples/)
- [Matplotlib Tutorials](https://matplotlib.org/stable/tutorials/)

## 🤝 Contribuições

Para melhorar o notebook:
1. Fork o projeto
2. Crie sua feature branch
3. Commit suas mudanças
4. Push para a branch
5. Abra um Pull Request

---

🎉 **Divirta-se explorando os dados do seu marketplace!**