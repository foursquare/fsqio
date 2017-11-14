package io.fsq.pants.avro.example.test

import io.fsq.pants.avro.example.User
import io.fsq.pants.avro.example.simple.{Kind, MD5, TestRecord}
import org.junit.{Assert => A, Test}
import scala.collection.JavaConverters._

class AvroJavaGenTest {
  @Test
  def testSchemaGen(): Unit = {
    val user = User.newBuilder()
      .setName("Test User")
      .setFavoriteColor("blue")
      .setFavoriteNumber(10)
      .build()

    A.assertEquals("Test User", user.getName)
    A.assertEquals("blue", user.getFavoriteColor)
    A.assertEquals(10, user.getFavoriteNumber)
  }

  @Test
  def testIdlGen(): Unit = {
    val testRecord = TestRecord.newBuilder()
      .setName("Name")
      .setKind(Kind.BAR)
      .setHash(new MD5(Array[Byte](0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15)))
      .setNullableHash(null)
      .setArrayOfLongs(Vector[Long](1L, 2L, 3L).map(Long.box).asJava)
      .build()

    A.assertEquals("Name", testRecord.getName)
    A.assertEquals(Kind.BAR, testRecord.getKind)
    A.assertArrayEquals(Array[Byte](0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15), testRecord.getHash.bytes())
    A.assertNull(testRecord.getNullableHash)
    A.assertEquals(Vector[Long](1L, 2L, 3L).map(Long.box).asJava, testRecord.getArrayOfLongs)
  }
}
