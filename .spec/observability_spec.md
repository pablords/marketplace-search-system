# Spec: Observabilidade

**Versão:** 1.0  
**Status:** Approved  
**Data:** 13/03/2026

---

## 1. Contexto e Objetivo
> Implementar observabilidade no sistema para monitorar e analisar o desempenho e comportamento do sistema.

* **Problema:** Não temos métricas e logs para monitorar o desempenho do sistema
* **Solução:** Implementar observabilidade no sistema
* **Público-alvo:** Todos os desenvolvedores e operacionais

---

## 2. Requisitos Funcionais e Técnicos (User Stories)
*Prioridade: P0 (Crítico), P1 (Importante), P2 (Desejável)*

| ID | Descrição | Prioridade |
|:---|:---|:---:|
| RF01 | Implementar instrumentação base no sistema para coleta unificada de métricas, logs e traces. | P0 |
| RF02 | Configurar retenção e envio de logs estruturados (JSON), mascarando e excluindo ativamente dados sensíveis (PII). | P0 |
| RF03 | Implementar métricas focadas em RED (Rate, Errors, Duration), exigindo medição de latência por percentis (p95 e p99) em vez de médias. | P0 |
| RF04 | Implementar tracing distribuído garantindo a propagação ininterrupta de contexto (ex: `traceparent`) entre todas as chamadas síncronas (HTTP/gRPC) e assíncronas (Mensageria/Filas). | P0 |
| RF05 | Configurar política de amostragem (Sampling) no Tracing para evitar sobrecarga (ex: guardar 1-5% do tráfego total normal e 100% dos traces que contém erros/timeouts). | P1 |
| RF06 | Implementar monitoramento de datastores, criando dashboards e alertas no Grafana para saúde e performance de PostgreSQL, Redis e Elasticsearch (ex: cache hit ratio, uso de ram, pool de conexões, slow queries). | P0 |

---

## 3. Restrições e Tratamento de Edge Cases (Boas Práticas)
Para mitigar os principais gargalos em sistemas de busca com alta concorrência e o risco de instabilidade na própria infraestrutura de observabilidade, a implementação deve obrigatoriamente aderir às seguintes restrições:

1. **Prevenção de Alta Cardinalidade (Prometheus):** É estritamente proibido o uso de dados de domínio ilimitado (IDs de usuário, IDs de produto, UUIDs ou termos de busca de usuários) como *labels/tags* nas métricas. Tais dimensões de alta cardinalidade devem ser enviadas exclusivamente para os Logs e os Traces.
2. **Visibilidade da Latência de Cauda e Alertas (Grafana):** Os dashboards e alertas de performance devem focar sistematicamente no "Tail Latency" (os casos extremos que afetam a experiência do cliente final). A latência média linear não deve ser utilizada como gatilho de alertas de lentidão do motor de busca.
3. **Propagação de Contexto (Jaeger):** A arquitetura deve garantir que nenhum proxy, cache, fila ou proxy reverso intermediário "quebre" os headers de propagação (ex: formato padrão do W3C ou B3). Se houver quebra nesses middlewares, a observabilidade do fluxo se torna míope.

---

## 4. Stack Tecnológico de Observabilidade
Deverá instrumentar o sistema aproveitando a seguinte stack disponível, preferencialmente usando o padrão OpenTelemetry (`OTel`):
- **Grafana** (Dashboards de Visualização e Gestão de Alertas)
- **Prometheus** (Armazenamento de Métricas e Timeseries)
- **Jaeger** (Rastreabilidade, Tracing Distribuído e Grafos de Serviços)
- **Exporters (Infraestrutura)**: Plugins e binários do Prometheus para os datastores (PostgreSQL Exporter, Redis Exporter, Elasticsearch Exporter).