#!/bin/bash

# Utilitário para configuração e inicialização do sistema de busca

set -e

PROJECT_DIR="/Users/pablosantos/estudos/search-system"

echo "🚀 Marketplace Search System - Configuração e Inicialização"
echo "============================================================"

# Função para verificar dependências
check_dependencies() {
    echo "📋 Verificando dependências..."
    
    if ! command -v docker &> /dev/null; then
        echo "❌ Docker não está instalado"
        exit 1
    fi
    
    if ! command -v docker-compose &> /dev/null; then
        echo "❌ Docker Compose não está instalado"
        exit 1
    fi
    
    if ! command -v mvn &> /dev/null; then
        echo "❌ Maven não está instalado"
        exit 1
    fi
    
    if ! command -v java &> /dev/null; then
        echo "❌ Java não está instalado"
        exit 1
    fi
    
    JAVA_VERSION=$(java -version 2>&1 | grep version | cut -d'"' -f2 | cut -d'.' -f1)
    if [ "$JAVA_VERSION" -lt "17" ]; then
        echo "❌ Java 17 ou superior é necessário (versão atual: $JAVA_VERSION)"
        exit 1
    fi
    
    echo "✅ Todas as dependências estão instaladas"
}

# Função para construir o projeto
build_project() {
    echo "🔨 Construindo o projeto..."
    cd "$PROJECT_DIR"
    mvn clean compile -q
    echo "✅ Projeto construído com sucesso"
}

# Função para iniciar infraestrutura
start_infrastructure() {
    echo "🐳 Iniciando infraestrutura..."
    cd "$PROJECT_DIR"
    docker-compose up -d postgres redis elasticsearch kafka prometheus grafana
    
    echo "⏳ Aguardando serviços ficarem prontos..."
    sleep 30
    
    # Verificar se os serviços estão rodando
    if docker-compose ps | grep -q "Up"; then
        echo "✅ Infraestrutura iniciada com sucesso"
    else
        echo "❌ Falha ao iniciar alguns serviços"
        docker-compose logs
        exit 1
    fi
}

# Função para parar infraestrutura
stop_infrastructure() {
    echo "🛑 Parando infraestrutura..."
    cd "$PROJECT_DIR"
    docker-compose down
    echo "✅ Infraestrutura parada"
}

# Função para executar a aplicação
run_application() {
    echo "🚀 Executando aplicação..."
    cd "$PROJECT_DIR"
    
    # Usar profile de desenvolvimento
    export SPRING_PROFILES_ACTIVE=development
    mvn spring-boot:run -pl bootstrap -Dspring-boot.run.profiles=development -Dspring.devtools.restart.enabled=true -q
}

# Função para executar a aplicação com hot reload
run_dev() {
    echo "🔥 Executando aplicação com hot reload..."
    cd "$PROJECT_DIR"
    
    # Usar profile de desenvolvimento com DevTools
    export SPRING_PROFILES_ACTIVE=development
    mvn compile spring-boot:run -pl bootstrap \
        -Dspring-boot.run.profiles=development \
        -Dspring.devtools.restart.enabled=true \
        -Dspring.devtools.livereload.enabled=true \
        -Dspring-boot.run.jvmArguments="-Xmx2G -XX:+UseG1GC" \
        -q
}

# Função para executar testes
run_tests() {
    echo "🧪 Executando testes..."
    cd "$PROJECT_DIR"
    mvn test -q
    echo "✅ Testes executados com sucesso"
}

# Função para limpar dados
clean_data() {
    echo "🧹 Limpando dados..."
    cd "$PROJECT_DIR"
    docker-compose down -v
    docker system prune -f
    echo "✅ Dados limpos"
}

# Função para verificar status
check_status() {
    echo "📊 Status dos serviços..."
    cd "$PROJECT_DIR"
    
    echo "Docker containers:"
    docker-compose ps
    
    echo -e "\n🔍 Verificando conectividade:"
    
    # PostgreSQL
    if docker-compose exec -T postgres pg_isready -U dev_user -d marketplace_search; then
        echo "✅ PostgreSQL: OK"
    else
        echo "❌ PostgreSQL: FALHA"
    fi
    
    # Redis
    if docker-compose exec -T redis redis-cli ping | grep -q "PONG"; then
        echo "✅ Redis: OK"
    else
        echo "❌ Redis: FALHA"
    fi
    
    # Elasticsearch
    if curl -s http://localhost:9200/_cluster/health | grep -q "green\|yellow"; then
        echo "✅ Elasticsearch: OK"
    else
        echo "❌ Elasticsearch: FALHA"
    fi
    
    # Kafka
    if docker-compose exec -T kafka kafka-topics.sh --bootstrap-server localhost:9092 --list &>/dev/null; then
        echo "✅ Kafka: OK"
    else
        echo "❌ Kafka: FALHA"
    fi
}

# Função para mostrar logs
show_logs() {
    cd "$PROJECT_DIR"
    if [ -n "$1" ]; then
        docker-compose logs -f "$1"
    else
        docker-compose logs -f
    fi
}

# Função para configurar Elasticsearch
setup_elasticsearch() {
    echo "🔧 Configurando Elasticsearch..."
    cd "$PROJECT_DIR"
    
    # Aguardar Elasticsearch ficar pronto
    echo "⏳ Aguardando Elasticsearch..."
    until curl -s http://localhost:9200/_cluster/health; do
        sleep 5
    done
    
    # Criar índice de produtos
    curl -X PUT "localhost:9200/products" -H 'Content-Type: application/json' -d'
    {
        "settings": {
            "number_of_shards": 1,
            "number_of_replicas": 0,
            "analysis": {
                "analyzer": {
                    "brazilian_analyzer": {
                        "type": "custom",
                        "tokenizer": "standard",
                        "filter": [
                            "lowercase",
                            "brazilian_stemmer",
                            "stop_brazilian"
                        ]
                    }
                },
                "filter": {
                    "brazilian_stemmer": {
                        "type": "stemmer",
                        "language": "brazilian"
                    },
                    "stop_brazilian": {
                        "type": "stop",
                        "stopwords": ["o", "a", "os", "as", "de", "do", "da", "dos", "das", "em", "no", "na", "nos", "nas", "para", "por", "com", "sem", "sobre", "entre", "e", "ou", "mas", "que", "se", "quando", "onde", "como", "porque"]
                    }
                }
            }
        },
        "mappings": {
            "properties": {
                "id": {"type": "keyword"},
                "title": {
                    "type": "text",
                    "analyzer": "brazilian_analyzer",
                    "fields": {
                        "keyword": {"type": "keyword"},
                        "suggest": {"type": "completion"}
                    }
                },
                "description": {
                    "type": "text",
                    "analyzer": "brazilian_analyzer"
                },
                "category": {
                    "type": "nested",
                    "properties": {
                        "id": {"type": "keyword"},
                        "name": {"type": "text", "analyzer": "brazilian_analyzer"},
                        "path": {"type": "keyword"}
                    }
                },
                "brand": {
                    "type": "object",
                    "properties": {
                        "name": {"type": "keyword"},
                        "displayName": {"type": "text", "analyzer": "brazilian_analyzer"}
                    }
                },
                "price": {"type": "double"},
                "currency": {"type": "keyword"},
                "availableQuantity": {"type": "integer"},
                "condition": {"type": "keyword"},
                "status": {"type": "keyword"},
                "seller": {
                    "type": "object",
                    "properties": {
                        "id": {"type": "keyword"},
                        "nickname": {"type": "keyword"},
                        "reputation": {
                            "type": "object",
                            "properties": {
                                "level": {"type": "keyword"},
                                "score": {"type": "double"}
                            }
                        }
                    }
                },
                "attributes": {
                    "type": "nested",
                    "properties": {
                        "name": {"type": "keyword"},
                        "value": {"type": "text", "analyzer": "brazilian_analyzer"},
                        "unit": {"type": "keyword"}
                    }
                },
                "metrics": {
                    "type": "object",
                    "properties": {
                        "totalSold": {"type": "integer"},
                        "viewCount": {"type": "integer"},
                        "conversionRate": {"type": "double"},
                        "averageRating": {"type": "double"},
                        "reviewCount": {"type": "integer"}
                    }
                },
                "createdAt": {"type": "date"},
                "lastModified": {"type": "date"}
            }
        }
    }'
    
    echo -e "\n✅ Elasticsearch configurado com sucesso"
}

# Função para mostrar ajuda
show_help() {
    echo "Marketplace Search System - Utilitário de Configuração"
    echo ""
    echo "Uso: ./setup.sh [COMANDO]"
    echo ""
    echo "Comandos:"
    echo "  check         Verificar dependências"
    echo "  build         Construir o projeto"
    echo "  start         Iniciar infraestrutura"
    echo "  stop          Parar infraestrutura"
    echo "  run           Executar aplicação"
    echo "  run-dev       Executar aplicação com hot reload (DevTools)"
    echo "  test          Executar testes"
    echo "  status        Verificar status dos serviços"
    echo "  logs [serviço] Mostrar logs (opcionalmente de um serviço específico)"
    echo "  setup-es      Configurar Elasticsearch"
    echo "  clean         Limpar dados"
    echo "  full-setup    Configuração completa (check + build + start + setup-es)"
    echo "  help          Mostrar esta ajuda"
    echo ""
    echo "Exemplos:"
    echo "  ./setup.sh full-setup    # Configuração completa"
    echo "  ./setup.sh start         # Iniciar apenas infraestrutura"
    echo "  ./setup.sh run-dev       # Executar com hot reload"
    echo "  ./setup.sh logs kafka    # Ver logs do Kafka"
}

# Main
case "${1:-help}" in
    check)
        check_dependencies
        ;;
    build)
        build_project
        ;;
    start)
        start_infrastructure
        ;;
    stop)
        stop_infrastructure
        ;;
    run)
        run_application
        ;;
    run-dev)
        run_dev
        ;;
    test)
        run_tests
        ;;
    status)
        check_status
        ;;
    logs)
        show_logs "$2"
        ;;
    setup-es)
        setup_elasticsearch
        ;;
    clean)
        clean_data
        ;;
    full-setup)
        check_dependencies
        build_project
        start_infrastructure
        setup_elasticsearch
        echo ""
        echo "🎉 Configuração completa finalizada!"
        echo "Execute './setup.sh run' para iniciar a aplicação"
        echo "Ou execute './setup.sh status' para verificar o status dos serviços"
        ;;
    help)
        show_help
        ;;
    *)
        echo "Comando inválido: $1"
        show_help
        exit 1
        ;;
esac