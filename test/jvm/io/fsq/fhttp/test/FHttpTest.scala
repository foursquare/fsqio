// Copyright 2011 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.fhttp.test

import com.twitter.conversions.time._
import com.twitter.finagle.{Service, TimeoutException}
import com.twitter.finagle.builder.{ClientBuilder, ServerBuilder}
import com.twitter.finagle.http.Http
import com.twitter.util.{Await, Future}
import io.fsq.fhttp.{FHttpClient, FHttpRequest, HttpStatusException, MultiPart, OAuth1Filter, Token}
import java.net.InetSocketAddress
import org.jboss.netty.channel.DefaultChannelConfig
import org.jboss.netty.handler.codec.http.HttpResponseStatus._
import org.jboss.netty.handler.codec.http._
import org.junit.{After, Before, Ignore, Test}
import org.junit.Assert._
import scala.collection.JavaConverters._

object FHttpRequestValidators {
  def matchesHeader(key: String, value: String): FHttpRequest.HttpOption = r => {
    assertNotNull(r.headers.getAll(key))
    assertEquals(r.headers.getAll(key).asScala.mkString("|"), value)
  }

  def matchesContent(content: String, length: Int): FHttpRequest.HttpOption = r => {
    matchesHeader(HttpHeaders.Names.CONTENT_LENGTH, length.toString)(r)
    assertEquals(r.getContent.toString(FHttpRequest.UTF_8), content)
  }

  def containsContent(content: String): FHttpRequest.HttpOption = r => {
    val actual = r.getContent.toString(FHttpRequest.UTF_8)
    assertTrue(actual.contains(content))
  }
}

class FHttpTestHelper {
  var serverWaitMillis: Int = 0
  var responseTransforms: List[FHttpRequest.HttpOption] = Nil
  var requestValidators: List[FHttpRequest.HttpOption] = Nil
  var responseStatus = OK

  def reset(): Unit = {
    requestValidators = Nil
    responseTransforms = Nil
    responseStatus = OK
  }

  def serverResponse: HttpResponse = {
    val res = new DefaultHttpResponse(HttpVersion.HTTP_1_1, responseStatus)
    responseTransforms.reverse.foreach(_(res))
    res
  }

  val service: Service[HttpRequest, HttpResponse] = new Service[HttpRequest, HttpResponse] {
    def apply(request: HttpRequest) = {
      try {
        requestValidators.foreach(_(request))
        responseTransforms ::= {
          (r: HttpMessage) => {
            HttpHeaders.setContentLength(r, 0)
          }
        }

      } catch {
        case exc: AssertionError =>
          responseTransforms ::= {
            (r: HttpMessage) => {
              val data = exc.toString.getBytes(FHttpRequest.UTF_8)
              r.setContent(new DefaultChannelConfig().getBufferFactory.getBuffer(data, 0, data.length))
              HttpHeaders.setContentLength(r, data.length)
            }
          }
      }
      Thread.sleep(serverWaitMillis)
      Future(serverResponse)
    }
  }

  val server = ServerBuilder()
    .codec(Http())
    .bindTo(new InetSocketAddress("127.0.0.1", 0))  // 0 allocates an ephemeral port
    .name("HttpServer")
    .maxConcurrentRequests(20)
    .build(service)

  def boundPort: Int = server.boundAddress.asInstanceOf[InetSocketAddress].getPort
}

class FHttpClientTest {
  var helper: FHttpTestHelper = null
  var client: FHttpClient = null

  def buildOAuth1Filter(
    client: FHttpClient,
    consumer: Token,
    token: Option[Token],
    verifier: Option[String]): OAuth1Filter = {

    val hostPort = client.firstHostPort.split(":", 2) match {
      case Array(k,v) => Some(k, v)
      case _ => None
    }

    new OAuth1Filter(client.scheme,
      hostPort.get._1,
      hostPort.get._2.toInt,
      consumer,
      token,
      verifier,
      () => 0,
      () => "ceci n'est pas une nonce")
  }

  @Before
  def setupHelper(): Unit = {
    helper = new FHttpTestHelper
    client = new FHttpClient("test-client","localhost:" + helper.boundPort)
  }

  @After
  def teardownHelper(): Unit = {
    client.service.close()
    helper.server.close()
  }

  @Test
  def testRequestAddParams(): Unit = {
    val expected1 = "/test"
    val expected2 = expected1 + "?this=is%20silly&no=you%2Bare"
    val expected3 = expected2 + "&no=this_is"
    val req1 = client("/test")
    assertEquals(req1.uri, expected1)

    val req2 = req1.params("this"->"is silly","no"->"you+are")
    assertEquals(req2.uri, expected2)

    // params get appended if called again
    val req3 = req2.params("no"->"this_is")
    assertEquals(req3.uri, expected3)

    assertEquals(req3.params().uri, expected3)
  }

  @Test
  def testRequestAddHeaders(): Unit = {
    helper.requestValidators = FHttpRequestValidators.matchesHeader("name", "johng") ::
      FHttpRequestValidators.matchesHeader("Host", client.hostPort) :: Nil
    val req = FHttpRequest(client, "/test").headers("name"->"johng")
    val resErr = req.timeout(5000).get_!()
    resErr isEmpty

    // must match both
    helper.requestValidators ::= FHttpRequestValidators.matchesHeader("city", "ny")
    val req2 = req.headers("city"->"ny")
    val resErr2 = req2.timeout(5000).get_!()
    resErr2 isEmpty

    // adding a header with the same key appends, not replaces
    helper.requestValidators = FHttpRequestValidators.matchesHeader("city", "ny|sf") ::
                              helper.requestValidators.tail
    val req3 = req2.headers("city"->"sf")
    val res3 = req3.timeout(5000).get_!()
    assertEquals(res3, "")

    // adding a header with the same key appends, not replaces
    helper.requestValidators = FHttpRequestValidators.matchesHeader("Authorization", "Basic QWxhZGRpbjpvcGVuIHNlc2FtZQ==") ::
                              helper.requestValidators.tail
    val req4 = req3.auth("Aladdin", "open sesame")
    val res4 = req4.timeout(5000).get_!()
    assertEquals(res4, "")
  }

  @Test
  def testSetContent(): Unit = {
    helper.requestValidators = FHttpRequestValidators.matchesContent("hi", 2) :: Nil
    val req = FHttpRequest(client, "/test").timeout(5000).post_!("hi")
    assertEquals(req, "")

    // Empty
    helper.requestValidators = FHttpRequestValidators.matchesContent("", 0) :: Nil
    val reqEmpty = FHttpRequest(client, "/test").timeout(5000).post_!("")
    assertEquals(reqEmpty, "")

  }

  @Test
  def testSetMultipart(): Unit = {
    val xml = """
      <?xml version="1.0"?>
      <soap:Envelope xmlns:soap="http://www.w3.org/2003/05/soap-envelope">
        <soap:Header>
        </soap:Header>
        <soap:Body>
          <m:GetStockPrice xmlns:m="http://www.example.org/stock">
            <m:StockName>IBM</m:StockName>
          </m:GetStockPrice>
        </soap:Body>
      </soap:Envelope>
      """
    val xmlbytes = xml.getBytes(FHttpRequest.UTF_8)
    val part1 = MultiPart("soap", "soap.xml", "application/soap+xml", xmlbytes)
    val json = """ { "some": "json" }"""
    val jsonBytes = json.getBytes(FHttpRequest.UTF_8)
    val part2 = MultiPart("json", "some.json", "application/json", jsonBytes)

    helper.requestValidators =
      FHttpRequestValidators.containsContent("Content-Disposition: form-data; name=\"hi\"") ::
      FHttpRequestValidators.containsContent("you") ::
      FHttpRequestValidators.containsContent(xml) ::
      FHttpRequestValidators.containsContent(json) ::
      FHttpRequestValidators.matchesHeader(HttpHeaders.Names.CONTENT_LENGTH, "908") :: Nil

    val reqEmpty = FHttpRequest(client, "/test").params("hi"->"you")
      .timeout(5000)
      .post_!(part1 :: part2 :: Nil, FHttpRequest.asString)
    assertEquals(reqEmpty, "")
  }

  @Test
  def testExceptionOnNonOKCode(): Unit = {
    helper.responseStatus = NOT_FOUND
    try {
      val reqNotFound = FHttpRequest(client, "/notfound").timeout(5000).get_!()
      throw new Exception("wrong code")
    } catch {
      case HttpStatusException(code, reason, response) if (code == NOT_FOUND.getCode) => Unit
    }

  }

  @Test
  def testExceptionOnTimeout(): Unit = {
    helper.serverWaitMillis = 10
    try {
      val reqTimedOut = FHttpRequest(client, "/timeout").timeout(1).get_!()
    } catch {
      case e: TimeoutException => Unit
    }
  }

  @Test
  def testFutureTimeout(): Unit = {
    helper.serverWaitMillis = 100
    var gotResult = false
    val f = FHttpRequest(client, "/future0").timeout(1).getFuture() onSuccess {
      r => gotResult = true
    } onFailure {
      e => gotResult = true
    }
    while (!gotResult) {
      Thread.sleep(10)
    }
    try {
      val r = Await.result(f)
      throw new Exception("should have timed out but got " + r)
    } catch {
      case e: TimeoutException => Unit
    }
  }


  @Test
  def testFutureResult(): Unit = {
    var r1 = "not set"
    var r2 = -1
    FHttpRequest(client, "/future1").timeout(5000).getFuture() onSuccess {
      r => r1 = r
    } onFailure {
      e => throw new Exception(e)
    }


    //asBytes
    FHttpRequest(client, "/future2").timeout(5000).getFuture(FHttpRequest.asBytes) onSuccess {
      r => r2 = r.length
    } onFailure {
      e => throw new Exception(e)
    }
    while( r1 == "not set" || r2 < 0) {
      Thread.sleep(10)
    }

    assertEquals(r1, "")
    assertEquals(r2, 0)
  }

  @Test
  def testLBHostHeaderUsesFirstHost(): Unit = {
    val port = client.firstHostPort.split(":",2)(1)
    val client2 = new FHttpClient("test-client-2", "localhost:" + port + ",127.0.0.1:" + port)
    helper.requestValidators = List(FHttpRequestValidators.matchesHeader("Host", "localhost:" + port))
    assertEquals(client2("/test").get_!(), "")
    client2.release()

    val client3 = new FHttpClient("test-client-2", "127.0.0.1:" + port + ",localhost:" + port)
    helper.requestValidators = List(FHttpRequestValidators.matchesHeader("Host", "127.0.0.1:" + port))
    assertEquals(client3("/test").get_!(), "")
    client3.release()
  }


  @Ignore("TODO: Figure out how we want to handle the external requests that these make")
  @Test
  def testOauthFlowGetPost(): Unit = {
    def testFlow(usePost: Boolean) {
      import io.fsq.fhttp.FHttpRequest.asOAuth1Token
      val clientOA =
        new FHttpClient(
          "oauth",
          "oauthbin.appspot.com:80",
          (ClientBuilder()
            .codec(Http())
            .hostConnectionLimit(1))
            .tcpConnectTimeout(1.seconds))
      val consumer = Token("key", "secret")

      // Get the request token
      val token = {
        val tkReq = clientOA("/v1/request-token").oauth(consumer)
        if(usePost) tkReq.post_!("", asOAuth1Token) else tkReq.get_!(asOAuth1Token)
      }

      // Get the access token
      val accessToken = {
        val atReq = clientOA("/v1/access-token").oauth(consumer, token)
        if(usePost) atReq.post_!("", asOAuth1Token) else atReq.get_!(asOAuth1Token)
      }

      // Try some queries
      val testParamsRes = {
        val testReq = clientOA("/v1/echo").params("k1"->"v1", "k2"->"v2", "callback" -> "http://example.com/?p1=v1&p2=v2")
          .oauth(consumer, accessToken)
        if(usePost) testReq.post_!() else testReq.get_!()
      }
      assertEquals(testParamsRes, "k1=v1&k2=v2&callback=http%3A%2F%2Fexample.com%2F%3Fp1%3Dv1%26p2%3Dv2")
    }

    testFlow(false)
    testFlow(true)
  }

  @Ignore("TODO: Figure out how we want to handle the external requests that these make")
  @Test
  def testOAuthSigning(): Unit = {
    val consumer = Token("key", "secret")
    val oauthFilter = buildOAuth1Filter(client, consumer, None, None)
    val expected = """OAuth oauth_signature="lAkLnsPI449AfTp7yuKJTD7olW8%3D",oauth_timestamp="0",oauth_nonce="ceci%20n%27est%20pas%20une%20nonce",oauth_version="1.0",oauth_consumer_key="key",oauth_signature_method="HMAC-SHA1""""
    helper.requestValidators = List(FHttpRequestValidators.matchesHeader("Authorization", expected))
    val res = FHttpRequest(client, "/request_token")
      .params("callback" -> "http://example.com/callback?some=param&someOther=param")
      .filter(oauthFilter)
      .timeout(5000).get_!()
    assertEquals(res, "")
  }

  @Test
  def testEncodedURLQuery(): Unit = {
    val expected1 = "/ラーメン"
    val req1 = client("/ラーメン")
    val res1 = req1.get_!()
    assertEquals(res1, "")

    val expected2 = "/%E3%83%A9%E3%83%BC%E3%83%A1%E3%83%B3"
    val req2 = client("/%E3%83%A9%E3%83%BC%E3%83%A1%E3%83%B3")
    val res2 = req2.get_!()
    assertEquals(res2, "")
  }
}
