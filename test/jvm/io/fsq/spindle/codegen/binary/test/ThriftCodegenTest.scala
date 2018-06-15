package io.fsq.spindle.codegen.binary.test

import io.fsq.spindle.codegen.binary.ThriftCodegen
import io.fsq.spindle.codegen.runtime.{CodegenException, ScalaProgram}
import java.io.File
import org.junit.Assert._
import org.junit.Test

class ThriftCodegenTest {
  val base = "test/thrift/io/fsq/spindle/codegen/parser/test"

  @Test
  def testParseDuplicateWireName(): Unit = {
    try {
      val info =
        ThriftCodegen.inputInfoForCompiler(Seq(new File(base + "/parse_duplicate_wire_name.thrift")), Vector.empty)
      val program = ScalaProgram(info.sources.head, info.types, info.enhanced)
      program.structs.foreach(println _)
      fail("Parsing duplicate field wire_names should fail.")
    } catch {
      case e: CodegenException => ()
    }
  }

}
