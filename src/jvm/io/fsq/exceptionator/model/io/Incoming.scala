// Copyright 2012 Foursquare Labs Inc. All Rights Reserved.
package io.fsq.exceptionator.model.io

import _root_.io.fsq.exceptionator.util.RegexUtil

object BacktraceLine {
  val BacktraceLineRegex = """^(.*) \((.*):(.*)\)$""".r // method, file, line

  def apply(l: String): BacktraceLine = l match {
    case BacktraceLineRegex(method, name, num) => BacktraceLine(method, name, num.toInt)
    case _ => BacktraceLine("", "", 0)
  }
}

case class BacktraceLine(method: String, fileName: String, number:Int) {
  def isValid = number > 0
}

case class Incoming (
  msgs: List[String], // messages
  excs: List[String], // exception classes
  bt: List[String], // exception stack
  sess: Map[String, String], // session
  env: Map[String, String], // environment
  h: String, // host
  v: String, // version
  n: Option[Int], // count
  d: Option[Long], //date
  tags: Option[List[String]],
  id: Option[Int] = None // optional data useful for debugging with tester.py
) {
  def structuredBacktrace: List[List[BacktraceLine]] = bt.map(_.split("\n").map(BacktraceLine(_)).toList)

  // TODO: replace these with something more modular
  def flatBacktrace = bt.flatMap(_.split("\n").toList)
  def firstInteresting: Option[BacktraceLine] =
    flatBacktrace.find(l => isInteresting(l)).map(l => BacktraceLine(l))
  def firstNInteresting(n: Int): List[BacktraceLine] =
    flatBacktrace.filter(l => isInteresting(l)).slice(0,n).map(l => BacktraceLine(l))
  def isInteresting(l: String): Boolean = {
    RegexUtil.matchesNoPatterns(l, "backtrace.interesting.filterNot") &&
    RegexUtil.matchesAPattern(l, "backtrace.interesting.filter")
  }
}
