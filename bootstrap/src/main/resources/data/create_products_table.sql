-- Script DDL para criar tabela products no PostgreSQL
-- Servidor: postgres.pablolab.online
-- Database: marketplace
-- User: marketplace

-- Criar tabela de produtos
CREATE TABLE IF NOT EXISTS products (
    id VARCHAR(255) PRIMARY KEY,
    title VARCHAR(500) NOT NULL,
    description TEXT,
    price DECIMAL(10, 2) NOT NULL,
    currency VARCHAR(10) NOT NULL DEFAULT 'BRL',
    available_quantity INTEGER NOT NULL DEFAULT 0,
    condition VARCHAR(20) NOT NULL DEFAULT 'NEW',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    
    -- Categoria
    category_id VARCHAR(255),
    category_name VARCHAR(255),
    category_path VARCHAR(500),
    
    -- Marca
    brand_id VARCHAR(255),
    brand_name VARCHAR(255),
    brand_description TEXT,
    
    -- Vendedor
    seller_id VARCHAR(255),
    seller_nickname VARCHAR(255),
    seller_type VARCHAR(50),
    seller_status VARCHAR(50),
    seller_score DECIMAL(5, 2),
    seller_total_reviews INTEGER,
    seller_cancellation_rate DECIMAL(5, 2),
    seller_delivery_performance DECIMAL(5, 2),
    
    -- Métricas
    total_sold INTEGER DEFAULT 0,
    view_count INTEGER DEFAULT 0,
    conversion_rate DECIMAL(5, 2) DEFAULT 0.0,
    average_rating DECIMAL(3, 2) DEFAULT 0.0,
    review_count INTEGER DEFAULT 0,
    
    -- Atributos como JSONB
    attributes JSONB,
    
    -- Timestamps
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Constraints
    CONSTRAINT products_price_check CHECK (price >= 0),
    CONSTRAINT products_available_quantity_check CHECK (available_quantity >= 0)
);

-- Criar índices para melhor performance
CREATE INDEX IF NOT EXISTS idx_products_category_id ON products(category_id);
CREATE INDEX IF NOT EXISTS idx_products_brand_id ON products(brand_id);
CREATE INDEX IF NOT EXISTS idx_products_seller_id ON products(seller_id);
CREATE INDEX IF NOT EXISTS idx_products_status ON products(status);
CREATE INDEX IF NOT EXISTS idx_products_created_at ON products(created_at);
CREATE INDEX IF NOT EXISTS idx_products_price ON products(price);

-- Trigger para atualizar updated_at automaticamente
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_products_updated_at 
    BEFORE UPDATE ON products 
    FOR EACH ROW 
    EXECUTE FUNCTION update_updated_at_column();

-- Habilitar replicação lógica para Debezium (CDC)
ALTER TABLE products REPLICA IDENTITY FULL;

-- Verificar se a tabela foi criada corretamente
SELECT 
    schemaname,
    tablename,
    tableowner 
FROM pg_tables 
WHERE tablename = 'products';

-- Mostrar estrutura da tabela
\d products;

COMMENT ON TABLE products IS 'Tabela de produtos para marketplace com suporte a CDC via Debezium';
COMMENT ON COLUMN products.id IS 'Identificador único do produto (ex: MLB001)';
COMMENT ON COLUMN products.attributes IS 'Atributos específicos do produto em formato JSON';
COMMENT ON COLUMN products.created_at IS 'Data/hora de criação do produto';
COMMENT ON COLUMN products.updated_at IS 'Data/hora da última atualização (auto-atualizada via trigger)';