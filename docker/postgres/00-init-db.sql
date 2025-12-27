-- Script DDL Normalizado para Marketplace
-- Database: catalog

-- 1. Tabela de Categorias (Dimensão)
CREATE TABLE IF NOT EXISTS categories (
    id VARCHAR(255) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    path VARCHAR(500) NOT NULL,
    parent_id VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 2. Tabela de Marcas (Dimensão)
CREATE TABLE IF NOT EXISTS brands (
    id VARCHAR(255) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 3. Tabela de Vendedores (Dimensão com Reputação)
CREATE TABLE IF NOT EXISTS sellers (
    id VARCHAR(255) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(50),
    status VARCHAR(50),
    
    -- Métricas de Reputação (Pertencem ao Vendedor, não ao Produto)
    score DECIMAL(5, 2),
    total_reviews INTEGER DEFAULT 0,
    positive_reviews INTEGER DEFAULT 0,
    negative_reviews INTEGER DEFAULT 0,
    neutral_reviews INTEGER DEFAULT 0,
    cancellation_rate DECIMAL(5, 2),
    delivery_performance DECIMAL(5, 2),
    
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 4. Tabela Principal de Produtos
CREATE TABLE IF NOT EXISTS products (
    id VARCHAR(255) PRIMARY KEY,
    title VARCHAR(500) NOT NULL,
    description TEXT,
    price DECIMAL(10, 2) NOT NULL,
    currency VARCHAR(10) NOT NULL DEFAULT 'BRL',
    available_quantity INTEGER NOT NULL DEFAULT 0,
    condition VARCHAR(20) NOT NULL DEFAULT 'NEW',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    
    -- Chaves Estrangeiras (Relacionamentos)
    category_id VARCHAR(255) NOT NULL REFERENCES categories(id),
    brand_id VARCHAR(255) NOT NULL REFERENCES brands(id),
    seller_id VARCHAR(255) NOT NULL REFERENCES sellers(id),
    
    -- Atributos Flexíveis (JSONB)
    attributes JSONB,
    
    -- Timestamps
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT products_price_check CHECK (price >= 0),
    CONSTRAINT products_quantity_check CHECK (available_quantity >= 0)
);

-- 5. Tabela de Métricas do Produto (OneToOne - Performance)
-- Separada para evitar locks na tabela de produtos durante updates de view count
CREATE TABLE IF NOT EXISTS product_metrics (
    product_id VARCHAR(255) PRIMARY KEY REFERENCES products(id) ON DELETE CASCADE,
    total_sales INTEGER DEFAULT 0,
    total_reviews INTEGER DEFAULT 0,
    ctr DECIMAL(5, 2) DEFAULT 0.0,
    average_rating DECIMAL(3, 2) DEFAULT 0.0,
    stock_quantity INTEGER DEFAULT 0,
    popularity INTEGER DEFAULT 0,
    last_sale TIMESTAMP,
    last_view TIMESTAMP,
    quality INTEGER DEFAULT 0,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- --- ÍNDICES E PERFORMANCE ---

CREATE INDEX idx_products_category ON products(category_id);
CREATE INDEX idx_products_brand ON products(brand_id);
CREATE INDEX idx_products_seller ON products(seller_id);
CREATE INDEX idx_products_price ON products(price);
CREATE INDEX idx_products_updated_at ON products(updated_at);

-- --- TRIGGERS PARA UPDATED_AT ---

CREATE OR REPLACE FUNCTION update_timestamp_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER trg_update_products_ts BEFORE UPDATE ON products FOR EACH ROW EXECUTE PROCEDURE update_timestamp_column();
CREATE TRIGGER trg_update_sellers_ts BEFORE UPDATE ON sellers FOR EACH ROW EXECUTE PROCEDURE update_timestamp_column();
CREATE TRIGGER trg_update_metrics_ts BEFORE UPDATE ON product_metrics FOR EACH ROW EXECUTE PROCEDURE update_timestamp_column();

-- 6. Tabela de Features ML para Feature Store Offline
-- Armazena features históricas para treinamento de modelos ML
CREATE TABLE IF NOT EXISTS product_features_ml (
    product_id VARCHAR(255) NOT NULL,
    features_json JSONB NOT NULL,
    calculated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version VARCHAR(50) NOT NULL DEFAULT '1.0',
    PRIMARY KEY (product_id, calculated_at, version)
);

-- Índices para consultas eficientes
CREATE INDEX IF NOT EXISTS idx_product_features_ml_product_id ON product_features_ml(product_id);
CREATE INDEX IF NOT EXISTS idx_product_features_ml_calculated_at ON product_features_ml(calculated_at DESC);
CREATE INDEX IF NOT EXISTS idx_product_features_ml_version ON product_features_ml(version);

-- Comentários para documentação
COMMENT ON TABLE product_features_ml IS 'Feature Store Offline: armazena features históricas de ML para treinamento de modelos';
COMMENT ON COLUMN product_features_ml.product_id IS 'ID do produto';
COMMENT ON COLUMN product_features_ml.features_json IS 'Features em formato JSON (17 features: BM25, k-NN, popularity, etc.)';
COMMENT ON COLUMN product_features_ml.calculated_at IS 'Timestamp de quando as features foram calculadas';
COMMENT ON COLUMN product_features_ml.version IS 'Versão do modelo/features (para rastreamento de mudanças)';

-- --- CONFIGURAÇÃO DEBEZIUM (CDC) ---
-- 'FULL' envia o estado anterior e novo da linha no evento
ALTER TABLE products REPLICA IDENTITY FULL;
ALTER TABLE product_metrics REPLICA IDENTITY FULL;
-- Para dimensões, geralmente 'DEFAULT' (apenas PK) basta, mas FULL ajuda na desnormalização
ALTER TABLE categories REPLICA IDENTITY FULL;
ALTER TABLE brands REPLICA IDENTITY FULL;
ALTER TABLE sellers REPLICA IDENTITY FULL;