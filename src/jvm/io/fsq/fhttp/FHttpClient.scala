// Copyright 2011 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.fhttp

import com.twitter.conversions.time._
import com.twitter.finagle.{Service, SimpleFilter}
import com.twitter.finagle.builder.ClientBuilder
import com.twitter.finagle.builder.ClientConfig.Yes
import com.twitter.finagle.http.Http
import com.twitter.util.Future
import org.jboss.netty.handler.codec.http._


class FHttpClient ( val name: String,
                    val hostPort: String, // host:port
                    builder: ClientBuilder[HttpRequest, HttpResponse, Nothing, Yes, Yes] =
                      ClientBuilder().codec(Http()).tcpConnectTimeout(1.second).hostConnectionLimit(1)) {

  object throwHttpErrorsFilter extends SimpleFilter[HttpRequest, HttpResponse] {
    def apply(request: HttpRequest, service: Service[HttpRequest, HttpResponse]) = {
      // flatMap asynchronously responds to requests and can "map" them to both
      // success and failure values:
      service(request) flatMap { response =>
        response.getStatus.getCode match {
          case x if (x >= 200 && x < 300) =>
            Future.value(response)
          case code =>
            Future.exception(HttpStatusException(code, response.getStatus.getReasonPhrase, response).addName(name))
        }
      }
    }
  }

  // hackazor!
  def scheme = if (builder.toString.contains("TLSEngine")) "https" else "http"

  val firstHostPort = hostPort.split(",",2)(0)

  def builtClient = builder.name(name).hosts(hostPort).build()

  val baseService = throwHttpErrorsFilter andThen builtClient

  def service: Service[HttpRequest, HttpResponse] = baseService

  def release() {
    baseService.close()
  }

  def releaseOnShutdown(): FHttpClient = {
    Runtime.getRuntime.addShutdownHook( new Thread() {
      override def run() {
        release()
      }
    })
    this
  }

  def uri(path: String): FHttpRequest = {
    FHttpRequest(this, path)
  }

  def apply(path: String): FHttpRequest = {
    uri(path)
  }

  override def toString(): String =  {
    return "com.foursquare.fhttp.FHttpClient(" + name + "," + scheme + "://" + hostPort + "," + builder + ")"
  }
}


