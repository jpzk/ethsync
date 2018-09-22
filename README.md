# ethsync

[![Build Status](https://travis-ci.org/jpzk/ethsync.svg?branch=master)](https://travis-ci.org/jpzk/ethsync) [![Codacy Badge](https://api.codacy.com/project/badge/Grade/fe92a454c96e4cc398de80a060ba3376)](https://www.codacy.com/app/jpzk/ethsync_2?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=jpzk/ethsync&amp;utm_campaign=Badge_Grade)
[![License](http://img.shields.io/:license-Apache%202-grey.svg)](http://www.apache.org/licenses/LICENSE-2.0.txt) [![GitHub stars](https://img.shields.io/github/stars/reeboio/ethsync.svg?style=flat)](https://github.com/jpzk/ethsync/stargazers) 

ethsync is a bridge between Ethereum nodes and Kafka. It stores in-flight transactions and the block offset to ensure at-least-once processing guarantees. Further it acts as a high availability layer for a cluster of Ethereum nodes. In case of crash, it will replay the missing blocks. This makes it ideal for the use in company environments. The project aims at minimal code written in modern Scala with Monix, sttp (uses Netty) and circe. It does not depend on Web3j. Collaboration is highly appreciated. 

## Main Features

* Ready to use Docker image
* At-least-once guarantee for blocks, transactions and receipts
* Fault-tolerant (backed by Kafka brokers)
* Serialization of transactions and receipts into Avro Binary format
* Input validation for transaction and receipt objects
* Different output formats

## Known Caveats / Issues
* In multi-instance mode, where there is more than one Eventeum instance in a system, your services are required to handle duplicate messages gracefully, as each instance will broadcast the same events.
