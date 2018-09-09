package com.reebo.ethsync.core.serialization

import java.io.ByteArrayOutputStream

import com.reebo.ethsync.core.Protocol.FullTX
import com.sksamuel.avro4s.AvroOutputStream

object AvroHelper {
  // Converting Ethereum hex encoding to Byte Array
  def hex2ByteArray(hex: String): Array[Byte] = {
    BigInt(hex.drop(2), 16).toByteArray
  }
}

// Explicit decoding to a data type with a decoder library, since Scala does not support unsigned ints
case class AvroTransactionCompact(blockHash: Array[Byte], // 32 byte
                                  blockNumber: Array[Byte], // 4 bytes, unsigned int
                                  from: Array[Byte], // 20 bytes - address of the sender.
                                  gas: Array[Byte], // 8 bytes, 64-bit @todo sometimes in documentation 32 bytes
                                  gasPrice: Array[Byte], // 8 bytes, 64-bit @todo sometimes in documentation 32 bytes
                                  hash: Array[Byte], // 32 bytes - hash of the transaction.
                                  input: Array[Byte], // dynamic
                                  nonce: Array[Byte], // 4 bytes, 32-bit, unsigned int
                                  to: Array[Byte], // 20 bytes - address of the receiver. null when its a contract creation transaction.
                                  transactionIndex: Array[Byte], // 4 bytes, 32-bit integer, index position in the block.
                                  value: Array[Byte], // 8 bytes, long for now @todo figure out the max of value
                                  v: Byte, // 1 byte (https://github.com/ethereum/go-ethereum/issues/456)
                                  r: Array[Byte], // 32 bytes
                                  s: Array[Byte]) // 32 bytes

case class AvroTransaction(blockHash: String,
                           blockNumber: Long,
                           from: String,
                           gas: Int,
                           gasPrice: Int,
                           hash: String,
                           input: String,
                           nonce: String,
                           to: String,
                           transactionIndex: Int,
                           value: Long,
                           v: Byte,
                           r: String,
                           s: String)

case class AvroTransactionBundle(hash: String)

// from JSON to Avro
object AvroSerialization {

  def toAvro(tx: FullTX): Array[Byte] = {
    val baos = new ByteArrayOutputStream()
    val output = AvroOutputStream.binary[AvroTransactionCompact](baos)
    //val atx = AvroTransaction(tx.data.hash)

    //output.write(atx)
    //output.close()
    baos.toByteArray
  }
}
