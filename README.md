# ethsync

[![Build Status](https://travis-ci.org/reeboio/ethsync.svg?branch=master)](https://travis-ci.org/reeboio/ethsync) [![Codacy Badge](https://api.codacy.com/project/badge/Grade/fe92a454c96e4cc398de80a060ba3376)](https://www.codacy.com/app/jpzk/ethsync_2?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=jpzk/ethsync&amp;utm_campaign=Badge_Grade)
[![codecov](https://codecov.io/gh/jpzk/ethsync/branch/master/graph/badge.svg)](https://codecov.io/gh/jpzk/ethsync) [![License](http://img.shields.io/:license-Apache%202-grey.svg)](http://www.apache.org/licenses/LICENSE-2.0.txt) [![GitHub stars](https://img.shields.io/github/stars/reeboio/ethsync.svg?style=flat)](https://github.com/reeboio/ethsync/stargazers) 

Ethsync is a reliable drop-in solution that acts as a bridge between Ethereum nodes syncing with the decentralised Ethereum network and traditional big-data pipelines. It syncs recent block and transaction updates with your traditional infrastructure via publishing the updates to a Kafka topic. 

It uses different persistence backends to store in-flight transactions and the block offset to ensure at-least-once processing guarantees. Further it acts as a high availability layer for a cluster of Ethereum nodes. In case of crash, it will replay the missing blocks. This makes it ideal for the use in company environments.

The project aims at minimal code written in modern Scala with Monix, sttp (uses Netty) and circe. Hence, it does not require any bigger legacy Java library such as Web3j. Collaboration is highly appreciated. 

## Starting without Docker

First compile, test & assemble the Jar using:
```$xslt
sbt 'core/assembly'
```

Then run the Jar with these e.g. ENV variables

```
#!/usr/bin/env bash
export MAINNET_NODES="http://localhost:8545"
export MAINNET_BROKERS="localhost:4545"
export MAINNET_TOPIC="mainnet"

export RINKEBY_NODES="http://localhost:8645"
export RINKEBY_BROKERS="localhost:4545"
export RINKEBY_TOPIC="rinkeby"

java -cp "core/src/resources/:core/target/scala-2.12/ethsync.jar" com.reebo.ethsync.core.Main

```

