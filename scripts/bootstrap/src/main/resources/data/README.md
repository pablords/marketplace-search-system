# Scripts DDL para PostgreSQL

Este diretório contém os scripts necessários para criar e popular a tabela `products` no servidor PostgreSQL externo.

## Configuração do Servidor

- **Host**: postgres.pablolab.online
- **Porta**: 5432
- **Database**: marketplace
- **Usuário**: marketplace
- **Senha**: marketplace123

## Scripts Disponíveis

### 1. create_products_table.sql
Cria a tabela `products` com:
- Estrutura completa da tabela
- Índices para performance
- Trigger para auto-atualização do campo `updated_at`
- Configuração para replicação lógica (CDC com Debezium)

### 2. insert_sample_products.sql
Insere dados de exemplo para teste:
- 3 produtos de categorias diferentes
- Dados completos incluindo atributos em JSONB
- Validação dos dados inseridos

## Como Executar

### Opção 1: Via psql (se disponível)
```bash
# Executar script de criação
psql -h postgres.pablolab.online -p 5432 -U marketplace -d marketplace -f create_products_table.sql

# Executar script de inserção (opcional)
psql -h postgres.pablolab.online -p 5432 -U marketplace -d marketplace -f insert_sample_products.sql
```

### Opção 2: Via cliente gráfico
1. Conecte-se ao servidor usando um cliente como pgAdmin, DBeaver, ou similar
2. Execute o conteúdo do arquivo `create_products_table.sql`
3. Opcionalmente, execute o arquivo `insert_sample_products.sql`

### Opção 3: Via aplicação web
1. Acesse o painel de administração do PostgreSQL
2. Copie e cole o conteúdo dos scripts na interface SQL
3. Execute os comandos

## Verificação

Após executar os scripts, você pode verificar se tudo foi criado corretamente:

```sql
-- Verificar se a tabela existe
SELECT tablename FROM pg_tables WHERE tablename = 'products';

-- Verificar estrutura da tabela
\d products;

-- Verificar dados (se inseriu os exemplos)
SELECT COUNT(*) FROM products;
```

## CDC (Change Data Capture)

A tabela está configurada com `REPLICA IDENTITY FULL` para suporte ao Debezium CDC. Isso permite que as alterações sejam capturadas e enviadas para o Kafka automaticamente.

## Troubleshooting

### Erro: "relation products does not exist"
- Execute o script `create_products_table.sql` primeiro

### Erro de conexão
- Verifique se o servidor PostgreSQL está acessível
- Confirme as credenciais de conexão
- Verifique se o firewall permite conexões na porta 5432

### Erro de permissões
- Certifique-se de que o usuário `marketplace` tem permissões para:
  - CREATE TABLE
  - CREATE INDEX
  - CREATE FUNCTION
  - CREATE TRIGGER