-- Active: 1777150396054@@127.0.0.1@5432@catalog
#!/bin/bash

python dataset-generate/data_gen.py --dataset-file ./dataset-generate/data/cache/amazon_products.csv \
	--total 100 \
	--concurrent-workers 1 \
	--api-url http://localhost:8081/api/v1