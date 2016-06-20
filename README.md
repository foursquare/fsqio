# Foursquare Fsq.io
### All of Foursquare's open source code in a single repo.

[![Build Status](https://travis-ci.org/foursquare/fsqio.svg?branch=master)](https://travis-ci.org/foursquare/fsqio)

All Foursquare code lives in a single repository, an architecture generally called a monorepo.
`Fsq.io` is a subset of that internal monorepo. `Fsq.io` holds many of Foursquare's
open source projects that had previously lived in their own separate Github repos. Foursquare contributes
to a build tool specifically designed to work with monorepos called [Pants](https://pantsbuild.github.io/).
The entire `Fsq.io` repo is is built and tested by Pants.

Deploying directly from our monorepo has some nice advantages, for consumers of our open source projects as
well as Foursquare itself. The entire repo is built daily by our CIs and internal contributions are open sourced
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
* postgresql
* [monogdb server](https://docs.mongodb.org/manual/tutorial/install-mongodb-on-os-x/) (required to pass some tests)
* An increased number of file descriptors (we use 32768)

Internally we use OSX Yosemite or later. Other OS may work but are officially unsupported. (_Unofficially_, if
building on Linux you should install `python-dev`, `build-essential`, and `libpq-dev` in addition to the above).


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
#### First Run
The first run will take a long time as
[pom-resolve](https://github.com/foursquare/fsqio/tree/master/src/python/fsqio/pants/pom)
(our custom resolver for 3rdparty dependencies) computes the project graph and downloads the dependencies.
Maybe as long as 15-20 mins! A good first run is to get this out of the way:

     ./pants pom-resolve

#### Targets and BUILD files
Targets are adressable project or dependency level modules that can be built by Pants. `BUILD` files are
configuration files that define targets that can be built by Pants. Each target has a name and can be built
by running a Pants task against the target's name and location.

For example, Fsq.io's JVM projects live under `src/jvm`
[here](https://github.com/foursquare/fsqio/tree/master/src/jvm/io/fsq). You can compile Rogue by running:

     ./pants compile src/jvm/io/fsq/rogue:rogue

#### Build and Test every project
Adding a `::` to a path will glob every target under that location. So to compile every target in Fsq.io:

     ./pants compile src::

Similarly, to run all the tests, (after starting the mongodb server):

    ./pants test test::

Projects aspirationally have READMEs at the project root.

## Acknowledgements

* Thanks to the great community supporting [Pants](https://github.com/pantsbuild/pants).
* Fsq.io is split from commits to our internal monorepo by [Sapling](https://github.com/jsirois/sapling),
a git porcelain tool by @jsirois.

## Discussion

Please open an issue if you have any questions or concerns.

## License
Apache License, Version 2.0 (Apache-2.0)
