#!/usr/bin/env bash

mkdir -p /tmp/data/graphite/{statsd,configs,data}

docker run \
 -d \
 --name graphite \
 --restart=always \
 --net=confluent \
 -p 80:80 \
 -p 8080:8080 \
 -p 2003-2004:2003-2004 \
 -p 2023-2024:2023-2024 \
 -p 8125:8125/udp \
 -p 8126:8126 \
 -v /tmp/data/graphite/configs:/opt/graphite/conf \
 -v /tmp/data/graphite/data:/opt/graphite/storage \
 -v /tmp/data/graphite/statsd:/opt/statsd \
 graphiteapp/graphite-statsd

# grafana
docker run \
  -d \
  -p 3000:3000 \
  --name=grafana \
  --net=confluent \
  -e "GF_SERVER_ROOT_URL=http://grafana.server.name" \
  -e "GF_SECURITY_ADMIN_PASSWORD=some" \
  grafana/grafana

# run zookeeper
docker run -d \
    --net=confluent \
    --name=zookeeper \
    -e ZOOKEEPER_CLIENT_PORT=2181 \
    confluentinc/cp-zookeeper:5.0.0

# run single kafka broker
docker run -d \
    --net=confluent \
    --name=kafka \
    -e KAFKA_ZOOKEEPER_CONNECT=zookeeper:2181 \
    -e KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://kafka:9092 \
    -e KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=1 \
    confluentinc/cp-kafka:5.0.0

# run schema registry
docker run -d \
  --net=confluent \
  --name=schema-registry \
  --mount type=bind,source="$(pwd)"/avro,target=/avro \
  -e SCHEMA_REGISTRY_KAFKASTORE_CONNECTION_URL=zookeeper:2181 \
  -e SCHEMA_REGISTRY_HOST_NAME=schema-registry \
  -e SCHEMA_REGISTRY_LISTENERS=http://0.0.0.0:8081 \
  confluentinc/cp-schema-registry:5.0.0

# running ksql server
docker run -d \
  --net=confluent \
  --name=ksql-server \
  -e COMPONENT=ksql-server \
  -e KSQL_CLASSPATH="/usr/share/java/ksql-server/*" \
  -e KSQL_KSQL_SERVICE_ID="example" \
  -e KSQL_BOOTSTRAP_SERVERS=kafka:9092 \
  -e KSQL_LISTENERS=http://0.0.0.0:8088/ \
  -e KSQL_OPTS="-Dksql.schema.registry.url=http://schema-registry:8081" \
  confluentinc/cp-ksql-server:5.0.0

sleep 15

# create a topic for full transactions
docker run \
  --net=confluent \
  --rm confluentinc/cp-kafka:5.0.0 \
  kafka-topics --create --topic full-transactions --partitions 1 --replication-factor 1 \
  --if-not-exists --zookeeper zookeeper:2181

# create a topic for compact transactions
docker run \
  --net=confluent \
  --rm confluentinc/cp-kafka:5.0.0 \
  kafka-topics --create --topic compact-transactions --partitions 1 --replication-factor 1 \
  --if-not-exists --zookeeper zookeeper:2181

# create a topic for compact transactions
docker run \
  --net=confluent \
  --rm confluentinc/cp-kafka:5.0.0 \
  kafka-topics --create --topic transactions --partitions 1 --replication-factor 1 \
  --if-not-exists --zookeeper zookeeper:2181

docker run \
  --net=confluent \
  --rm confluentinc/cp-kafka:5.0.0 \
  kafka-topics --create --topic compact-logs --partitions 1 --replication-factor 1 \
  --if-not-exists --zookeeper zookeeper:2181

docker run \
  --net=confluent \
  --rm confluentinc/cp-kafka:5.0.0 \
  kafka-topics --create --topic logs --partitions 1 --replication-factor 1 \
  --if-not-exists --zookeeper zookeeper:2181

docker run \
  --net=confluent \
  --rm confluentinc/cp-kafka:5.0.0 \
  kafka-topics --create --topic ethsync-0-block-offset --partitions 1 --replication-factor 1 \
  --if-not-exists --zookeeper zookeeper:2181

docker run \
  --net=confluent \
  --rm confluentinc/cp-kafka:5.0.0 \
  kafka-topics --create --topic ethsync-0-tx-persistence --partitions 1 --replication-factor 1 \
  --if-not-exists --zookeeper zookeeper:2181

docker exec -ti schema-registry chmod +x /avro/import.sh
docker exec -ti schema-registry /avro/import.sh

# setting offset before starting ethsync
docker run -d \
    --net=confluent \
    --name=ethsync_setOffset \
    -e NAME=ethsync-0 \
    -e KAFKA_BROKER=kafka:9092 \
    -e OFFSET=6355000\
    --entrypoint "java" \
    com.reebo/core \
    -cp "/app/ethsync.jar" \
    "com.reebo.ethsync.core.utils.SettingBlockOffset"

# running ethsync
docker run -d \
    --net=confluent \
    --name=ethsync \
    -e GRAPHITE=graphite \
    -e NODES=http://206.189.192.144:8545 \
    -e BROKERS=kafka:9092 \
    -e SCHEMA_REGISTRY=http://schema-registry:8081 \
    -e TOPIC=full-transactions \
    -e FORMAT=full\
    -e NAME=ethsync-0 \
    -e NETWORK=mainnet \
    com.reebo/core

docker run -it \
	--name=ksql-cli \
	--net=confluent \
	confluentinc/cp-ksql-cli:5.0.0 http://ksql-server:8088

