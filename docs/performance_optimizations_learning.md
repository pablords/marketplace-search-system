# Lições Aprendidas: Otimização de Performance em Ambientes Baseados em Mensageria

Este documento resume as estratégias adotadas para estabilizar o sistema e elevar drasticamente nossa taxa de transferência, escalando de aproximadamente **300 RPS com alta latência (>5s)** para **1000 RPS com baixíssima latência (~220ms)**.

## O Problema Inicial
Nossos testes de carga com o K6 (Load Test) revelaram um acúmulo gigante de "lag" no Kafka nos grupos de consumo de dois serviços essenciais (`indexing-service` e `catalog-service`). 
Apesar da infraestrutura ser robusta e descentralizada, os consumidores estavam atuando como o gargalo principal, incapazes de escoar a fila na mesma velocidade que os eventos chegavam.

### 1. Processamento Sequencial (1-a-1)
Ambos os serviços estavam processando os eventos *mensagem por mensagem*. Para milhares de mensagens, cada processamento incorria no custo da latência de rede.
- O `catalog-service` tentava adquirir um Lock Distribuído no Redis, verificava no banco se o item existia e depois fazia o `INSERT` no PostgreSQL de cada item de forma sequencial.
- O `indexing-service` iterava sobre produtos, enriquecia-os consultando o Redis um a um, e enviava embeddings de um a um (ou os truncava erroneamente).

Esse comportamento é conhecido como **Padrão N+1**, um assassino silencioso de performance em ambientes distribuídos.

---

## Estratégias de Solução Implementadas

### A. Leitura em Lote (Batch Listeners) no Kafka
Alteramos os ouvintes (Listeners) do Spring Kafka para o tipo `batch`. 
Isso permite que a aplicação consuma centenas de mensagens de uma só vez (ex: blocos de 500 mensagens), minimizando o custo de commit de *offset* no Kafka e preparando o terreno para processamento em massa.

**Configuração essencial:**
```yaml
spring:
  kafka:
    listener:
      type: batch
      concurrency: 3
```

### B. Paralelização de I/O de Rede com Virtual Threads
Para não ficar aguardando respostas da rede em sequência (como no Redis Lock ou no cache de features), aproveitamos as **Virtual Threads do Java 21**.

Envolvemos as requisições síncronas usando `CompletableFuture.supplyAsync(..., executor)` apontando para o `applicationTaskExecutor` do Spring. 
* **Impacto**: Em vez de fazer 500 idas ao Redis em sequência, agora disparamos 500 chamadas instantaneamente. O tempo de resposta para 500 itens passou a ser praticamente o mesmo do item mais lento daquele bloco.

### C. Inserções em Lote e Eliminação de Queries N+1
No `catalog-service`, eliminamos o gargalo no PostgreSQL fazendo deduplicação em memória antes de encostar no banco. 
1. Fizemos uma busca em lote `findAllById` enviando os 500 IDs para verificar a idempotência em **uma única query**.
2. Filtramos em memória os já existentes.
3. Enviamos o lote para inserção de uma só vez utilizando o `productRepository.saveAll`, aproveitando a capacidade de `batch_size: 20` já definida no `application-production.yml`.

### D. Segurança, Resiliência e Truncamentos
1. **Fallback via Dead Letter Queue (DLQ)**: Em um modelo Batch, se uma única mensagem no grupo de 500 falhar, todo o lote inteiro sofre *rollback*. Para evitar loops infinitos, implementamos uma captura de erro (`try-catch`) no `saveAll` e publicamos manualmente os registros rejeitados em um tópico de DLQ. Um consumidor secundário processa a DLQ iterando um-a-um, desfazendo o bloco para que eventuais falhas persistam isoladamente sem afetar os "trens expressos" normais.
2. **Resolução do Silent Truncation**: No cliente de embeddings do `indexing-service`, corrigimos um erro onde um lote > 100 itens era brutalmente podado (`subList(0, 100)`). A solução foi fazer um "chunking" (particionamento dinâmico em blocos de 100) da lista de eventos de entrada e processar as requisições externas para o motor de IA em paralelo usando a mesma lógica de Virtual Threads descrita acima.

---

## Resultados
A migração de um fluxo puramente sequencial reativo de "registro-a-registro" (Single-Record) para o modelo de "Lotes Concorrentes" (Batch + Virtual Threads) causou o salto imediato de **3x mais carga sustentada**, caindo o tempo de resposta em **~95%**. 

Este padrão arquitetural agora atua como nosso pilar para os microsserviços de entrada e de indexação no Marketplace.
