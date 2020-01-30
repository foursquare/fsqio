// Copyright 2012 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.json.lift

import java.lang.{Appendable, StringBuilder}
import net.liftweb.json.JsonAST.{JArray, JBool, JDouble, JField, JInt, JNothing, JNull, JObject, JString, JValue}
import net.liftweb.json.JsonParser.parse
import scala.annotation.tailrec

// A JValue -> String serializer that performs better than Lift's.
object JsonSerialization {
  def serialize(json: JValue, builderSize: Int = 4096): String = {
    serialize(json, new StringBuilder(builderSize)).toString
  }

  def serialize(json: JValue, sb: Appendable): Appendable = json match {
    case null | JNull | JString(null) => sb.append("null")
    case JNothing => sys.error("tried to serialize JNothing")
    case JBool(b) => sb.append(b.toString)
    case JDouble(n) => sb.append(n.toString)
    case JInt(n) => sb.append(n.toString)
    case JString(s) =>
      sb.append('"')
      serializeQuoted(s, sb)
      sb.append('"')
    case JArray(arr) =>
      sb.append('[')
      serializeList(arr, sb)
      sb.append(']')
    case JObject(obj) =>
      sb.append('{')
      serializeFields(obj, sb)
      sb.append('}')
  }

  def serializeField(jf: JField, sb: Appendable): Appendable = jf match {
    case JField(n, JNothing) =>
      sb
    case JField(n, v) =>
      sb.append('"')
      serializeQuoted(n, sb)
      sb.append('"').append(':')
      serialize(v, sb)
  }

  def serializeFields(_xs: List[JField], sb: Appendable): Appendable = {
    val xs = _xs.filter(_.value != JNothing)
    @tailrec
    def serializeFields1(xs: List[JField]): Appendable = xs match {
      case y +: ys =>
        sb.append(',')
        serializeField(y, sb)
        serializeFields1(ys)
      case Nil => sb
    }

    xs match {
      case y +: ys =>
        serializeField(y, sb)
        serializeFields1(ys)
      case Nil => sb
    }
  }

  def serializeList(_xs: List[JValue], sb: Appendable): Appendable = {
    val xs = _xs.filter(_ != JNothing)
    @tailrec
    def serializeList1(xs: List[JValue]): Appendable = xs match {
      case y +: ys =>
        sb.append(',')
        serialize(y, sb)
        serializeList1(ys)
      case Nil => sb
    }

    xs match {
      case y +: ys =>
        serialize(y, sb)
        serializeList1(ys)
      case Nil => sb
    }
  }

  def serializeQuoted(s: String, sb: Appendable): Appendable = {
    @tailrec
    def loop(i: Int, n: Int): Appendable = {
      if (i < n) {
        s.charAt(i) match {
          case '"' => sb.append('\\').append('"')
          case '\\' => sb.append('\\').append('\\')
          case '/' => sb.append('\\').append('/')
          case '\b' => sb.append('\\').append('b')
          case '\f' => sb.append('\\').append('f')
          case '\n' => sb.append('\\').append('n')
          case '\r' => sb.append('\\').append('r')
          case '\t' => sb.append('\\').append('t')
          case c if Character.isISOControl(c) || (c >= '\u2000' && c < '\u2100') =>
            sb.append('\\').append('u').append("%04x".format(c: Int))
          case c => sb.append(c)
        }
        loop(i + 1, n)
      } else {
        sb
      }
    }
    loop(0, s.size)
  }
}
