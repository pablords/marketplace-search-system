#!/bin/bash

echo "Waiting for Kafka Connect to start..."
sleep 5

echo "Creating Debezium PostgreSQL connector..."

curl -i -X POST -H "Accept:application/json" -H "Content-Type:application/json" \
  http://localhost:8083/connectors/ -d '{
  "name": "marketplace-products-connector",
  "config": {
    "connector.class": "io.debezium.connector.postgresql.PostgresConnector",
    "database.hostname": "postgres",
    "database.port": "5432",
    "database.user": "marketplace",
    "database.password": "marketplace123",
    "database.dbname": "marketplace",
    "database.server.name": "marketplace-db",
    "table.include.list": "public.products",
    "plugin.name": "pgoutput",
    "topic.prefix": "dbserver",
    "slot.name": "debezium_products",
    "publication.name": "dbz_publication",
    "key.converter": "org.apache.kafka.connect.json.JsonConverter",
    "value.converter": "org.apache.kafka.connect.json.JsonConverter",
    "key.converter.schemas.enable": "false",
    "value.converter.schemas.enable": "false",
    "transforms": "unwrap,route",
    "transforms.unwrap.type": "io.debezium.transforms.ExtractNewRecordState",
    "transforms.route.type": "io.debezium.transforms.EventRouter",
    "transforms.route.route.by.field": "op",
    "transforms.route.routes": "c:product.created",
    "transforms.unwrap.drop.tombstones": "false"
  }
}'

echo ""
echo "Connector registration complete!"
