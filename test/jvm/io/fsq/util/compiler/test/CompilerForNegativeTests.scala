// Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.util.compiler.test

import java.io.PrintWriter
import org.junit.Assert
import org.reflections.util.ClasspathHelper
import scala.collection.JavaConverters._
import scala.tools.nsc.Settings
import scala.tools.nsc.interpreter.{IMain, Results}

class CompilerForNegativeTests(imports: Seq[String]) {
  private val settings = new Settings
  settings.usejavacp.value = true
  settings.deprecation.value = true // enable detailed deprecation warnings
  settings.unchecked.value = true // enable detailed unchecked warnings

  // Sometimes we are run from a "thin" jar where a wrapper jar defines
  // the full classpath in a MANIFEST.  IMain's default classloader does
  // not respect MANIFEST Class-Path entries by default, so we force it
  // here.
  settings.classpath.value = ClasspathHelper
    .forManifest()
    .asScala
    .map(_.toString)
    .mkString(":")

  // It's a good idea to comment out this second parameter (PrintWriter) when adding or
  // modifying tests that shouldn't compile, to make sure that the tests don't compile for the
  // right reason.  If the param isn't passed, the interpreter defaults to writing
  // warnings and errors out to the console.
  private val stringWriter = new java.io.StringWriter()
  private val interpreter = new IMain(settings, new PrintWriter(stringWriter))

  imports.foreach(pkg => {
    interpreter.interpret(s"import $pkg") match {
      case Results.Success => ()
      case Results.Error => throw new IllegalArgumentException(s"Error importing '$pkg'")
      case Results.Incomplete => throw new IllegalArgumentException(s"'$pkg' is an incomplete import?")
    }
  })

  def typeCheck(code: String): Option[String] = {
    stringWriter.getBuffer.delete(0, stringWriter.getBuffer.length)
    val thunked = "() => { %s }".format(code)
    interpreter.interpret(thunked) match {
      case Results.Success => None
      case Results.Error => Some(stringWriter.toString)
      case Results.Incomplete => throw new Exception("Incomplete code snippet")
    }
  }

  def check(code: String, expectedErrorREOpt: Option[String] = Some("")): Unit = {
    (expectedErrorREOpt, typeCheck(code)) match {
      case (Some(expectedErrorRE), Some(actualError)) => {
        val msg =
          s"Expected\n'$code'\nto fail with an error matching\n'$expectedErrorRE'\nbut it failed with\n'$actualError'"
        Assert.assertTrue(
          msg,
          expectedErrorRE.r.findFirstIn(actualError.replaceAll("\n", "")).isDefined
        )
      }
      case (Some(expectedErrorRE), None) => {
        Assert.fail(
          s"Expected\n'$code'\nto fail with an error matching\n'$expectedErrorRE'\nbut it succeeded."
        )
      }
      case (None, Some(actualError)) => {
        Assert.fail(
          s"Expected\n'$code'\nto compile successfully, but it failed with\n'$actualError'"
        )
      }
      case (None, None) => ()
    }
  }
}
