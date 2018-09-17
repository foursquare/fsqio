// Copyright 2012 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.exceptionator.util

import java.util.concurrent.ConcurrentHashMap
import scala.collection.JavaConverters._
import scala.util.matching.Regex

object RegexUtil {
  val cachedPatterns = new ConcurrentHashMap[String, Regex]()

  def cached(pattern: String): Regex = {
    Option(cachedPatterns.get(pattern)) getOrElse {
      val regex = new Regex(pattern)
      cachedPatterns.putIfAbsent(pattern, regex)
      regex
    }
  }

  def cached(patterns: Seq[String]): Seq[Regex] = {
    patterns.map(cached _)
  }

  def patterns(patternsPath: String): Seq[Regex] = {
    Config.opt(_.getStringList(patternsPath).asScala.toList).map(cached _).getOrElse(Nil)
  }

  def matchesAPattern(line: String, patterns: Seq[Regex]): Boolean =
    patterns.exists(_.pattern.matcher(line).find)

  def matchesAPattern(line: String, patternsPath: String): Boolean =
    matchesAPattern(line, patterns(patternsPath))

  def matchesNoPatterns(line: String, patterns: Seq[Regex]): Boolean =
    !matchesAPattern(line, patterns)

  def matchesNoPatterns(line: String, patternsPath: String): Boolean =
    matchesNoPatterns(line, patterns(patternsPath))

  def listMatchesAPattern(list: Seq[String], patterns: Seq[Regex]): Boolean =
    list.exists(line => matchesAPattern(line, patterns))

  def listMatchesAPattern(list: Seq[String], patternsPath: String): Boolean =
    listMatchesAPattern(list, patterns(patternsPath))

  def listMatchesNoPatterns(list: Seq[String], patterns: Seq[Regex]): Boolean =
    !listMatchesAPattern(list, patterns)

  def listMatchesNoPatterns(list: Seq[String], patternsPath: String): Boolean =
    listMatchesNoPatterns(list, patterns(patternsPath))
}
