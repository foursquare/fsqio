# Spindle

Spindle is a Scala code generator for Thrift.
The complete documentation lives [here](http://spindle.readthedocs.org/en/v1.8/).

## Installation

Because Spindle is a code generator, it needs to plug into your build system.

If you're using sbt 0.12, you can use Spindle's thrift-codegen-plugin. In your project/plugins.sbt,
add the following line:

    addSbtPlugin("com.foursquare" % "spindle-codegen-plugin" % "1.8.3")

Then, in the build.sbt for the project with the *.thrift files to compile, add the following line:

    seq(thriftSettings: _*)

By default, the plugin will compile all *.thrift sources in src/main/thrift, though this is
configurable.

Keep in mind that this will add a few library dependencies to your project. Currently, this
includes spindle-runtime, apache thrift, joda-time, jackson, rogue-field, finagle-thrift, and
scalaj-collection. If you need a specific version of any of these dependencies, the plugin can be
configured to use those.

## Releases

The latest release is 1.8.3. See the [changelog](https://github.com/foursquare/spindle/blob/master/CHANGELOG.md) for more details.

Major changes in 1.8.x:

- Support for hashed, 2dsphere and text indices
- Map support in TReadableJSONProtocol
- TBSONProtocol improvements
- Unknown field handling for forwards-compatibility
- Codegen newtype implicits
- Groundwork for optional proxy generation
- Hooks for alternateFk

## Dependencies

Apache thrift, joda-time, jackson, rogue-field, finagle-thrift, and scalaj-collection.

## Contributors

Spindle was initially developed by Foursquare Labs for internal use.
Many people have contributed:

- Adam Powslowsky
- Ben Lee
- Benjy Weinberger
- Daniel Harrison
- Daniel Salinas
- David Blackman
- David Taylor
- Jackson Davis
- Jason Liszka
- Jorge Ortiz
- Matthew Rathbone
- Neil Sanchala
- Tom Dyas

Further contributions welcome!
