// Copyright 2011 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.fhttp

import com.twitter.finagle.{Service, SimpleFilter}
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.apache.commons.codec.binary.Base64
import org.jboss.netty.handler.codec.http._
import scala.collection.JavaConverters._

case class Token(key: String, secret: String)

class OAuth1Filter (scheme: String,
                    host: String,
                    port: Int,
                    consumer: Token,
                    token: Option[Token],
                    verifier: Option[String],
                    // parameterize clock/nonce for testing
                    clock: Function0[Long] = () => System.currentTimeMillis,
                    nonce: Function0[String] = () => UUID.randomUUID.toString)
  extends SimpleFilter[HttpRequest, HttpResponse] {

  val MAC = "HmacSHA1"
  val PostParamsType = "application/x-www-form-urlencoded"

  def apply(request: HttpRequest, service: Service[HttpRequest, HttpResponse]) = {

    // Build parameters
    val time = clock()
    var oauthParams: List[(String, String)] =
      ("oauth_timestamp", (time / 1000).toString) ::
      ("oauth_nonce", nonce()) ::
      ("oauth_version", "1.0") ::
      ("oauth_consumer_key", consumer.key) ::
      ("oauth_signature_method", "HMAC-SHA1") :: Nil


    token.foreach{t =>
      oauthParams ::= ("oauth_token", t.key)
    }

    verifier.foreach{v =>
      oauthParams ::= ("oauth_verifier", v)
    }

    val portString = (port, scheme.toLowerCase) match {
      case (80, "http") => ""
      case (443, "https") => ""
      case _ => ":" + port
    }


    val (path, paramDecoder) = request.getMethod match {
      case HttpMethod.GET =>
        val qd = new QueryStringDecoder(request.getUri)
        (qd.getPath, qd)
      case HttpMethod.POST if (request.headers.get(HttpHeaders.Names.CONTENT_TYPE) == PostParamsType) =>
        (
          request.getUri,
          new QueryStringDecoder("?" + request.getContent.toString(FHttpRequest.UTF_8))
        )
      case _ => (request.getUri, new QueryStringDecoder("?"))
    }

    val reqParams: List[(String, String)] = paramDecoder.getParameters.asScala.toList.flatMap {
      case (key, values) => values.asScala.toList.map(v => (key, v))
    }

    val sigParams = reqParams ::: oauthParams

    // Normalize
    val normParams = percentEncode(sigParams).sortWith(_ < _).mkString("&")
    val normUrl = (scheme + "://" + host + portString + path).toLowerCase
    val normReq = List(request.getMethod.getName.toUpperCase, normUrl, normParams).map(percentEncode).mkString("&")

    // Sign
    val normKey = percentEncode(consumer.secret) + "&" + token.map(t => percentEncode(t.secret)).getOrElse("")
    val key = new SecretKeySpec(normKey.getBytes(FHttpRequest.UTF_8), MAC)
    val mac = Mac.getInstance(MAC)
    mac.init(key)
    val signature: String = new String( Base64.encodeBase64(mac.doFinal(normReq.getBytes(FHttpRequest.UTF_8))),
                                        FHttpRequest.UTF_8)

    oauthParams ::= ("oauth_signature", signature)

    // finally, add the header
    request.headers.add("Authorization", "OAuth " +
      oauthParams.map(p => p._1 + "=\"" + percentEncode(p._2) + "\"").mkString(","))

    service(request)
  }

  private def percentEncode(params: List[(String,String)]): List[String] =
    params.map(p => percentEncode(p._1) + "=" + percentEncode(p._2))

  private def percentEncode(s: String): String =
    java.net.URLEncoder.encode(s, "utf-8").replace("+", "%20").replace("*", "%2A").replace("%7E", "~")
}

