// Copyright 2012 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.spindle.runtime.test

import java.io.{StringWriter, Writer}
import org.codehaus.jackson.{JsonFactory, JsonGenerator, JsonParser}

// Takes a JSON string and emits the same JSON object, but pretty-printed.
object JsonPrettyPrinter {
  // Safe for concurrent use.
  val factory: JsonFactory = new JsonFactory()

  def prettify(in: String) = {
    val parser: JsonParser = factory.createJsonParser(in)
    val out: Writer = new StringWriter((1.1f * in.length).toInt)
    val generator: JsonGenerator = factory.createJsonGenerator(out)
    generator.useDefaultPrettyPrinter()

    while (parser.nextToken != null) {
      generator.copyCurrentStructure(parser)
    }
    generator.close()
    out.close()

    out.toString
  }
}
