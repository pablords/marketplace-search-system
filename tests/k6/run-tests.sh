#!/usr/bin/env bash
# run-tests.sh — Orquestrador da suíte de testes de carga k6
#
# Uso:
#   ./tests/k6/run-tests.sh search    # Teste de busca semântica
#   ./tests/k6/run-tests.sh write     # Teste de criação de produtos
#   ./tests/k6/run-tests.sh mixed     # Carga mista 80% leitura / 20% escrita
#   ./tests/k6/run-tests.sh all       # Executa todos em sequência

set -e
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RESULTS_DIR="${SCRIPT_DIR}/results"
SCENARIOS_DIR="${SCRIPT_DIR}/scenarios"
SCENARIO="${1:-search}"

# ─── Cores ────────────────────────────────────────────────────────────────────
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'
BLUE='\033[0;34m'; BOLD='\033[1m'; NC='\033[0m'

# ─── Verificação / Instalação do k6 ───────────────────────────────────────────
check_k6() {
  if command -v k6 &>/dev/null; then
    echo -e "${GREEN}✅ k6 encontrado: $(k6 version | head -1)${NC}"
    return 0
  fi

  echo -e "${YELLOW}⚠️  k6 não encontrado. Instalando via apt...${NC}"
  echo "   Isso requer sudo. Execute manualmente se não funcionar:"
  echo "   https://grafana.com/docs/k6/latest/set-up/install-k6/"
  echo ""

  sudo gpg --no-default-keyring \
    --keyring /usr/share/keyrings/k6-archive-keyring.gpg \
    --keyserver hkp://keyserver.ubuntu.com:80 \
    --recv-keys C5AD17C747E3415A3642D57D77C6C491D6AC1D69
  echo "deb [signed-by=/usr/share/keyrings/k6-archive-keyring.gpg] https://dl.k6.io/deb stable main" \
    | sudo tee /etc/apt/sources.list.d/k6.list
  sudo apt-get update -qq
  sudo apt-get install -y k6

  echo -e "${GREEN}✅ k6 instalado: $(k6 version | head -1)${NC}"
}

# ─── Verificação de conectividade com a API ────────────────────────────────────
check_api() {
  echo -e "${BLUE}🔍 Verificando conectividade com api.lab.com.br...${NC}"
  if curl -sf --max-time 5 "http://api.lab.com.br/api/v1/health" &>/dev/null; then
    echo -e "${GREEN}✅ API Gateway acessível${NC}"
  else
    echo -e "${RED}❌ API Gateway NÃO acessível em http://api.lab.com.br${NC}"
    echo "   Verifique se o cluster está rodando e o /etc/hosts está configurado."
    echo "   Execute: kubectl get svc -n istio-system istio-ingressgateway"
    exit 1
  fi
}

# ─── Descoberta automática do Prometheus para Remote Write ─────────────────────
detect_prometheus_rw() {
  # Tenta descobrir o NodePort/endpoint do Prometheus
  local prom_port
  prom_port=$(kubectl get svc prometheus -n observability \
    -o jsonpath='{.spec.ports[0].nodePort}' 2>/dev/null || echo "")

  if [[ -n "$prom_port" ]]; then
    local node_ip
    node_ip=$(minikube ip 2>/dev/null || echo "")
    if [[ -n "$node_ip" ]]; then
      echo "${node_ip}:${prom_port}"
      return
    fi
  fi
  echo ""
}

# ─── Executor de cenário ───────────────────────────────────────────────────────
run_scenario() {
  local name="$1"
  local file="$2"
  local ts
  ts=$(date +%Y%m%d_%H%M%S)

  echo ""
  echo -e "${BOLD}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
  echo -e "${BOLD}🚀 Executando: ${name}${NC}"
  echo -e "${BOLD}   Arquivo:    ${file}${NC}"
  echo -e "${BOLD}   Horário:    $(date '+%Y-%m-%d %H:%M:%S')${NC}"
  echo -e "${BOLD}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"

  mkdir -p "${RESULTS_DIR}"

  # Monta argumentos de saída
  local k6_args=()
  k6_args+=("run")

  # Remote Write para Prometheus (se disponível)
  local prom_addr
  prom_addr=$(detect_prometheus_rw)
  if [[ -n "$prom_addr" ]]; then
    echo -e "${BLUE}📡 Enviando métricas para Prometheus em ${prom_addr}${NC}"
    k6_args+=("--out" "experimental-prometheus-rw")
    export K6_PROMETHEUS_RW_SERVER_URL="http://${prom_addr}/api/v1/write"
  else
    echo -e "${YELLOW}⚠️  Prometheus não detectado — métricas apenas no terminal${NC}"
  fi


  # Arquivo do cenário
  k6_args+=("${file}")

  # Executa o k6
  k6 "${k6_args[@]}"
  local exit_code=$?

  if [[ $exit_code -eq 0 ]]; then
    echo -e "\n${GREEN}✅ ${name} PASSOU — todos os thresholds foram atingidos${NC}"
  else
    echo -e "\n${RED}❌ ${name} FALHOU — um ou mais thresholds foram violados${NC}"
    echo "   Verifique o relatório em: ${RESULTS_DIR}/${name}_summary.json"
  fi

  return $exit_code
}

# ─── Main ──────────────────────────────────────────────────────────────────────
echo -e "${BOLD}"
echo "╔══════════════════════════════════════════════════════╗"
echo "║   Marketplace Search System — k6 Load Test Suite    ║"
echo "╚══════════════════════════════════════════════════════╝"
echo -e "${NC}"

check_k6
check_api

case "$SCENARIO" in
  search)
    run_scenario "search_load" "${SCENARIOS_DIR}/search_load.js"
    ;;
  write)
    run_scenario "catalog_write" "${SCENARIOS_DIR}/catalog_write.js"
    ;;
  mixed)
    run_scenario "mixed_load" "${SCENARIOS_DIR}/mixed_load.js"
    ;;
  all)
    echo -e "${YELLOW}⏳ Executando todos os cenários em sequência...${NC}"
    run_scenario "search_load"   "${SCENARIOS_DIR}/search_load.js"
    echo -e "\n${BLUE}⏸  Pausa de 60s entre cenários para estabilizar o cluster...${NC}"
    sleep 60
    run_scenario "catalog_write" "${SCENARIOS_DIR}/catalog_write.js"
    echo -e "\n${BLUE}⏸  Pausa de 60s entre cenários para estabilizar o cluster...${NC}"
    sleep 60
    run_scenario "mixed_load"    "${SCENARIOS_DIR}/mixed_load.js"
    ;;
  *)
    echo -e "${RED}Cenário desconhecido: '${SCENARIO}'${NC}"
    echo "Uso: $0 [search|write|mixed|all]"
    exit 1
    ;;
esac
