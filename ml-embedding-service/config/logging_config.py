"""
Configuração de logging estruturado para o ML Embedding Service
"""

import logging
import sys
import os
from typing import Optional
import json
from datetime import datetime


class StructuredFormatter(logging.Formatter):
    """Formatter que gera logs estruturados em formato JSON"""
    
    def format(self, record: logging.LogRecord) -> str:
        log_data = {
            "timestamp": datetime.utcnow().isoformat() + "Z",
            "level": record.levelname,
            "logger": record.name,
            "message": record.getMessage(),
            "module": record.module,
            "function": record.funcName,
            "line": record.lineno,
        }
        
        # Adicionar campos extras do record (todos os atributos customizados)
        # Excluir atributos padrão do LogRecord
        standard_attrs = {
            'name', 'msg', 'args', 'created', 'filename', 'funcName', 'levelname',
            'levelno', 'lineno', 'module', 'msecs', 'message', 'pathname', 'process',
            'processName', 'relativeCreated', 'thread', 'threadName', 'exc_info',
            'exc_text', 'stack_info', 'getMessage'
        }
        
        for key, value in record.__dict__.items():
            if key not in standard_attrs and not key.startswith('_'):
                log_data[key] = value
        
        # Adicionar exception info se existir
        if record.exc_info:
            log_data["exception"] = self.formatException(record.exc_info)
        
        return json.dumps(log_data, ensure_ascii=False)


class TextFormatter(logging.Formatter):
    """Formatter padrão com informações detalhadas"""
    
    def __init__(self):
        super().__init__(
            fmt='%(asctime)s - %(name)s - %(levelname)s - [%(module)s:%(funcName)s:%(lineno)d] - %(message)s',
            datefmt='%Y-%m-%d %H:%M:%S'
        )


def setup_logging(log_level: Optional[str] = None, log_format: Optional[str] = None) -> None:
    """
    Configura o sistema de logging do serviço.
    
    Args:
        log_level: Nível de log (DEBUG, INFO, WARNING, ERROR, CRITICAL). 
                   Se None, usa LOG_LEVEL do ambiente ou INFO como padrão.
        log_format: Formato de log ('json' ou 'text'). 
                   Se None, usa LOG_FORMAT do ambiente ou 'text' como padrão.
    """
    # Obter configurações do ambiente
    if log_level is None:
        log_level = os.getenv("LOG_LEVEL", "INFO").upper()
    
    if log_format is None:
        log_format = os.getenv("LOG_FORMAT", "text").lower()
    
    # Converter string para nível de logging
    numeric_level = getattr(logging, log_level, logging.INFO)
    
    # Configurar root logger
    root_logger = logging.getLogger()
    root_logger.setLevel(numeric_level)
    
    # Remover handlers existentes
    for handler in root_logger.handlers[:]:
        root_logger.removeHandler(handler)
    
    # Criar handler para stdout
    handler = logging.StreamHandler(sys.stdout)
    handler.setLevel(numeric_level)
    
    # Escolher formatter baseado no formato
    if log_format == "json":
        formatter = StructuredFormatter()
    else:
        formatter = TextFormatter()
    
    handler.setFormatter(formatter)
    root_logger.addHandler(handler)
    
    # Configurar loggers específicos
    logging.getLogger("uvicorn").setLevel(logging.WARNING)
    logging.getLogger("uvicorn.access").setLevel(logging.WARNING)
    logging.getLogger("fastapi").setLevel(logging.WARNING)
    
    # Log inicial
    logger = logging.getLogger(__name__)
    logger.info(
        f"Sistema de logging configurado - nível: {log_level}, formato: {log_format}"
    )


def get_logger(name: str) -> logging.Logger:
    """
    Obtém um logger configurado para o módulo especificado.
    
    Args:
        name: Nome do módulo (geralmente __name__)
        
    Returns:
        Logger configurado
    """
    return logging.getLogger(name)

