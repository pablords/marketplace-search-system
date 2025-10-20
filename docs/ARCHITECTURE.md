# Arquitetura Hexagonal - Fluxo de Dependências

## ❌ ERRADO - Violação da Arquitetura

```
┌──────────────────────────────────┐
│ CreateProductUseCase             │
│ (application)                    │
│                                  │
│ imports:                         │
│ ❌ ProductEntity                 │ ← ERRADO!
│ ❌ ProductEntityMapper           │ ← ERRADO!
│ ❌ ProductJpaRepository          │ ← ERRADO!
└──────────────────────────────────┘
              │
              ▼
┌──────────────────────────────────┐
│ infrastructure                   │
└──────────────────────────────────┘
```

**Problema**: Application depende de Infrastructure = acoplamento, viola Clean Architecture

---

## ✅ CORRETO - Arquitetura Hexagonal (Port/Adapter)

```
┌─────────────────────────────────────────────────────────────┐
│                         INTERFACES                          │
│                    (REST Controllers)                       │
└────────────────────────────┬────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────┐
│                        APPLICATION                          │
│                        (Use Cases)                          │
│                                                             │
│  CreateProductUseCase                                       │
│  ├─ ProductMapper (converte DTO → Domain)                  │
│  └─ ProductRepository (PORT/INTERFACE) ✅                   │
└────────────────────────────┬────────────────────────────────┘
                             │
                             │ depende apenas de
                             ▼
┌─────────────────────────────────────────────────────────────┐
│                          DOMAIN                             │
│                 (Entities, Value Objects)                   │
│                                                             │
│  Product (entidade)                                         │
│  ProductRepository (INTERFACE/PORT) ✅                      │
│  EventPublisher (INTERFACE/PORT)                            │
└─────────────────────────────────────────────────────────────┘
                             ▲
                             │ implementa
                             │
┌─────────────────────────────────────────────────────────────┐
│                      INFRASTRUCTURE                         │
│                         (Adapters)                          │
│                                                             │
│  ProductRepositoryAdapter implements ProductRepository ✅   │
│  ├─ ProductEntity (JPA)                                     │
│  ├─ ProductEntityMapper                                     │
│  └─ ProductJpaRepository (Spring Data)                      │
│                                                             │
│  KafkaEventPublisher implements EventPublisher              │
│  ElasticsearchProductRepository                             │
└─────────────────────────────────────────────────────────────┘
```

---

## 📋 Regras de Dependência

### ✅ PERMITIDO:

1. **interfaces** → **application** → **domain**
2. **infrastructure** → **domain** (implementa ports)
3. **bootstrap** → todas as camadas (wiring/DI)

### ❌ PROIBIDO:

1. **application** → **infrastructure** ❌
2. **domain** → qualquer camada ❌
3. **application** → **interfaces** ❌

---

## 🔧 Solução Implementada

### 1. Port (Interface no Domain)

```java
// domain/repositories/ProductRepository.java
package com.marketplace.search.domain.repositories;

public interface ProductRepository {
    void save(Product product);
    void update(Product product);
    void delete(String productId);
}
```

### 2. Adapter (Implementação na Infrastructure)

```java
// infrastructure/persistence/adapters/ProductRepositoryAdapter.java
package com.marketplace.search.infrastructure.persistence.adapters;

@Component
public class ProductRepositoryAdapter implements ProductRepository {
    
    private final ProductJpaRepository productJpaRepository;
    private final ProductEntityMapper productEntityMapper;
    
    @Override
    public void save(Product product) {
        ProductEntity entity = productEntityMapper.toEntity(product);
        productJpaRepository.save(entity);
    }
}
```

### 3. Use Case (Application)

```java
// application/usecases/CreateProductUseCase.java
package com.marketplace.search.application.usecases;

@Service
public class CreateProductUseCase {
    
    private final ProductMapper productMapper;
    private final ProductRepository productRepository; // ✅ Interface do domain
    
    @Transactional
    public void execute(ProductDTO productDTO) {
        Product product = productMapper.toDomain(productDTO);
        productRepository.save(product); // ✅ Usa interface, não implementação
    }
}
```

---

## 🎯 Benefícios

1. **Testabilidade**: Use cases podem ser testados com mocks do ProductRepository
2. **Flexibilidade**: Trocar PostgreSQL por MongoDB sem mudar use cases
3. **Independência**: Domain e Application não conhecem detalhes de infraestrutura
4. **SOLID**: Princípio de Inversão de Dependências (DIP)
5. **Clean Architecture**: Dependências apontam para dentro (domain)

---

## 📦 Estrutura de Arquivos

```
domain/
└── repositories/
    └── ProductRepository.java (PORT/INTERFACE)

application/
└── usecases/
    └── CreateProductUseCase.java (usa ProductRepository)

infrastructure/
├── persistence/
│   ├── entities/
│   │   └── ProductEntity.java (JPA)
│   ├── mappers/
│   │   └── ProductEntityMapper.java
│   ├── repositories/
│   │   └── ProductJpaRepository.java (Spring Data)
│   └── adapters/
│       └── ProductRepositoryAdapter.java (ADAPTER - implementa PORT)
```

---

## 🔄 Fluxo Completo

```
1. REST Request → ProductCommandController
                  ↓
2. Controller chama CreateProductUseCase.execute(productDTO)
                  ↓
3. Use Case converte DTO → Product (domain)
                  ↓
4. Use Case chama productRepository.save(product)
                  ↓
5. ProductRepositoryAdapter recebe a chamada
                  ↓
6. Adapter converte Product → ProductEntity (JPA)
                  ↓
7. Adapter salva via ProductJpaRepository.save(entity)
                  ↓
8. PostgreSQL persiste os dados
                  ↓
9. Debezium captura mudança via CDC
                  ↓
10. Kafka recebe evento
                  ↓
11. Consumer indexa no Elasticsearch
```

---

## 📝 Resumo

✅ **CERTO**: Application depende de **interfaces** (ports) no Domain
❌ **ERRADO**: Application depende de **implementações** (adapters) na Infrastructure

**Port/Adapter = Dependency Inversion Principle (DIP)**
