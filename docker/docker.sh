# run zookeeper
docker run -d \
    --net=confluent \
    --name=zookeeper \
    -e ZOOKEEPER_CLIENT_PORT=2181 \
    confluentinc/cp-zookeeper:4.1.2

# run single kafka broker
docker run -d \
    --net=confluent \
    --name=kafka \
    -e KAFKA_ZOOKEEPER_CONNECT=zookeeper:2181 \
    -e KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://kafka:9092 \
    -e KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=1 \
    confluentinc/cp-kafka:4.1.2

# run schema registry
docker run -d \
  --net=confluent \
  --name=schema-registry \
  --mount type=bind,source="$(pwd)"/avro,target=/avro \
  -e SCHEMA_REGISTRY_KAFKASTORE_CONNECTION_URL=zookeeper:2181 \
  -e SCHEMA_REGISTRY_HOST_NAME=schema-registry \
  -e SCHEMA_REGISTRY_LISTENERS=http://0.0.0.0:8081 \
  confluentinc/cp-schema-registry:4.1.2

sleep 10

# create a topic for full transactions
docker run \
  --net=confluent \
  --rm confluentinc/cp-kafka:4.1.2 \
  kafka-topics --create --topic full-transactions --partitions 1 --replication-factor 1 \
  --if-not-exists --zookeeper zookeeper:2181

# create a topic for compact transactions
docker run \
  --net=confluent \
  --rm confluentinc/cp-kafka:4.1.2 \
  kafka-topics --create --topic compact-transactions --partitions 1 --replication-factor 1 \
  --if-not-exists --zookeeper zookeeper:2181

docker run \
  --net=confluent \
  --rm confluentinc/cp-kafka:4.1.2 \
  kafka-topics --create --topic block-offset --partitions 1 --replication-factor 1 \
  --if-not-exists --zookeeper zookeeper:2181

docker run \
  --net=confluent \
  --rm confluentinc/cp-kafka:4.1.2 \
  kafka-topics --create --topic tx-persistence --partitions 1 --replication-factor 1 \
  --if-not-exists --zookeeper zookeeper:2181

docker exec -ti schema-registry chmod +x /avro/import.sh
docker exec -ti schema-registry /avro/import.sh
