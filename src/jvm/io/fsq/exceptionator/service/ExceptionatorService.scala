// Copyright 2012 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.exceptionator.service

import com.mongodb.{MongoClient, MongoClientOptions, ServerAddress}
import com.twitter.finagle.{Http, Service}
import com.twitter.finagle.builder.ServerBuilder
import com.twitter.finagle.http.{Method, Response, Status, Version}
import com.twitter.finagle.http.filter.ExceptionFilter
import com.twitter.io.Buf
import com.twitter.util.{Future, FuturePool}
import io.fsq.common.logging.Logger
import io.fsq.exceptionator.actions.{HasBucketActions, HasHistoryActions, HasNoticeActions}
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
import io.fsq.exceptionator.model.gen.{BucketRecord, BucketRecordHistogram, HistoryRecord, NoticeRecord}
import io.fsq.exceptionator.mongo.HasExceptionatorMongoService
import io.fsq.exceptionator.mongo.concrete.ConcreteExceptionatorMongoService
import io.fsq.exceptionator.util.Config
import io.fsq.net.stats.FoursquareStatsReceiver
import io.fsq.rogue.connection.DefaultMongoIdentifier
import io.fsq.spindle.runtime.UntypedMetaRecord
import java.io.InputStream
import java.net.InetSocketAddress
import java.util.Arrays
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
  val defaultDbHost = "localhost:27017"
  val defaultDbName = "test"
  val defaultMongoIdentifier = new DefaultMongoIdentifier("exceptionator")

  def bootMongo(
    services: HasExceptionatorMongoService,
    ensureIndexesMetaRecords: Seq[UntypedMetaRecord] = Nil
  ): Unit = {
    Runtime
      .getRuntime()
      .addShutdownHook(new Thread {
        override def run(): Unit = {
          services.exceptionatorMongo.clientManager.closeAll()
        }
      })

    val serverAddresses = Config
      .opt(_.getString("db.host"))
      .getOrElse(defaultDbHost)
      .split(',')
      .map(hostString => {
        hostString.split(':') match {
          case Array(host, port) => new ServerAddress(host, port.toInt)
          case Array(host) => new ServerAddress(host)
          case _ => throw new IllegalArgumentException(s"Malformed host string: $hostString")
        }
      })

    val mongoOptions = MongoClientOptions.builder
      .socketTimeout(ConcreteExceptionatorMongoService.defaultDbSocketTimeoutMS.toInt)
      .build

    val client = new MongoClient(Arrays.asList(serverAddresses: _*), mongoOptions)
    val dbName = Config.opt(_.getString("db.name")).getOrElse(defaultDbName)
    services.exceptionatorMongo.clientManager.defineDb(defaultMongoIdentifier, client, dbName)

    ensureIndexesMetaRecords.foreach(metaRecord => {
      services.exceptionatorMongo.collectionFactory
        .getIndexes(metaRecord)
        .foreach(services.exceptionatorMongo.executor.createIndexes(metaRecord)(_: _*))
    })
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
      lazy val exceptionatorMongo = new ConcreteExceptionatorMongoService
    }

    // Create services
    val incomingActions = new FilteredConcreteIncomingActions(new ConcreteIncomingActions(services))

    // Start mongo
    try {
      bootMongo(
        services,
        Vector(
          BucketRecord,
          BucketRecordHistogram,
          HistoryRecord,
          NoticeRecord
        )
      )
    } catch {
      case e: Exception => {
        logger.error(e, "Failed to connect to mongo")
        System.exit(1)
      }
    }

    val backgroundActions = new ConcreteBackgroundActions(services)

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

    ServerBuilder()
      .bindTo(new InetSocketAddress(httpPort))
      .stack(Http.server)
      .name("exceptionator-http")
      .reportTo(new FoursquareStatsReceiver)
      .build(service)
  }
}
