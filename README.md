# ethsync

[![Build Status](https://travis-ci.org/reeboio/ethsync.svg?branch=master)](https://travis-ci.org/reeboio/ethsync) [![Codacy Badge](https://api.codacy.com/project/badge/Grade/fe92a454c96e4cc398de80a060ba3376)](https://www.codacy.com/app/jpzk/ethsync_2?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=jpzk/ethsync&amp;utm_campaign=Badge_Grade)
[![codecov](https://codecov.io/gh/jpzk/ethsync/branch/master/graph/badge.svg)](https://codecov.io/gh/jpzk/ethsync) [![License](http://img.shields.io/:license-Apache%202-grey.svg)](http://www.apache.org/licenses/LICENSE-2.0.txt) [![GitHub stars](https://img.shields.io/github/stars/reeboio/ethsync.svg?style=flat)](https://github.com/reeboio/ethsync/stargazers) 

Ethsync is a a extractor bridge between Ethereum nodes syncing with the **decentralised Ethereum network** and **traditional big data pipelines**. It syncs recent block and transaction updates with your traditional infrastructure via publishing the transactions with receipts to a Kafka topic.

It uses different persistence backends to store in-flight transactions and the block offset to ensure **at-least-once processing guarantees**. Further it acts as a high availability layer for a cluster of Ethereum nodes. In case of crash, it will replay the missing blocks. This makes it ideal for the use in company environments.

The project aims at minimal code written in modern Scala with Monix, sttp (uses Netty) and circe. Hence, it does not require any bigger legacy Java library such as Web3j. Collaboration is highly appreciated. 

## Main Features

* At-least-once guarantee for blocks, transactions and receipts
* Fault-tolerant (backed by Kafka brokers)
* Serialization of transactions and receipts 
* Compatible with Kafka Connect 
* Kafka sink

## Starting the Test Environment

Assuming you have Docker installed on your computer. 

```$xslt
$ sbt docker 
```

When it succeeds, take a look at /docker/docker.sh. And modify the host address of your Ethereum node.

```$xslt
$ docker run -d \
    --net=confluent \
    --name=ethsync \
    -e MAINNET_NODES=http://localhost:8645 \
    -e MAINNET_BROKERS=kafka:9092 \
    -e MAINNET_TOPIC=transactions \
    com.reebo/core
```

If you changed the environment variables accordingly running the docker/docker.sh script will setup a Zookeeper and a Kafka broker. Further it will create two topics **transactions** and **block-offset**. 

The transactions topic holds all the transactions ingested from the Ethereum nodes (starting from 0, if no other starting offset is set.). The block-offset topic contains the current acknowledged / committed block-offset. Ethsync will **continue processing from that block-offset when restarted.**

```$xslt
$ sh docker/docker.sh
```

If you want to validate that ethsync is working properly. You can use the following command to tail the output of the running ethsync docker container.
```$xslt
$ docker logs -f ethsync
```

If you want to run ethsync in production, have a look at the bottom of the documentation. It contains valueable information of how to run ethsync properly.

## Configuration (in progress)
Many values within Ethsync are configurable by changing the associated environment variable.

| Env Variable | Default | Description |
| -------- | -------- | -------- |
| MAINNET_NODES | http://localhost:8545 | The ethereum node urls. Comma-seperated |
| MAINNET_BROKERS | localhost:9092 | Comma-seperated list of Kafka broker hosts |
| MAINNET_TOPIC | localhost:9092 | Topic of transactions |

## Setting Block Offset

In case you want to change the block offset, you can use the following program. You can pass the desired block offset via an environment variable. Make sure ethsync is not running. 

```$xslt
$ docker run -d \
    --net=confluent \
    --name=ethsync_setOffset \
    -e KAFKA_BROKER=kafka:9092 \
    -e OFFSET=450000\
    --entrypoint "java" \
    com.reebo/core \
    -cp "/app/ethsync.jar" \
    "com.reebo.ethsync.core.SettingBlockOffset"
```


## Known Caveats / Issues
* In multi-instance mode, where there is more than one Eventeum instance in a system, your services are required to handle duplicate messages gracefully, as each instance will broadcast the same events.
