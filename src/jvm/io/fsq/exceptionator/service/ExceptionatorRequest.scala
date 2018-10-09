// Copyright 2012 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.exceptionator.service

import com.twitter.finagle.{Filter, Service}
import com.twitter.finagle.http.{Request, Response}
import com.twitter.util.Future

/**
  * An HTTP request class that wraps extra functionality around a standard HTTP request.
  */
case class ExceptionatorRequest(request: Request, userId: Option[String] = None)

/**
  * A filter that wraps standard Finagle HTTP Request objects with our RichRequest.
  */
class DefaultRequestEnricher extends Filter[Request, Response, ExceptionatorRequest, Response] {
  def apply(request: Request, service: Service[ExceptionatorRequest, Response]): Future[Response] =
    service(ExceptionatorRequest(request, Some("test@example.com")))
}
