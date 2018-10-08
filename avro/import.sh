#!/bin/bash

curl -v -H "Content-Type: application/vnd.schemaregistry.v1+json" --data @/avro/CompactTransactionImport.json -X POST http://localhost:8081/subjects/compact-transactions-value/versions

curl -v -H "Content-Type: application/vnd.schemaregistry.v1+json" --data @/avro/FullTransactionImport.json -X POST http://localhost:8081/subjects/full-transactions-value/versions

curl -v -H "Content-Type: application/vnd.schemaregistry.v1+json" --data @/avro/FullTransactionKeyImport.json -X POST http://localhost:8081/subjects/full-transactions-key/versions

curl -v -H "Content-Type: application/vnd.schemaregistry.v1+json" --data @/avro/LogImport.json -X POST http://localhost:8081/subjects/logs-value/versions

curl -v -H "Content-Type: application/vnd.schemaregistry.v1+json" --data @/avro/CompactLogImport.json -X POST http://localhost:8081/subjects/compact-logs-value/versions

curl -v -H "Content-Type: application/vnd.schemaregistry.v1+json" --data @/avro/TransactionImport.json -X POST http://localhost:8081/subjects/transactions-value/versions

