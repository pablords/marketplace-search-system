# Análise Crítica de Arquitetura e Escalonamento - Marketplace Search System

Este documento apresenta uma revisão arquitetural detalhada dos microsserviços do **Marketplace Search System** e de sua infraestrutura Kubernetes no repositório **k8s-lab**. Focamos em identificar gargalos latentes, calcular limites de capacidade para eventos de pico (como a Black Friday) e definir uma estratégia robusta para preparação.

---

## 1. Mapeamento e Análise dos Componentes

A arquitetura do sistema divide-se em dois fluxos principais:
*   **Fluxo de Leitura (Busca Semântica):** `API Gateway` $\rightarrow$ `Search Service` $\rightarrow$ `ML Services` (Embedding e Ranking) & `OpenSearch`.
*   **Fluxo de Escrita (Ingestão/Indexação):** `PostgreSQL (Catalog DB)` $\rightarrow$ `Debezium (Kafka Connect)` $\rightarrow$ `Kafka` $\rightarrow$ `Indexing Service` $\rightarrow$ `OpenSearch`.

Abaixo, analisamos criticamente as configurações atuais de cada elemento:

### A. Banco de Dados (PostgreSQL)
*   **Arquivo de Configuração:** [manifest.yml (catalog-db)](file:///home/pablo/projetos/k8s-lab/apps/marketplace/data/catalog-db/manifest.yml)
*   **Configuração de Memória:** `shared_buffers = 1GB` (com limite de container de 3Gi) e `synchronous_commit = off`.
*   **Gargalo de Conexões:** `max_connections = 50`.
*   **Análise Crítica:**
    *   **Riscos ACID:** `synchronous_commit = off` melhora a taxa de escrita, mas introduz risco de perda das últimas transações em caso de queda do Pod do banco. Para um catálogo de produtos isso pode ser aceitável, mas requer atenção em transações financeiras ou atualização de estoque crítico.
    *   **Ausência de Alta Disponibilidade (HA):** O banco roda como StatefulSet de 1 réplica. Qualquer falha derruba o fluxo de escrita do catálogo.
    *   **Saturação de Conexões:** O pool Hikari do `catalog-service` está configurado para um máximo de 5 conexões por Pod. O HPA do `catalog-service` pode escalar até 10 réplicas ($10 \times 5 = 50$ conexões). Como o Postgres está limitado a `max_connections = 50`, no pico de escala a aplicação irá consumir **todas** as conexões, impedindo conexões do `postgres-exporter` (que monitora o banco) e de ferramentas administrativas, além de causar erros de timeout de conexão na própria aplicação.

### B. Ingestão de Dados (Debezium e Kafka)
*   **Arquivos de Configuração:** [manifest.yml (Kafka)](file:///home/pablo/projetos/k8s-lab/apps/kafka/manifest.yml), [manifest.yml (kafka-connect)](file:///home/pablo/projetos/k8s-lab/apps/marketplace/integration/kafka-connect/manifest.yml), e [application.yml (catalog-service)](file:///home/pablo/projetos/marketplace-search-system/catalog-service/bootstrap/src/main/resources/application.yml)
*   **Configurações:** Kafka com 3 réplicas (1GB Heap), Debezium criando tópicos com 3 partições.
*   **Análise Crítica:**
    *   **Subutilização do Indexador:** Conforme analisado anteriormente, as 3 partições padrão dos tópicos do Debezium limitam o paralelismo do `indexing-service` a apenas 3 threads de execução em todo o cluster, tornando o HPA de até 10 pods inútil e gerando atraso (*lag*) na indexação de novos produtos sob carga.
    *   **Limitação do Debezium Connector:** O conector Postgres do Debezium lê o WAL (Write-Ahead Log) de forma sequencial utilizando uma única task (`tasks.max: 1`). Embora isso seja uma limitação inerente do PostgreSQL (que possui apenas uma thread de replicação lógica ativa por slot), a publicação inicial do snapshot pode se tornar extremamente lenta se o banco crescer muito.

### C. Serviços de Machine Learning (ml-embedding e ml-ranking)
*   **Arquivos de Configuração:** [manifest.yml (embedding-service)](file:///home/pablo/projetos/k8s-lab/apps/marketplace/ml/embedding-service/manifest.yml) e [manifest.yml (ranking-service)](file:///home/pablo/projetos/k8s-lab/apps/marketplace/ml/ranking-service/manifest.yml)
*   **Configurações de Threads:** O embedding-service desativa o paralelismo de bibliotecas internas (`OMP_NUM_THREADS=1`, `MKL_NUM_THREADS=1`, etc.).
*   **Recursos do Embedding:** Requests de 500m CPU e 1Gi RAM; Limits de 2000m CPU e 2Gi RAM. HPA máximo de 5 réplicas.
*   **Análise Crítica:**
    *   **Gargalo de CPU:** Modelos de Machine Learning (como BERT ou CLIP) rodando em CPU para gerar embeddings são computacionalmente caros. O tempo médio de resposta pode variar de 15ms a 80ms por requisição.
    *   **Efeito Cascata no Fluxo de Busca:** Como o `search-service` executa uma requisição síncrona para o `ml-embedding-service` para cada busca realizada pelo usuário, se o serviço de ML saturar a CPU, o tempo de resposta da busca aumentará drasticamente. Isso causará exaustão dos pools de conexões HTTP (ex: OkHttp/WebClient) no `search-service` e no `API Gateway`, resultando em erros 504 (Timeout) generalizados.

### D. Armazenamento de Busca (OpenSearch)
*   **Arquivo de Configuração:** [opensearch.yml](file:///home/pablo/projetos/k8s-lab/k8s/observability/opensearch.yml)
*   **Recursos:** StatefulSet de 1 réplica, com heap JVM limitado a apenas 512MB (`OPENSEARCH_JAVA_OPTS="-Xms512m -Xmx512m"`).
*   **Análise Crítica:**
    *   **Sério Risco de OOM:** Uma heap de 512MB é extremamente baixa para o OpenSearch, especialmente operando com busca vetorial (k-NN), que exige muita memória RAM para carregar os grafos de proximidade (HNSW) na memória nativa. Sob carga real, o OpenSearch sofrerá com pausas de Garbage Collector (Stop-the-World) longas ou falhará com erro de Out Of Memory (OOM).
    *   **Ponto Único de Falha:** Rodando com apenas 1 réplica, a busca do e-commerce fica 100% indisponível se o nó sofrer reinicialização.

---

## 2. Modelagem Matemática de Capacidade (Black Friday)

Vamos projetar a infraestrutura para suportar uma meta de pico típica de Black Friday:
*   **Carga de Pico Alvo:** **5.000 buscas por segundo (QPS)** no fluxo de leitura.
*   **Carga de Escrita Alvo:** **200 atualizações de produtos por segundo (Write QPS)** (mudanças rápidas de preço e estoque).

### Cálculo 1: Dimensionamento do Serviço de Embedding (Busca Semântica)
*   **Tempo médio de execução de 1 embedding em CPU (1 Core):** $T_{emb} = 15\text{ ms} = 0,015\text{ s}$
*   **Capacidade de processamento por Core:**
    $$\text{QPS por core} = \frac{1}{T_{emb}} = \frac{1}{0,015} \approx 66,6\text{ QPS/core}$$
*   **Cores de CPU necessários para suportar 5.000 QPS:**
    $$\text{Total Cores} = \frac{5.000\text{ QPS}}{66,6\text{ QPS/core}} \approx 75\text{ Cores de CPU}$$
*   **Configuração de Pods:**
    *   Se cada pod de embedding tem limite de `2 Cores (2000m)`:
        $$\text{Número de Pods Necessários} = \frac{75\text{ cores}}{2\text{ cores/pod}} \approx 38\text{ Pods}$$
    *   *Nota Histórica:* O HPA atual do embedding-service está configurado com `maxReplicas: 5` (suporta apenas ~660 QPS). **Ele quebraria com apenas 13% da carga projetada.**

### Cálculo 2: Dimensionamento do Consumo de Mensagens (Kafka -> Indexing)
*   **Tempo médio para indexar 1 produto:** $T_{index} = 50\text{ ms} = 0,05\text{ s}$ (inclui chamar o embedding-service + salvar no OpenSearch).
*   **Vazão por thread consumidora:**
    $$\text{Vazão por thread} = \frac{1}{0,05} = 20\text{ msg/segundo}$$
*   **Threads de consumo necessárias para vazão de 200 Write QPS:**
    $$\text{Total Threads} = \frac{200\text{ msg/s}}{20\text{ msg/s/thread}} = 10\text{ threads}$$
*   **Configuração de Partições e Pods:**
    *   Como cada Pod do `indexing-service` usa concorrência 3, precisamos de pelo menos $\lceil 10 / 3 \rceil = 4\text{ Pods}$.
    *   O número de partições nos tópicos do Kafka deve ser **pelo menos igual ao número de threads** (ou seja, $\ge 10$, recomendado **30 partições** para dar margem de escala para até 10 Pods).

### Cálculo 3: Dimensionamento do Banco de Dados (PostgreSQL)
*   **Throughput de Escrita:** 200 Write QPS.
*   **Conexões simultâneas estimadas:** Se as consultas de escrita são rápidas ($10\text{ ms}$ de tempo de banco):
    $$\text{Conexões ativas necessárias} = 200\text{ QPS} \times 0,010\text{ s} = 2\text{ conexões simultâneas}$$
*   **Conexões do Pool Hikari:** 
    *   O gargalo não está no tempo de execução da query, mas nas conexões ociosas mantidas pelos Pools das réplicas da aplicação.
    *   Com HPA do `catalog-service` no máximo (10 pods): $10\text{ pods} \times 5\text{ conns/pod} = 50\text{ conexões}$.
    *   Para evitar a exaustão de conexões no Postgres:
        $$\text{max\_connections do Postgres} \ge (\text{Max Pods} \times \text{Hikari Pool Size}) + \text{Conexões Extras (Exporters/Debezium/Admins)}$$
        $$\text{max\_connections} \ge 50 + 20 = 70\text{ conexões (Configurar 100 ou 150 para segurança)}$$

---

## 3. Estratégia de Preparação e Mitigação de Gargalos

Para preparar essa arquitetura para a Black Friday, dividimos o plano em três pilares estratégicos:

### Pilar I: Otimizações de Código e Arquitetura (Curto Prazo)
1.  **Criação de Cache de Embeddings de Busca (Crítico):**
    *   A maior parte das buscas em eventos de varejo foca nos mesmos 500 a 1000 termos de busca ("smart TV", "geladeira", etc.).
    *   Implementar um cache no Redis no `search-service` para guardar o vetor de embedding gerado a partir de strings de busca.
    *   *Impacto:* Com uma taxa de acerto (Cache Hit Rate) de 80%, a demanda sobre o `ml-embedding-service` cairia de 5.000 QPS para 1.000 QPS, reduzindo a necessidade de pods de embedding de 38 para apenas 8 pods.
2.  **Mecanismo de Degradamento Funcional (Fallback/Circuit Breaker):**
    *   Se o `ml-ranking-service` (reranking) falhar ou demorar mais do que 150ms, o `search-service` deve desativar o reranking automaticamente e retornar os resultados brutos pontuados pelo OpenSearch (busca léxica/vetorial simples). É melhor exibir resultados ligeiramente menos precisos de forma instantânea do que retornar erro 500 para o cliente.
3.  **Processamento em Lote (Batching) no Consumidor:**
    *   Alterar o `indexing-service` para consumir mensagens do Kafka em blocos (batches) em vez de processar registro por registro. Isso reduz consideravelmente a latência de comunicação com o OpenSearch (utilizando a API `_bulk`).

### Pilar II: Ajustes de Infraestrutura Kubernetes (Médio Prazo)
1.  **OpenSearch resiliente e com mais recursos:**
    *   Escalar o cluster de OpenSearch para no mínimo **3 nós** (1 master-eligible dedicado e 2 data nodes).
    *   Aumentar o limite de memória dos nós do OpenSearch e configurar a heap JVM para pelo menos **2GB** (evitando Stop-the-World GC).
2.  **Aumento de Partições no Kafka e Debezium:**
    *   Configurar a criação de tópicos para **30 partições** no Debezium e no Broker Kafka.
3.  **Ajuste dos Limites do PostgreSQL:**
    *   Aumentar o `max_connections` para **150** no `catalog-db` e redimensionar o pool Hikari das aplicações para manter um tamanho enxuto (ex: `minimum-idle: 2` e `maximum-pool-size: 8`).
    *   Subir uma réplica de leitura do PostgreSQL caso o `catalog-service` precise ler dados em alta volumetria durante a Black Friday.

### Pilar III: Execução e Validação (Testes de Carga)
1.  **Testes de Stress com Ferramentas Modernas (Ex: K6 ou Locust):**
    *   Simular os cenários descritos nos cálculos utilizando ferramentas de teste de carga.
    *   Executar o teste injetando carga progressiva até atingir os 5.000 QPS no `API Gateway` para validar se as HPAs respondem rápido o suficiente e se a latência se mantém abaixo de 300ms.
2.  **Monitoramento via OpenTelemetry:**
    *   Utilizar os rastreamentos (traces) do OpenTelemetry que já estão configurados nas suas aplicações para identificar em tempo real qual chamada externa (OpenSearch, ML Services ou Redis) é o elo mais fraco da corrente durante o teste de stress.
