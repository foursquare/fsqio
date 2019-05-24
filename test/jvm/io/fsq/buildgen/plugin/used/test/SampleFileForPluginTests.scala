// Copyright 2019 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.buildgen.plugin.used.test

import io.fsq.common.scala.Lists.Implicits._
import io.fsq.common.scala.TryO

class SameFileClass

class SampleCode {
  def useSymbols: Unit = {
    val samePackageSameFile = new SameFileClass
    val samePackageDifferentFile = new EmitUsedSymbolsPluginTest
    val differentPackageViaImport = TryO
    val differentPackageImplicitsViaImport = Some("hello").has("goodbye")
    val differentPackageAbsolute = io.fsq.common.scala.Identity
    val differentPackageAbsoluteRoot = _root_.io.fsq.common.scala.LazyLocal
  }
}
