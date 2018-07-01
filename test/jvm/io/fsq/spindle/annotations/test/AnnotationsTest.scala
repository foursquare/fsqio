// Copyright 2017 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.spindle.annotations.test

import io.fsq.spindle.annotations.SpindleAnnotations
import org.junit.{Ignore, Test}
import org.junit.Assert.assertEquals

class AnnotationsTest {

  // This test fails if the current fingerprint for the target was run with annotations-skip=True.
  // It would be best decoupled from the execution of the most recent codegen.
  // The most naive thing would be to remove the skip option from the Pants test.
  @Ignore("")
  @Test
  def testResourceLoading(): Unit = {
    val merged = SpindleAnnotations.mergedAnnotations()
    val annotations = merged.get("io.fsq.common.thrift.descriptors.types.gen.Typedef").get
    assertEquals(annotations.size, 1)
    assertEquals(annotations.get("generate_proxy").get, "true")
  }
}
