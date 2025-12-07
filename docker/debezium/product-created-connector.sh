#!/bin/bash

echo "Waiting for Kafka Connect to start..."
sleep 5

echo "Creating Debezium PostgreSQL connector..."

curl -i -X POST -H "Accept:application/json" -H "Content-Type:application/json" \
  http://localhost:8083/connectors/ -d '{
  "name": "catalog-products-connector",
  "config": {
    "connector.class": "io.debezium.connector.postgresql.PostgresConnector",
    "tasks.max": "1",
    "database.hostname": "catalog-db",
    "database.port": "5432",
    "database.user": "catalog",
    "database.password": "catalog",
    "database.dbname": "catalog",
    "database.server.name": "catalog",
    "table.include.list": "public.catalog_products",
    "topic.prefix": "catalog-db",
    "snapshot.mode": "initial",
    "plugin.name": "pgoutput",
    "schema.include": "public",
    "key.converter": "org.apache.kafka.connect.json.JsonConverter",
    "key.converter.schemas.enable": "false",
    "value.converter": "org.apache.kafka.connect.json.JsonConverter",
    "value.converter.schemas.enable": "false",
    "transforms": "router",
    "transforms.router.type": "io.debezium.transforms.ByLogicalTableRouter",
    "transforms.router.topic.regex": "catalog-db\\.public\\.catalog_products",
    "transforms.router.topic.replacement": "marketplace.public.catalog_products",
    "decimal.handling.mode": "string"
  }
}'

echo ""
echo "Connector registration complete!"
