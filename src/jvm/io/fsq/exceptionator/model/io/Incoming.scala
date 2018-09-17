// Copyright 2012 Foursquare Labs Inc. All Rights Reserved.
package io.fsq.exceptionator.model.io

import _root_.io.fsq.exceptionator.model.gen.{Incoming, IncomingProxy}
import _root_.io.fsq.exceptionator.util.RegexUtil

object BacktraceLine {
  val BacktraceLineRegex = """^(.*) \((.*):(.*)\)$""".r // method, file, line

  def apply(l: String): BacktraceLine = l match {
    case BacktraceLineRegex(method, name, num) => BacktraceLine(method, name, num.toInt)
    case _ => BacktraceLine("", "", 0)
  }
}

case class BacktraceLine(method: String, fileName: String, number: Int) {
  def isValid = number > 0
}

case class IncomingRequest(
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
  ttl: Option[Int] = None, // expire this notice after `ttl` seconds
  id: Option[Int] = None // optional data useful for debugging with tester.py
) {
  def toThrift: Incoming = {
    Incoming.newBuilder
      .messages(msgs)
      .exceptions(excs)
      .exceptionStack(bt)
      .session(sess)
      .enviroment(env)
      .host(h)
      .version(v)
      .count(n)
      .date(d)
      .tags(tags)
      .timeToExpire(ttl)
      .id(id)
      .result()
  }
}

class RichIncoming(thriftModel: Incoming) extends IncomingProxy {
  override val underlying = thriftModel

  lazy val msgs: Seq[String] = messages
  lazy val excs: Seq[String] = exceptions
  lazy val excStack: Seq[String] = exceptionStack
  lazy val sess: Map[String, String] = session
  lazy val env: Map[String, String] = enviroment

  def structuredBacktrace: Seq[Seq[BacktraceLine]] = excStack.map(_.split("\n").map(BacktraceLine(_)).toVector)
  // TODO: replace these with something more modular
  def flatBacktrace = excStack.flatMap(_.split("\n").toVector)
  def firstInteresting: Option[BacktraceLine] =
    flatBacktrace.find(l => isInteresting(l)).map(l => BacktraceLine(l))
  def firstNInteresting(n: Int): Seq[BacktraceLine] =
    flatBacktrace.filter(l => isInteresting(l)).slice(0, n).map(l => BacktraceLine(l))
  def isInteresting(l: String): Boolean = {
    RegexUtil.matchesNoPatterns(l, "backtrace.interesting.filterNot") &&
    RegexUtil.matchesAPattern(l, "backtrace.interesting.filter")
  }
}

object RichIncoming {
  def apply(thriftModel: Incoming) = new RichIncoming(thriftModel)
}
