const fs = require('fs');

const SELLERS = [
  {"id": "TechStore", "name": "TechStore Brasil", "type": "PROFESSIONAL", "status": "ACTIVE", "reputation": {"score": 4.9, "total_reviews": 2000, "cancellation_rate": 0.01, "delivery_performance": 0.99}},
  {"id": "ModaBrasil", "name": "Moda Brasil Online", "type": "PROFESSIONAL", "status": "ACTIVE", "reputation": {"score": 4.5, "total_reviews": 1200, "cancellation_rate": 0.03, "delivery_performance": 0.97}},
  {"id": "SportBr", "name": "Sport Center Brasil", "type": "PROFESSIONAL", "status": "ACTIVE", "reputation": {"score": 4.7, "total_reviews": 900, "cancellation_rate": 0.02, "delivery_performance": 0.98}}
];

function randomItem(arr) {
  return arr[Math.floor(Math.random() * arr.length)];
}

let errors = 0;
let tries = 1000000;
for (let i = 0; i < tries; i++) {
  const sellerObj = JSON.parse(JSON.stringify(randomItem(SELLERS)));
  const quality = 4.5;
  const quality_factor = quality / 5.0;
  
  const base_total_reviews = sellerObj.reputation.total_reviews;
  const total_reviews = Math.max(0, Math.floor(base_total_reviews * (0.5 + quality_factor * 0.5)));
  
  const positive_ratio = 0.5 + (quality_factor * 0.3);
  const neutral_ratio = 0.3 - (quality_factor * 0.15);
  
  const positive_reviews = Math.max(0, Math.floor(total_reviews * positive_ratio));
  const neutral_reviews = Math.max(0, Math.floor(total_reviews * neutral_ratio));
  const negative_reviews = Math.max(0, total_reviews - positive_reviews - neutral_reviews);
  
  if (positive_reviews + neutral_reviews + negative_reviews !== total_reviews) {
    errors++;
  }
}
console.log(`Errors out of ${tries}: ${errors}`);
