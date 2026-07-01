/**
 * mixed_load.js — Cenário de carga MISTO (80% leitura / 20% escrita).
 *
 * Objetivo: Simular o padrão real de uso do marketplace e validar o
 * comportamento do sistema como um todo sob demanda combinada.
 *
 * Execução:
 *   k6 run tests/k6/scenarios/mixed_load.js
 *   k6 run --out experimental-prometheus-rw tests/k6/scenarios/mixed_load.js
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Counter, Rate } from 'k6/metrics';
import {
  BASE_URL,
  SEARCH_TERMS,
  CATEGORIES,
  randomItem,
  generateProductPayload,
  JSON_HEADERS,
} from '../lib/helpers.js';

// Métricas separadas por operação para análise no Grafana
const searchDuration = new Trend('mixed_search_duration_ms', true);
const writeDuration  = new Trend('mixed_write_duration_ms',  true);
const searchErrors   = new Rate('mixed_search_error_rate');
const writeErrors    = new Rate('mixed_write_error_rate');
const writeConflicts = new Counter('mixed_write_conflicts_total');

export const options = {
  // Dois executores paralelos com proporção 80/20
  scenarios: {
    readers: {
      executor:          'ramping-vus',
      startVUs:          0,
      stages: [
        { duration: '1m',  target: 10  },
        { duration: '3m',  target: 80  },  // 80 VUs de leitura
        { duration: '5m',  target: 80  },
        { duration: '2m',  target: 160 },  // Pico
        { duration: '1m',  target: 0   },
      ],
      gracefulRampDown: '30s',
      exec: 'searchScenario',
    },
    writers: {
      executor:          'ramping-vus',
      startVUs:          0,
      stages: [
        { duration: '1m',  target: 2   },
        { duration: '3m',  target: 20  },  // 20 VUs de escrita
        { duration: '5m',  target: 20  },
        { duration: '2m',  target: 40  },  // Pico
        { duration: '1m',  target: 0   },
      ],
      gracefulRampDown: '30s',
      exec: 'writeScenario',
    },
  },

  thresholds: {
    // Leitura
    'mixed_search_duration_ms':  ['p(95)<1000', 'p(99)<3000'],
    'mixed_search_error_rate':   ['rate<0.01'],
    // Escrita
    'mixed_write_duration_ms':   ['p(95)<700'],
    'mixed_write_error_rate':    ['rate<0.03'],
    // Geral
    'http_req_failed':           ['rate<0.02'],
  },
};

// ---- Cenário de Leitura ----
export function searchScenario() {
  const term   = randomItem(SEARCH_TERMS);
  const addCat = Math.random() > 0.5;
  const params = `query=${encodeURIComponent(term)}&limit=20&offset=0` +
    (addCat ? `&category=${randomItem(CATEGORIES)}` : '');

  const res = http.get(`${BASE_URL}/search/products?${params}`, {
    headers: JSON_HEADERS,
    tags: { scenario: 'read', operation: 'search' },
  });

  searchDuration.add(res.timings.duration);
  const ok = check(res, { 'search 200': (r) => r.status === 200 });
  searchErrors.add(!ok);

  sleep(Math.random() * 0.5 + 0.1);
}

// ---- Cenário de Escrita ----
export function writeScenario() {
  const payload = generateProductPayload(__VU, __ITER);

  const res = http.post(`${BASE_URL}/products`, JSON.stringify(payload), {
    headers: JSON_HEADERS,
    tags: { scenario: 'write', operation: 'create_product' },
  });

  writeDuration.add(res.timings.duration);

  if (res.status === 201) {
    writeErrors.add(false);
    check(res, { 'write 201': () => true });
  } else if (res.status === 409) {
    writeConflicts.add(1);
    writeErrors.add(false);
  } else {
    writeErrors.add(true);
    console.error(`[WRITE] ${res.status} | id=${payload.id} | ${res.body.slice(0, 150)}`);
  }

  sleep(Math.random() * 0.3);
}

import { htmlReport } from "https://raw.githubusercontent.com/benc-uk/k6-reporter/main/dist/bundle.js";
import { textSummary } from "https://jslib.k6.io/k6-summary/0.0.1/index.js";

export function handleSummary(data) {
  return {
    'tests/k6/results/mixed_load_report.html': htmlReport(data),
    stdout: textSummary(data, { indent: " ", enableColors: true }),
  };
}
