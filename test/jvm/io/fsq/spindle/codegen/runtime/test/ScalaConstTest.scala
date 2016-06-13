// Copyright 2013 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.spindle.codegen.runtime.test

import io.fsq.spindle.codegen.runtime.constants.test.gen.{ConstantineConstants, Foo}
import org.junit.Assert.{assertEquals, assertFalse, assertTrue}
import org.junit.Test
import org.specs.SpecsMatchers


class ScalaConstTest extends SpecsMatchers {

  @Test
  def testConstantGeneration {
    ConstantineConstants.LISTCONST mustEqual List(1.2, 2.1, 1.1)
    ConstantineConstants.MAPCONST mustEqual Map("hello" -> "world", "wisconsin" -> "badgers")
    ConstantineConstants.BOOL mustEqual true
    ConstantineConstants.SIMPLE mustEqual "hello"
    ConstantineConstants.CRAZY.get("foo") mustEqual Some("hello")
    ConstantineConstants.CRAZY.get("bar") mustEqual Some(ConstantineConstants.REFERS)
    ConstantineConstants.CRAZYENUMS.get("foo") mustEqual Some(Foo.bar)
  }
}
