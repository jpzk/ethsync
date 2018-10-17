#!/bin/bash

curl -v -H "Content-Type: application/vnd.schemaregistry.v1+json" --data @/avro/FullTransactionImport.json -X POST http://localhost:8081/subjects/full-transactions-value/versions

curl -v -H "Content-Type: application/vnd.schemaregistry.v1+json" --data @/avro/BlockImport.json -X POST http://localhost:8081/subjects/blocks-value/versions

