// Copyright 2012 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.exceptionator.service

import com.twitter.finagle.Service
import com.twitter.finagle.httpx.{Method, Response, Status, Version}
import com.twitter.io.BufInputStream
import com.twitter.ostrich.stats.Stats
import com.twitter.util.Future
import io.fsq.common.logging.Logger
import io.fsq.exceptionator.actions.{BackgroundActions, IncomingActions}
import io.fsq.exceptionator.filter.FilteredIncoming
import io.fsq.exceptionator.model.io.{IncomingRequest, RichIncoming}
import io.fsq.exceptionator.util.Config
import java.io.{BufferedWriter, FileWriter, InputStreamReader}
import org.json4s.{DefaultFormats, Formats}
import org.json4s.jackson.Serialization

class IncomingHttpService(incomingActions: IncomingActions, backgroundActions: BackgroundActions)
  extends Service[ExceptionatorRequest, Response]
  with Logger {

  val incomingLog = Config
    .opt(_.getString("log.incoming"))
    .map(fn => new BufferedWriter(new FileWriter(fn, true)))
  implicit val formats: Formats = DefaultFormats

  def apply(exceptionatorRequest: ExceptionatorRequest): Future[Response] = {
    val request = exceptionatorRequest.request
    request.method match {
      case Method.Post =>
        request.path match {
          case "/api/notice" =>
            val incomingReq = Serialization.read[IncomingRequest](
              new InputStreamReader(new BufInputStream(request.content))
            )
            val incoming = RichIncoming(incomingReq.toThrift)
            process(incoming).map(res => {
              val response = Response(Version.Http11, Status.Ok)
              response.contentString = res
              response
            })
          case "/api/multi-notice" =>
            val incomingReqSeq = Serialization.read[Seq[IncomingRequest]](
              new InputStreamReader(new BufInputStream(request.content))
            )
            val incomingSeq = incomingReqSeq.map(_.toThrift).map(RichIncoming(_))
            Future
              .collect(incomingSeq.map(incoming => process(incoming)))
              .map(res => {
                val response = Response(Version.Http11, Status.Ok)
                response.contentString = res.mkString(",")
                response
              })
          case _ =>
            ServiceUtil.errorResponse(Status.NotFound)
        }

      case _ =>
        ServiceUtil.errorResponse(Status.NotFound)
    }
  }

  def process(incoming: RichIncoming): Future[String] = {
    incomingLog.foreach(log => {
      log.write(Serialization.write(incoming))
      log.write("\n")
    })

    val n = incoming.countOption.getOrElse(1)
    Stats.incr("notices", n)
    incomingActions(FilteredIncoming(incoming)).map(processed => {
      Stats.time("backgroundActions.postSave") {
        backgroundActions.postSave(processed)
      }
      processed.id
        .map(_.toString)
        .getOrElse({
          logger.warning("blocked:\n" + incoming)
          ""
        })
    })
  }
}
