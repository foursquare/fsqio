// Copyright 2012 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.spindle.rogue.testlib

object Models {
  // Used for selectCase tests.
  case class V1(legacyid: Option[Long])
  case class V2(legacyid: Option[Long], userid: Option[Long])
  case class V3(legacyid: Option[Long], userid: Option[Long], mayor: Option[Long])
  case class V4(legacyid: Option[Long], userid: Option[Long], mayor: Option[Long], mayor_count: Option[Int])
  case class V5(
    legacyid: Option[Long],
    userid: Option[Long],
    mayor: Option[Long],
    mayor_count: Option[Int],
    closed: Option[Boolean]
  )
  case class V6(
    legacyid: Option[Long],
    userid: Option[Long],
    mayor: Option[Long],
    mayor_count: Option[Int],
    closed: Option[Boolean],
    tags: Option[Seq[String]]
  )
}
