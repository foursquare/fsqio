// Copyright 2013 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.spindle.codegen.runtime.test

import io.fsq.spindle.codegen.runtime.constants.test.gen.{ConstantineConstants, Foo}
import org.junit.{Assert => A, Test}

class ScalaConstTest {
  @Test
  def testConstantGeneration(): Unit = {
    A.assertEquals(List(1.2, 2.1, 1.1), ConstantineConstants.LISTCONST)
    A.assertEquals(Map("hello" -> "world", "wisconsin" -> "badgers"), ConstantineConstants.MAPCONST)
    A.assertTrue(ConstantineConstants.BOOL)
    A.assertEquals("hello", ConstantineConstants.SIMPLE)
    A.assertEquals(Some("hello"), ConstantineConstants.CRAZY.get("foo"))
    A.assertEquals(Some(ConstantineConstants.REFERS), ConstantineConstants.CRAZY.get("bar"))
    A.assertEquals(Some(Foo.bar), ConstantineConstants.CRAZYENUMS.get("foo"))
  }
}
