// Copyright 2017 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.spindle.codegen.runtime

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode

/* Renders thrift annotations from a [[ScalaProgram]] into json. Entry-point [[jsonBody]]. */
case class RenderJson() {
  val mapper = new ObjectMapper

  /* for each class with nonempty annotations, yield a json dictionary from the annotations */
  def allJsonAnnotations(program: ScalaProgram): Seq[(ScalaClass, ObjectNode)] =
    // TODO(awinter): in theory Extraction.unflatten does this, but I think unflatten has weird
    //   type inference we don't want
    for (clazz <- program.structs if !clazz.annotations.toSeq.isEmpty)
      yield {
        val node = mapper.createObjectNode()
        for ((k, v) <- clazz.annotations.toSeq)
          node.put(k, v)
        (clazz, node)
      }

  // TODO(awinter): double check binaryName is what we want and that this is a binaryName
  /* throws if program.pkg is None */
  def binaryName(program: ScalaProgram, clazz: ScalaClass): String = {
    require(program.pkg.isDefined, "--write_json_annotations requires package names in structs")
    program.pkg.get + "." + clazz.name
  }

  /*
  Render program into a list of json dicts using [[allJsonAnnotations]] to render each struct.
  Skip classes with no annotations.
  Return includes number of processed classes (so you can skip writing the file if empty).
   */
  def jsonBody(program: ScalaProgram): (Int, String) = {
    val node = mapper.createObjectNode()
    var nfields = 0
    for ((clazz, json) <- allJsonAnnotations(program)) {
      nfields += 1
      node.set(binaryName(program, clazz), json)
    }
    (
      nfields,
      mapper.writerWithDefaultPrettyPrinter.writeValueAsString(node)
    )
  }
}
