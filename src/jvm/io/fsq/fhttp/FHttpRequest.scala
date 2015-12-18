// Copyright 2011 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.fhttp

import com.twitter.conversions.time._
import com.twitter.finagle.{Filter, NoStacktrace, Service, SimpleFilter}
import com.twitter.finagle.service.TimeoutFilter
import com.twitter.util.{Await, Future}
import java.nio.charset.Charset
import org.apache.commons.codec.binary.Base64
import org.apache.commons.httpclient.methods.multipart._
import org.apache.commons.httpclient.params.HttpMethodParams
import org.jboss.netty.buffer.{ChannelBuffer, ChannelBufferInputStream, ChannelBufferOutputStream, ChannelBuffers}
import org.jboss.netty.channel.DefaultChannelConfig
import org.jboss.netty.handler.codec.http._
import scala.collection.JavaConverters._

object FHttpRequest {
  type HttpOption = HttpMessage => Unit

  val UTF_8 = Charset.forName("UTF-8")
  val PARAM_TYPE = "application/x-www-form-urlencoded"
  val BOUNDARY = "gc0pMUlT1B0uNdArYc0p"
  val MULTIPART_PARAMS = {
    val p = new HttpMethodParams()
    p.setParameter(HttpMethodParams.MULTIPART_BOUNDARY, BOUNDARY)
    p
  }

  def apply(client: FHttpClient, uri: String): FHttpRequest =
    FHttpRequest(client,
             uri,
             "",
             client.service, Nil).headers("Host" -> client.firstHostPort)

  // HttpResponse conversions

  /**
   * Extracts the raw contents as a ChannelBuffer
   */
  def asContentsBuffer: HttpResponse => ChannelBuffer = res => res.getContent

  /**
   * Extracts the contents as a String (default for all request methods)
   */
  def asString: HttpResponse => String = res => res.getContent.toString(UTF_8)

  /**
   * Extracts the contents as a byte array
   */
  def asBytes: HttpResponse => Array[Byte] = res => {
    val buffer = res.getContent.toByteBuffer
    val bytes = new Array[Byte](buffer.remaining)
    buffer.get(bytes, buffer.arrayOffset, buffer.remaining)
    bytes
  }

  /**
   * Extracts the contents as an input stream
   */
  def asInputStream: HttpResponse => java.io.InputStream = res => new ChannelBufferInputStream(res.getContent)

  /**
   * Extracts the contents as XML
   */
  def asXml: HttpResponse => scala.xml.Elem = res => scala.xml.XML.load(asInputStream(res))

  def asParams: HttpResponse => List[(String, String)] = res => {
    val params = new QueryStringDecoder("/null?" + asString(res)).getParameters
    params.asScala.map(kv => kv._2.asScala.toList.map(v => (kv._1, v))).flatten.toList
  }

  def asParamMap: HttpResponse => Map[String, String] = res => Map(asParams(res):_*)

  /**
   * Extracts the contents as an oauth Token
   */
  def asOAuth1Token: HttpResponse => Token = res => {
    val params = asParamMap(res)
    Token(params("oauth_token"), params("oauth_token_secret"))
  }

  /**
   * Returns the original response (convenient because asString is the default extraction type)
   */
  def asHttpResponse: HttpResponse => HttpResponse = res => res


  /**
   * A filter for printing the request and reponse as it passes through service stack
   */
  object debugFilter extends SimpleFilter[HttpRequest, HttpResponse] {
    val printMessage: HttpOption = r => {
      println(r)
      if(r.getContent != ChannelBuffers.EMPTY_BUFFER) {
        println("--CONTENT--")
        println(r.getContent.toString(UTF_8))
      }
    }

    def apply(request: HttpRequest, service: Service[HttpRequest, HttpResponse]) = {
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
case class FHttpRequest ( client: FHttpClient,
                          uri: String,
                          traceName: String,
                          service: Service[HttpRequest, HttpResponse],
                          options: List[FHttpRequest.HttpOption]) {

  /**
   * Adds parameters to the request
   * @param p The parameters to add
   */
  def params(p: (String, String)*): FHttpRequest = params(p.toList)

  /**
   * Adds parameters to the request
   * @param p The parameters to add
   */
  def params(p: List[(String, String)]): FHttpRequest = {
    // QueryStringEncoder doesn't handle existing params well, so decode first
    val qOld = new QueryStringDecoder(uri)
    val qEnc = new QueryStringEncoder(qOld.getPath)
    (paramList ++ p).foreach(kv => qEnc.addParam(kv._1, kv._2))
    FHttpRequest(client, qEnc.toString, traceName, service, options)
  }

  /**
   * Sets the content type header of the request
   * @param t The content type
   */
  def contentType(t: String): FHttpRequest = headers(HttpHeaders.Names.CONTENT_TYPE -> t)

  /**
   * Sets the keep alive header of the request (useful for HTTP 1.0)
   */
  def keepAlive(isKeepAlive: Boolean): FHttpRequest =
    option((r: HttpMessage) => HttpHeaders.setKeepAlive(r, isKeepAlive))

  /**
   * Adds headers to the request
   * @param p The headers to add
   */
  def headers(h: (String, String)*): FHttpRequest = headers(h.toList)

  /**
   * Adds headers to the request
   * @param p The headers to add
   */
  def headers(h: List[(String, String)]): FHttpRequest =
    option((r: HttpMessage) => h.foreach(kv => r.headers.add(kv._1, kv._2)))

  /**
   * Adds a basic http auth header to the request
   * @param user The username
   * @param password The password
   */
  def auth(user: String, password: String): FHttpRequest =
    headers("Authorization" -> ("Basic " + base64(user + ":" + password)))

  /**
   * Adds a filter to the service
   * @param f the filter to add to the stack
   */
  def filter(f: Filter[HttpRequest, HttpResponse, HttpRequest, HttpResponse]): FHttpRequest =
    FHttpRequest(client, uri, traceName, f andThen service, options)

  /**
   * Adds a request timeout (using the TimeoutFilter) to the stack.  Applies blocking or future responses.
   * @param millis The number of milliseconds to wait
   */
  def timeout(millis: Int):FHttpRequest =
    filter(new TimeoutFilter(millis.milliseconds, FTimer.finagleTimer))

  /**
   * Adds a debugging filter to print the request and the response.  Can be added multiple times
   * to inspect the filter transformations
   */
  def debug() =
    filter(FHttpRequest.debugFilter)

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
  def option(o: FHttpRequest.HttpOption): FHttpRequest =
    FHttpRequest(client, uri, traceName, service, o :: options)

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
  def oauth(consumer: Token, token: Token, verifier: String): FHttpRequest =
    oauth(consumer, Some(token), Some(verifier))

  protected def oauth(consumer: Token, token: Option[Token], verifier: Option[String]): FHttpRequest = {
    val hostPort = client.firstHostPort.split(":", 2) match {
      case Array(k,v) => Some(k, v)
      case _ => None
    }

    filter(new OAuth1Filter(client.scheme,
                            hostPort.get._1,
                            hostPort.get._2.toInt,
                            consumer,
                            token,
                            verifier))
  }

  def hasParams: Boolean = uri.indexOf('?') != -1

  def paramList: List[(String, String)] =
    new QueryStringDecoder(uri).getParameters.asScala.map(kv => kv._2.asScala.toList.map(v=>(kv._1, v))).flatten.toList


  // Response retrieval methods

  /**
   * Issue a non-blocking GET request
   * @param resMap a function to convert the HttpResponse to the desired response type
   */
  def getFuture[T] (resMap: HttpResponse => T = FHttpRequest.asString): Future[T] =
    process(HttpMethod.GET, f => f.map(resMap))

  /**
   * Issue a non-blocking POST request
   * @param data The content to provide in the message
   * @param resMap A function to convert the HttpResponse to the desired response type
   */
  def postFuture[T] (data: Array[Byte], resMap: HttpResponse => T): Future[T] =
    prepPost(data).process(HttpMethod.POST, f => f.map(resMap))

  /**
   * Issue a non-blocking POST request
   * @param data The content to provide in the message
   * @param resMap A function to convert the HttpResponse to the desired response type
   */
  def postFuture[T] (data: String = "", resMap: HttpResponse => T = FHttpRequest.asString): Future[T] =
    postFuture(data.getBytes(FHttpRequest.UTF_8), resMap)

  /**
   * Issue a non-blocking multipart POST request
   * @param data The parts to provide in the multipart message
   * @param resMap A function to convert the HttpResponse to the desired response type
   */
  def postFuture[T] (data: List[MultiPart], resMap: HttpResponse => T): Future[T] =
    prepMultipart(data.map(toPart(_))).process(HttpMethod.POST, f => f.map(resMap))

  /**
   * Issue a non-blocking PUT request
   * @param data The content to provide in the message
   * @param resMap A function to convert the HttpResponse to the desired response type
   */
  def putFuture[T] (data: Array[Byte], resMap: HttpResponse => T): Future[T] =
    prepPost(data).process(HttpMethod.PUT, f => f.map(resMap))

  /**
   * Issue a non-blocking PUT request
   * @param data The content to provide in the message
   * @param resMap A function to convert the HttpResponse to the desired response type
   */
  def putFuture[T] (data: String = "", resMap: HttpResponse => T = FHttpRequest.asString): Future[T] =
    putFuture(data.getBytes(FHttpRequest.UTF_8), resMap)

  /**
   * Issue a non-blocking multipart POST request
   * @param data The parts to provide in the multipart message
   * @param resMap A function to convert the HttpResponse to the desired response type
   */
  def putFuture[T] (data: List[MultiPart], resMap: HttpResponse => T): Future[T] =
    prepMultipart(data.map(toPart(_))).process(HttpMethod.PUT, f => f.map(resMap))

  /**
   * Issue a non-blocking HEAD request
   * @param resMap a function to convert the HttpResponse to the desired response type
   */
  def headFuture[T] (resMap: HttpResponse => T = FHttpRequest.asString): Future[T] =
    process(HttpMethod.HEAD, f => f.map(resMap))

  /**
   * Issue a non-blocking DELETE request
   * @param resMap a function to convert the HttpResponse to the desired response type
   */
  def deleteFuture[T] (resMap: HttpResponse => T = FHttpRequest.asString): Future[T] =
    process(HttpMethod.DELETE, f => f.map(resMap))


  // Blocking Option
  /**
   * Issue a blocking GET request
   * @param resMap a function to convert the HttpResponse to the desired response type
   */
  def getOption[T] (resMap: HttpResponse => T = FHttpRequest.asString): Option[T] =
    process(HttpMethod.GET, block andThen finishOpt).map(resMap)

  /**
   * Issue a blocking POST request
   * @param data The content to provide in the message
   * @param resMap A function to convert the HttpResponse to the desired response type
   */
  def postOption[T] (data: Array[Byte], resMap: HttpResponse => T): Option[T] =
    prepPost(data).process(HttpMethod.POST, block andThen finishOpt).map(resMap)

  /**
   * Issue a blocking POST request
   * @param data The content to provide in the message
   * @param resMap A function to convert the HttpResponse to the desired response type
   */
  def postOption[T] (data: String = "", resMap: HttpResponse => T = FHttpRequest.asString): Option[T] =
    postOption(data.getBytes(FHttpRequest.UTF_8), resMap)

  /**
   * Issue blocking multipart POST request
   * @param data The parts to provide in the multipart message
   * @param resMap A function to convert the HttpResponse to the desired response type
   */
  def postOption[T] (data: List[MultiPart], resMap: HttpResponse => T): Option[T] =
    prepMultipart(data.map(toPart(_))).process(HttpMethod.POST, block andThen finishOpt).map(resMap)

  /**
   * Issue a blocking PUT request
   * @param data The content to provide in the message
   * @param resMap A function to convert the HttpResponse to the desired response type
   */
  def putOption[T] (data: Array[Byte], resMap: HttpResponse => T): Option[T] =
    prepPost(data).process(HttpMethod.PUT, block andThen finishOpt).map(resMap)

  /**
   * Issue a blocking PUT request
   * @param data The content to provide in the message
   * @param resMap A function to convert the HttpResponse to the desired response type
   */
  def putOption[T] (data: String = "", resMap: HttpResponse => T = FHttpRequest.asString): Option[T] =
    putOption(data.getBytes(FHttpRequest.UTF_8), resMap)

  /**
   * Issue blocking multipart PUT request
   * @param data The parts to provide in the multipart message
   * @param resMap A function to convert the HttpResponse to the desired response type
   */
  def putOption[T] (data: List[MultiPart], resMap: HttpResponse => T): Option[T] =
    prepMultipart(data.map(toPart(_))).process(HttpMethod.PUT, block andThen finishOpt).map(resMap)

  /**
   * Issue a blocking HEAD request
   * @param resMap a function to convert the HttpResponse to the desired response type
   */
  def headOption[T] (resMap: HttpResponse => T = FHttpRequest.asString): Option[T] =
    process(HttpMethod.HEAD, block andThen finishOpt).map(resMap)

  /**
   * Issue a blocking DELETE request
   * @param resMap a function to convert the HttpResponse to the desired response type
   */
  def deleteOption[T] (resMap: HttpResponse => T = FHttpRequest.asString): Option[T] =
    process(HttpMethod.DELETE, block andThen finishOpt).map(resMap)

  // Blocking Throw
  /**
   * Issue a blocking GET request and throw on failure
   * @param resMap a function to convert the HttpResponse to the desired response type
   */
  def get_![T] (resMap: HttpResponse => T = FHttpRequest.asString): T =
    process(HttpMethod.GET, block andThen finishBang andThen resMap)

  /**
   * Issue a blocking POST request and throw on failure
   * @param data The content to provide in the message
   * @param resMap A function to convert the HttpResponse to the desired response type
   */
  def post_![T] (data: Array[Byte], resMap: HttpResponse => T): T =
    prepPost(data).process(HttpMethod.POST, block andThen finishBang andThen resMap)

  /**
   * Issue a blocking POST request and throw on failure
   * @param data The content to provide in the message
   * @param resMap A function to convert the HttpResponse to the desired response type
   */
  def post_![T] (data: String = "", resMap: HttpResponse => T = FHttpRequest.asString): T =
    post_!(data.getBytes(FHttpRequest.UTF_8), resMap)

  /**
   * Issue blocking multipart POST request and throw on failure
   * @param data The parts to provide in the multipart message
   * @param resMap A function to convert the HttpResponse to the desired response type
   */
  def post_![T] (data: List[MultiPart], resMap: HttpResponse => T): T =
    prepMultipart(data.map(toPart(_))).process(HttpMethod.POST, block andThen finishBang andThen resMap)

  /**
   * Issue a blocking PUT request and throw on failure
   * @param data The content to provide in the message
   * @param resMap A function to convert the HttpResponse to the desired response type
   */
  def put_![T] (data: Array[Byte], resMap: HttpResponse => T): T =
    prepPost(data).process(HttpMethod.PUT, block andThen finishBang andThen resMap)

  /**
   * Issue a blocking PUT request and throw on failure
   * @param data The content to provide in the message
   * @param resMap A function to convert the HttpResponse to the desired response type
   */
  def put_![T] (data: String = "", resMap: HttpResponse => T = FHttpRequest.asString): T =
    put_!(data.getBytes(FHttpRequest.UTF_8), resMap)

  /**
   * Issue blocking multipart PUT request and throw on failure
   * @param data The parts to provide in the multipart message
   * @param resMap A function to convert the HttpResponse to the desired response type
   */
  def put_![T] (data: List[MultiPart], resMap: HttpResponse => T): T =
    prepMultipart(data.map(toPart(_))).process(HttpMethod.PUT, block andThen finishBang andThen resMap)

  /**
   * Issue a blocking HEAD request and throw on failure
   * @param resMap a function to convert the HttpResponse to the desired response type
   */
  def head_![T] (resMap: HttpResponse => T = FHttpRequest.asString): T =
    process(HttpMethod.HEAD, block andThen finishBang andThen resMap)

  /**
   * Issue a blocking DELETE request and throw on failure
   * @param resMap a function to convert the HttpResponse to the desired response type
   */
  def delete_![T] (resMap: HttpResponse => T = FHttpRequest.asString): T =
    process(HttpMethod.DELETE, block andThen finishBang andThen resMap)

  // Request issuing internals

  protected def content(data: Array[Byte]): FHttpRequest = {
    option((r: HttpMessage) => {
      r.setContent(new DefaultChannelConfig().getBufferFactory.getBuffer(data, 0, data.length))
      HttpHeaders.setContentLength(r, data.length)
    })
  }

  protected def process[T] (method: HttpMethod, processor: Future[HttpResponse] => T): T = {
    val uriObj = new java.net.URI(uri)
    val req =
      new DefaultHttpRequest(
        HttpVersion.HTTP_1_1,
        method,
        uri.substring(uri.indexOf(uriObj.getRawPath))
      )
    options.reverse.foreach(_(req))
    processor(service(req))
  }

  protected val block: Future[HttpResponse] => ClientResponseOrException =
    responseFuture => {
      try{
        val r = Await.result(responseFuture)
        ClientResponse[HttpResponse](r)
      } catch {
        case e: Exception => {
          // wrap the exception in a ClientException
          ClientException(e)
        }
      }
    }

  protected val finishBang: ClientResponseOrException => HttpResponse = _ match {
    case ClientResponse(response: HttpResponse) => response
    case ClientException(e: DeferredStackTrace) => throw e.withNewStackTrace()
    case ClientException(e) => e match {
      case ns: NoStacktrace => {
        ns.setStackTrace(new Throwable().getStackTrace)
        throw ns
      }
      case s => throw s.fillInStackTrace
    }
  }

  protected val finishOpt: ClientResponseOrException => Option[HttpResponse] = _ match {
    case ClientResponse(response: HttpResponse) => Some(response)
    case _ => None
  }

  protected def prepPost(data: Array[Byte]) = {
    // If there is no data, but params,
    // then the params are the data
    if(data.length == 0 && hasParams) {
      val qDec = new QueryStringDecoder(uri)
      FHttpRequest(client, qDec.getPath, traceName, service, options)
        .contentType(FHttpRequest.PARAM_TYPE)
        .prepData(uri.substring(qDec.getPath.length + 1).getBytes(FHttpRequest.UTF_8))
    } else {
      prepData(data)
    }
  }

  protected def toPart(part: MultiPart) = {
    new FilePart(part.name,
      new ByteArrayPartSource(part.filename, part.data),
      part.mime,
      FHttpRequest.UTF_8.name)
  }

  protected def prepMultipart(parts: List[Part]): FHttpRequest = {
    if(hasParams) {
      val qDec = new QueryStringDecoder(uri)
      return FHttpRequest(client, qDec.getPath, traceName, service, options)
        .prepMultipart(paramList.map(p =>
           new StringPart(p._1, p._2, FHttpRequest.UTF_8.name)) ::: parts)
    }
    val mpr = new MultipartRequestEntity(parts.toArray, FHttpRequest.MULTIPART_PARAMS)
    val os = new ChannelBufferOutputStream( new DefaultChannelConfig()
                                            .getBufferFactory
                                            .getBuffer(mpr.getContentLength.toInt))
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

case class HttpStatusException(code: Int, reason: String, response: HttpResponse)
  extends RuntimeException with DeferredStackTrace {
  var clientId = ""
  def addName(name: String) = {
    clientId = " in " + name
    this
  }

  def asString: String = FHttpRequest.asString(response)

  override def getMessage(): String = {
    "HttpStatusException%s: Code: %d Reason: %s Body: %s" format(clientId, code, reason, asString)
  }
}


abstract class ClientResponseOrException
case class ClientResponse[T](response: T) extends ClientResponseOrException
case class ClientException(exception: Throwable) extends ClientResponseOrException


object MultiPart {
  def apply(name: String, filename: String, mime: String, data: String): MultiPart =
    MultiPart(name, filename, mime, data.getBytes(FHttpRequest.UTF_8))
}

case class MultiPart(val name: String, val filename: String, val mime: String, val data: Array[Byte])
