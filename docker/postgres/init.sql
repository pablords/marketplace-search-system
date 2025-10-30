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
    seller_name VARCHAR(255),
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
    
    -- Índices
    CONSTRAINT products_price_check CHECK (price >= 0),
    CONSTRAINT products_available_quantity_check CHECK (available_quantity >= 0)
);

-- Criar índices para melhor performance
CREATE INDEX idx_products_category_id ON products(category_id);
CREATE INDEX idx_products_brand_id ON products(brand_id);
CREATE INDEX idx_products_seller_id ON products(seller_id);
CREATE INDEX idx_products_status ON products(status);
CREATE INDEX idx_products_created_at ON products(created_at);
CREATE INDEX idx_products_price ON products(price);

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

-- Habilitar replicação lógica para Debezium
ALTER TABLE products REPLICA IDENTITY FULL;

-- Inserir alguns produtos de exemplo para teste
INSERT INTO products (
    id, title, description, price, currency, available_quantity, condition, status,
    category_id, category_name, category_path,
    brand_id, brand_name, brand_description,
    seller_id, seller_name, seller_type, seller_status,
    seller_score, seller_total_reviews, seller_cancellation_rate, seller_delivery_performance,
    total_sold, view_count, conversion_rate, average_rating, review_count,
    attributes
) VALUES 
(
    'MLB001', 
    'Smartphone Samsung Galaxy S23', 
    'Smartphone top de linha com câmera de 50MP',
    3499.99, 
    'BRL', 
    50, 
    'NEW', 
    'ACTIVE',
    'CAT001', 
    'Eletrônicos', 
    'Eletrônicos > Celulares',
    'BRAND001', 
    'Samsung', 
    'Marca líder em tecnologia',
    'SELLER001', 
    'TechStore', 
    'PROFESSIONAL', 
    'ACTIVE',
    4.8, 
    1500, 
    0.02, 
    0.98,
    2500, 
    15000, 
    0.16, 
    4.7, 
    850,
    '{"cor": "Preto", "memoria": "256GB", "garantia": "1 ano"}'::jsonb
),
(
    'MLB002', 
    'Notebook Dell Inspiron 15', 
    'Notebook para uso profissional com Intel i7',
    4299.00, 
    'BRL', 
    30, 
    'NEW', 
    'ACTIVE',
    'CAT002', 
    'Informática', 
    'Informática > Notebooks',
    'BRAND002', 
    'Dell', 
    'Qualidade e confiabilidade',
    'SELLER002', 
    'InfoShop', 
    'PROFESSIONAL', 
    'ACTIVE',
    4.9, 
    2000, 
    0.01, 
    0.99,
    1800, 
    12000, 
    0.15, 
    4.8, 
    650,
    '{"processador": "Intel i7", "ram": "16GB", "ssd": "512GB"}'::jsonb
);
