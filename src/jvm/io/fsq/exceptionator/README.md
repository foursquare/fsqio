Exceptionator
===============

An exception aggregator using mongodb.

![Exceptionator screenshot](http://cl.ly/image/1I2c2H0W1N3V/exceptionator-screenshot.png)

Usage
-----

    ./sbt assembly
    java -jar target/exceptionator-assembly-2.0-beta21.jar

The jar file will run standalone, so just copy it where ever you need it.  Its recommended to customize and copy config.json to the working directory.  The default mongo connection is localhost:27017/test.


Options and Defaults
--------------------
Configuration is json-based.  A sample config file (just fill in email.pass) is provided.  The default location is:

    -Dconfig=config.json

Some default values to note:

    http.port: 8080
    db.host: localhost:27017
    db.name: test
    git.repo: undefined


Git Blame Support
-----------------
Prerequisites:

*  Ensure that the running user has access to fetch new commits from git without entering credentials
*  Supply a valid git revision as `"v": "<revision>"` or as `"env": { "git": "<revision>" }`


To enable and configure git blame support:

*  Supply a local clone of the git repository in the `git.repo` option
*  Supply `backtrace.interesting.filter`, a list of regular expressions specifying lines to
   include when traversing down the stack to find who to blame (match at least one)
*  Optionally supply `backtrace.interesting.filterNot`, a list of regular expressions specifying lines to
   exclude when traversing down the stack to find who to blame (must match none)
*  Optionally supply `email.drop.excs`.  If a notice contains any of these exceptions, an email will not be sent.


Example Message
---------------

    {
      "bt": [
        "com.foursquare.lib.SoftError$.error (SoftError.scala:18)",
        "com.foursquare.api2.endpoints.Endpoint.handle (Endpoint.scala:45)",
        "com.foursquare.api2.RestAPIV2$$anon$1$$anonfun$apply$1.apply (RestAPIV2.scala:69)",
        "com.foursquare.api2.RestAPIV2$$anon$1$$anonfun$apply$1.apply (RestAPIV2.scala:65)",
        "net.liftweb.http.LiftServlet$$anonfun$1$$anonfun$apply$mcZ$sp$1.apply (LiftServlet.scala:260)",
        "net.liftweb.http.LiftServlet$$anonfun$1$$anonfun$apply$mcZ$sp$1.apply (LiftServlet.scala:260)",
        "net.liftweb.common.Full.map (Box.scala:491)",
        "net.liftweb.http.LiftServlet$$anonfun$1.apply$mcZ$sp (LiftServlet.scala:260)",
        ":------------------------------- SNIP -------------------------------"
      ],
      "env": {
        "cmd": "/data/loko/foursquare.web-api-jetty-r010837p1/bin/java -server -cp /data/loko/foursquare.web-api-jetty-r010837p1/webapp/ StartJetty",
        "mode": "Staging",
        "pid": "4165"
      },
      "excs": [
        "com.foursquare.lib.SoftException"
      ],
      "h": "dev-10.prod.foursquare.com",
      "msgs": [
        "com.foursquare.lib.SoftException: Indexing error: Query does not match an index! query: db.tiplists.find"
      ],
      "sess": {
        "domain": "general",
        "url": "soft error"
      },
      "v": "r010837"
    }
