-- Inicialização do banco de dados para desenvolvimento

-- Criar extensões necessárias
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pg_trgm";

-- Criar schema para a aplicação
CREATE SCHEMA IF NOT EXISTS marketplace_search;

-- Conceder permissões ao usuário de desenvolvimento
GRANT ALL PRIVILEGES ON SCHEMA marketplace_search TO dev_user;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA marketplace_search TO dev_user;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA marketplace_search TO dev_user;

-- Configurações de performance para desenvolvimento
ALTER SYSTEM SET shared_preload_libraries = 'pg_stat_statements';
ALTER SYSTEM SET pg_stat_statements.track = 'all';
ALTER SYSTEM SET log_statement = 'all';

-- Índices para texto full-text (se necessário para fallback)
-- Exemplo de tabela de produtos (será criada pelo Hibernate)
/*
CREATE TABLE IF NOT EXISTS marketplace_search.products (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    title TEXT NOT NULL,
    description TEXT,
    price DECIMAL(12,2),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Índice para busca textual
CREATE INDEX IF NOT EXISTS idx_products_text_search 
ON marketplace_search.products 
USING gin(to_tsvector('portuguese', title || ' ' || COALESCE(description, '')));
*/