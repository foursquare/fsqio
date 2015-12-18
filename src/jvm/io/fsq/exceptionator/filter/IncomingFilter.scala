// Copyright 2012 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.exceptionator.filter

import com.typesafe.config.{Config => TSConfig}
import io.fsq.exceptionator.model.io.Incoming
import io.fsq.exceptionator.util.RegexUtil
import scala.collection.JavaConverters._
import scala.util.matching.Regex

case class FilterResult(matchedConfig: TSConfig, allowed: Option[Boolean], incoming: Incoming)

object IncomingFilter {
  case class RuleResult(allowed: Option[Boolean], incoming: Incoming)

  def fieldGetter(f: String): Incoming => List[String] = f match {
    case "msgs" => _.msgs
    case "excs" => _.excs
    case "bt" => _.bt
    case "h" => i => List(i.h)
    case "v" => i => List(i.v)
    case _ => throw new IllegalArgumentException("Can't find a field match for " + f)
  }

  def fieldSetter(f: String, v: List[String]): Incoming => Incoming = f match {
    case "msgs" => i => i.copy(msgs = v)
    case "excs" => i => i.copy(excs = v)
    case "bt" => i => i.copy(bt = v)
    case "h" => i => v.headOption.map(newH => i.copy(h = newH)).getOrElse(i)
    case "v" => i => v.headOption.map(newV => i.copy(v = newV)).getOrElse(i)
    case _ => throw new IllegalArgumentException("Can't find a field match for " + f)
  }

  def applyRule(
      incoming: Incoming,
      ruleType: String,
      ruleField: String,
      ruleValue: List[Regex]): RuleResult = {

    val incomingValue = fieldGetter(ruleField)(incoming)

    ruleType match {
      case "allow" => RuleResult(
        Option(RegexUtil.listMatchesAPattern(incomingValue, ruleValue)).filter(_ == true),
        incoming)
      case "deny" => RuleResult(
        Option(!RegexUtil.listMatchesAPattern(incomingValue, ruleValue)).filter(_ == false),
        incoming)
      case "filter" => ruleField match {
        case "bt" =>
          val remainingLines = incomingValue.map(
              _.split("\n").filter(RegexUtil.matchesAPattern(_, ruleValue)).mkString("\n"))
          RuleResult(None, fieldSetter(ruleField, remainingLines)(incoming))
        case _ =>
          val remainingLines = incomingValue.filter(RegexUtil.matchesAPattern(_, ruleValue))
          RuleResult(None, fieldSetter(ruleField, remainingLines)(incoming))

      }
      case "filterNot" => ruleField match {
        case "bt" =>
          val remainingLines = incomingValue.map(
              _.split("\n").filterNot(RegexUtil.matchesAPattern(_, ruleValue)).mkString("\n"))
          RuleResult(None, fieldSetter(ruleField, remainingLines)(incoming))
        case _ =>
          val remainingLines = incomingValue.filterNot(RegexUtil.matchesAPattern(_, ruleValue))
          RuleResult(None, fieldSetter(ruleField, remainingLines)(incoming))

      }
      case unknownType => throw new IllegalArgumentException("Rule " + unknownType + " not supported")
    }
  }


  def checkFilter(i: Incoming, filterConfig: TSConfig): FilterResult = {
    val order: Seq[String] = filterConfig.getStringList("order").asScala
    val rules: Seq[(String, TSConfig)] = order.map(rule => rule -> filterConfig.getConfig(rule))
    var incoming = i
    val results: Seq[Set[RuleResult]] = rules.map{ case (rule, ruleConfig) => {
      val fields: Set[String] = ruleConfig.entrySet.asScala.map(_.getKey).toSet
      fields.map(field => {
        val res = applyRule(
            incoming,
            rule,
            field,
            RegexUtil.cached(ruleConfig.getStringList(field).asScala.toList))
        incoming = res.incoming
        res
      })
    }}


    // Purposely making use of set.flatten to collapse entries
    // Find will eagerly return first defined result.  If no result is defined, None is returned
    FilterResult(filterConfig, results.map(_.map(_.allowed).flatten.toList).find(_ match {
      case true +: Nil => true  // Eagerly return positive result (allow)
      case false +: Nil => true // Eagerly return negatice result (deny)
      case Nil => false        // Keep going
      case _ => throw new IllegalArgumentException(
          "Incoming " + i + " and filter " + filterConfig + " conflicted on apply")
    }).flatMap(_.headOption), incoming)
  }

  def checkFilters(i: Incoming, filterConfigs: Seq[TSConfig]): Option[FilterResult] = {
    filterConfigs.view.map(checkFilter(i, _)).find(_.allowed.isDefined)
  }
}
