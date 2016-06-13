// Copyright 2013 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.spindle.codegen.parser.test

import io.fsq.spindle.__shaded_for_spindle_bootstrap__.descriptors.{Annotation, Program, SimpleBaseType,
    SimpleContainerType}
import io.fsq.spindle.codegen.parser.{ParserException, ThriftParser}
import org.junit.Assert.{assertEquals, assertTrue}
import org.junit.Test

class ThriftParserTest {
  val base = "test/thrift/io/fsq/spindle/codegen/parser/test"

  @Test
  def testParseDuplicate(): Unit = {
    try {
      ThriftParser.parseProgram(base + "/parse_duplicate.thrift")
      assertTrue("Parsing duplicate field ids should fail.", false)
    } catch {
      case e: ParserException => ()
    }
  }

  @Test
  def testParseProgram(): Unit = {
    val program = ThriftParser.parseProgram(base + "/parse_program.thrift")

    assertEquals(program.namespaces.size, 2)
    assertEquals(program.constants.size, 3)
  }

  @Test
  def testParseHeader(): Unit = {
    val program = ThriftParser.parseProgram(base + "/parse_header.thrift.testdata")

    assertEquals(program.includes.size, 2)
    assertTrue(program.includes.forall(_.path == "io/fsq/spindle/parser/test/parse_program.thrift"))
    assertEquals(program.namespaces.size, 15)
    assertTrue(program.namespaces.forall(_.name == "io.fsq.spindle.codegen.parser.test"))
  }

  @Test
  def testParseConstants(): Unit = {
    val program = ThriftParser.parseProgram(base + "/parse_const.thrift")

    assertEquals(program.constants.size, 31)
    assertTrue(program.constants.slice(0, 4).forall(const => {
      idToSimpleBaseType(const.typeId, program) == SimpleBaseType.I32
    }))
    assertEquals(idToSimpleBaseType(program.constants()(4).typeId, program), SimpleBaseType.STRING)
    assertEquals(idToSimpleBaseType(program.constants()(5).typeId, program), SimpleBaseType.BINARY)
    assertEquals(idToSimpleBaseType(program.constants()(6).typeId, program), SimpleBaseType.BOOL)
    assertEquals(idToSimpleBaseType(program.constants()(7).typeId, program), SimpleBaseType.BYTE)
    assertEquals(idToSimpleBaseType(program.constants()(8).typeId, program), SimpleBaseType.I16)
    assertEquals(idToSimpleBaseType(program.constants()(9).typeId, program), SimpleBaseType.I32)
    assertEquals(idToSimpleBaseType(program.constants()(10).typeId, program), SimpleBaseType.I64)
    assertEquals(idToSimpleBaseType(program.constants()(11).typeId, program), SimpleBaseType.DOUBLE)
    assertTrue(program.constants.slice(12, 15).forall(const => {
      idToSimpleBaseType(const.typeId, program) == SimpleBaseType.STRING
    }))
    assertEquals(idToBaseTypeAnnotations(program.constants()(12).typeId, program).size, 0)
    assertEquals(idToBaseTypeAnnotations(program.constants()(13).typeId, program).size, 1)
    assertEquals(idToBaseTypeAnnotations(program.constants()(14).typeId, program).size, 2)
    assertTrue(program.constants.slice(15, 21).forall(const => {
      idToSimpleContainerType(const.typeId, program).listTypeIsSet
    }))
    assertTrue(program.constants.slice(21, 23).forall(const => {
      idToSimpleContainerType(const.typeId, program).setTypeIsSet
    }))
    assertTrue(program.constants.slice(23, 30).forall(const => {
      idToSimpleContainerType(const.typeId, program).mapTypeIsSet
    }))
    assertEquals(
      program.typeRegistry.idToType()(program.constants()(30).typeId).simpleType.typerefOrThrow.typeAlias, "myint")
  }

  @Test
  def testParseStructs(): Unit = {
    val program = ThriftParser.parseProgram(base + "/parse_struct.thrift")

    assertEquals(program.structs.size, 24)
    assertTrue(program.structs.slice(0, 7).forall(struct => struct.__fields.size == 0))
    assertEquals(program.structs()(3).__annotations.size, 0)
    assertEquals(program.structs()(4).__annotations.size, 1)
    assertEquals(program.structs()(5).__annotations.size, 2)
    assertTrue(program.structs.slice(7, 19).forall(struct => struct.__fields.size == 1))
    assertTrue(program.structs.slice(19, 24).forall(struct => struct.__fields.size == 2))
  }

  @Test
  def testParseDefinitions(): Unit = {
    val program = ThriftParser.parseProgram(base + "/parse_definition.thrift")

    assertEquals(program.typedefs.size, 4)
    assertEquals(program.enums.size, 13)
    assertEquals(program.unions.size, 3)
    assertEquals(program.exceptions.size, 3)
  }

  @Test
  def testParseServices(): Unit = {
    val program = ThriftParser.parseProgram(base + "/parse_service.thrift")

    assertEquals(program.services.size, 15)
  }

  def idToSimpleBaseType(id: String, program: Program): SimpleBaseType = {
    program.typeRegistry.idToType()(id).simpleType.baseTypeOrThrow.simpleBaseType
  }

  def idToSimpleContainerType(id: String, program: Program): SimpleContainerType = {
    program.typeRegistry.idToType()(id).simpleType.containerTypeOrThrow.simpleContainerType
  }

  def idToBaseTypeAnnotations(id: String, program: Program): Seq[Annotation] = {
    program.typeRegistry.idToType()(id).simpleType.baseTypeOrThrow.__annotations
  }
}
