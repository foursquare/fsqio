// Copyright 2012 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.exceptionator.service

import com.twitter.finagle.Service
import com.twitter.finagle.http.Response
import com.twitter.ostrich.stats.Stats
import com.twitter.util.Future
import io.fsq.common.logging.Logger
import io.fsq.exceptionator.actions.{BackgroundActions, IncomingActions}
import io.fsq.exceptionator.filter.FilteredIncoming
import io.fsq.exceptionator.model.io.Incoming
import io.fsq.exceptionator.util.Config
import java.io.{BufferedWriter, FileWriter, InputStreamReader}
import org.jboss.netty.buffer.ChannelBufferInputStream
import org.jboss.netty.handler.codec.http._
import org.json4s.{DefaultFormats, Formats}
import org.json4s.jackson.Serialization

class IncomingHttpService(incomingActions: IncomingActions, backgroundActions: BackgroundActions)
    extends Service[ExceptionatorRequest, Response] with Logger {

  val incomingLog = Config.opt(_.getString("log.incoming"))
    .map(fn => new BufferedWriter(new FileWriter(fn, true)))
  implicit val formats: Formats = DefaultFormats

  def apply(request: ExceptionatorRequest) = {
    request.method match {
      case HttpMethod.POST =>
        request.path match {
          case "/api/notice" =>
            val incoming = Serialization.read[Incoming](
              new InputStreamReader(new ChannelBufferInputStream(request.getContent))
            )
            process(incoming).map(res => {
              val response = Response(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)
              response.contentString = res
              response
            })
          case "/api/multi-notice" =>
            val incomingSeq = Serialization.read[List[Incoming]](
              new InputStreamReader(new ChannelBufferInputStream(request.getContent))
            )
            Future.collect(incomingSeq.map(incoming => process(incoming))).map(res => {
              val response = Response(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)
              response.contentString = res.mkString(",")
              response
            })
          case _ =>
            ServiceUtil.errorResponse(HttpResponseStatus.NOT_FOUND)
        }

      case _ =>
        ServiceUtil.errorResponse(HttpResponseStatus.NOT_FOUND)
    }
  }

  def process(incoming: Incoming) = {
    incomingLog.foreach(log => {
      log.write(Serialization.write(incoming))
      log.write("\n")
    })

    val n = incoming.n.getOrElse(1)
    Stats.incr("notices", n)
    incomingActions(FilteredIncoming(incoming)).map(processed => {
      Stats.time("backgroundActions.postSave") {
        backgroundActions.postSave(processed)
      }
      processed.id.map(_.toString).getOrElse({
        logger.warning("blocked:\n" + incoming)
        ""
      })
    })
  }
}
