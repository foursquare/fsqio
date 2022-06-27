// Copyright 2022 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.spindle.codegen.runtime.test

import io.fsq.spindle.codegen.runtime.{CodegenException, Validator}
import io.fsq.testlib.FSAssert
import org.junit.{Assert, Test, rules}

class ValidatorTest {
  private val className = "someclass"
  private val fieldNames = Vector("a", "b", "c")

  @Test
  def testValidateShardKeyAnnotationsSuccess(): Unit = {
    Validator.validateShardKeyAnnotations(shardKeyAnnotations = "b:1", fieldNames, className)
  }

  @Test
  def testValidateShardKeyAnnotationsInvalidShardKeyThrowsException(): Unit = {
    val e = FSAssert.assertThrows[CodegenException](
      Validator.validateShardKeyAnnotations(shardKeyAnnotations = "e:1", fieldNames, className)
    )
    Assert.assertEquals("Unknown field name 'e' in shard_key annotation for class someclass", e.getMessage)
  }

  @Test
  def testValidateShardKeyAnnotationsInvalidFormatThrowsException(): Unit = {
    val e = FSAssert.assertThrows[CodegenException](
      Validator.validateShardKeyAnnotations(shardKeyAnnotations = "invalid", fieldNames, className)
    )
    Assert.assertEquals(
      "Invalid shard_key specifier 'invalid' for class someclass -- format must be FIELD_NAME:SHARD_TYPE; " +
        "e.g., `id:hashed`",
      e.getMessage
    )

  }

  @Test
  def testValidateShardKeyAnnotationsThrowIfCompoundShardKey(): Unit = {
    val e = FSAssert.assertThrows[CodegenException](
      Validator.validateShardKeyAnnotations(shardKeyAnnotations = "a:1,b:1", fieldNames, className)
    )
    Assert.assertEquals(
      "Invalid shard_key specifier: a:1,b:1 for class someclass; compound shard" +
        " keys are not yet supported",
      e.getMessage
    )

  }
}
