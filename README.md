# Foursquare Fsq.io
### All of Foursquare's opensource code in a single repo.

All Foursquare code lives in a single repository, an architecture generally called a monorepo.
`Fsq.io` is a subset of that internal monorepo. `Fsq.io` is a single repo that holds many of Foursquare's
opensource projects that had previously lived in their own separate Github repos. Foursquare contributes
to a build tool specifically designed to work with monorepos called [Pants](https://pantsbuild.github.io/).
The entire `Fsq.io` repo is is built and tested by Pants.

Deploying directly from our monorepo has some nice advantages, for consumers of our opensource projects as
well as Foursquare itself. The entire repo is built daily by our CIs and internal contributions are opensourced
automatically without the overhead of publishing. This repo will always contain the latest code that we use
internally, all of the tools can be built just as we use them, directly from HEAD.

## Projects include:
* [Exceptionator](https://github.com/foursquare/fsqio/tree/master/src/jvm/io/fsq/exceptionator):
An exception aggregator using mongodb.
* [Fhttp](https://github.com/foursquare/fsqio/tree/master/src/jvm/io/fsq/fhttp):
A request building interface similar to scalaj-http for Finagle http clients.
* [Pants pom-resolve](https://github.com/foursquare/fsqio/tree/master/src/python/fsqio/pants/pom):
A drop-in Python replacement for the Pants ivy resolver.
* [Rogue](https://github.com/foursquare/fsqio/tree/master/src/jvm/io/fsq/rogue): A Scala DSL for MongoDB
* [Spindle](https://github.com/foursquare/fsqio/tree/master/src/jvm/io/fsq/spindle): A Scala code generator for Thrift
* [Twofishes](https://github.com/foursquare/fsqio/tree/master/src/jvm/io/fsq/twofishes):
A coarse, splitting geocoder and reverse geocoder in Scala
* and others.


## Requirements

* JDK 1.8 (1.8.0_40 preferred)
* python2.6+ (2.7 preferred)
* [monogdb server](https://docs.mongodb.org/manual/tutorial/install-mongodb-on-os-x/) (required to pass some tests)

Internally we use OSX Yosemite or later. Other OS may work but are officially unsupported.


## Pants build system
Pants is a build system that works particularly well for a source code workspace containing many
distinct but interdependent pieces.

Pants is similar to `make`, `maven`, `ant`, `gradle`, `sbt`, etc. but pants pursues different design goals.
Pants optimizes for:

* building multiple, dependent things from source
* building code in a variety of languages
* speed of build execution

Pants is a true community project, with major contributions from Twitter, Foursquare, Square, among other
companies and independent contributors. It has friendly documentation [here](https://pantsbuild.github.io),
in this README we will just touch on how to compile and test the code.


## Compiling and Testing
#### Targets and BUILD files
Targets are adressable project or dependency level modules that can be built by Pants. `BUILD` files are
configuration files that define targets that can be built by Pants. Each target has a name and can be built
by running a Pants task against the target's name and location.

For example, Fsq.io's JVM projects live under `src/jvm`
[here](https://github.com/foursquare/fsqio/tree/master/src/jvm/io/fsq). You can bundle the Twofishes server by running:

     ./pants run src/jvm/io/fsq/twofishes/server:server-bin

Built artifacts can be found under `dist/`.

#### Build and Test every project
Adding a `::` to a path will glob every target under that location. So to compile every target in Fsq.io:

     ./pants compile src::

Similarly, to run all the tests, (after starting the mongodb server):

    ./pants test test::

Projects aspirationally have READMEs at the project root.



## Discussion

Please open an issue if you have any questions or concerns.

## License
Apache License, Version 2.0 (Apache-2.0)
