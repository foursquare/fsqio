// Copyright 2012 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.exceptionator.service

import com.codahale.jerkson.Json
import com.twitter.finagle.Service
import com.twitter.finagle.http.{Request, Response}
import com.twitter.util.{Future, FuturePool}
import io.fsq.common.logging.Logger
import io.fsq.exceptionator.actions.{HasBucketActions, HasHistoryActions, HasNoticeActions, HasUserFilterActions}
import io.fsq.exceptionator.model.io.{Outgoing, UserFilterView}
import io.fsq.exceptionator.util.Config
import java.net.URLDecoder
import java.util.concurrent.Executors
import org.jboss.netty.handler.codec.http._
import org.joda.time.DateTime
import scala.collection.JavaConverters._

object ApiHttpService {
   val Notices = """/api/notices(?:/([^/]+)(?:/([^/?&=]+))?)?""".r
   val Filters = """/api/filters(?:/([^/]+))?""".r
   val History = """/api/history/([^/]+)(?:/([^/]+))?/(\d+)""".r
}

object InternalResponse {
  def notFound: Future[InternalResponse] = {
    Future.value(InternalResponse("", HttpResponseStatus.NOT_FOUND))
  }

  def notAuthorized(msgOpt: Option[String] = None): Future[InternalResponse] = {
    Future.value(InternalResponse(msgOpt.map(msg =>
      Json.generate(Map("error" -> msg))).getOrElse(""), HttpResponseStatus.UNAUTHORIZED))
  }

  def apply(content: String): Future[InternalResponse] = {
    Future.value(InternalResponse(content, HttpResponseStatus.OK))
  }

  def apply(contentFuture: Future[String]): Future[InternalResponse] = {
    contentFuture.map(content => InternalResponse(content, HttpResponseStatus.OK))
  }
}

case class InternalResponse(content: String, status: HttpResponseStatus) {
  def toResponse: Response = {
    val response = Response(HttpVersion.HTTP_1_1, status)
    response.contentString = content
    response.setContentTypeJson
    response
  }
}

class ApiHttpService(
  services: HasBucketActions with HasHistoryActions with HasNoticeActions with HasUserFilterActions,
  bucketFriendlyNames: Map[String, String]) extends Service[ExceptionatorRequest, Response] with Logger {

  val apiFuturePool = FuturePool(Executors.newFixedThreadPool(10))

  def apply(request: ExceptionatorRequest) = {
    val res: Future[InternalResponse] = request.path match {
      case "/api/config" =>
        config(request)
      case ApiHttpService.Filters(key) =>
        val keyOpt = Option(key)
        request.method match {
          case HttpMethod.GET => filters(keyOpt, request)
          case HttpMethod.DELETE => deleteFilter(keyOpt, request)
          case HttpMethod.PUT | HttpMethod.POST =>
            saveFilter(request)
          case _ =>
            InternalResponse.notFound
        }
      case ApiHttpService.Notices(name, key) =>
        notices(Option(name).map(decodeURIComponent), Option(key).map(decodeURIComponent), request)
      case ApiHttpService.History(name, key, timestamp) =>
        history(Option(name).map(decodeURIComponent), Option(key).map(decodeURIComponent), timestamp.toLong, request)
      case "/api/search" =>
        search(decodeURIComponent(request.getParam("q")).toLowerCase, request)
      case _ =>
        InternalResponse.notFound
    }

    res.map(_.toResponse)
  }

  def decodeURIComponent(component: String) = {
    URLDecoder.decode(component.replace("+", "%2B"), "UTF-8")
  }

  def limitParam(request: Request): Int = request.getIntParam("limit", 20)

  def bucketNotices(bucketName: String, bucketKey: String, request: Request): Future[InternalResponse] = {
    InternalResponse(apiFuturePool({
      val outgoingElems = services.bucketActions.get(bucketName, bucketKey, DateTime.now)
      val outgoing = Outgoing.compact(outgoingElems.take(limitParam(request)))
      outgoing
    }))
  }

  def recent(bucketName: String, request: Request): Future[InternalResponse] = {
    InternalResponse(apiFuturePool({
      val limit = limitParam(request)
      val outgoingElems = services.bucketActions.get(
        services.bucketActions.recentKeys(bucketName, Some(limit)), Some(1), DateTime.now)
      val outgoing = Outgoing.compact(outgoingElems.take(limit))
      outgoing
    }))
  }

  def notices(
    bucketName: Option[String],
    bucketId: Option[String],
    request: Request): Future[InternalResponse] = {

    (bucketName, bucketId) match {
      case (None, None) => recent("s", request)
      case (Some(n), None) => recent(n, request)
      case (Some(n), Some(k)) => bucketNotices(n, k, request)
      case _ => InternalResponse.notFound
    }
  }

  def search(terms: String, request: Request): Future[InternalResponse] = {
    InternalResponse(apiFuturePool({
      val limit = limitParam(request)
      val outgoingElems = services.noticeActions.search(terms.split("\\s+").toList, Some(limit))
      val outgoing = Outgoing.compact(outgoingElems.take(limit))
      outgoing
    }))
  }

  def deleteFilter(keyOpt: Option[String], request: ExceptionatorRequest) = {
    keyOpt.map(key =>
      InternalResponse(apiFuturePool({services.userFilterActions.remove(key, request.userId); ""})))
      .getOrElse(InternalResponse.notFound)
  }

  def filters(keyOpt: Option[String], request: ExceptionatorRequest): Future[InternalResponse] = {
    keyOpt.map(key => {
      apiFuturePool(services.userFilterActions.get(key)).flatMap(
        _.map(view => InternalResponse(view.compact))
          .getOrElse(InternalResponse.notFound))
    }).getOrElse(
      InternalResponse(apiFuturePool({
        UserFilterView.compact(services.userFilterActions.getAll(request.userId))
      }))
    )
  }

  def saveFilter(request: ExceptionatorRequest): Future[InternalResponse] = {
    if (request.userId.isEmpty) {
      InternalResponse.notAuthorized()
    } else {
      logger.info("saving filter: %s".format(request.contentString))
      apiFuturePool(services.userFilterActions.save(request.contentString, request.userId.get)).flatMap(
        _.map(view => InternalResponse(view.compact))
        .getOrElse(InternalResponse.notAuthorized()))
    }
  }

  def config(request: ExceptionatorRequest): Future[InternalResponse] = {
    val values = Map(
      "friendlyNames" -> bucketFriendlyNames,
      "homepage" -> Config.renderJson("web.homepage").map(Json.parse[List[_]](_)).getOrElse(
        List(
          Map("list" -> Map("bucketName" -> "all"), "view" -> Map("showList" -> false)),
          Map("list" -> Map("bucketName" -> "s")
        )))) ++
      request.userId.map("userId" -> _).toMap ++
      Config.opt(_.getInt("http.port")).map("apiPort" -> _).toMap ++
      Config.opt(_.getString("http.hostname")).map("apiHost" -> _).toMap
    InternalResponse(Json.generate(values))
  }

  def bucketHistory(
    bucketName: String,
    bucketKey: String,
    timestamp: Long,
    request: Request
  ): Future[InternalResponse] = {
    InternalResponse(apiFuturePool({
      val outgoingElems = services.historyActions.get(
        bucketName,
        bucketKey,
        new DateTime(timestamp),
        limitParam(request))
      Outgoing.compact(outgoingElems)
    }))
  }

  // NOTE: This differs from the realtime functionality in that it does not
  //       gurantee a single notice per matching bucket, something the history
  //       record format does not provide an easy/performant way to enforce.
  def groupHistory(bucketName: String, timestamp: Long, request: Request): Future[InternalResponse] = {
    InternalResponse(apiFuturePool({
      val limit = limitParam(request)
      val outgoingElems = services.historyActions.get(bucketName, new DateTime(timestamp), limitParam(request))
      Outgoing.compact(outgoingElems)
    }))
  }

  def history(
    bucketName: Option[String],
    bucketId: Option[String],
    timestamp: Long,
    request: Request
  ): Future[InternalResponse] = {
    (bucketName, bucketId) match {
      case (None, None) => groupHistory("s", timestamp, request)
      case (Some(n), None) => groupHistory(n, timestamp, request)
      case (Some(n), Some(k)) => bucketHistory(n, k, timestamp, request)
      case _ => InternalResponse.notFound
    }
  }

}
