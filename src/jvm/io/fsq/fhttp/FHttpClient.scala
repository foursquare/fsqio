// Copyright 2011 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.fhttp

import com.twitter.conversions.DurationOps._
import com.twitter.finagle.{Http, Service}
import com.twitter.finagle.builder.ClientBuilder
import com.twitter.finagle.builder.ClientConfig.Yes
import com.twitter.finagle.http.{Request, Response}
import com.twitter.util.{Await, Future}

class FHttpClient(
  val name: String,
  val hostPort: String, // host:port
  builder: ClientBuilder[Request, Response, Nothing, Yes, Yes] =
    ClientBuilder().stack(Http.client).tcpConnectTimeout(1.second).hostConnectionLimit(1)
) {

  // hackazor!
  def scheme: String = if (builder.toString.contains("TLSEngine")) "https" else "http"

  val firstHostPort: String = hostPort.split(",", 2)(0)

  def builtClient: Service[Request, Response] = builder.name(name).hosts(hostPort).build()

  private val baseService = new ThrowHttpErrorsFilter(name) andThen builtClient

  def service: Service[Request, Response] = baseService

  def release(): Future[Unit] = {
    baseService.close()
  }

  def releaseOnShutdown(): FHttpClient = {
    Runtime.getRuntime.addShutdownHook(new Thread() {
      override def run(): Unit = {
        Await.result(release())
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
    s"io.fsq.fhttp.FHttpClient($name,$scheme://$hostPort,$builder)"
  }
}
