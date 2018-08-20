#!/usr/bin/env bash
export MAINNET_NODES="http://localhost:8545"
export MAINNET_BROKERS="localhost:4545"
export MAINNET_TOPIC="mainnet"

export RINKEBY_NODES="http://localhost:8645"
export RINKEBY_BROKERS="localhost:4545"
export RINKEBY_TOPIC="rinkeby"

java -cp "core/src/resources/:core/target/scala-2.12/ethsync.jar" com.reebo.ethsync.core.Main
