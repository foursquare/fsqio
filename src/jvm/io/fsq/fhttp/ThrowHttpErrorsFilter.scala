// Copyright 2018 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.fhttp

import com.twitter.finagle.{Service, SimpleFilter}
import com.twitter.finagle.http.{Request, Response, Status}
import com.twitter.util.Future

class ThrowHttpErrorsFilter(name: String) extends SimpleFilter[Request, Response] {
  def apply(request: Request, service: Service[Request, Response]): Future[Response] = {
    // flatMap asynchronously responds to requests and can "map" them to both
    // success and failure values:
    service(request).flatMap(response => {
      response.statusCode match {
        case x if x >= 200 && x < 300 => Future.value(response)
        case statusCode => {
          val reasonPhrase = Status(statusCode).reason
          Future.exception(HttpStatusException(statusCode, reasonPhrase, response).addName(name))
        }
      }
    })
  }
}
