// Copyright 2011 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.fhttpx

import com.twitter.conversions.time._
import com.twitter.finagle.{Service, SimpleFilter}
import com.twitter.finagle.builder.ClientBuilder
import com.twitter.finagle.builder.ClientConfig.Yes
import com.twitter.finagle.httpx.{Http, Request, Response}
import com.twitter.util.Future
import org.jboss.netty.handler.codec.http.HttpResponseStatus

class FHttpClient(
  val name: String,
  val hostPort: String, // host:port
  builder: ClientBuilder[Request, Response, Nothing, Yes, Yes] =
    ClientBuilder().codec(Http()).tcpConnectTimeout(1.second).hostConnectionLimit(1)
) {
  object throwHttpErrorsFilter extends SimpleFilter[Request, Response] {
    def apply(request: Request, service: Service[Request, Response]) = {
      // flatMap asynchronously responds to requests and can "map" them to both
      // success and failure values:
      service(request) flatMap(response => {
        response.statusCode match {
          case x if x >= 200 && x < 300 =>
            Future.value(response)
          case statusCode =>
            val reasonPhrase = HttpResponseStatus.valueOf(statusCode ).getReasonPhrase
            Future.exception(HttpStatusException(statusCode , reasonPhrase, response).addName(name))
        }
      })
    }
  }

  // hackazor!
  def scheme = if (builder.toString.contains("TLSEngine")) "https" else "http"

  val firstHostPort = hostPort.split(",",2)(0)

  def builtClient = builder.name(name).hosts(hostPort).build()

  val baseService = throwHttpErrorsFilter andThen builtClient

  def service: Service[Request, Response] = baseService

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

  override def toString: String = {
    "io.fsq.fhttpx.FHttpClient(" + name + "," + scheme + "://" + hostPort + "," + builder + ")"
  }
}
