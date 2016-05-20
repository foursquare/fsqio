// Copyright 2012 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.exceptionator.service

import com.codahale.jerkson.Json
import com.twitter.finagle.Service
import com.twitter.finagle.http.Response
import com.twitter.ostrich.stats.Stats
import com.twitter.util.Future
import io.fsq.common.logging.Logger
import io.fsq.exceptionator.actions.{BackgroundActions, IncomingActions}
import io.fsq.exceptionator.filter.FilteredIncoming
import io.fsq.exceptionator.model.io.Incoming
import io.fsq.exceptionator.util.Config
import java.io.{BufferedWriter, FileWriter}
import org.jboss.netty.buffer.ChannelBufferInputStream
import org.jboss.netty.handler.codec.http._
import scala.collection.JavaConverters._

class IncomingHttpService(incomingActions: IncomingActions, backgroundActions: BackgroundActions)
    extends Service[ExceptionatorRequest, Response] with Logger {

  val incomingLog = Config.opt(_.getString("log.incoming")).map(fn => new BufferedWriter(new FileWriter(fn, true)))

  def apply(request: ExceptionatorRequest) = {
    request.method match {
      case HttpMethod.POST =>
        request.path match {
          case "/api/notice" =>
            val incoming = Json.parse[Incoming](new ChannelBufferInputStream(request.getContent))
            process(incoming).map(res => {
              val response = Response(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)
              response.contentString = res
              response
            })
          case "/api/multi-notice" =>
            val incomingSeq = Json.stream[Incoming](new ChannelBufferInputStream(request.getContent)).toSeq
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
      log.write(Json.generate(incoming))
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
