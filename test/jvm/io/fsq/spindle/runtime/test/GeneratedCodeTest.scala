// Copyright 2012 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.spindle.runtime.test

import com.google.i18n.phonenumbers.PhoneNumberUtil
import io.fsq.spindle.common.thrift.bson.TBSONProtocol
import io.fsq.spindle.common.thrift.json.TReadableJSONProtocol
import io.fsq.spindle.thriftexample.av.gen.Movie
import io.fsq.spindle.thriftexample.av.gen.MovieTypedefs.{MinutesId, MovieId}
import io.fsq.spindle.thriftexample.gen.{Content, MutableTvListingEntry, TvListingEntry}
import io.fsq.spindle.thriftexample.gen.TvlistingTypedefs.{MyLong, MyObjectId, MyString}
import io.fsq.spindle.thriftexample.people.gen.{ContactInfo, Gender, Person, PhoneNumber, PhoneType}
import io.fsq.spindle.thriftexample.talent.gen.{Actor, CrewMember}
import org.apache.thrift.protocol.{TBinaryProtocol, TProtocolFactory}
import org.apache.thrift.transport.{TMemoryBuffer, TTransport}
import org.bson.types.ObjectId
import org.junit.Assert.{assertEquals, assertFalse, assertTrue, fail}
import org.junit.Test


class GeneratedCodeTest {
  private def makePhone(phoneNumberStr: String, phoneType: PhoneType): ContactInfo = {
    val phoneNumberMatch = PhoneNumberUtil.getInstance().findNumbers(phoneNumberStr, "US").iterator().next()
    val phoneNumber =
      (PhoneNumber
        .newBuilder
        .countryCode(phoneNumberMatch.number.getCountryCode.toShort)
        .areaCode((phoneNumberMatch.number.getNationalNumber / 10000000).toInt)
        .number(phoneNumberMatch.number.getNationalNumber % 10000000)
        .phoneType(phoneType)
        .result())

    ContactInfo.newBuilder.phone(phoneNumber).result()
  }

  private def makeStreetAddress(streetAddressStr: String) = {

  }

  private def makeEmail(e: String): ContactInfo =
    ContactInfo.newBuilder.email(e).result()

  private def makeMovie() = {
    val vinceVaughn =
      (Actor
        .newBuilder
        .details(Person("Vince", "Vaughn", Gender.MALE, List(makeEmail("vincevaughn@fake.com"))))
        .agentDetails(
          Person("Ari", "Gold", Gender.MALE, List(
            makePhone("(212) 555 7345", PhoneType.CELL),
            makeEmail("arig@fake.com"))))
        .result())

    val christineTaylor =
      (Actor
        .newBuilder
        .details(Person("Christine", "Taylor", Gender.FEMALE, List(makeEmail("ctaylor@evenfaker.com"))))
        .result())

    val rawsonThurber =
      (CrewMember
        .newBuilder
        .details(Person("Rawson", "Thurber", Gender.MALE, Nil))
        .credits(List("Director", "Writer"))
        .result())

    val movie =
      (Movie
        .newBuilder
        .id(MovieId(new ObjectId("522e3e9f4b90871874292b48")))
        .name("Dodgeball: A True Underdog Story")
        .lengthMinutes(MinutesId(92L))
        .cast(Map("Peter La Fleur" -> vinceVaughn, "Kate Veatch" -> christineTaylor))
        .crew(List(rawsonThurber))
        .result())

    movie
  }

  private def makeTvListingEntry(): MutableTvListingEntry = {
    (TvListingEntry
      .newBuilder
      .startTime("2012-01-18 20:00:00")
      .endTime("2012-01-18 21:59:59")
      .content(Content
        .newBuilder
        .movie(makeMovie)
        .result())
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
  def testEqualsMethod() {
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
  def testBinaryProtocolRoundTrip() {
    doTestRoundTrip(new TBinaryProtocol.Factory())
  }

  @Test
  def testBSONProtocolRoundTrip() {
    doTestRoundTrip(new TBSONProtocol.WriterFactory(), new TBSONProtocol.ReaderFactory())
  }

  @Test
  def testWrite() {
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
  def testFieldSettersAndGetters() {
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
  def testIds {
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
  def testIdImplicits {
    import io.fsq.spindle.thriftexample.gen.TvlistingTypedefImplicits._

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
}
