// Copyright 2012 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.exceptionator.service

import com.twitter.finagle.{Filter, Service}
import com.twitter.finagle.http.{Request, RequestProxy, Response}
import com.twitter.util.Future


/**
 * An HTTP request class that wraps extra functionality around a standard HTTP request.
 */
class ExceptionatorRequest(val request: Request, val userId: Option[String] = None) extends RequestProxy

/**
 * A filter that wraps standard Finagle HTTP Request objects with our RichRequest.
 */
class DefaultRequestEnricher extends Filter[Request, Response, ExceptionatorRequest, Response] {
  def apply(request: Request, service: Service[ExceptionatorRequest, Response]): Future[Response] =
    service(new ExceptionatorRequest(request, Some("test@example.com")))
}
