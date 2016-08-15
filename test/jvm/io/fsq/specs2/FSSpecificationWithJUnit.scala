// Copyright 2016 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.specs2

import org.junit.runner.RunWith
import org.specs2.control.consoleLogging
import org.specs2.mutable.SpecificationWithJUnit
import org.specs2.runner.{JUnitRunner => BaseJUnitRunner}
import org.specs2.specification.core.SpecificationStructure

/**
  * Customize specs2 [[org.specs2.runner.JUnitRunner]] so that it uses the [[ClassLoader]] of the
  * test class and not the context [[ClassLoader]] of the current thread because, in some cases,
  * they may differ. (They have been seen to differ under Scala 2.11 when running `./pants test`
  * with a large number of targets.)
  */
class JUnitRunner(klass: Class[_]) extends BaseJUnitRunner(klass) {
  override lazy val specification: SpecificationStructure = {
    SpecificationStructure.create(klass.getName, klass.getClassLoader).execute(consoleLogging).unsafePerformIO.fold(
      ok => ok,
      error => error.fold(m => throw new Exception(m), t => throw t, (m, t) => throw t)
    )
  }
}

@RunWith(classOf[JUnitRunner])
abstract class FSSpecificationWithJUnit extends SpecificationWithJUnit
