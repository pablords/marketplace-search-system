# Multi-stage build para API Gateway em Go

# Stage 1: Build
# Usar versão estável do Go (ajustar conforme necessário)
FROM golang:1.23-alpine AS builder

# Instalar dependências de build
RUN apk add --no-cache git ca-certificates tzdata

# Configurar GOTOOLCHAIN para permitir download automático de versões mais novas se necessário
ENV GOTOOLCHAIN=auto

# Definir diretório de trabalho
WORKDIR /build

# Copiar arquivos de dependências
COPY go.mod go.sum ./

# Baixar dependências (cache layer)
RUN go mod download

# Copiar código fonte
COPY . .

# Build da aplicação
# CGO_ENABLED=0 para criar binário estático
# -ldflags para reduzir tamanho do binário
RUN CGO_ENABLED=0 GOOS=linux GOARCH=amd64 go build \
    -ldflags='-w -s -extldflags "-static"' \
    -a -installsuffix cgo \
    -o gateway \
    ./cmd/gateway

# Stage 2: Runtime
FROM alpine:latest

# Instalar ca-certificates para HTTPS, tzdata para timezone e wget para healthcheck
RUN apk --no-cache add ca-certificates tzdata wget

# Criar usuário não-root para segurança
RUN addgroup -g 1000 appuser && \
    adduser -D -u 1000 -G appuser appuser

# Criar diretórios necessários
RUN mkdir -p /app/configs /app/logs && \
    chown -R appuser:appuser /app

# Definir diretório de trabalho
WORKDIR /app

# Copiar binário do stage de build
COPY --from=builder /build/gateway /app/gateway

# Copiar arquivos de configuração
COPY --from=builder /build/configs /app/configs

# Mudar propriedade dos arquivos para o usuário não-root
RUN chown -R appuser:appuser /app

# Mudar para usuário não-root
USER appuser

# Expor porta (padrão 8080, pode ser sobrescrito via config)
EXPOSE 8080

# Variáveis de ambiente padrão
ENV ENVIRONMENT=production
ENV LOG_LEVEL=INFO
ENV LOG_FORMAT=json

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/api/v1/health || exit 1

# Comando para executar a aplicação
# Permite passar argumentos como --config para especificar arquivo de configuração
ENTRYPOINT ["/app/gateway"]

