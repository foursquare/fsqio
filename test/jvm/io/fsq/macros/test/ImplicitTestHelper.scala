// Copyright 2014 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.macros.test

import io.fsq.macros.{CodeRef, StackElement}

object ImplicitTestHelper {
  def testImplicit()(implicit caller: CodeRef): CodeRef = {
    caller
  }

  def testStackElementImplicit()(implicit caller: StackElement): StackTraceElement = {
    caller
  }
}
