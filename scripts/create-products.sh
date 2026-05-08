#!/bin/bash

python dataset-generate/data_gen.py --dataset-file ./dataset-generate/data/cache/amazon_products.csv --total 100000 --concurrent-workers 50 --api-url http://localhost:8081/api/v1