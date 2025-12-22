#!/usr/bin/env python3
"""
Script de teste para verificar se o servidor pode ser iniciado
"""

import sys
import os

print("=" * 70)
print("TESTE DE INICIALIZAÇÃO DO EMBEDDING SERVICE")
print("=" * 70)
print(f"Python: {sys.executable}")
print(f"Versão: {sys.version}")
print(f"Diretório atual: {os.getcwd()}")
print()

# Testar importações básicas
print("1. Testando importações básicas...")
try:
    import fastapi
    print(f"   ✓ FastAPI {fastapi.__version__}")
except ImportError as e:
    print(f"   ✗ FastAPI não encontrado: {e}")
    sys.exit(1)

try:
    import uvicorn
    print(f"   ✓ Uvicorn {uvicorn.__version__}")
except ImportError as e:
    print(f"   ✗ Uvicorn não encontrado: {e}")
    sys.exit(1)

try:
    import pydantic
    print(f"   ✓ Pydantic {pydantic.__version__}")
except ImportError as e:
    print(f"   ✗ Pydantic não encontrado: {e}")
    sys.exit(1)

print()

# Testar importação do main
print("2. Testando importação do módulo main...")
try:
    import main
    print("   ✓ Módulo main importado com sucesso")
    print(f"   ✓ App FastAPI criado: {main.app}")
except Exception as e:
    print(f"   ✗ Erro ao importar main: {e}")
    import traceback
    traceback.print_exc()
    sys.exit(1)

print()

# Testar se o app pode ser criado
print("3. Testando criação do app FastAPI...")
try:
    app = main.app
    print(f"   ✓ App criado: {app}")
    print(f"   ✓ Título: {app.title}")
    print(f"   ✓ Versão: {app.version}")
except Exception as e:
    print(f"   ✗ Erro ao criar app: {e}")
    import traceback
    traceback.print_exc()
    sys.exit(1)

print()
print("=" * 70)
print("✓ TODOS OS TESTES PASSARAM!")
print("=" * 70)
print()
print("O servidor deve estar pronto para iniciar.")
print("Execute: python -m uvicorn main:app --host 0.0.0.0 --port 8085 --reload")

