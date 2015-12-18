// Copyright 2014 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.spindle.runtime.test

import io.fsq.spindle.codegen.test.gen.{InnerStruct, KeyStruct, MapsWithNonStringKeys, TestStruct}
import java.nio.ByteBuffer
import org.bson.types.ObjectId
import org.junit.Assert.assertEquals
import org.junit.Test

class ToStringTest {

  @Test
  def testToString() {
    val struct = TestStruct.newBuilder
      .aBool(true)
      .aByte(120.toByte)
      .anI16(30000.toShort)
      .anI32(7654321)
      .anI64(987654321L)
      .aDouble(0.57)
      .aString("hello, world")
      .aBinary(ByteBuffer.wrap(Array[Byte](1, 2, 3, 4, 5)))
      .aStruct(InnerStruct("hi!", 5))
      .aSet(Set("foo", "bar", "baz"))
      .aList(List(4, 8, 15, 16, 23, 42))
      .aMap(Map("uno" -> InnerStruct("one", 1), "dos" -> InnerStruct("two", 2)))
      .aMyBinary(ByteBuffer.wrap(Array[Byte](1, 2, 3, 4, 5, 6)))
      .aStructList(Seq(InnerStruct("hello", 4), InnerStruct("world", 2)))
      .result()

    val expected =
      """{ "aBool": true, "aByte": 120, "anI16": 30000, "anI32": 7654321, "anI64": 987654321, "aDouble": 0.57, "aString": "hello, world", "aBinary": "AQIDBAU=", "aStruct": { "aString": "hi!", "anInt": 5 }, "aSet": { "foo", "bar", "baz" }, "aList": [ 4, 8, 15, 16, 23, 42 ], "aMap": { "uno": { "aString": "one", "anInt": 1 }, "dos": { "aString": "two", "anInt": 2 } }, "aMyBinary": "AQIDBAUG", "aStructList": [ { "aString": "hello", "anInt": 4 }, { "aString": "world", "anInt": 2 } ] }"""

    val str = struct.toString
    assertEquals(expected, str)
  }

  @Test
  def testNonStringMapKeys() {
    val struct = MapsWithNonStringKeys.newBuilder
      .foo(Map(123 -> "A", 456 -> "B"))
      .bar(Map(new ObjectId("000102030405060708090a0b") -> 77,
               new ObjectId("0c0d0e0f1011121314151617") -> 42))
      .baz(Map(KeyStruct("a", 1) -> 123, KeyStruct("b", 2) -> 456))
      .result()

    val expected =
      """{ "foo": { 123: "A", 456: "B" }, "bar": { ObjectId("000102030405060708090a0b"): 77, ObjectId("0c0d0e0f1011121314151617"): 42 }, "baz": { { "a": "a", "b": 1 }: 123, { "a": "b", "b": 2 }: 456 } }"""

    val str = struct.toString
    assertEquals(expected, str)
  }
}
