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

# create a topic for transactions
docker run \
  --net=confluent \
  --rm confluentinc/cp-kafka:4.1.2 \
  kafka-topics --create --topic transactions --partitions 1 --replication-factor 1 \
  --if-not-exists --zookeeper zookeeper:2181

# run ethsync 
docker run -d \
    --net=confluent \
    --name=ethsync \
    -e MAINNET_NODES=http://localhost:8645 \
    -e MAINNET_BROKERS=kafka:9092 \
    -e MAINNET_TOPIC=transactions \
    com.reebo/core
