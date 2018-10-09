// Copyright 2012 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.exceptionator.service

import com.twitter.finagle.Service
import com.twitter.finagle.builder.{Server, ServerBuilder}
import com.twitter.finagle.http.{Http, Method, Response, Status, Version}
import com.twitter.finagle.http.filter.ExceptionFilter
import com.twitter.finagle.stats.OstrichStatsReceiver
import com.twitter.io.Buf
import com.twitter.ostrich.admin.{AdminServiceFactory, RuntimeEnvironment, StatsFactory, TimeSeriesCollectorFactory}
import com.twitter.util.{Future, FuturePool}
import io.fsq.common.logging.Logger
import io.fsq.exceptionator.actions.{HasBucketActions, HasHistoryActions, HasNoticeActions, IndexActions}
import io.fsq.exceptionator.actions.concrete.{
  ConcreteBackgroundActions,
  ConcreteBucketActions,
  ConcreteHistoryActions,
  ConcreteIncomingActions,
  ConcreteNoticeActions,
  FilteredConcreteIncomingActions
}
import io.fsq.exceptionator.loader.concrete.ConcretePluginLoaderService
import io.fsq.exceptionator.loader.service.HasPluginLoaderService
import io.fsq.exceptionator.mongo.HasExceptionatorMongoService
import io.fsq.exceptionator.mongo.concrete.ConcreteExceptionatorMongoService
import io.fsq.exceptionator.util.Config
import java.io.{IOException, InputStream}
import java.net.InetSocketAddress
import java.util.concurrent.Executors
import scala.collection.mutable.ListBuffer

object ServiceUtil {
  def errorResponse(status: Status): Future[Response] = {
    val response = Response(Version.Http11, status)
    Future.value(response)
  }
}

class StaticFileService(prefix: String) extends Service[ExceptionatorRequest, Response] with Logger {

  val staticFileFuturePool = FuturePool(Executors.newFixedThreadPool(8))

  def inputStreamToByteArray(is: InputStream): Array[Byte] = {
    val buf = ListBuffer[Byte]()
    var b = is.read()
    while (b != -1) {
      buf.append(b.byteValue)
      b = is.read()
    }
    buf.toArray
  }

  def apply(exceptionatorRequest: ExceptionatorRequest): Future[Response] = {
    val request = exceptionatorRequest.request
    val path = if (request.path.matches("^/(?:css|html|js)/.*")) {
      request.path
    } else {
      "/html/index.html"
    }
    val resourcePath = prefix + path

    logger.info("GET %s from %s".format(path, resourcePath))
    val stream = Option(getClass.getResourceAsStream(resourcePath))

    stream
      .map(s => {
        staticFileFuturePool(inputStreamToByteArray(s)).map(data => {
          val response = Response(Version.Http11, Status.Ok)
          response.content = Buf.ByteArray.Shared(data)
          if (path.endsWith(".js")) {
            response.headerMap.add("Content-Type", "application/x-javascript")
          }
          if (path.endsWith(".css")) {
            response.headerMap.add("Content-Type", "text/css")
          }
          response
        })
      })
      .getOrElse(ServiceUtil.errorResponse(Status.NotFound))
  }
}

class ExceptionatorHttpService(
  fileService: Service[ExceptionatorRequest, Response],
  apiService: Service[ExceptionatorRequest, Response],
  incomingService: Service[ExceptionatorRequest, Response]
) extends Service[ExceptionatorRequest, Response] {

  def apply(exceptionatorRequest: ExceptionatorRequest): Future[Response] = {
    val request = exceptionatorRequest.request
    if (!request.path.startsWith("/api/")) {
      fileService(exceptionatorRequest)
    } else {
      // TODO why did i make this hard on myself?
      if (request.method == Method.Post &&
          (request.path.startsWith("/api/notice") ||
          request.path.startsWith("/api/multi-notice"))) {
        incomingService(exceptionatorRequest)
      } else {
        apiService(exceptionatorRequest)
      }
    }
  }
}

object ExceptionatorServer extends Logger {
  val defaultPort = 8080
  val defaultStatsPort = defaultPort + 1
  val defaultDbHost = "localhost:27017"
  val defaultDbName = "test"
  val defaultDbSocketTimeout = 10 * 1000

  def bootMongo(indexesToEnsure: List[IndexActions] = Nil) {
    indexesToEnsure.foreach(_.ensureIndexes)
  }

  def main(args: Array[String]) {
    logger.info("Starting ExceptionatorServer")
    Config.defaultInit()

    val services = new HasBucketActions with HasHistoryActions with HasNoticeActions with HasPluginLoaderService
    with HasExceptionatorMongoService {
      lazy val historyActions = new ConcreteHistoryActions(this)
      lazy val noticeActions = new ConcreteNoticeActions(this)
      lazy val pluginLoader = new ConcretePluginLoaderService(this)
      lazy val bucketActions = new ConcreteBucketActions(this)
      lazy val exceptionatorMongoService = new ConcreteExceptionatorMongoService
    }

    // Create services
    val incomingActions = new FilteredConcreteIncomingActions(new ConcreteIncomingActions(services))

    // Start mongo
    try {
      bootMongo(
        List(services.bucketActions, services.historyActions, services.noticeActions)
      )
    } catch {
      case e: IOException => {
        logger.error(e, "Failed to connect to mongo")
        System.exit(1)
      }
    }

    val backgroundActions = new ConcreteBackgroundActions(services)

    // Start ostrich
    val runtime = new RuntimeEnvironment(this)

    AdminServiceFactory(httpPort = (Config.opt(_.getInt("stats.port")).getOrElse(defaultStatsPort)))
      .addStatsFactory(StatsFactory(reporters = List(TimeSeriesCollectorFactory())))
      .apply(runtime)

    val httpPort = Config.opt(_.getInt("http.port")).getOrElse(defaultPort)
    val pathPrefix = Config.opt(_.getString("web.pathPrefix")).getOrElse("")
    logger.info("Starting ExceptionatorHttpService on port %d".format(httpPort))

    // Start Http Service
    val service = ExceptionFilter andThen new DefaultRequestEnricher andThen
      new ExceptionatorHttpService(
        new StaticFileService(pathPrefix),
        new ApiHttpService(services, incomingActions.bucketFriendlyNames),
        new IncomingHttpService(incomingActions, backgroundActions)
      )

    val server: Server = ServerBuilder()
      .bindTo(new InetSocketAddress(httpPort))
      .codec(Http.get)
      .name("exceptionator-http")
      .reportTo(new OstrichStatsReceiver)
      .build(service)
  }
}
