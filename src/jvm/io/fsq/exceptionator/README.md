Exceptionator
===============

An exception aggregator built on top of twitter finagle and mongodb.

![Exceptionator screenshot](http://cl.ly/image/1I2c2H0W1N3V/exceptionator-screenshot.png)


Usage
-----

#### Setup:

Download MongoDB [here](https://www.mongodb.org/downloads).

At Foursquare, we run a single instance of exceptionator next to a standalone `mongod` process, which is enough to handle the exception load of our production JVM fleet. To do this yourself:

```
mkdir mongo_data
path_to_mongo_download/bin/mongod --dbpath mongo_data
```

Note that exceptionator's data is ephemeral by nature, but you may still wish to run using a replica set or a sharded cluster. This will not be covered here.

For testing purposes, this is all that is needed. See the `History` section below for some additional recommended setup for a long-running deployment.

#### Running locally:

Exceptionator can be started locally with a single `./pants run` command. This will execute a pom resolve and compile, if necessary, before booting the server using settings in the included [config.json](https://github.com/foursquare/fsqio/blob/master/src/resources/io/fsq/exceptionator/config/config.json) file:

```
./pants run src/jvm/io/fsq/exceptionator
```

#### Deployment:

Exceptionator can also be bundled into a self-contained, runnable jar file (it will appear in the `dist` folder):

```
./pants binary src/jvm/io/fsq/exceptionator
```

The jar file will run standalone, so just copy it where needed and run with `java -jar`. To customize the server configuration when using this method, either modify the in-repo [config.json](https://github.com/foursquare/fsqio/blob/master/src/resources/io/fsq/exceptionator/config/config.json) prior to running `./pants binary` or place a new `config.json` in the working directory. Configuration can also be overridden as JVM properties on the command line.


Options and Defaults
--------------------

Configuration is json-based using a thin wrapper around the [Typesafe Config library](https://github.com/typesafehub/config).  A sample config file (just fill in any email account information) is provided [here](https://github.com/foursquare/fsqio/blob/master/src/resources/io/fsq/exceptionator/config/config.json). The default file location is `-Dconfig=config.json`, with fallback to the included sample file.

Some default values to note:

```
http.port: 8080
db.host: localhost:27017
db.name: test
git.repo: undefined
```


History
-------

#### Using:

Exceptionator is primarily useful for live monitoring, but it also supports sampled replay of exception notices that are no longer visible in the live UI. Simply click into any UI graph to access a realtime playback of history from that point in time.

History sampling is controlled through two config options. The defaults here will uniformly sample 50 notices from every 30 second time window:

```
history.sampleRate: 50
history.sampleWindowSeconds: 30
```

Note that if there are fewer than `sampleRate` total notices in a time window they will all be recorded.

#### Mongo setup:

Generally, exceptionator will handle creation of its collections and indexes on startup, and, assuming you are happy with the defaults mongo provides, there is no setup work required on the part of the user. The sole exception to this is the `history` collection which should be created as a capped collection. This is easy to do through a mongo shell after starting the `mongod` instance but before booting exceptionator for the first time:

```
path_to_mongo_download/bin/mongo localhost:27017
// in the mongo shell:
use test
var max_size_in_bytes = 10*1024*1024
db.createCollection('history', { capped: true, size: max_size_in_bytes })
// verify collection details, specifically that 'capped' and 'maxSize' are correct
db.history.stats()
exit
```

It is possible to forgo this step and cap the history collection later using the [convertToCapped](https://docs.mongodb.org/manual/reference/command/convertToCapped) command, but existing indexes will have to be recreated manually (or by restarting exceptionator).

Note that this could theoretically be handled by exceptionator itself. However, due to the ease of shooting oneself in the foot when working with capped collections (ex- resizing a capped collection requires dropping and recreating it from scratch), setup is being left to the user.


Git Blame Support
-----------------

Prerequisites:

*  Ensure that the running user has access to fetch new commits from git without entering credentials
*  Supply a valid git revision as `"v": "<revision>"` or as `"env": { "git": "<revision>" }`

To enable and configure git blame support:

*  Supply a local clone of the git repository in the `git.repo` option
*  Configure `backtrace.interesting.filter`, a list of regular expressions specifying lines to include when traversing down the stack to find who to blame (match at least one)
*  Optionally supply `backtrace.interesting.filterNot`, a list of regular expressions specifying lines to exclude when traversing down the stack to find who to blame (must match none)
*  Optionally supply `email.drop.excs`.  If a notice contains any of these exceptions, an email will not be sent.


Example Message
---------------

```json
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
```
