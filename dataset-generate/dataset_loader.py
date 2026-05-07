#!/usr/bin/env python3
"""
Módulo para download e carregamento de datasets do Kaggle.
Suporta cache local, validação de dados e múltiplos formatos (CSV, JSON).
"""

import os
import pandas as pd
from pathlib import Path
from typing import Optional, List
import logging

# Configuração de logging
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)


class DatasetLoader:
    def download_dataset_public_url(self, url: str, output_path: str) -> str:
        """
        Baixa um dataset público via URL direta, sem necessidade de credenciais.
        Args:
            url: URL direta para o arquivo do dataset
            output_path: Caminho do arquivo de saída
        Returns:
            Caminho do arquivo salvo
        Raises:
            Exception: Se houver erro no download
        """
        import requests
        output_path = Path(output_path)
        output_path.parent.mkdir(parents=True, exist_ok=True)
        logger.info(f"Baixando dataset público de {url} para {output_path}")
        try:
            with requests.get(url, stream=True) as r:
                r.raise_for_status()
                with open(output_path, 'wb') as f:
                    for chunk in r.iter_content(chunk_size=8192):
                        if chunk:
                            f.write(chunk)
            logger.info(f"Download concluído: {output_path}")
            return str(output_path)
        except Exception as e:
            raise Exception(f"Erro ao baixar dataset público: {e}")
    """
    Classe responsável por baixar e carregar datasets do Kaggle.
    
    Funcionalidades:
    - Download de datasets do Kaggle usando kaggle API
    - Cache local para evitar downloads repetidos
    - Suporte para múltiplos formatos (CSV, JSON)
    - Validação básica do dataset
    """
    
    def __init__(self, cache_dir: str = "./data/cache"):
        """
        Inicializa o DatasetLoader.
        
        Args:
            cache_dir: Diretório onde os datasets serão armazenados em cache
        """
        self.cache_dir = Path(cache_dir)
        self.cache_dir.mkdir(parents=True, exist_ok=True)
        
    def download_dataset(self, dataset_name: str, output_dir: Optional[str] = None) -> str:
        """
        Baixa um dataset do Kaggle.
        
        Args:
            dataset_name: Nome do dataset no formato 'username/dataset-name' ou apenas 'dataset-name'
            output_dir: Diretório de saída (opcional, usa cache_dir por padrão)
            
        Returns:
            Caminho do diretório onde o dataset foi baixado
            
        Raises:
            ImportError: Se o pacote kaggle não estiver instalado
            ValueError: Se o dataset_name estiver em formato inválido
            Exception: Se houver erro no download
        """
        try:
            from kaggle.api.kaggle_api_extended import KaggleApi
        except ImportError:
            raise ImportError(
                "Pacote 'kaggle' não encontrado. "
                "Instale com: pip install kaggle\n"
                "Também é necessário configurar as credenciais do Kaggle. "
                "Veja: https://github.com/Kaggle/kaggle-api#api-credentials"
            )
        
        # Usar output_dir fornecido ou cache_dir padrão
        if output_dir is None:
            output_dir = self.cache_dir
        else:
            output_dir = Path(output_dir)
            output_dir.mkdir(parents=True, exist_ok=True)
        
        # Verificar se o dataset já existe no cache
        dataset_slug = dataset_name.split('/')[-1] if '/' in dataset_name else dataset_name
        cached_path = output_dir / dataset_slug
        
        if cached_path.exists() and any(cached_path.iterdir()):
            logger.info(f"Dataset '{dataset_slug}' já existe no cache: {cached_path}")
            return str(cached_path)
        
        # Inicializar API do Kaggle
        try:
            api = KaggleApi()
            api.authenticate()
        except Exception as e:
            logger.warning(f"Erro ao autenticar na API do Kaggle: {e}. Tentando download público como fallback...")
            # Fallback: tentar baixar via URL pública se possível
            # Exemplo para datasets públicos conhecidos
            public_urls = {
                # Dataset Amazon US (legado)
                "asaniczka/amazon-products-dataset-2023-1-4m-products":
                    "https://www.kaggle.com/api/v1/datasets/download/asaniczka/amazon-products-dataset-2023-1-4m-products",
                # Dataset Amazon Brazil (ativo)
                "asaniczka/amazon-brazil-products-2023-1-3m-products":
                    "https://www.kaggle.com/api/v1/datasets/download/asaniczka/amazon-brazil-products-2023-1-3m-products",
            }
            dataset_key = dataset_name if '/' in dataset_name else f"asaniczka/{dataset_name}"
            public_url = public_urls.get(dataset_key)
            if public_url:
                output_zip = str(output_dir / f"{dataset_key.split('/')[-1]}.zip")
                # Se já existe o arquivo zip, não baixa novamente
                if Path(output_zip).exists() and os.path.getsize(output_zip) > 0:
                    logger.info(f"Arquivo já existe, não será baixado novamente: {output_zip}")
                else:
                    self.download_dataset_public_url(public_url, output_zip)
                    logger.info(f"Dataset baixado via fallback público: {output_zip}")
                return str(output_dir)
            else:
                raise Exception(
                    f"Erro ao autenticar na API do Kaggle: {e}\n"
                    "Certifique-se de que as credenciais estão configuradas corretamente. "
                    "Veja: https://github.com/Kaggle/kaggle-api#api-credentials\n"
                    "Ou forneça uma URL pública para download do dataset."
                )
        
        # Baixar dataset
        logger.info(f"Baixando dataset '{dataset_name}' do Kaggle...")
        try:
            api.dataset_download_files(
                dataset_name,
                path=str(output_dir),
                unzip=True
            )
            logger.info(f"Dataset baixado com sucesso em: {output_dir}")
        except Exception as e:
            raise Exception(f"Erro ao baixar dataset '{dataset_name}': {e}")
        
        # Retornar caminho do dataset baixado
        # O Kaggle geralmente cria um diretório com o nome do dataset
        dataset_path = output_dir / dataset_slug
        if not dataset_path.exists():
            # Se não criou diretório específico, os arquivos estão em output_dir
            dataset_path = output_dir
        
        return str(dataset_path)
    
    def load_dataset(self, file_path: str) -> pd.DataFrame:
        """
        Carrega um dataset de um arquivo CSV ou JSON.
        
        Args:
            file_path: Caminho para o arquivo (CSV ou JSON) ou diretório contendo o dataset
            
        Returns:
            DataFrame do pandas com os dados carregados
            
        Raises:
            FileNotFoundError: Se o arquivo não existir
            ValueError: Se o formato do arquivo não for suportado
            Exception: Se houver erro ao carregar o arquivo
        """
        path = Path(file_path)
        
        if not path.exists():
            raise FileNotFoundError(f"Arquivo ou diretório não encontrado: {file_path}")
        
        # Se for um diretório, procurar por arquivos CSV ou JSON
        if path.is_dir():
            csv_files = list(path.glob("*.csv"))
            json_files = list(path.glob("*.json"))
            
            if csv_files:
                # Priorizar CSV se houver múltiplos arquivos
                file_path = str(csv_files[0])
                if len(csv_files) > 1:
                    logger.warning(f"Múltiplos arquivos CSV encontrados. Usando: {file_path}")
            elif json_files:
                file_path = str(json_files[0])
                if len(json_files) > 1:
                    logger.warning(f"Múltiplos arquivos JSON encontrados. Usando: {file_path}")
            else:
                raise FileNotFoundError(
                    f"Nenhum arquivo CSV ou JSON encontrado no diretório: {file_path}"
                )
            path = Path(file_path)
        
        # Determinar formato e carregar
        file_extension = path.suffix.lower()
        
        try:
            if file_extension == '.csv':
                logger.info(f"Carregando arquivo CSV: {file_path}")
                df = pd.read_csv(file_path, encoding='utf-8')
            elif file_extension == '.json':
                logger.info(f"Carregando arquivo JSON: {file_path}")
                # Tentar carregar como JSON lines primeiro, depois como JSON array
                try:
                    df = pd.read_json(file_path, lines=True, encoding='utf-8')
                except ValueError:
                    # Se falhar, tentar como JSON array
                    df = pd.read_json(file_path, encoding='utf-8')
            else:
                raise ValueError(
                    f"Formato de arquivo não suportado: {file_extension}. "
                    "Formatos suportados: .csv, .json"
                )
            
            logger.info(f"Dataset carregado com sucesso. Shape: {df.shape}")
            return df
            
        except Exception as e:
            raise Exception(f"Erro ao carregar arquivo '{file_path}': {e}")
    
    def validate_dataset(self, df: pd.DataFrame, required_columns: Optional[List[str]] = None) -> bool:
        """
        Valida um dataset carregado.
        
        Args:
            df: DataFrame do pandas para validar
            required_columns: Lista de colunas obrigatórias (opcional)
                             Se não fornecido, usa colunas padrão esperadas
            
        Returns:
            True se o dataset for válido
            
        Raises:
            ValueError: Se o dataset não atender aos critérios de validação
        """
        if df is None or df.empty:
            raise ValueError("Dataset está vazio ou é None")
        
        # Colunas mínimas esperadas para um dataset de produtos
        # Apenas title e price são realmente obrigatórios
        # category, brand e description podem ser gerados/mapeados se não existirem
        if required_columns is None:
            required_columns = ['title', 'price']
        
        # Verificar se as colunas obrigatórias existem
        missing_columns = [col for col in required_columns if col not in df.columns]
        if missing_columns:
            raise ValueError(
                f"Colunas obrigatórias não encontradas no dataset: {missing_columns}\n"
                f"Colunas disponíveis: {list(df.columns)}"
            )
        
        # Verificar se há linhas duplicadas
        duplicates = df.duplicated().sum()
        if duplicates > 0:
            logger.warning(f"Dataset contém {duplicates} linhas duplicadas")
        
        # Verificar valores nulos nas colunas obrigatórias
        null_counts = df[required_columns].isnull().sum()
        if null_counts.any():
            logger.warning(f"Valores nulos encontrados:\n{null_counts[null_counts > 0]}")
        
        # Validar tipos básicos
        if 'price' in df.columns:
            # Tentar converter price para numérico
            try:
                df['price'] = pd.to_numeric(df['price'], errors='coerce')
                invalid_prices = df['price'].isna().sum()
                if invalid_prices > 0:
                    logger.warning(f"{invalid_prices} preços inválidos encontrados")
            except Exception as e:
                logger.warning(f"Erro ao validar preços: {e}")
        
        logger.info(f"Dataset validado com sucesso. Total de registros: {len(df)}")
        return True
    
    def download_and_load(self, dataset_name: str, validate: bool = True, 
                         required_columns: Optional[List[str]] = None) -> pd.DataFrame:
        """
        Método de conveniência que combina download e carregamento.
        
        Args:
            dataset_name: Nome do dataset no formato 'username/dataset-name'
            validate: Se True, valida o dataset após carregar
            required_columns: Colunas obrigatórias para validação
            
        Returns:
            DataFrame do pandas com o dataset carregado e validado
        """
        # Baixar dataset
        dataset_path = self.download_dataset(dataset_name)
        
        # Carregar dataset
        df = self.load_dataset(dataset_path)
        
        # Validar se solicitado
        if validate:
            self.validate_dataset(df, required_columns)
        
        return df


# Exemplo de uso
if __name__ == "__main__":
    loader = DatasetLoader(cache_dir="./data/cache")
    
    # Exemplo 1: Baixar e carregar um dataset
    # dataset_name = "username/dataset-name"
    # df = loader.download_and_load(dataset_name)
    # print(f"Dataset carregado: {df.shape}")
    # print(df.head())
    
    # Exemplo 2: Carregar um arquivo local
    # df = loader.load_dataset("./data/products.json")
    # loader.validate_dataset(df)
    
    print("DatasetLoader pronto para uso!")
    print("Exemplo de download público sem credenciais:")
    # Exemplo: baixar dataset Amazon Products Dataset 2023 (4M products) sem credenciais
    public_url = "https://www.kaggle.com/api/v1/datasets/download/asaniczka/amazon-products-dataset-2023-1-4m-products"
    output_zip = "./data/cache/amazon-products-dataset-2023-1-4m-products.zip"
    try:
        loader.download_dataset_public_url(public_url, output_zip)
        print(f"Arquivo baixado em: {output_zip}")
    except Exception as e:
        print(f"Falha no download público: {e}")

