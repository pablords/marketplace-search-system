# Análise de Latência de Escrita & Otimização do Kafka Producer

Este documento apresenta a análise matemática e arquitetural das latências de escrita do `catalog-service` observadas durante os testes de carga com o k6, fornecendo o embasamento teórico para os ajustes necessários para atingir a meta de **P95 < 500ms**.

---

## 1. Cenário e Métricas de Benchmark

Nos testes de estresse recentes com **200-250 usuários virtuais concorrentes**, o sistema apresentou os seguintes resultados:

*   **Total de Requisições:** 165.707
*   **Taxa de Erro:** 0.0006% (apenas 1 falha isolada)
*   **Throughput de Pico:** 651 RPS (Requests Per Second)
*   **Latência Média:** 288.56 ms
*   **Latência P95:** 698.57 ms (Meta desejada: < 500 ms)

---

## 2. Decomposição Matemática da Latência

A latência total da requisição de escrita ($RT_{\text{total}}$) no endpoint `POST /products` é dada pela equação:

$$RT_{\text{total}} = T_{\text{app}} + T_{\text{linger}} + T_{\text{broker\_ack}} + T_{\text{buffer\_wait}}$$

Onde:
1.  **$T_{\text{app}}$ (Processamento Interno):** Tempo gasto no Pod (JSON parsing, validação de beans, mapeamento de DTOs e serialização Avro). Geralmente constante e de baixíssimo custo: **~5ms a 15ms**.
2.  **$T_{\text{linger}}$ (Tempo de Espera do Acumulador):** Atraso artificial configurado via `linger.ms` para agrupar mensagens em lotes. Adiciona um atraso de **até $X$ ms** (no nosso caso, 10ms).
3.  **$T_{\text{broker\_ack}}$ (Confirmação do Broker):** Tempo que o broker do Kafka leva para registrar a mensagem no log e retornar a confirmação (ack). Depende do nível de confiabilidade (`acks`) e da velocidade do disco físico.
4.  **$T_{\text{buffer\_wait}}$ (Bloqueio por Memória Cheia):** Atraso sofrido pela thread HTTP quando o buffer do produtor (`buffer.memory`, 32MB) está cheio. Se o Kafka atrasa os acks, esta métrica cresce exponencialmente.

Como a taxa de rejeição do semáforo foi praticamente nula, sabemos que o gargalo de 698ms no P95 reside inteiramente na combinação de $T_{\text{broker\_ack}}$ e $T_{\text{buffer\_wait}}$ durante o tráfego de pico de 651 RPS.

---

## 3. Estudo de Caso: Dimensionamento de Lote (Batching) a 651 RPS

Analisando a distribuição de tráfego entre as réplicas no pico de 651 RPS com **6 Pods ativos**:

*   **Vazão por Pod ($TPS_{\text{pod}}$):** 
    $$TPS_{\text{pod}} = \frac{651 \text{ req/s}}{6 \text{ Pods}} \approx 108.5 \text{ req/s}$$
*   **Intervalo médio entre requisições no mesmo Pod:** 
    $$\text{Intervalo} = \frac{1000\text{ms}}{108.5} \approx 9.2\text{ms}$$
*   **Volume de dados gerado por Pod (com Payload Avro estimado em 1 KB):**
    $$\text{Taxa de dados} = 108.5 \text{ req/s} \times 1\text{ KB} = 108.5\text{ KB/s}$$

### O Desalinhamento do Batch de 64 KB:
Anteriormente, configuramos `batch-size: 65536` (64 KB) e `linger.ms: 10`.
*   Para que o produtor preenchesse os 64 KB apenas por tamanho de dados, ele precisaria esperar:
    $$\text{Tempo para encher o lote} = \frac{64 \text{ KB}}{108.5 \text{ KB/s}} \approx 590\text{ms}$$
*   Como 590ms é muito maior que o limite de tempo do `linger.ms` (10ms), o produtor **nunca atingia o tamanho máximo do lote**. Ele era forçado a despachar o lote a cada 10ms pelo timer do `linger`.
*   Com o intervalo de requisições a cada 9.2ms, o lote médio continha apenas **1.08 mensagens** ($10\text{ms} / 9.2\text{ms}$). Ou seja, o overhead de rede continuava alto por falta de agrupamento real.

---

## 4. Otimização dos Níveis de Acknowledgment (`acks`)

Sob a configuração padrão `acks=all` (ou `-1`), o produtor espera que o broker líder grave a mensagem e que todos os seguidores sincronizados (ISR) confirmem a gravação em disco. 

Em um ambiente localizado virtualizado (Minikube compartilhando o mesmo disco SSD com múltiplos serviços e bancos de dados), essa replicação síncrona causa alta contenção de I/O de escrita de arquivos de log no broker. Sob estresse, isso introduz picos de latência que jogam o P95 para quase 700ms.

Ao alterar para `acks=1`:
*   O broker líder responde imediatamente após persistir a mensagem em sua memória/disco local, sem esperar os seguidores.
*   Isso economiza a latência da viagem de rede interna (inter-broker) e a concorrência de escrita paralela nos seguidores durante a chamada HTTP síncrona da API.
*   **Impacto esperado:** Redução direta de **150ms a 300ms** nas latências de cauda (P95/P99).

---

## 5. Dimensionamento do Número de Partições

Se o tópico do Kafka tiver poucas partições (como 3), mas temos 6 Pods rodando a API de escrita e concorrendo, as escritas em disco do Kafka se concentram em poucos arquivos físicos de partição de log no broker líder.

Para maximizar a paralelização de escrita em disco e evitar filas de bloqueio de arquivos, o número de partições deve seguir a regra:

$$\text{Partições Mínimas} = \text{Número de Pods Produtores} \times \text{Threads de Consumo} = 6 \text{ Pods} \times 3 \text{ threads} = 18 \text{ partições}$$

---

## 6. Configuração de Baixa Latência Recomendada (P95 < 500ms)

Para ajustar o comportamento do produtor e reduzir a latência síncrona sem sacrificar a resiliência básica, altere o arquivo `application.yml` para as seguintes diretrizes:

```yaml
  kafka:
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: io.confluent.kafka.serializers.KafkaAvroSerializer
      compression-type: lz4            # Compactação rápida para poupar I/O de rede e disco
      batch-size: 32768                # Reduzido de 64KB para 32KB (lote mais ágil e fácil de encher)
      acks: 1                          # ALTA PRIORIDADE: Agradecimento rápido do líder do cluster
      properties:
        linger.ms: 5                   # Reduzido de 10ms para 5ms (liberação veloz de lotes)
        max.block.ms: 2000             # Liberação rápida de thread HTTP em caso de saturação
```
