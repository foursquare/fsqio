// Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.spindle.common.thrift.bson.test

import com.mongodb.DBObject
import io.fsq.spindle.codegen.test.gen._
import io.fsq.spindle.common.thrift.bson.{TBSONBinaryProtocol, TBSONObjectProtocol}
import io.fsq.spindle.runtime.UntypedRecord
import java.io.{ByteArrayInputStream, InputStream}
import java.nio.ByteBuffer
import org.apache.thrift.protocol.{TProtocol, TProtocolUtil, TType}
import org.bson.{BasicBSONDecoder, BasicBSONEncoder}
import scala.collection.JavaConverters._

object TProtoBench {

  def largeNestedStruct = TestStruct.newBuilder
    .aByte(12.toByte)
    .anI16(1234.toShort)
    .anI32(123456)
    .anI64(123456789999L)
    .aDouble(123456.123456)
    .aString("hello, how are you today?")
    .aBinary(ByteBuffer.wrap("foobar".getBytes("UTF-8")))
    .aStruct(InnerStruct("inner hello", 1234567))
    .aSet(Set("1","2","3","4","5"))
    .aList(List(1,2,3,4,5))
    .aMap(
      Map("one" -> InnerStruct("inner in map one", 1),
          "two" -> InnerStruct("inner in map two", 2)
      )
    )
    .aMyBinary(ByteBuffer.wrap(Array[Byte](1, 2, 3, 4, 5, 6)))
    .aStructList(List(
      InnerStruct("inner in list one", 1),
      InnerStruct("inner in list one", 2)
    ))
    .result()

  def smallStruct = InnerStruct("inner in list one", 1)

  def readStruct(proto: TProtocol) {
    TProtocolUtil.skip(proto, TType.STRUCT)
  }

  val decoder = new BasicBSONDecoder()
  val dboProtocol = new TBSONObjectProtocol.ReaderFactory().getProtocol
  def parseBytesDBO(is: InputStream) {
    dboProtocol.reset()
    dboProtocol.setSource(decoder.readObject(is))
    readStruct(dboProtocol)
  }

  val binaryProtocol = new TBSONBinaryProtocol()
  def parseBytesBinary(is: InputStream) {
    binaryProtocol.setSource(is)
    readStruct(binaryProtocol)
  }

  def getMemoryUsage(): Long = {
    val runtime = Runtime.getRuntime()
    runtime.totalMemory() - runtime.freeMemory()
  }

  /**
   * returns (allocated bytes, average time per decode in nanoseconds)
   */
  def runBench(iterations: Int, testStruct: UntypedRecord, func: (InputStream) => Unit): (Long, Long) = {
    val protocolFactory = new TBSONObjectProtocol.WriterFactoryForDBObject
    val writeProtocol = protocolFactory.getProtocol
    testStruct.write(writeProtocol)
    val dbo = writeProtocol.getOutput.asInstanceOf[DBObject]
    val encoder = new BasicBSONEncoder()
    val bytes: Array[Byte] = encoder.encode(dbo)
    var counter = 0
    System.gc()
    val startUsage = getMemoryUsage()
    val startTime = System.nanoTime
    while (counter < iterations) {
      func(new ByteArrayInputStream(bytes))
      counter += 1
    }
    val totalTime = (System.nanoTime - startTime)
    (getMemoryUsage() - startUsage, totalTime / iterations)
  }

  def main(args: Array[String]): Unit = {
    val iterations = 10000
    def benchDbo(struct: UntypedRecord) = runBench(iterations, struct, parseBytesDBO)
    def benchBinary(struct: UntypedRecord) = runBench(iterations, struct, parseBytesBinary)

    // warmups
    (1 to 5).foreach{i =>
      benchDbo(largeNestedStruct)
      benchBinary(largeNestedStruct)
    }

    def benchWithStruct(struct: UntypedRecord) {
      println(s"\nBenching with ${struct.getClass}")
      (1 to 5).foreach{i =>
        println(s"Run $i: ")
        println(s"Dbo takes ${benchDbo(struct)}")
        println(s"Binary takes ${benchBinary(struct)}")
      }
    }

    benchWithStruct(largeNestedStruct)
    benchWithStruct(smallStruct)

  }

}
