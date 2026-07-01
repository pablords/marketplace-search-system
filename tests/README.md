# 🧪 Testes de Carga — k6 Load Testing Suite

Suíte de testes de carga para o **Marketplace Search System**, baseada em [k6](https://k6.io/).  
Cobre três cenários: busca semântica, criação de produtos e carga mista realista.

---

## 📋 Pré-requisitos

### 1. Cluster Kubernetes rodando
```bash
cd ~/projetos/k8s-lab
make all        # sobe o cluster do zero
# ou
make prepare && make observability && make monitoring && make deploy-marketplace
```

### 2. `/etc/hosts` configurado
```bash
# Descubra o IP do LoadBalancer (Istio Ingress)
kubectl get svc -n istio-system istio-ingressgateway \
  -o jsonpath='{.status.loadBalancer.ingress[0].ip}'

# Adicione ao /etc/hosts:
# 192.168.49.200  api.lab.com.br grafana.lab.com.br
```

### 3. Instalar o k6

```bash
# Ubuntu / Debian
sudo gpg --no-default-keyring \
  --keyring /usr/share/keyrings/k6-archive-keyring.gpg \
  --keyserver hkp://keyserver.ubuntu.com:80 \
  --recv-keys C5AD17C747E3415A3642D57D77C6C491D6AC1D69

echo "deb [signed-by=/usr/share/keyrings/k6-archive-keyring.gpg] https://dl.k6.io/deb stable main" \
  | sudo tee /etc/apt/sources.list.d/k6.list

sudo apt-get update && sudo apt-get install k6

# Verifique:
k6 version
```

> 💡 Outras plataformas: https://grafana.com/docs/k6/latest/set-up/install-k6/

---

## 🚀 Execução

O script orquestrador [`run-tests.sh`](k6/run-tests.sh) verifica a conectividade com a API, detecta automaticamente o endpoint do Prometheus para Remote Write e executa o cenário escolhido.

```bash
cd marketplace-search-system

# Teste de busca semântica (leitura)
./tests/k6/run-tests.sh search

# Teste de criação de produtos (escrita)
./tests/k6/run-tests.sh write

# Teste de carga mista realista (80% leitura / 20% escrita)
./tests/k6/run-tests.sh mixed

# Todos os cenários em sequência (com 60s de pausa entre eles)
./tests/k6/run-tests.sh all
```

### Execução manual com k6 diretamente

```bash
# Sem remote write (só saída no terminal)
k6 run tests/k6/scenarios/search_load.js

# Com remote write para o Prometheus do cluster
export K6_PROMETHEUS_RW_SERVER_URL="http://$(minikube ip):30090/api/v1/write"
export K6_PROMETHEUS_RW_TREND_AS_NATIVE_HISTOGRAM=true
k6 run --out experimental-prometheus-rw tests/k6/scenarios/search_load.js
```

---

## 📊 Cenários

### 1. `search_load.js` — Busca Semântica (Leitura)

**Objetivo:** Descobrir o throughput máximo sustentável do `search-service`.

| Fase | Duração | VUs |
|---|---|---|
| Warm-up | 1 min | 10 |
| Ramp-up moderado | 2 min | 10 → 50 |
| Carga sustentada | 3 min | 100 |
| Pico | 2 min | 200 |
| Ramp-down | 1 min | 0 |

**Thresholds (critério de pass/fail):**
- P95 de latência < 800ms
- P99 de latência < 2000ms
- Taxa de erros HTTP < 1%

---

### 2. `catalog_write.js` — Criação de Produtos (Escrita)

**Objetivo:** Descobrir o limite de throughput do `catalog-service` + PostgreSQL para inserts.

| Fase | Duração | VUs |
|---|---|---|
| Warm-up | 1 min | 5 |
| Ramp-up | 2 min | 5 → 25 |
| Carga sustentada | 3 min | 50 |
| Pico | 2 min | 100 |
| Ramp-down | 1 min | 0 |

**Thresholds:**
- P95 de latência < 500ms
- Taxa de erros < 2% *(respostas 409 Conflict são excluídas — comportamento esperado de idempotência)*

---

### 3. `mixed_load.js` — Carga Mista Realista (80/20)

**Objetivo:** Simular o padrão real de uso — 80% leitura / 20% escrita — com dois executores paralelos.

| Executor | VUs sustentados | VUs no pico |
|---|---|---|
| `readers` (80%) | 80 VUs | 160 VUs |
| `writers` (20%) | 20 VUs | 40 VUs |

**Thresholds independentes por operação:**
- Leitura: P95 < 1000ms, P99 < 3000ms, erros < 1%
- Escrita: P95 < 700ms, erros < 3%

---

## 📁 Estrutura de Arquivos

```
tests/
├── README.md                   ← este arquivo
└── k6/
    ├── lib/
    │   └── helpers.js          # Gerador de payload, IDs únicos, search terms
    ├── scenarios/
    │   ├── search_load.js      # Cenário de leitura
    │   ├── catalog_write.js    # Cenário de escrita
    │   └── mixed_load.js       # Cenário misto
    ├── results/                # Relatórios JSON gerados (gitignore recomendado)
    └── run-tests.sh            # Script orquestrador
```

---

## 📈 Métricas no Grafana

Durante a execução, o k6 envia métricas para o Prometheus via **Remote Write** (porta `30090`).  
Acesse o Grafana em `http://grafana.lab.com.br` e filtre as métricas prefixadas com `k6_`:

| Métrica | Descrição |
|---|---|
| `k6_http_req_duration` | Latência por percentil (P50, P95, P99) |
| `k6_http_reqs` | Throughput (RPS) |
| `k6_http_req_failed` | Taxa de erros |
| `k6_vus` | Virtual users ativos ao longo do tempo |
| `k6_iterations` | Total de iterações concluídas |
| `k6_search_duration_ms` | Latência específica de buscas |
| `k6_write_duration_ms` | Latência específica de escritas |

---

## 📄 Relatórios

Após cada execução, um relatório JSON é salvo em `tests/k6/results/`:

```bash
tests/k6/results/
├── search_load_20260701_090000.json
├── catalog_write_20260701_091000.json
└── mixed_load_summary.json
```

> 💡 Adicione `tests/k6/results/` ao `.gitignore` para não versionar os relatórios.

---

## 🔗 Referências

- [k6 Documentation](https://grafana.com/docs/k6/latest/)
- [k6 Metrics Reference](https://grafana.com/docs/k6/latest/using-k6/metrics/reference/)
- [k6 Prometheus Remote Write](https://grafana.com/docs/k6/latest/results-output/real-time/prometheus-remote-write/)
- [CAPACITY.md](../../k8s-lab/CAPACITY.md) — Análise de capacidade e gargalos do sistema
