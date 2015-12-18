// Copyright 2012 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.spindle.__shaded_for_spindle_bootstrap__.runtime

import org.joda.time.format.{DateTimeFormat, DateTimeFormatter}

object DateFieldHelpers {
  /** Used primarily to parse/format the java.util.Date enhanced type. */
  private val javaUtilDateFormatter: DateTimeFormatter = DateTimeFormat.forPattern("yyyy-MM-dd\'T\'HH:mm:ss\'Z\'")

  def printJavaDate(date: java.util.Date): String = {
    javaUtilDateFormatter.print(date.getTime)
  }

  def parseJavaDate(dateString: String): java.util.Date = {
    javaUtilDateFormatter.parseDateTime(dateString).toDate
  }

}
