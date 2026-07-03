/**
 * catalog_write.js — Cenário de carga focado em ESCRITA (criação de produtos).
 *
 * Objetivo: Descobrir o throughput máximo do catalog-service + PostgreSQL
 * para inserts, e identificar gargalos de CPU, pool de conexões e lock Redis.
 *
 * Execução:
 *   k6 run tests/k6/scenarios/catalog_write.js
 *   k6 run --out experimental-prometheus-rw tests/k6/scenarios/catalog_write.js
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Counter, Rate } from 'k6/metrics';
import {
  BASE_URL,
  generateProductPayload,
  JSON_HEADERS,
} from '../lib/helpers.js';

// Métricas customizadas
const writeDuration = new Trend('write_duration_ms', true);
const writeCreated = new Counter('write_created_total');
const writeConflicts = new Counter('write_conflicts_total');  // 409 esperado
const writeErrors = new Counter('write_errors_total');     // 4xx/5xx inesperado
const writeErrorRate = new Rate('write_error_rate');

// Configuração do perfil de carga
export const options = {
  stages: [
    { duration: '1m',  target: 10  }, // Warm-up
    { duration: '2m',  target: 50  }, // Ramp-up moderado
    { duration: '3m',  target: 100 }, // Carga sustentada
    { duration: '2m',  target: 200 }, // Pico de carga
    { duration: '2m',  target: 100 }, // Redução
    { duration: '1m',  target: 0   }, // Ramp-down
  ],

  thresholds: {
    // Erros inesperados (exclui 409 via check customizado abaixo)
    'write_error_rate': ['rate<0.02'],    // < 2% de erros reais
    'write_duration_ms': ['p(95)<500'],    // P95 < 500ms
    'http_req_duration': ['p(99)<2000'],   // P99 < 2s (safety net)
  },
};

export default function () {
  const payload = generateProductPayload(__VU, __ITER);
  const body = JSON.stringify(payload);

  const res = http.post(`${BASE_URL}/products`, body, {
    headers: JSON_HEADERS,
    tags: { scenario: 'write' },
  });

  writeDuration.add(res.timings.duration);

  // 201 Created = sucesso real
  if (res.status === 201) {
    writeCreated.add(1);
    writeErrorRate.add(false);
    check(res, { 'created 201': (r) => r.status === 201 });

    // 409 Conflict = produto já existe (comportamento esperado de idempotência)
  } else if (res.status === 409) {
    writeConflicts.add(1);
    writeErrorRate.add(false);  // não conta como erro de teste

    // Qualquer outro código = falha real
  } else {
    writeErrors.add(1);
    writeErrorRate.add(true);
    console.error(
      `[WRITE] ERRO | id=${payload.id} | status=${res.status} | ` +
      `${res.timings.duration.toFixed(0)}ms | body=${res.body.slice(0, 200)}`
    );
    check(res, { 'unexpected error': () => false });
  }

  sleep(Math.random() * 0.2); // think time mínimo (simula alta pressão)
}

import { htmlReport } from "https://raw.githubusercontent.com/benc-uk/k6-reporter/main/dist/bundle.js";
import { textSummary } from "https://jslib.k6.io/k6-summary/0.0.1/index.js";

export function handleSummary(data) {
  // Adiciona breakdown customizado ao summary
  const custom = {
    created: data.metrics['write_created_total']?.values?.count ?? 0,
    conflicts: data.metrics['write_conflicts_total']?.values?.count ?? 0,
    errors: data.metrics['write_errors_total']?.values?.count ?? 0,
  };
  console.log('\n=== Write Breakdown ===');
  console.log(`  201 Created:   ${custom.created}`);
  console.log(`  409 Conflict:  ${custom.conflicts}  (esperado — idempotência)`);
  console.log(`  Outros Erros:  ${custom.errors}\n`);

  return {
    // Relatório visual bonito em HTML
    'tests/k6/results/catalog_write_report.html': htmlReport(data),
    // Summary padrão do k6 no terminal
    stdout: textSummary(data, { indent: " ", enableColors: true }),
  };
}
