#!/bin/bash


if [ ! -d ".venv" ]; then
    python3 -m venv .venv
fi

source .venv/bin/activate

# pip install -r ./dataset-generate/requirements.txt

python dataset-generate/data_gen.py --dataset-file ./dataset-generate/data/cache/amazon_products.csv --total 1000 --concurrent-workers 50 --api-url http://api.lab.com.br/api/v1