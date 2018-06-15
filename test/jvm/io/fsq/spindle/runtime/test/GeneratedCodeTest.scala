// Copyright 2012 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.spindle.runtime.test

import com.google.i18n.phonenumbers.PhoneNumberUtil
import io.fsq.spindle.common.thrift.bson.TBSONProtocol
import io.fsq.spindle.common.thrift.json.TReadableJSONProtocol
import io.fsq.spindle.runtime.common.gen.TestStruct
import io.fsq.spindle.runtime.structs.gen.InnerStruct
import io.fsq.spindle.runtime.test.gen.TestFirstException
import io.fsq.thriftexample.av.gen.Movie
import io.fsq.thriftexample.av.gen.MovieTypedefs.{MinutesId, MovieId}
import io.fsq.thriftexample.gen.{Content, MutableTvListingEntry, TvListingEntry}
import io.fsq.thriftexample.gen.TvlistingTypedefs.{MyLong, MyObjectId, MyString}
import io.fsq.thriftexample.people.gen.{ContactInfo, Gender, Person, PhoneNumber, PhoneType}
import io.fsq.thriftexample.talent.gen.{Actor, CrewMember}
import java.nio.ByteBuffer
import org.apache.thrift.protocol.{TBinaryProtocol, TProtocolFactory}
import org.apache.thrift.transport.{TMemoryBuffer, TTransport}
import org.bson.types.ObjectId
import org.junit.Assert.{assertEquals, assertFalse, assertTrue, fail}
import org.junit.Test

class GeneratedCodeTest {
  private def makePhone(phoneNumberStr: String, phoneType: PhoneType): ContactInfo = {
    val phoneNumberMatch = PhoneNumberUtil.getInstance().findNumbers(phoneNumberStr, "US").iterator().next()
    val phoneNumber =
      (PhoneNumber.newBuilder
        .countryCode(phoneNumberMatch.number.getCountryCode.toShort)
        .areaCode((phoneNumberMatch.number.getNationalNumber / 10000000).toInt)
        .number(phoneNumberMatch.number.getNationalNumber % 10000000)
        .phoneType(phoneType)
        .result())

    ContactInfo.newBuilder.phone(phoneNumber).result()
  }

  private def makeStreetAddress(streetAddressStr: String) = {}

  private def makeEmail(e: String): ContactInfo =
    ContactInfo.newBuilder.email(e).result()

  private def makeMovie() = {
    val vinceVaughn =
      (Actor.newBuilder
        .details(Person("Vince", "Vaughn", Gender.MALE, List(makeEmail("vincevaughn@fake.com"))))
        .agentDetails(
          Person(
            "Ari",
            "Gold",
            Gender.MALE,
            List(makePhone("(212) 555 7345", PhoneType.CELL), makeEmail("arig@fake.com"))
          )
        )
        .result())

    val christineTaylor =
      (Actor.newBuilder
        .details(Person("Christine", "Taylor", Gender.FEMALE, List(makeEmail("ctaylor@evenfaker.com"))))
        .result())

    val rawsonThurber =
      (CrewMember.newBuilder
        .details(Person("Rawson", "Thurber", Gender.MALE, Nil))
        .credits(List("Director", "Writer"))
        .result())

    val movie =
      (Movie.newBuilder
        .id(MovieId(new ObjectId("522e3e9f4b90871874292b48")))
        .name("Dodgeball: A True Underdog Story")
        .lengthMinutes(MinutesId(92L))
        .cast(Map("Peter La Fleur" -> vinceVaughn, "Kate Veatch" -> christineTaylor))
        .crew(List(rawsonThurber))
        .result())

    movie
  }

  private def makeTvListingEntry(): MutableTvListingEntry = {
    (TvListingEntry.newBuilder
      .startTime("2012-01-18 20:00:00")
      .endTime("2012-01-18 21:59:59")
      .content(
        Content.newBuilder
          .movie(makeMovie)
          .result()
      )
      .resultMutable())
  }

  private def doWrite(protocolFactory: TProtocolFactory, tvListingEntry: TvListingEntry): TMemoryBuffer = {
    val trans = new TMemoryBuffer(1024)
    val oprot = protocolFactory.getProtocol(trans)
    tvListingEntry.write(oprot)
    trans
  }

  private def doRead(protocolFactory: TProtocolFactory, trans: TTransport): TvListingEntry = {
    val tvListingEntry = TvListingEntry.createRawRecord
    val iprot = protocolFactory.getProtocol(trans)
    tvListingEntry.read(iprot)
    tvListingEntry
  }

  private def doTestRoundTrip(oprotocolFactory: TProtocolFactory, iprotocolFactory: TProtocolFactory) {
    val originalTvListingEntry = makeTvListingEntry()
    val trans: TTransport = doWrite(oprotocolFactory, originalTvListingEntry)
    val resultingTvListingEntry = doRead(iprotocolFactory, trans)
    assertEquals(originalTvListingEntry, resultingTvListingEntry)
  }

  // Convenience method for the protocols that don't distinguish between input and output factories.
  private def doTestRoundTrip(protocolFactory: TProtocolFactory) { doTestRoundTrip(protocolFactory, protocolFactory) }

  @Test
  def testEqualsMethod(): Unit = {
    val tvListingEntry1 = makeTvListingEntry()
    val tvListingEntry2 = makeTvListingEntry()

    assertFalse(tvListingEntry1.equals(null))
    assertFalse(tvListingEntry1.equals(new Object()))
    assertTrue(tvListingEntry1.equals(tvListingEntry2))
    assertTrue(tvListingEntry1.hashCode == tvListingEntry2.hashCode)
    assertTrue(tvListingEntry1.## == tvListingEntry2.##)

    tvListingEntry1.startTime_=("2012-01-18 20:00:01")
    assertFalse(tvListingEntry1.equals(tvListingEntry2))
    tvListingEntry2.startTime_=("2012-01-18 20:00:01")
    assertTrue(tvListingEntry1.equals(tvListingEntry2))
    assertTrue(tvListingEntry1.hashCode == tvListingEntry2.hashCode)
    assertTrue(tvListingEntry1.## == tvListingEntry2.##)

    tvListingEntry1.endTimeUnset()
    assertFalse(tvListingEntry1.equals(tvListingEntry2))
    tvListingEntry2.endTimeUnset()
    assertTrue(tvListingEntry1.equals(tvListingEntry2))
    assertTrue(tvListingEntry1.hashCode == tvListingEntry2.hashCode)
    assertTrue(tvListingEntry1.## == tvListingEntry2.##)

    tvListingEntry2.content_=(null)
    assertFalse(tvListingEntry1.equals(tvListingEntry2))
    tvListingEntry1.content_=(null)
    assertTrue(tvListingEntry1.equals(tvListingEntry2))
    assertTrue(tvListingEntry1.hashCode == tvListingEntry2.hashCode)
    assertTrue(tvListingEntry1.## == tvListingEntry2.##)
  }

  @Test
  def testBinaryProtocolRoundTrip(): Unit = {
    doTestRoundTrip(new TBinaryProtocol.Factory())
  }

  @Test
  def testBSONProtocolRoundTrip(): Unit = {
    doTestRoundTrip(new TBSONProtocol.WriterFactory(), new TBSONProtocol.ReaderFactory())
  }

  @Test
  def testWrite(): Unit = {
    var expected = """{
  "st" : "2012-01-18 20:00:00",
  "et" : "2012-01-18 21:59:59",
  "content" : {
    "movie" : {
      "id" : "ObjectId(\"522e3e9f4b90871874292b48\")",
      "name" : "Dodgeball: A True Underdog Story",
      "lengthMinutes" : 92,
      "cast" : {
        "Peter La Fleur" : {
          "details" : {
            "firstName" : "Vince",
            "lastName" : "Vaughn",
            "gender" : 1,
            "contacts" : [ {
              "email" : "vincevaughn@fake.com"
            } ]
          },
          "agentDetails" : {
            "firstName" : "Ari",
            "lastName" : "Gold",
            "gender" : 1,
            "contacts" : [ {
              "phone" : {
                "countryCode" : 1,
                "areaCode" : 212,
                "number" : 5557345,
                "phoneType" : 2
              }
            }, {
              "email" : "arig@fake.com"
            } ]
          }
        },
        "Kate Veatch" : {
          "details" : {
            "firstName" : "Christine",
            "lastName" : "Taylor",
            "gender" : 2,
            "contacts" : [ {
              "email" : "ctaylor@evenfaker.com"
            } ]
          }
        }
      },
      "crew" : [ {
        "details" : {
          "firstName" : "Rawson",
          "lastName" : "Thurber",
          "gender" : 1,
          "contacts" : [ ]
        },
        "credits" : [ "Director", "Writer" ]
      } ]
    }
  }
}"""

    val tvListingEntry = makeTvListingEntry()
    val trans = doWrite(new TReadableJSONProtocol.Factory(), tvListingEntry)
    val actual = JsonPrettyPrinter.prettify(trans.toString("UTF8"))
    assertEquals(expected, actual)
    assertEquals(expected.hashCode, actual.hashCode)
    assertEquals(expected.##, actual.##)
  }

  @Test
  def testFieldSettersAndGetters(): Unit = {
    println("Testing getters and setters")

    val e = TvListingEntry.createRawRecord

    TvListingEntry.startTime.setterRaw(e, "2012-01-18 20:00:01")
    assertEquals(Some("2012-01-18 20:00:01"), e.startTimeOption)
    assertEquals(Some("2012-01-18 20:00:01"), TvListingEntry.startTime.getter(e))
    assertEquals("2012-01-18 20:00:01", e.startTimeOrThrow)

    TvListingEntry.startTime.unsetterRaw(e)
    assertEquals(None, e.startTimeOption)
    assertEquals(None, TvListingEntry.startTime.getter(e))

    try {
      e.startTimeOrThrow
      fail("OrThrow on unset field should have thrown")
    } catch {
      case ex: NullPointerException => {
        assertEquals("field startTime of TvListingEntry missing", ex.getMessage)
      }
    }
  }

  /* this test should just compile */
  @Test
  def testIds(): Unit = {
    def takesObjectId(x: ObjectId) {}
    def takesString(x: String) {}
    def takesLong(x: Long) {}
    def takesMyObjectId(x: MyObjectId) {}
    def takesMyString(x: MyString) {}
    def takesMyLong(x: MyLong) {}

    val oid = new ObjectId
    val oidStr = oid.toString
    val sid = "1"
    val lid = 1L

    takesObjectId(MyObjectId(oid))
    takesString(MyString(sid))
    takesLong(MyLong(lid))

    takesMyObjectId(MyObjectId(oid))
    takesMyString(MyString(sid))
    takesMyLong(MyLong(lid))

    assertTrue(true)
  }

  /* this test should just compile */
  @Test
  def testIdImplicits(): Unit = {
    import io.fsq.thriftexample.gen.TvlistingTypedefImplicits._

    def takesMyObjectId(x: MyObjectId) {}
    def takesMyString(x: MyString) {}
    def takesMyLong(x: MyLong) {}

    val oid = new ObjectId
    val oidStr = oid.toString
    val sid = "1"
    val lid = 1L
    val iid = 1

    takesMyObjectId(oidStr)
    takesMyString(sid)
    takesMyLong(lid)
    takesMyLong(iid)

    assertTrue(true)
  }

  @Test
  def testDeepMergeCopy(): Unit = {
    val struct1 = TestStruct.newBuilder
      .aBool(true)
      .aByte(60.toByte)
      .anI16(42.toShort)
      .anI32(123)
      .anI64(12345L)
      .aDouble(123.456d)
      .aString("Hello")
      .aBinary(ByteBuffer.wrap(Array[Byte](1, 2, 3)))
      .aStruct(InnerStruct.newBuilder.aString("String").result())
      .aSet(Set("A", "B", "C"))
      .aList(Vector(1, 1, 2, 3, 5, 8))
      .aMap(
        Map(
          "1" -> InnerStruct.newBuilder.aString("One").result(),
          "2" -> InnerStruct.newBuilder.aString("Two").result()
        )
      )
      .aMyBinary(ByteBuffer.wrap(Array[Byte](4, 5, 6)))
      .aStructList(Vector(InnerStruct.newBuilder.anInt(1).result(), InnerStruct.newBuilder.anInt(2).result()))
      .result()
    // Trivial case - check that if only 1 is defined, that side is used
    assertEquals(struct1, struct1.deepMergeCopy(TestStruct.newBuilder.result()))
    assertEquals(struct1, TestStruct.newBuilder.result().deepMergeCopy(struct1))

    val struct2 = TestStruct.newBuilder
      .anI32(12)
      .aString("World")
      .aBinary(ByteBuffer.wrap(Array[Byte](2, 4, 6)))
      .aStruct(InnerStruct.newBuilder.aString("Different String").anInt(42).result())
      .aSet(Set("A", "D", "E"))
      .aList(Vector(2, 3, 5, 7, 11))
      .aMap(
        Map(
          "1" -> InnerStruct.newBuilder.aString("NewOne").result(),
          "3" -> InnerStruct.newBuilder.aString("Three").result()
        )
      )
      .aMyBinary(ByteBuffer.wrap(Array[Byte](8, 10, 12)))
      .aStructList(Vector(InnerStruct.newBuilder.anInt(1).result()))
      .result()

    val TwoIntoOne = TestStruct.newBuilder
      .aBool(true)
      .aByte(60.toByte)
      .anI16(42.toShort)
      .anI32(123)
      .anI64(12345L)
      .aDouble(123.456d)
      .aString("Hello")
      .aBinary(ByteBuffer.wrap(Array[Byte](1, 2, 3)))
      .aStruct(InnerStruct.newBuilder.aString("String").anInt(42).result()) //Merge substructs
      .aSet(Set("A", "B", "C", "D", "E")) //Merge sets
      .aList(Vector(1, 1, 2, 3, 5, 8, 2, 3, 5, 7, 11)) //Concatenate vectors
      .aMap(
        Map(
          "1" -> InnerStruct.newBuilder.aString("NewOne").result(), //this++that means values from 2 overwrite(!?)
          "2" -> InnerStruct.newBuilder.aString("Two").result(),
          "3" -> InnerStruct.newBuilder.aString("Three").result()
        )
      )
      .aMyBinary(ByteBuffer.wrap(Array[Byte](4, 5, 6)))
      //Concatenate vectors of structs
      .aStructList(
        Vector(
          InnerStruct.newBuilder.anInt(1).result(),
          InnerStruct.newBuilder.anInt(2).result(),
          InnerStruct.newBuilder.anInt(1).result()
        )
      )
      .result()

    val OneIntoTwo = TestStruct.newBuilder
      .aBool(true)
      .aByte(60.toByte)
      .anI16(42.toShort)
      .anI32(12)
      .anI64(12345L)
      .aDouble(123.456d)
      .aString("World")
      .aBinary(ByteBuffer.wrap(Array[Byte](2, 4, 6)))
      .aStruct(InnerStruct.newBuilder.aString("Different String").anInt(42).result()) //Merge substructs
      .aSet(Set("A", "B", "C", "D", "E")) //Merge sets
      .aList(Vector(2, 3, 5, 7, 11, 1, 1, 2, 3, 5, 8)) //Concatenate vectors
      .aMap(
        Map(
          "1" -> InnerStruct.newBuilder.aString("One").result(), //this++that means values from 1 overwrite(!?)
          "2" -> InnerStruct.newBuilder.aString("Two").result(),
          "3" -> InnerStruct.newBuilder.aString("Three").result()
        )
      )
      .aMyBinary(ByteBuffer.wrap(Array[Byte](8, 10, 12)))
      //Concatenate vectors of structs
      .aStructList(
        Vector(
          InnerStruct.newBuilder.anInt(1).result(),
          InnerStruct.newBuilder.anInt(1).result(),
          InnerStruct.newBuilder.anInt(2).result()
        )
      )
      .result()

    assertEquals(TwoIntoOne, struct1.deepMergeCopy(struct2))
    assertEquals(OneIntoTwo, struct2.deepMergeCopy(struct1))

    // Exception behavior is odd - only the underlying struct is merged (Ditto for equality etc)
    val exception1 = TestFirstException.Struct.newBuilder.value(42).result().toException
    val exception2 = TestFirstException.Struct.newBuilder.value(123).result().toException
    assertEquals(exception1, exception1.deepMergeCopy(exception2))
    assertEquals(exception2, exception2.deepMergeCopy(exception1))

    val exceptionString = new TestFirstException("Hello World")
    assertEquals(exception1, exceptionString.deepMergeCopy(exception1))
    assertEquals(exception1, exception1.deepMergeCopy(exceptionString))

    val cause = new Throwable("placeholder")
    val exceptionCause = new TestFirstException(cause)
    assertEquals(exception1, exceptionCause.deepMergeCopy(exception1))
    assertEquals(exception1, exception1.deepMergeCopy(exceptionCause))

    val emptyException = TestFirstException.Struct.createRecord.toException
    assertEquals(emptyException, exceptionString.deepMergeCopy(exceptionCause))
    assertEquals(emptyException, exceptionCause.deepMergeCopy(exceptionString))
  }
}
