// Copyright 2011 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.fhttp

import com.twitter.conversions.DurationOps._
import com.twitter.finagle.{Filter, IndividualRequestTimeoutException, Service, SimpleFilter}
import com.twitter.finagle.http.{Message, Method, Request, RequestBuilder, Response, Version}
import com.twitter.finagle.service.TimeoutFilter
import com.twitter.finagle.util.DefaultTimer
import com.twitter.io.Buf
import com.twitter.util.{Await, Future, Timer}
import java.nio.charset.Charset
import org.apache.commons.codec.binary.Base64
import org.apache.commons.httpclient.methods.multipart._
import org.apache.commons.httpclient.params.HttpMethodParams
import org.jboss.netty.buffer.ChannelBufferOutputStream
import org.jboss.netty.channel.DefaultChannelConfig
import org.jboss.netty.handler.codec.http.{QueryStringDecoder, QueryStringEncoder}
import scala.collection.JavaConverters._
import scala.util.control.NoStackTrace

object FHttpRequest {
  type HttpOption = Message => Unit

  val UTF_8 = Charset.forName("UTF-8")
  val PARAM_TYPE = "application/x-www-form-urlencoded"
  val BOUNDARY = "gc0pMUlT1B0uNdArYc0p"
  val MULTIPART_PARAMS = {
    val p = new HttpMethodParams()
    p.setParameter(HttpMethodParams.MULTIPART_BOUNDARY, BOUNDARY)
    p
  }

  def apply(client: FHttpClient, uri: String): FHttpRequest = {
    FHttpRequest(client, uri, "", client.service, Nil)
      .headers("Host" -> client.firstHostPort)
  }

  // HttpResponse conversions

  /**
    * Extracts the raw contents as a [[com.twitter.io.Buf]].
    */
  def asBuf: Response => Buf = res => res.content

  /**
    * Extracts the contents as a String (default for all request methods)
    */
  def asString: Response => String = res => res.contentString

  /**
    * Extracts the contents as a byte array
    */
  def asBytes: Response => Array[Byte] = res => {
    val buffer = res.content
    val bytes = new Array[Byte](buffer.length)
    buffer.write(bytes, 0)
    bytes
  }

  /**
    * Extracts the contents as an input stream
    */
  def asInputStream: Response => java.io.InputStream = res => res.getInputStream

  /**
    * Extracts the contents as XML
    */
  def asXml: Response => scala.xml.Elem = res => scala.xml.XML.load(asInputStream(res))

  def asParams: Response => List[(String, String)] = res => {
    val params = new QueryStringDecoder("/null?" + asString(res)).getParameters
    val result = params.asScala.flatMap(kv => kv._2.asScala.toList.map(v => (kv._1, v))).toList
    result
  }

  def asParamMap: Response => Map[String, String] = res => Map(asParams(res): _*)

  /**
    * Extracts the contents as an oauth Token
    */
  def asOAuth1Token: Response => Token = res => {
    val params = asParamMap(res)
    Token(params("oauth_token"), params("oauth_token_secret"))
  }

  /**
    * Returns the original response (convenient because asString is the default extraction type)
    */
  def asResponse: Response => Response = res => res

  /**
    * A filter for printing the request and response as it passes through service stack
    */
  object debugFilter extends SimpleFilter[Request, Response] {
    val printMessage: HttpOption = r => {
      println(r)
      if (!r.content.isEmpty) {
        println("--CONTENT--")
        println(r.contentString)
      }
    }

    def apply(request: Request, service: Service[Request, Response]): Future[Response] = {
      printMessage(request)
      service(request) map (response => {
        printMessage(response)
        response
      })
    }
  }
}

/**
  * An HTTP request
  */
case class FHttpRequest(
  client: FHttpClient,
  uri: String,
  traceName: String,
  service: Service[Request, Response],
  options: List[FHttpRequest.HttpOption]
) {

  /**
    * Adds parameters to the request
    * @param p The parameters to add
    */
  def params(p: (String, String)*): FHttpRequest = params(p)

  /**
    * Adds parameters to the request
    * @param p The parameters to add
    */
  def params(p: TraversableOnce[(String, String)]): FHttpRequest = {
    // QueryStringEncoder doesn't handle existing params well, so decode first
    val qOld = new QueryStringDecoder(uri)
    val qEnc = new QueryStringEncoder(qOld.getPath)
    (paramList ++ p).foreach(kv => qEnc.addParam(kv._1, kv._2))
    FHttpRequest(client, qEnc.toString, traceName, service, options)
  }

  /**
    * Sets the content type header of the request
    * @param contentType The content type
    */
  def contentType(contentType: String): FHttpRequest = headers(("Content-Type", contentType))

  /**
    * Sets the keep alive header of the request (useful for HTTP 1.0).
    */
  def keepAlive(isKeepAlive: Boolean): FHttpRequest = {
    option((request: Message) => {
      request.headerMap.put("Connection", if (isKeepAlive) "Keep-alive" else "Close")
    })
  }

  /**
    * Adds headers to the request
    * @param h The headers to add
    */
  def headers(h: (String, String)*): FHttpRequest = headers(h)

  /**
    * Adds headers to the request
    * @param h The headers to add
    */
  def headers(h: TraversableOnce[(String, String)]): FHttpRequest = {
    option((r: Message) => h.foreach(kv => r.headerMap.add(kv._1, kv._2)))
  }

  /**
    * Adds a basic http auth header to the request
    * @param user The username
    * @param password The password
    */
  def auth(user: String, password: String): FHttpRequest = {
    headers(("Authorization", "Basic " + base64(user + ":" + password)))
  }

  /**
    * Adds a filter to the service
    * @param f the filter to add to the stack
    */
  def filter(f: Filter[Request, Response, Request, Response]): FHttpRequest = {
    FHttpRequest(client, uri, traceName, f andThen service, options)
  }

  /**
    * Adds a request timeout (using the TimeoutFilter) to the stack.  Applies blocking or future responses.
    * @param millis The number of milliseconds to wait
    *
    * NOTE(jacob): We create our own exception to pass down here, as the default used by
    *    TimeoutFilter does not include any stack trace information due to the way the
    *    timeout is implemented (future.within) causing a loss of all useful stack data.
    *    However, in some cases just having the call stack for this method invocation makes
    *    debugging significantly easier, so we subclass IndividualRequestTimeoutException
    *    and attach a useful stack trace to it via a proxied Exception, ensuring the
    *    service name is set as we do.
    */
  def timeout(millis: Int, timer: Timer = DefaultTimer): FHttpRequest = {
    val exception = new IndividualRequestTimeoutException(millis.milliseconds) {
      val creationStackException = new Exception
      override def getStackTrace: Array[StackTraceElement] = creationStackException.getStackTrace
    }
    exception.serviceName = client.name

    filter(new TimeoutFilter(millis.milliseconds, exception, timer))
  }

  /**
    * Adds a debugging filter to print the request and the response.  Can be added multiple times
    * to inspect the filter transformations
    */
  def debug(): FHttpRequest = {
    filter(FHttpRequest.debugFilter)
  }

  /**
    * An approximate representation of the full uri: including scheme, sever, port, and params as a string
    */
  def fullUri: String = {
    client.scheme + "://" + client.firstHostPort + uri
  }

  /**
    * Adds a pre-filter transformation to the HttpMessage
    * @param o A function to transform the HttpMessage
    */
  def option(o: FHttpRequest.HttpOption): FHttpRequest = {
    FHttpRequest(client, uri, traceName, service, o :: options)
  }

  /**
    * Adds a consumer token to the request. The request will be signed with this token
    * @param consumer The token to add
    */
  def oauth(consumer: Token): FHttpRequest = oauth(consumer, None, None)

  /**
    * Adds tokens to the request. The request will be signed with both tokens
    * @param consumer The consumer token
    * @param token The oauth token
    */
  def oauth(consumer: Token, token: Token): FHttpRequest = oauth(consumer, Some(token), None)

  /**
    * Adds tokens to the request. The request will be signed with both tokens
    * @param consumer The consumer token
    * @param token The oauth token
    * @param verifier The verifier parameter (1.0a)
    */
  def oauth(consumer: Token, token: Token, verifier: String): FHttpRequest = {
    oauth(consumer, Some(token), Some(verifier))
  }

  protected def oauth(consumer: Token, token: Option[Token], verifier: Option[String]): FHttpRequest = {
    val hostPort = client.firstHostPort.split(":", 2) match {
      case Array(k, v) => Some(k, v)
      case _ => None
    }

    filter(
      new OAuth1Filter(
        scheme = client.scheme,
        host = hostPort.get._1,
        port = hostPort.get._2.toInt,
        consumer = consumer,
        token = token,
        verifier = verifier
      )
    )
  }

  def hasParams: Boolean = uri.indexOf('?') != -1

  def paramList: List[(String, String)] = {
    (new QueryStringDecoder(uri)).getParameters.asScala
      .map(kv => kv._2.asScala.toList.map(v => (kv._1, v)))
      .flatten
      .toList
  }

  // Response retrieval methods

  /**
    * Issue a non-blocking GET request
    * @param resMap a function to convert the HttpResponse to the desired response type
    */
  def getFuture[T](resMap: Response => T = FHttpRequest.asString): Future[T] = {
    process(Method.Get, f => f.map(resMap))
  }

  /**
    * Issue a non-blocking POST request
    * @param data The content to provide in the message
    * @param resMap A function to convert the HttpResponse to the desired response type
    */
  def postFuture[T](data: Array[Byte], resMap: Response => T): Future[T] = {
    prepPost(data).process(Method.Post, f => f.map(resMap))
  }

  /**
    * Issue a non-blocking POST request
    * @param data The content to provide in the message
    * @param resMap A function to convert the HttpResponse to the desired response type
    */
  def postFuture[T](data: String = "", resMap: Response => T = FHttpRequest.asString): Future[T] = {
    postFuture(data.getBytes(FHttpRequest.UTF_8), resMap)
  }

  /**
    * Issue a non-blocking multipart POST request
    * @param data The parts to provide in the multipart message
    * @param resMap A function to convert the HttpResponse to the desired response type
    */
  def postFuture[T](data: List[MultiPart], resMap: Response => T): Future[T] = {
    prepMultipart(data.map(toPart(_))).process(Method.Post, f => f.map(resMap))
  }

  /**
    * Issue a non-blocking PUT request
    * @param data The content to provide in the message
    * @param resMap A function to convert the HttpResponse to the desired response type
    */
  def putFuture[T](data: Array[Byte], resMap: Response => T): Future[T] = {
    prepPost(data).process(Method.Put, f => f.map(resMap))
  }

  /**
    * Issue a non-blocking PUT request
    * @param data The content to provide in the message
    * @param resMap A function to convert the HttpResponse to the desired response type
    */
  def putFuture[T](data: String = "", resMap: Response => T = FHttpRequest.asString): Future[T] = {
    putFuture(data.getBytes(FHttpRequest.UTF_8), resMap)
  }

  /**
    * Issue a non-blocking multipart POST request
    * @param data The parts to provide in the multipart message
    * @param resMap A function to convert the HttpResponse to the desired response type
    */
  def putFuture[T](data: List[MultiPart], resMap: Response => T): Future[T] = {
    prepMultipart(data.map(toPart(_))).process(Method.Put, f => f.map(resMap))
  }

  /**
    * Issue a non-blocking HEAD request
    * @param resMap a function to convert the HttpResponse to the desired response type
    */
  def headFuture[T](resMap: Response => T = FHttpRequest.asString): Future[T] = {
    process(Method.Head, f => f.map(resMap))
  }

  /**
    * Issue a non-blocking DELETE request
    * @param resMap a function to convert the HttpResponse to the desired response type
    */
  def deleteFuture[T](resMap: Response => T = FHttpRequest.asString): Future[T] = {
    process(Method.Delete, f => f.map(resMap))
  }

  // Blocking Option
  /**
    * Issue a blocking GET request
    * @param resMap a function to convert the HttpResponse to the desired response type
    */
  def getOption[T](resMap: Response => T = FHttpRequest.asString): Option[T] = {
    process(Method.Get, block andThen finishOpt).map(resMap)
  }

  /**
    * Issue a blocking POST request
    * @param data The content to provide in the message
    * @param resMap A function to convert the HttpResponse to the desired response type
    */
  def postOption[T](data: Array[Byte], resMap: Response => T): Option[T] = {
    prepPost(data).process(Method.Post, block andThen finishOpt).map(resMap)
  }

  /**
    * Issue a blocking POST request
    * @param data The content to provide in the message
    * @param resMap A function to convert the HttpResponse to the desired response type
    */
  def postOption[T](data: String = "", resMap: Response => T = FHttpRequest.asString): Option[T] = {
    postOption(data.getBytes(FHttpRequest.UTF_8), resMap)
  }

  /**
    * Issue blocking multipart POST request
    * @param data The parts to provide in the multipart message
    * @param resMap A function to convert the HttpResponse to the desired response type
    */
  def postOption[T](data: List[MultiPart], resMap: Response => T): Option[T] =
    prepMultipart(data.map(toPart(_))).process(Method.Post, block andThen finishOpt).map(resMap)

  /**
    * Issue a blocking PUT request
    * @param data The content to provide in the message
    * @param resMap A function to convert the HttpResponse to the desired response type
    */
  def putOption[T](data: Array[Byte], resMap: Response => T): Option[T] = {
    prepPost(data).process(Method.Put, block andThen finishOpt).map(resMap)
  }

  /**
    * Issue a blocking PUT request
    * @param data The content to provide in the message
    * @param resMap A function to convert the HttpResponse to the desired response type
    */
  def putOption[T](data: String = "", resMap: Response => T = FHttpRequest.asString): Option[T] = {
    putOption(data.getBytes(FHttpRequest.UTF_8), resMap)
  }

  /**
    * Issue blocking multipart PUT request
    * @param data The parts to provide in the multipart message
    * @param resMap A function to convert the HttpResponse to the desired response type
    */
  def putOption[T](data: List[MultiPart], resMap: Response => T): Option[T] = {
    prepMultipart(data.map(toPart(_))).process(Method.Put, block andThen finishOpt).map(resMap)
  }

  /**
    * Issue a blocking HEAD request
    * @param resMap a function to convert the HttpResponse to the desired response type
    */
  def headOption[T](resMap: Response => T = FHttpRequest.asString): Option[T] = {
    process(Method.Head, block andThen finishOpt).map(resMap)
  }

  /**
    * Issue a blocking DELETE request
    * @param resMap a function to convert the HttpResponse to the desired response type
    */
  def deleteOption[T](resMap: Response => T = FHttpRequest.asString): Option[T] = {
    process(Method.Delete, block andThen finishOpt).map(resMap)
  }

  // Blocking Throw
  /**
    * Issue a blocking GET request and throw on failure
    * @param resMap a function to convert the HttpResponse to the desired response type
    */
  def get_![T](resMap: Response => T = FHttpRequest.asString): T = {
    process(Method.Get, block andThen finishBang andThen resMap)
  }

  /**
    * Issue a blocking POST request and throw on failure
    * @param data The content to provide in the message
    * @param resMap A function to convert the HttpResponse to the desired response type
    */
  def post_![T](data: Array[Byte], resMap: Response => T): T = {
    prepPost(data).process(Method.Post, block andThen finishBang andThen resMap)
  }

  /**
    * Issue a blocking POST request and throw on failure
    * @param data The content to provide in the message
    * @param resMap A function to convert the HttpResponse to the desired response type
    */
  def post_![T](data: String = "", resMap: Response => T = FHttpRequest.asString): T = {
    post_!(data.getBytes(FHttpRequest.UTF_8), resMap)
  }

  /**
    * Issue blocking multipart POST request and throw on failure
    * @param data The parts to provide in the multipart message
    * @param resMap A function to convert the HttpResponse to the desired response type
    */
  def post_![T](data: List[MultiPart], resMap: Response => T): T = {
    prepMultipart(data.map(toPart(_))).process(Method.Post, block andThen finishBang andThen resMap)
  }

  /**
    * Issue a blocking PUT request and throw on failure
    * @param data The content to provide in the message
    * @param resMap A function to convert the HttpResponse to the desired response type
    */
  def put_![T](data: Array[Byte], resMap: Response => T): T = {
    prepPost(data).process(Method.Put, block andThen finishBang andThen resMap)
  }

  /**
    * Issue a blocking PUT request and throw on failure
    * @param data The content to provide in the message
    * @param resMap A function to convert the HttpResponse to the desired response type
    */
  def put_![T](data: String = "", resMap: Response => T = FHttpRequest.asString): T = {
    put_!(data.getBytes(FHttpRequest.UTF_8), resMap)
  }

  /**
    * Issue blocking multipart PUT request and throw on failure
    * @param data The parts to provide in the multipart message
    * @param resMap A function to convert the HttpResponse to the desired response type
    */
  def put_![T](data: List[MultiPart], resMap: Response => T): T = {
    prepMultipart(data.map(toPart(_))).process(Method.Put, block andThen finishBang andThen resMap)
  }

  /**
    * Issue a blocking HEAD request and throw on failure
    * @param resMap a function to convert the HttpResponse to the desired response type
    */
  def head_![T](resMap: Response => T = FHttpRequest.asString): T = {
    process(Method.Head, block andThen finishBang andThen resMap)
  }

  /**
    * Issue a blocking DELETE request and throw on failure
    * @param resMap a function to convert the HttpResponse to the desired response type
    */
  def delete_![T](resMap: Response => T = FHttpRequest.asString): T = {
    process(Method.Delete, block andThen finishBang andThen resMap)
  }

  // Request issuing internals

  protected def content(data: Array[Byte]): FHttpRequest = {
    option((r: Message) => {
      r.content = Buf.ByteArray.Owned(data)
      r.contentLength = data.length
    })
  }

  protected def process[T](method: Method, processor: Future[Response] => T): T = {
    val uriObj = new java.net.URI(uri)
    val request = Request(Version.Http11, method, uri.substring(uri.indexOf(uriObj.getRawPath)))
    options.reverse.foreach(callback => callback(request))
    processor(service(request))
  }

  protected val block: Future[Response] => ClientResponseOrException =
    responseFuture => {
      try {
        val r = Await.result(responseFuture)
        ClientResponse[Response](r)
      } catch {
        case e: Exception => {
          // wrap the exception in a ClientException
          ClientException(e)
        }
      }
    }

  protected val finishBang: ClientResponseOrException => Response = _ match {
    case ClientResponse(response: Response) => response
    case ClientException(e: DeferredStackTrace) => throw e.withNewStackTrace()
    case ClientException(e) =>
      e match {
        case ns: NoStackTrace => {
          ns.setStackTrace(new Throwable().getStackTrace)
          throw ns
        }
        case s => throw s.fillInStackTrace
      }
  }

  protected val finishOpt: ClientResponseOrException => Option[Response] = _ match {
    case ClientResponse(response: Response) => Some(response)
    case _ => None
  }

  protected def prepPost(data: Array[Byte]) = {
    // If there is no data, but params,
    // then the params are the data
    if (data.length == 0 && hasParams) {
      val qDec = new QueryStringDecoder(uri)
      FHttpRequest(client, qDec.getPath, traceName, service, options)
        .contentType(FHttpRequest.PARAM_TYPE)
        .prepData(uri.substring(qDec.getPath.length + 1).getBytes(FHttpRequest.UTF_8))
    } else {
      prepData(data)
    }
  }

  protected def toPart(part: MultiPart) = {
    new FilePart(part.name, new ByteArrayPartSource(part.filename, part.data), part.mime, FHttpRequest.UTF_8.name)
  }

  protected def prepMultipart(parts: List[Part]): FHttpRequest = {
    if (hasParams) {
      val qDec = new QueryStringDecoder(uri)
      return FHttpRequest(client, qDec.getPath, traceName, service, options)
        .prepMultipart(paramList.map(p => new StringPart(p._1, p._2, FHttpRequest.UTF_8.name)) ::: parts)
    }
    val mpr = new MultipartRequestEntity(parts.toArray, FHttpRequest.MULTIPART_PARAMS)
    val request = RequestBuilder()
    val os = new ChannelBufferOutputStream(
      new DefaultChannelConfig().getBufferFactory
        .getBuffer(mpr.getContentLength.toInt)
    )
    mpr.writeRequest(os)
    prepData(os.buffer.toByteBuffer.array)
      .contentType("multipart/form-data; boundary=" + FHttpRequest.BOUNDARY)
      .headers("MIME-Version" -> "1.0")

  }

  protected def prepData(data: Array[Byte]) = {
    content(data)
  }
  protected def base64(bytes: Array[Byte]) = new String(Base64.encodeBase64(bytes))

  protected def base64(in: String): String = base64(in.getBytes(FHttpRequest.UTF_8))

}

trait DeferredStackTrace extends RuntimeException {
  override def fillInStackTrace = this

  def withNewStackTrace() = {
    super.fillInStackTrace()
    this
  }
}

case class HttpStatusException(
  code: Int,
  reason: String,
  response: Response
) extends RuntimeException
  with DeferredStackTrace {
  var clientId = ""
  def addName(name: String) = {
    clientId = " in " + name
    this
  }

  def asString: String = FHttpRequest.asString(response)

  override def getMessage: String = {
    "HttpStatusException%s: Code: %d Reason: %s Body: %s".format(clientId, code, reason, asString)
  }
}

abstract class ClientResponseOrException
case class ClientResponse[T](response: T) extends ClientResponseOrException
case class ClientException(exception: Throwable) extends ClientResponseOrException

object MultiPart {
  def apply(name: String, filename: String, mime: String, data: String): MultiPart = {
    MultiPart(name, filename, mime, data.getBytes(FHttpRequest.UTF_8))
  }
}

case class MultiPart(name: String, filename: String, mime: String, data: Array[Byte])
