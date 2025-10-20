# Configuração DevTools para Projeto Multi-Módulo

## Como Funciona o DevTools em Projetos Multi-Módulo

### 1. IntelliJ IDEA

#### Configuração Automática:
1. **File → Settings → Build, Execution, Deployment → Compiler**
   - ✅ Marque "Build project automatically"

2. **Help → Find Action → Registry** (Ctrl+Shift+A)
   - ✅ Marque "compiler.automake.allow.when.app.running"

3. **Run Configuration** (sua aplicação principal):
   - ✅ Em "VM options" adicione: `-Dspring.devtools.restart.enabled=true`
   - ✅ Em "Before launch" → "+" → "Build Project"

#### Como Usar:
- **Ctrl+F9** (Build Project) ou salvar arquivo automaticamente recompila
- DevTools detecta mudanças e reinicia automaticamente
- LiveReload atualiza browser automaticamente

### 2. VS Code

#### Extensões Necessárias:
```bash
# Instalar extensões essenciais
code --install-extension vscjava.vscode-java-pack
code --install-extension redhat.vscode-spring-boot
code --install-extension pivotal.vscode-spring-boot
```

#### Configuração `.vscode/settings.json`:
```json
{
    "java.compile.nullAnalysis.mode": "automatic",
    "java.configuration.updateBuildConfiguration": "automatic",
    "java.autobuild.enabled": true,
    "spring-boot.live-reload.enabled": true,
    "files.autoSave": "afterDelay",
    "files.autoSaveDelay": 1000
}
```

#### Configuração `.vscode/launch.json`:
```json
{
    "version": "0.2.0",
    "configurations": [
        {
            "type": "java",
            "name": "Spring Boot-SearchSystemApplication",
            "request": "launch",
            "mainClass": "com.marketplace.search.SearchSystemApplication",
            "projectName": "bootstrap",
            "args": "--spring.profiles.active=development",
            "vmArgs": "-Dspring.devtools.restart.enabled=true",
            "envFile": "${workspaceFolder}/.env"
        }
    ]
}
```

### 3. Eclipse

#### Configuração:
1. **Window → Preferences → General → Workspace**
   - ✅ "Build automatically"

2. **Project Properties → Java Build Path**
   - Incluir todos os módulos como dependencies

## Execução com DevTools

### Usando Maven:
```bash
# No diretório raiz do projeto
mvn spring-boot:run -pl bootstrap -Dspring-boot.run.profiles=development

# Ou com build automático
mvn compile spring-boot:run -pl bootstrap -Dspring-boot.run.profiles=development
```

### Script de Desenvolvimento:
```bash
# Usar o script setup.sh
./setup.sh run

# Ou diretamente:
cd bootstrap
mvn spring-boot:run -Dspring-boot.run.profiles=development
```

## Funcionamento Multi-Módulo

### O que o DevTools Monitora:
1. **bootstrap/src/main/java** - Classes da aplicação principal
2. **domain/src/main/java** - Entidades e value objects
3. **application/src/main/java** - DTOs, mappers, use cases
4. **infrastructure/src/main/java** - Repositories, adapters
5. **interfaces/src/main/java** - Controllers, consumers

### Fluxo de Recompilação:
1. **Salva arquivo** em qualquer módulo
2. **IDE compila** automaticamente o módulo afetado
3. **DevTools detecta** mudança no classpath
4. **Restart da aplicação** (mais rápido que restart completo)
5. **LiveReload** atualiza browser se configurado

### Vantagens:
- ⚡ **Restart rápido** (2-5 segundos vs 30+ segundos)
- 🔄 **Detecção automática** de mudanças
- 🌐 **LiveReload** para desenvolvimento web
- 📦 **Funciona com todos os módulos** Maven

### Limitações:
- ❌ **Não recompila** mudanças em `pom.xml`
- ❌ **Não detecta** novas dependências
- ❌ **Não funciona** com mudanças em properties/yaml às vezes
- ❌ **Recursos estáticos** podem precisar refresh manual

## Troubleshooting

### Problema: DevTools não detecta mudanças
**Solução:**
```bash
# Limpar e recompilar
mvn clean compile
# Verificar se IDE está compilando automaticamente
# Verificar logs do DevTools
```

### Problema: Restart muito lento
**Solução:**
```yaml
# application-development.yml
spring:
  devtools:
    restart:
      poll-interval: 500ms  # Reduzir intervalo
      quiet-period: 200ms   # Reduzir período quieto
```

### Problema: Conflitos de ClassLoader
**Solução:**
```yaml
spring:
  devtools:
    restart:
      additional-exclude:
        - "some-problematic-library/**"
```

## Dicas de Performance

1. **Use profile de desenvolvimento**:
   ```yaml
   spring.profiles.active=development
   ```

2. **Desabilite caches**:
   ```yaml
   spring.cache.type=none
   ```

3. **Use build incremental**:
   ```bash
   mvn compile -T 1C  # Parallel build
   ```

4. **Monitor memória JVM**:
   ```bash
   -Xmx2G -XX:+UseG1GC
   ```