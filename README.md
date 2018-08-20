# ethsync

[![Build Status](https://travis-ci.org/reeboio/ethsync.svg?branch=master)](https://travis-ci.org/reeboio/ethsync) [![Codacy Badge](https://api.codacy.com/project/badge/Grade/39162faccc2e46dc86673e38022defa8)](https://www.codacy.com/app/jpzk/ethsync?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=reeboio/ethsync&amp;utm_campaign=Badge_Grade)
[![codecov](https://codecov.io/gh/reeboio/ethsync/branch/master/graph/badge.svg)](https://codecov.io/gh/reeboio/ethsync) [![License](http://img.shields.io/:license-Apache%202-grey.svg)](http://www.apache.org/licenses/LICENSE-2.0.txt) [![GitHub stars](https://img.shields.io/github/stars/reeboio/ethsync.svg?style=flat)](https://github.com/reeboio/ethsync/stargazers) 

ethsync is a reliable Ethereum transaction sink for an Ethereum client cluster written in Scala. Different sinks are available such as a Kafka producer, serialisation is in JSON. The docker container can be used out-of-the-box, see usage below. 

It uses different persistence backends to store in-flight transaction and the block offset to ensure at-least-once guarantees. Fault-tolerance is achieved by synchronising with the blockchain. This makes it ideal for the use in company environments. 

The key design priciple is minimal elegant code, using only required  dependencies to do the job. The component is powered by Monix, sttp and circe. Hence, it does not require any legacy Java library such as Web3j. 

## Principles

* Modern Scala using Monix, sttp, circe, avoid Java code (e.g. web3j)
* Asynchronous and parallel (non-blocking) code
* Fault-tolerant (e.g. restart consuming where crashed)
* At-least-once data guarantees
* Configurable via ENV variables (suitable to Docker)

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

