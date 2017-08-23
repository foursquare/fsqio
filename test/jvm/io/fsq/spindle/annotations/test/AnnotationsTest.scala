// Copyright 2017 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.spindle.annotations.test

import io.fsq.spindle.annotations.SpindleAnnotations
import org.junit.Assert.assertEquals
import org.junit.Test

class AnnotationsTest {
  @Test
  def testResourceLoading(): Unit = {
    val merged = SpindleAnnotations.mergedAnnotations()
    val annotations = merged.get("io.fsq.common.thrift.descriptors.types.gen.Typedef").get
    assertEquals(annotations.size, 1)
    assertEquals(annotations.get("generate_proxy").get, "true")
  }
}
