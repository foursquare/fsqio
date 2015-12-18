// Copyright 2013 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.exceptionator.actions.concrete

import com.twitter.util.Future
import io.fsq.exceptionator.actions.{BackgroundAction, HasBucketActions, HasNoticeActions, HasUserFilterActions}
import io.fsq.exceptionator.filter.{IncomingFilter, ProcessedIncoming}
import io.fsq.exceptionator.filter.concrete.FreshBucketFilter
import io.fsq.exceptionator.model.io.BacktraceLine
import io.fsq.exceptionator.util.{ConcreteBlamer, ConcreteMailSender, Config, Logger}
import scala.collection.JavaConverters._


class EmailFreshExceptionBackgroundAction (
  services: HasBucketActions
    with HasNoticeActions
    with HasUserFilterActions) extends EmailExceptionBackgroundAction {
  def route(processedIncoming: ProcessedIncoming): (List[String], List[String]) = {

    // Find the config whose filter matches
    val routeConfig = Config.opt(_.getConfigList("email.routes").asScala)
    val route = routeConfig.map(IncomingFilter.checkFilters(processedIncoming.incoming, _))
      .flatten.toList.map(_.matchedConfig)

    // Extract the to and cc string list fields from the matching config if available
    val additionalTo = route.map(Config.opt(_, _.getStringList("to").asScala.toList)).toList.flatten.flatten
    val additionalCc = route.map(Config.opt(_, _.getStringList("cc").asScala.toList)).toList.flatten.flatten
    (additionalTo, additionalCc)
  }

  def shouldEmail(processedIncoming: ProcessedIncoming): Future[Option[SendInfo]] = {
    if ((processedIncoming.buckets.exists(_.name == FreshBucketFilter.name)) &&
        !processedIncoming.incoming.sess.get("_email").exists(_ == "false")) {
      val (to, cc) = route(processedIncoming)
      Future.value(Some(SendInfo("", to, cc)))
    } else {
      Future.value[Option[SendInfo]](None)
    }
  }
}

case class SendInfo(extraInfo: String, to: List[String], cc: List[String])

object EmailExceptionBackgroundAction {
  val mailSender = new ConcreteMailSender
  val blamer = new ConcreteBlamer
}

abstract class EmailExceptionBackgroundAction extends BackgroundAction with Logger {
  val hostname = java.net.InetAddress.getLocalHost.getHostName.toString

  def postSave(processedIncoming: ProcessedIncoming): Future[Unit] = {
    sendMail(processedIncoming)
  }

  def prettyHost: String = {
    Config.opt(_.getString("email.prettylinkhost"))
      .getOrElse("%s:%d".format(hostname, Config.opt(_.getInt("http.port")).getOrElse(8080)))
  }

  // Implement
  // None: don't email
  // Some(string) email, and append this message.
  def shouldEmail(processedIncoming: ProcessedIncoming): Future[Option[SendInfo]]

  def sendMail(processedIncoming: ProcessedIncoming): Future[Unit] = {
    shouldEmail(processedIncoming).flatMap(_.map(sendInfo => {
      // Get interesting lines from the stack trace
      val interesting = processedIncoming.incoming
        .firstNInteresting(Config.opt(_.getInt("email.nInteresting")).getOrElse(1))
      val rev = processedIncoming.incoming.env.get("git").filter(_ != "0")
        .getOrElse(processedIncoming.incoming.v)
      val blameInfo = blameList(rev, interesting)
      formatAndSendMail(processedIncoming, sendInfo, blameInfo)
    }).getOrElse(Future.Unit))
  }

  def blameList(rev: String, interesting: List[BacktraceLine]): Future[List[String]] = {
    val blames: List[Future[Option[String]]] = interesting.map(bl => {
      EmailExceptionBackgroundAction.blamer.blame(
        rev,
        bl.fileName,
        bl.number,
        bl.method.split(".").toList.toSet).map( _.filter(_.isValid).map(blame =>
          "(%s:%d)\n[%s on %s]\n%s\n".format(
            blame.filePath,
            blame.lineNum,
            blame.author,
            blame.date,
            blame.lineString))
      )
    })
    Future.collect(blames).map(_.flatten.toList)
  }

  def formatAndSendMail(
    processedIncoming: ProcessedIncoming,
    sendInfo: SendInfo,
    interestingInfo: Future[List[String]]): Future[Unit] = {

    val incoming = processedIncoming.incoming
    val buckets = processedIncoming.buckets
    val subject = incoming.msgs.head.substring(0, math.min(incoming.msgs.head.length, 50))
    interestingInfo.flatMap(info => {
      val message =
"""
%s

New Exception:
%s

%s

Host:
%s

Potentially interesting line(s):
%s

Full trace:
%s

Truly yours,
Exceptionator""".format(sendInfo.extraInfo,
        buckets.find(_.name == "s").map(sb => "http://%s/notices/s/%s".format(prettyHost,
          sb.key)).getOrElse("<unknown>"),
        incoming.msgs.head,
        incoming.h,
        info.mkString("\n"),
        incoming.flatBacktrace.mkString("\n"))
      EmailExceptionBackgroundAction.mailSender.send(
        sendInfo.to, sendInfo.cc, "[exceptionator] " + subject, message)
    }) onFailure { e =>
      logger.error("Failed to send email: %s".format(e), e)
    }
  }
}
