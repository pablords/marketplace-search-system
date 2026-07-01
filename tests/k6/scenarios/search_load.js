/**
 * search_load.js — Cenário de carga focado em LEITURA (busca semântica).
 *
 * Objetivo: Descobrir o throughput máximo sustentável do search-service
 * e identificar a latência P95/P99 sob diferentes volumes de VUs.
 *
 * Execução:
 *   k6 run tests/k6/scenarios/search_load.js
 *   k6 run --out experimental-prometheus-rw tests/k6/scenarios/search_load.js
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Counter, Rate } from 'k6/metrics';
import { BASE_URL, SEARCH_TERMS, CATEGORIES, randomItem, JSON_HEADERS } from '../lib/helpers.js';

// Métricas customizadas
const searchDuration  = new Trend('search_duration_ms', true);
const searchErrors    = new Counter('search_errors_total');
const searchSuccesses = new Counter('search_successes_total');
const errorRate       = new Rate('search_error_rate');

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

  // Critérios de sucesso (pass/fail automático)
  thresholds: {
    'http_req_duration{scenario:search}': ['p(95)<800', 'p(99)<2000'],
    'http_req_failed':                    ['rate<0.01'],   // < 1% de erros
    'search_error_rate':                  ['rate<0.01'],
    'search_duration_ms':                 ['p(95)<800'],
  },
};

export default function () {
  const term     = randomItem(SEARCH_TERMS);
  const category = Math.random() > 0.5 ? randomItem(CATEGORIES) : null;

  const params = { query: term, limit: 20, offset: 0 };
  if (category) params['category'] = category;

  const queryString = Object.entries(params)
    .map(([k, v]) => `${k}=${encodeURIComponent(v)}`)
    .join('&');

  const url = `${BASE_URL}/search/products?${queryString}`;

  const res = http.get(url, {
    headers: JSON_HEADERS,
    tags: { scenario: 'search', term },
  });

  const success = check(res, {
    'status 200':           (r) => r.status === 200,
    'response has results': (r) => {
      try {
        const body = JSON.parse(r.body);
        return Array.isArray(body.products) || Array.isArray(body.content) || Array.isArray(body);
      } catch { return false; }
    },
    'latency < 1s':         (r) => r.timings.duration < 1000,
  });

  searchDuration.add(res.timings.duration);
  errorRate.add(!success);

  if (success) {
    searchSuccesses.add(1);
  } else {
    searchErrors.add(1);
    console.warn(`[SEARCH] FALHA | status=${res.status} | term='${term}' | ${res.timings.duration.toFixed(0)}ms`);
  }

  sleep(Math.random() * 0.5 + 0.1); // think time: 100–600ms
}

import { htmlReport } from "https://raw.githubusercontent.com/benc-uk/k6-reporter/main/dist/bundle.js";
import { textSummary } from "https://jslib.k6.io/k6-summary/0.0.1/index.js";

export function handleSummary(data) {
  return {
    'tests/k6/results/search_load_report.html': htmlReport(data),
    stdout: textSummary(data, { indent: " ", enableColors: true }),
  };
}
