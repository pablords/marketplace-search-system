/**
 * helpers.js — Funções utilitárias compartilhadas entre os cenários de teste k6.
 */

export const BASE_URL = 'http://api.lab.com.br/api/v1';

export const SEARCH_TERMS = [
  'smartphone', 'laptop', 'monitor', 'headphone', 'teclado',
  'mouse', 'cadeira gamer', 'mesa', 'caixa de som', 'carregador',
  'iphone', 'samsung', 'dell', 'lg', 'logitech', 'razer', 'camisa',
  'notebook', 'tablet', 'impressora', 'fone de ouvido', 'placa de vídeo',
];

export const CATEGORIES = [
  'electronics', 'office', 'computing', 'accessories', 'gaming',
];

// IDs de dimensão que já existem na base (seed populado previamente)
export const SELLERS = [
  {"id": "TechStore", "name": "TechStore Brasil", "type": "PROFESSIONAL", "status": "ACTIVE", "reputation": {"score": 4.9, "total_reviews": 2000, "cancellation_rate": 0.01, "delivery_performance": 0.99}},
  {"id": "ModaBrasil", "name": "Moda Brasil Online", "type": "PROFESSIONAL", "status": "ACTIVE", "reputation": {"score": 4.5, "total_reviews": 1200, "cancellation_rate": 0.03, "delivery_performance": 0.97}},
  {"id": "SportBr", "name": "Sport Center Brasil", "type": "PROFESSIONAL", "status": "ACTIVE", "reputation": {"score": 4.7, "total_reviews": 900, "cancellation_rate": 0.02, "delivery_performance": 0.98}}
];

export const BRANDS = [
  {"id":"PEÇAS","name":"PEÇAS","description":""},
  {"id":"TOYVIAN","name":"TOYVIAN","description":""},
  {"id":"SUPORTE","name":"SUPORTE","description":""},
  {"id":"BESPORTBLE","name":"BESPORTBLE","description":""},
  {"id":"IBASENICE","name":"IBASENICE","description":""}
];

export const CATEGORIES_DB = [
  {"id":"29517","name":"Acessórios de Ferramentas Elétricas","parent_id":null,"path":"/acessórios-de-ferramentas-elétricas"},
  {"id":"22504","name":"Acessórios e Artigos Eletrônicos","parent_id":null,"path":"/acessórios-e-artigos-eletrônicos"},
  {"id":"60629","name":"Acessórios e Peças para Motos","parent_id":null,"path":"/acessórios-e-peças-para-motos"},
  {"id":"63771","name":"Acessórios para Celular","parent_id":null,"path":"/acessórios-para-celular"},
  {"id":"66956","name":"Acessórios para Computador","parent_id":null,"path":"/acessórios-para-computador"}
];

/**
 * Retorna um elemento aleatório de um array.
 */
export function randomItem(arr) {
  return arr[Math.floor(Math.random() * arr.length)];
}

/**
 * Gera um ID de produto único no formato Amazon (B0XXXXXXXXX).
 * Usa o VU ID + timestamp para garantir unicidade entre execuções paralelas.
 */
export function generateProductId(vuId, iterationId) {
  const ts = Date.now().toString(36).toUpperCase().slice(-4);
  const vu = vuId.toString(36).toUpperCase().padStart(3, '0');
  const it = iterationId.toString(36).toUpperCase().padStart(4, '0');
  return `K6${vu}${it}${ts}`;
}

/**
 * Gera um payload de produto válido para o endpoint POST /api/v1/products.
 */
export function generateProductPayload(vuId, iterationId) {
  const id = generateProductId(vuId, iterationId);
  const qty = Math.floor(Math.random() * 500) + 1;
  const pop = Math.floor(Math.random() * 5000);
  
  // Clone profundo do seller para evitar modificar a constante original e permitir alterar reputation
  const sellerObj = JSON.parse(JSON.stringify(randomItem(SELLERS)));
  
  const quality = 4.5;
  const quality_factor = quality / 5.0;
  
  // Base total reviews
  const base_total_reviews = sellerObj.reputation.total_reviews;
  const total_reviews = Math.max(0, Math.floor(base_total_reviews * (0.5 + quality_factor * 0.5)));
  
  const positive_ratio = 0.5 + (quality_factor * 0.3);
  const neutral_ratio = 0.3 - (quality_factor * 0.15);
  
  const positive_reviews = Math.max(0, Math.floor(total_reviews * positive_ratio));
  const neutral_reviews = Math.max(0, Math.floor(total_reviews * neutral_ratio));
  const negative_reviews = Math.max(0, total_reviews - positive_reviews - neutral_reviews);
  
  sellerObj.reputation.total_reviews = total_reviews;
  sellerObj.reputation.positive_reviews = positive_reviews;
  sellerObj.reputation.neutral_reviews = neutral_reviews;
  sellerObj.reputation.negative_reviews = negative_reviews;
  
  return {
    id: id,
    title: `Produto de Teste k6 ${id}`,
    description: `Produto gerado automaticamente pelo k6 para teste de carga. VU=${vuId} iter=${iterationId}`,
    price: parseFloat((Math.random() * 4900 + 100).toFixed(2)),
    currency: 'BRL',
    available_quantity: qty,
    condition: 'NEW',
    is_active: true,
    
    category: randomItem(CATEGORIES_DB),
    brand: randomItem(BRANDS),
    seller: sellerObj,
    
    images: [
      `https://marketplace.com/img/${id}_1.jpg`,
      `https://marketplace.com/img/${id}_2.jpg`,
      `https://marketplace.com/img/${id}_3.jpg`
    ],
    attributes: [
      `Cor: ${randomItem(['Preto', 'Branco', 'Prata', 'Azul'])}`,
      "Produto original",
      "Garantia de fábrica"
    ],
    tags: ["teste", "k6", "carga", "produto"],
    
    metrics: {
      popularity: pop,
      quality: quality,
      ctr: 0.05,
      total_views: pop * 10,
      total_sales: Math.floor(pop * 0.5),
      total_reviews: Math.floor(pop * 0.05),
      average_rating: quality,
      stock_quantity: qty,
      last_sale: new Date().toISOString(),
      last_view: new Date().toISOString()
    }
  };
}

/**
 * Headers padrão para requisições JSON.
 */
export const JSON_HEADERS = {
  'Content-Type': 'application/json',
  'Accept': 'application/json',
};
