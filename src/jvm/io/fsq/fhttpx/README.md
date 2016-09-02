# Foursquare Finagle Http Library #

[Finagle](https://github.com/twitter/finagle) is a wonderful protocol agnostic communication library.
Building an http client using finagle is simple. However, building an http request and
parsing the response can ba a chore.
FHttp is a scala-idiomatic request building interface similar to
[scalaj-http](https://github.com/scalaj/scalaj-http) for finagle http clients.

Like [scalaj-http](https://github.com/scalaj/scalaj-http), it supports multipart data and oauth1.

You will probably want to override FHttpClient.service to add your own logging and tracing filters.

##How to Build##
    ./pants compile src/jvm/io/fsq/fhttp/
    ./pants test test/jvm/io/fsq/fhttp/test/

##[API Docs](http://foursquare.github.io.fsq.fhttp/api/)##


## Some Simple Examples ##
you can try these in `./pants repl src/jvm/io/fsq/fhttp/`


    import io.fsq.fhttp._
    import io.fsq.fhttp.FHttpRequest._
    import com.twitter.conversions.storage._
    import com.twitter.conversions.time._
    import com.twitter.finagle.builder.ClientBuilder
    import com.twitter.finagle.http.Http

    // Create the singleton client object using a default client spec (hostConnectionLimit=1, no SSL)
    val clientDefault = new FHttpClient("test", "localhost:80").releaseOnShutdown()

    // or customize the ClientBuilder
    val client = new FHttpClient("test2", "localhost:80",
        ClientBuilder()
          .codec(Http(_maxRequestSize = 1024.bytes,_maxResponseSize = 1024.bytes))
          .hostConnectionLimit(15)
          .tcpConnectTimeout(30.milliseconds)
          .retries(0)).releaseOnShutdown()

    // add parameters
    val clientWParams = client("/path").params("msg"->"hello", "to"->"world").params(List("from"->"scala"))

    // or headers
    val clientWParamsWHeaders = clientWParams.headers(List("a_header"->"a_value"))

    // non-blocking POST
    val responseFut = clientWParamsWHeaders.postFuture()

    // or issue a blocking request
    clientWParamsWHeaders.getOption()



## OAuth Example ##
    import io.fsq.fhttp._
    import io.fsq.fhttp.FHttpRequest._

    // Create the singleton client object using a default client spec
    val client = new FHttpClient("oauth", "oauthbin.appspot.com:80")
    val consumer = Token("key", "secret")

    // Get the request token
    val token = client("/v1/request-token").oauth(consumer).get_!(asOAuth1Token)

    // Get the access token
    val accessToken = client("/v1/access-token").oauth(consumer, token).get_!(asOAuth1Token)

    // Try some queries
    client("/v1/echo").params("k1"->"v1", "k2"->"v2").oauth(consumer, accessToken).get_!()
    // res0: String = k1=v1&k2=v2


## Dropbox OAuth Example ##
Here's a slightly more complicated oauth (and HTTPS) example, using a [Dropbox API](https://www.dropbox.com/developers/apps) account.

    import io.fsq.fhttp._
    import io.fsq.fhttp.FHttpRequest._
    import com.twitter.conversions.storage._
    import com.twitter.conversions.time._
    import com.twitter.finagle.builder.ClientBuilder
    import com.twitter.finagle.http.Http

    // Using the App key and App Secret, fill out the consumer token here:
    val consumer = Token(dbApiKey, dbApiSecret)

    val api = new FHttpClient("dropbox-api", "api.dropbox.com:443",
        ClientBuilder()
          .codec(Http())
          .tls("api.dropbox.com")
          .tcpConnectTimeout(1.second)
          .hostConnectionLimit(1)
          .retries(0))

    val reqToken = api("/1/oauth/request_token").oauth(consumer).post_!("", asOAuth1Token)

    // Go authorize usage of the app in a web browser using this link:
    println("""go visit

    https://www.dropbox.com/1/oauth/authorize?oauth_token=%s&oauth_token_secret=%s

    and accept the app!""".format(reqToken.key, reqToken.secret))


    // wait for the app to be accepted by the user


    // Finally, get the access token
    val accessToken = api("/1/oauth/access_token").oauth(consumer, reqToken).post_!("", asOAuth1Token)


    // and go do some stuff with it.
    api("/1/account/info").oauth(consumer, accessToken).get_!()
