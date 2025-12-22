-- Limpar dados anteriores (opcional, cuidado em produção)
TRUNCATE product_metrics, products, sellers, brands, categories CASCADE;

-- 1. Inserir Categorias
INSERT INTO categories (id, name, path) VALUES 
('CAT001', 'Eletrônicos', 'Eletrônicos > Celulares > Smartphones'),
('CAT002', 'Informática', 'Informática > Notebooks > Notebooks para Trabalho'),
('CAT003', 'Moda e Beleza', 'Moda e Beleza > Calçados > Tênis'),
('CAT004', 'Casa e Moveis', 'Casa e Moveis');

-- 2. Inserir Marcas
INSERT INTO brands (id, name, description) VALUES 
('BRAND001', 'Samsung', 'Marca líder mundial em tecnologia e inovação'),
('BRAND002', 'Dell', 'Qualidade e confiabilidade'),
('BRAND003', 'Nike', 'Just Do It'),
('BRAND004', 'Adidas', 'adidas'),

-- 3. Inserir Vendedores
INSERT INTO sellers (id, name, type, status, score, total_reviews, positive_reviews, cancellation_rate, delivery_performance) VALUES 
('SELLER001', 'TechStore', 'PROFESSIONAL', 'ACTIVE', 4.8, 1500, 1400, 0.02, 0.98),
('SELLER002', 'InfoShop', 'PROFESSIONAL', 'ACTIVE', 4.9, 2000, 1950, 0.01, 0.99),
('SELLER003', 'SportCenter', 'PROFESSIONAL', 'ACTIVE', 4.6, 800, 700, 0.05, 0.95);

-- 4. Inserir Produtos
INSERT INTO products (id, title, description, price, currency, available_quantity, condition, active, category_id, brand_id, seller_id, attributes) VALUES 
(
    'MLB001', 
    'Smartphone Samsung Galaxy S23', 
    'Smartphone top de linha com câmera de 50MP', 
    3499.99, 'BRL', 50, 'NEW', TRUE,
    'CAT001', 'BRAND001', 'SELLER001',
    '{"cor": "Preto", "memoria": "256GB"}'::jsonb
),
(
    'MLB002', 
    'Notebook Dell Inspiron 15', 
    'Notebook com i7 e 16GB RAM', 
    4299.00, 'BRL', 30, 'NEW', TRUE,
    'CAT002', 'BRAND002', 'SELLER002',
    '{"processador": "i7", "ram": "16GB"}'::jsonb
),
(
    'MLB003', 
    'Tênis Nike Air Max 270', 
    'Conforto máximo', 
    599.90, 'BRL', 100, 'NEW', TRUE,
    'CAT003', 'BRAND003', 'SELLER003',
    '{"tamanho": "42", "cor": "Branco"}'::jsonb
);


-- 5. Inserir Métricas dos Produtos
INSERT INTO product_metrics (
    product_id, 
    total_sales, 
    popularity, 
    ctr, 
    average_rating, 
    total_reviews
) VALUES 
('MLB001', 2500, 15000, 0.16, 4.7, 850),
('MLB002', 1800, 12000, 0.15, 4.8, 650),
('MLB003', 5000, 25000, 0.20, 4.5, 2100);

-- Validação
SELECT 
    p.title, 
    c.name as category, 
    b.name as brand, 
    s.name as seller
FROM products p
JOIN categories c ON p.category_id = c.id
JOIN brands b ON p.brand_id = b.id
JOIN sellers s ON p.seller_id = s.id
JOIN product_metrics m ON p.id = m.product_id;