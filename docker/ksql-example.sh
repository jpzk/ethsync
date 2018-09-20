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

sleep 10

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
  kafka-topics --create --topic block-offset --partitions 1 --replication-factor 1 \
  --if-not-exists --zookeeper zookeeper:2181

docker run \
  --net=confluent \
  --rm confluentinc/cp-kafka:5.0.0 \
  kafka-topics --create --topic tx-persistence --partitions 1 --replication-factor 1 \
  --if-not-exists --zookeeper zookeeper:2181

docker exec -ti schema-registry chmod +x /avro/import.sh
docker exec -ti schema-registry /avro/import.sh

# setting offset before starting ethsync
docker run -d \
    --net=confluent \
    --name=ethsync_setOffset \
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
    -e NODES=http://178.128.204.38:8545 \
    -e BROKERS=kafka:9092 \
    -e TOPIC=transactions \
    -e FORMAT=full\
    -e NAME=mainnet \
    com.reebo/core

docker run -it \
	--name=ksql-cli \
	--net=confluent \
	confluentinc/cp-ksql-cli:5.0.0 http://ksql-server:8088
