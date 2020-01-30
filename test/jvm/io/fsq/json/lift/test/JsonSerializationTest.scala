// Copyright 2012 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.json.lift.test

import io.fsq.common.scala.Identity._
import io.fsq.json.lift.JsonSerialization
import net.liftweb.json.{JsonAST, JsonParser}
import net.liftweb.json.JsonAST.{JArray, JBool, JDouble, JField, JInt, JNothing, JNull, JObject, JString, JValue}
import net.liftweb.json.JsonDSL._
import net.liftweb.json.JsonParser.parse
import org.junit.Assert.{assertEquals, assertTrue}
import org.junit.Test
import org.scalacheck.{Gen, Prop, Test => Check}
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen._
import org.scalacheck.util.ConsoleReporter

class JsonSerializationTest {
  def testRoundtrip(json: JValue): Boolean = {
    val jsonStr = JsonSerialization.serialize(json)
    val json2 = JsonParser.parse(jsonStr)
    assertEquals(json, json2)
    json =? json2
  }

  def jsonMustBeSame(json: JValue): Boolean = {
    val custom = JsonSerialization.serialize(json)
    val lift = JsonAST.compactRender(json)
    assertEquals(lift, custom)
    custom =? lift
  }

  @Test
  def escapingFieldNames: Unit = {
    val obj = JObject(List(JField("field\"", "value")))
    val str = JsonSerialization.serialize(obj)
    assertEquals(str, """{"field\"":"value"}""")
  }

  @Test
  def escapingSlashes: Unit = {
    val obj = JObject(List(JField("field", "</script>")))
    val str = JsonSerialization.serialize(obj)
    assertEquals(str, """{"field":"<\/script>"}""")
  }

  @Test
  def nothingTest: Unit = {
    val nothing: Option[String] = None
    val json = ("int" -> 1) ~
      ("str" -> "jon hoffman") ~
      ("embed" -> ("k1" -> "v1") ~ ("k2" -> "v2") ~ ("nothing" -> nothing))

    assertEquals(JsonSerialization.serialize(json), """{"int":1,"str":"jon hoffman","embed":{"k1":"v1","k2":"v2"}}""")
  }

  @Test
  def nothingList: Unit = {
    val nothing: Option[Int] = None
    val json = ("list" -> JArray(List(1, nothing, 2))) ~
      ("str" -> "jon hoffman")

    assertEquals(JsonSerialization.serialize(json), """{"list":[1,2],"str":"jon hoffman"}""")
  }

  @Test
  def newLines: Unit = {
    val json = ("ok" -> 1) ~
      ("str" -> "jon\n hoffman")

    jsonMustBeSame(json)
  }

  @Test
  def escapesQuotes: Unit = {
    val json = ("ok" -> 1) ~
      ("str" -> "you said: \"bla bla bla\" lone quote: \"")

    jsonMustBeSame(json)
  }

  @Test
  def escapeControlCharacters: Unit = {
    val c = '\u001f'
    assertEquals(JsonSerialization.serialize(JString(c.toString)), "\"\\u001f\"")
  }

  @Test
  def roundtripRandomJObjectsCorrectly: Unit = {
    val res = Check.check(Check.Parameters.default, Prop.forAll(JValueGen.genObject)(testRoundtrip))
    ConsoleReporter(1).onTestResult(this.getClass.getName, res)
    assertTrue(res.passed)
  }
}

object JValueGen extends JValueGen

trait JValueGen {
  def genJValue: Gen[JValue] = frequency((50, delay(genSimple)), (1, delay(genArray)), (1, delay(genObject)))
  def genSimple: Gen[JValue] = oneOf(
    const(JNull),
    arbitrary[Int].map(JInt(_)),
    arbitrary[Double].map(JDouble(_)),
    arbitrary[Boolean].map(JBool(_)),
    genString.map(JString(_))
  )

  def genString: Gen[String] = arbitrary[String]
  def genArray: Gen[JValue] = for (l <- genList) yield JArray(l)
  def genObject: Gen[JObject] = for (l <- genFieldList) yield JObject(l)

  def genList = Gen.containerOfN[List, JValue](listSize, genJValue)
  def genFieldList = Gen.containerOfN[List, JField](listSize, genField)
  def genField = for {
    name <- identifier
    value <- genJValue
    id <- choose(0, 1000000)
  } yield JField(name + id, value)

  def genJValueClass: Gen[Class[_ <: JValue]] = oneOf(
    JNull.getClass.asInstanceOf[Class[JValue]],
    JNothing.getClass.asInstanceOf[Class[JValue]],
    classOf[JInt],
    classOf[JDouble],
    classOf[JBool],
    classOf[JString],
    classOf[JArray],
    classOf[JObject]
  )

  def listSize = choose(0, 5).sample.get
}
