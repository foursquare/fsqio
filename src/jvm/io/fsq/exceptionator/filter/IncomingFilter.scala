// Copyright 2012 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.exceptionator.filter

import com.typesafe.config.{Config => TSConfig}
import io.fsq.exceptionator.model.io.RichIncoming
import io.fsq.exceptionator.util.RegexUtil
import scala.collection.JavaConverters._
import scala.util.matching.Regex

case class FilterResult(matchedConfig: TSConfig, allowed: Option[Boolean], incoming: RichIncoming)

object IncomingFilter {
  case class RuleResult(allowed: Option[Boolean], incoming: RichIncoming)

  def fieldGetter(f: String): RichIncoming => Seq[String] = f match {
    case "msgs" => _.msgs
    case "excs" => _.excs
    case "bt" => _.excStack
    case "h" => i => List(i.hostOrThrow)
    case "v" => i => List(i.versionOrThrow)
    case _ => throw new IllegalArgumentException("Can't find a field match for " + f)
  }

  def fieldSetter(f: String, v: Seq[String]): RichIncoming => RichIncoming = f match {
    case "msgs" => i => RichIncoming(i.copy(messages = v))
    case "excs" => i => RichIncoming(i.copy(exceptions = v))
    case "bt" => i => RichIncoming(i.copy(exceptionStack = v))
    case "h" => i => v.headOption.map(newH => RichIncoming(i.copy(host = newH))).getOrElse(i)
    case "v" => i => v.headOption.map(newV => RichIncoming(i.copy(version = newV))).getOrElse(i)
    case _ => throw new IllegalArgumentException("Can't find a field match for " + f)
  }

  def applyRule(incoming: RichIncoming, ruleType: String, ruleField: String, ruleValue: Seq[Regex]): RuleResult = {

    val incomingValue = fieldGetter(ruleField)(incoming)

    ruleType match {
      case "allow" =>
        RuleResult(Option(RegexUtil.listMatchesAPattern(incomingValue, ruleValue)).filter(_ == true), incoming)
      case "deny" =>
        RuleResult(Option(!RegexUtil.listMatchesAPattern(incomingValue, ruleValue)).filter(_ == false), incoming)
      case "filter" =>
        ruleField match {
          case "bt" =>
            val remainingLines =
              incomingValue.map(_.split("\n").filter(RegexUtil.matchesAPattern(_, ruleValue)).mkString("\n"))
            RuleResult(None, fieldSetter(ruleField, remainingLines)(incoming))
          case _ =>
            val remainingLines = incomingValue.filter(RegexUtil.matchesAPattern(_, ruleValue))
            RuleResult(None, fieldSetter(ruleField, remainingLines)(incoming))

        }
      case "filterNot" =>
        ruleField match {
          case "bt" =>
            val remainingLines =
              incomingValue.map(_.split("\n").filterNot(RegexUtil.matchesAPattern(_, ruleValue)).mkString("\n"))
            RuleResult(None, fieldSetter(ruleField, remainingLines)(incoming))
          case _ =>
            val remainingLines = incomingValue.filterNot(RegexUtil.matchesAPattern(_, ruleValue))
            RuleResult(None, fieldSetter(ruleField, remainingLines)(incoming))

        }
      case unknownType => throw new IllegalArgumentException("Rule " + unknownType + " not supported")
    }
  }

  def checkFilter(i: RichIncoming, filterConfig: TSConfig): FilterResult = {
    val order: Seq[String] = filterConfig.getStringList("order").asScala
    val rules: Seq[(String, TSConfig)] = order.map(rule => rule -> filterConfig.getConfig(rule))
    var incoming = i
    val results: Seq[Set[RuleResult]] = rules.map {
      case (rule, ruleConfig) => {
        val fields: Set[String] = ruleConfig.entrySet.asScala.map(_.getKey).toSet
        fields.map(field => {
          val res = applyRule(incoming, rule, field, RegexUtil.cached(ruleConfig.getStringList(field).asScala.toList))
          incoming = res.incoming
          res
        })
      }
    }

    // Purposely making use of set.flatten to collapse entries
    // Find will eagerly return first defined result.  If no result is defined, None is returned
    FilterResult(
      filterConfig,
      results
        .map(_.map(_.allowed).flatten.toList)
        .find(_ match {
          case true +: Nil => true // Eagerly return positive result (allow)
          case false +: Nil => true // Eagerly return negatice result (deny)
          case Nil => false // Keep going
          case _ =>
            throw new IllegalArgumentException("Incoming " + i + " and filter " + filterConfig + " conflicted on apply")
        })
        .flatMap(_.headOption),
      incoming
    )
  }

  def checkFilters(i: RichIncoming, filterConfigs: Seq[TSConfig]): Option[FilterResult] = {
    filterConfigs.view.map(checkFilter(i, _)).find(_.allowed.isDefined)
  }
}
