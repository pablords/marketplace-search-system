# Guia de Integração: Apache Avro & Confluent Schema Registry

Este documento explica como o ecossistema do Marketplace está integrado com **Apache Avro** e o **Confluent Schema Registry** para garantir compatibilidade de schemas e tipagem estática nas mensagens do Kafka.

---

## 1. O que é o Schema Registry e por que usar Avro?

* **Apache Avro**: É um sistema de serialização de dados compacto e binário. Em vez de enviar payloads JSON extensos com nomes de propriedades repetidos a cada mensagem, o Avro envia apenas o binário puro e um ID de schema associado.
* **Schema Registry**: É um serviço centralizado que armazena os schemas dos tópicos. Os produtores registram seus schemas no Registry e os consumidores buscam o schema associado para desserializar as mensagens.
* **Estratégia de Compatibilidade**: Por padrão, o nível de compatibilidade está configurado como `BACKWARD`. Isso impede alterações que quebrem os consumidores antigos.

---

## 2. Como criar e gerar novas classes Avro (.avsc)

Os schemas Avro são descritos no formato JSON com extensão `.avsc`.

### Passo 1: Definir o schema
Crie o arquivo em `src/main/avro/` do módulo desejado (ex: `catalog-service/infrastructure/src/main/avro/ProductAvro.avsc`).

Exemplo:
```json
{
  "type": "record",
  "name": "ProductAvro",
  "namespace": "com.marketplace.search.catalog.infrastructure.avro",
  "fields": [
    { "name": "id", "type": "string" },
    { "name": "title", "type": "string" },
    { "name": "price", "type": "string" }
  ]
}
```

### Passo 2: Configurar o build no Maven
Adicione os plugins `avro-maven-plugin` e `build-helper-maven-plugin` no seu `pom.xml`:

```xml
<build>
    <plugins>
        <!-- Compilador Avro -->
        <plugin>
            <groupId>org.apache.avro</groupId>
            <artifactId>avro-maven-plugin</artifactId>
            <version>${avro.version}</version>
            <executions>
                <execution>
                    <phase>generate-sources</phase>
                    <goals>
                        <goal>schema</goal>
                    </goals>
                    <configuration>
                        <sourceDirectory>${project.basedir}/src/main/avro/</sourceDirectory>
                        <outputDirectory>${project.build.directory}/generated-sources/avro/</outputDirectory>
                    </configuration>
                </execution>
            </executions>
        </plugin>
        <!-- Expõe o diretório gerado para a IDE -->
        <plugin>
            <groupId>org.codehaus.mojo</groupId>
            <artifactId>build-helper-maven-plugin</artifactId>
            <version>3.5.0</version>
            <executions>
                <execution>
                    <id>add-source</id>
                    <phase>generate-sources</phase>
                    <goals>
                        <goal>add-source</goal>
                    </goals>
                    <configuration>
                        <sources>
                            <source>${project.build.directory}/generated-sources/avro/</source>
                        </sources>
                    </configuration>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

### Passo 3: Compilar o projeto
Rode o build do Maven para gerar as classes Java correspondentes:
```bash
mvn clean package -DskipTests
```

---

## 3. Configuração das Aplicações Java (Spring Boot)

### Produtor Kafka (Ex: `catalog-service`)
No `application.yml`, configure o serializer de valor para usar a classe do Confluent e informe a URL do Schema Registry:

```yaml
spring:
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS}
    properties:
      schema.registry.url: ${SCHEMA_REGISTRY_URL:http://schema-registry.kafka.svc.cluster.local:8081}
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: io.confluent.kafka.serializers.KafkaAvroSerializer
```

No código Java:
```java
// O valor da mensagem deve ser a classe gerada (ex: ProductAvro)
private final KafkaTemplate<String, ProductAvro> kafkaTemplate;

public void sendEvent(String key, ProductAvro payload) {
    kafkaTemplate.send("catalog.product.create.requests", key, payload);
}
```

---

### Consumidor Kafka (Ex: `indexing-service` ou `catalog-service`)
Para evitar que erros de desserialização travem o consumo, encapsule o desserializador no `ErrorHandlingDeserializer` do Spring:

```yaml
spring:
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS}
    consumer:
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.ErrorHandlingDeserializer
      properties:
        spring.deserializer.value.delegate.class: io.confluent.kafka.serializers.KafkaAvroDeserializer
        schema.registry.url: ${SCHEMA_REGISTRY_URL:http://schema-registry.kafka.svc.cluster.local:8081}
        specific.avro.reader: true # Usar true se consumir classe gerada, false se usar GenericRecord
```

---

## 4. Integração com Kafka Connect (Debezium CDC)

O Debezium captura alterações diretamente do banco de dados PostgreSQL e publica no Kafka em formato Avro. As propriedades de conversão devem ser especificadas na configuração do conector:

```yaml
key.converter: io.confluent.connect.avro.AvroConverter
key.converter.schema.registry.url: http://schema-registry.kafka.svc.cluster.local:8081
value.converter: io.confluent.connect.avro.AvroConverter
value.converter.schema.registry.url: http://schema-registry.kafka.svc.cluster.local:8081
```

O Kafka Connect fará automaticamente o registro dos schemas correspondentes a chaves primárias (`*-key`) e colunas (`*-value`) no Schema Registry.

---

## 5. Dicas de Diagnóstico e Troubleshooting

### Listar schemas registrados
Para ver todos os subjects (tópicos/schemas) registrados, execute de dentro de qualquer pod do cluster:
```bash
wget -q -O - http://schema-registry.kafka.svc.cluster.local:8081/subjects
```

### Visualizar uma versão específica do schema
Para ver os detalhes de um schema específico (ex: versão 1 de `catalog.product.create.requests-value`):
```bash
wget -q -O - http://schema-registry.kafka.svc.cluster.local:8081/subjects/catalog.product.create.requests-value/versions/1
```

### Erro comum: `UnsatisfiedLinkError` ao usar compressão Snappy no Alpine
Se suas aplicações rodando em imagens Alpine Docker lançarem erros como `No class def found for Snappy` ou `UnsatisfiedLinkError: ...libsnappyjava.so`, configure o produtor para desabilitar a compressão snappy em `KafkaConfig.java`:
```java
props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "none");
```
Isso evita dependências de bibliotecas de compatibilidade nativas do glibc (inexistentes no Alpine).
