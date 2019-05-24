// Copyright 2019 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.buildgen.plugin.used.test

import com.fasterxml.jackson.databind.{DeserializationFeature, ObjectMapper, PropertyNamingStrategy}
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.fasterxml.jackson.module.scala.experimental.ScalaObjectMapper
import io.fsq.buildgen.plugin.used.EmitUsedSymbolsPlugin
import java.io.{File, FileWriter}
import java.nio.file.Files
import org.junit.{Assert, Test}
import org.reflections.util.ClasspathHelper
import scala.collection.JavaConverters._
import scala.io.Source
import scala.reflect.internal.util.BatchSourceFile
import scala.tools.nsc.{Global, Settings}
import scala.tools.nsc.reporters.ConsoleReporter

case class EmitUsedSymbolsPluginOutput(
  source: String,
  imports: Seq[String],
  fullyQualifiedNames: Seq[String]
)

class EmitUsedSymbolsPluginTest {

  val json = new ObjectMapper with ScalaObjectMapper
  json.registerModule(new DefaultScalaModule)
  json.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true)
  json.setPropertyNamingStrategy(new PropertyNamingStrategy.LowerCaseWithUnderscoresStrategy)

  @Test
  def testSymbolTraversal(): Unit = {
    val whitelist = Set(
      "io.fsq.common.scala.Identity",
      "io.fsq.common.scala.LazyLocal"
    )
    val whitelistFile = Files.createTempFile("EmitUsedSymbolsPluginTest_whitelist", ".tmp").toFile
    val whitelistWriter = new FileWriter(whitelistFile)
    whitelist.foreach(symbol => {
      whitelistWriter.write(symbol)
      whitelistWriter.write('\n')
    })
    whitelistWriter.close()
    System.setProperty("io.fsq.buildgen.plugin.used.whitelist", whitelistFile.getAbsolutePath)

    val outputDir = Files.createTempDirectory("EmitUsedSymbolsPluginTest_outputDir").toFile
    System.setProperty("io.fsq.buildgen.plugin.used.outputDir", outputDir.getAbsolutePath)

    val settings = new Settings
    settings.usejavacp.value = true
    settings.stopAfter.value = List(EmitUsedSymbolsPlugin.name)

    // Sometimes we are run from a "thin" jar where a wrapper jar defines
    // the full classpath in a MANIFEST.  IMain's default classloader does
    // not respect MANIFEST Class-Path entries by default, so we force it
    // here.
    settings.classpath.value = ClasspathHelper
      .forManifest()
      .asScala
      .map(_.toString)
      .mkString(":")

    val global = new Global(settings, new ConsoleReporter(settings)) {
      override def computeInternalPhases(): Unit = {
        super.computeInternalPhases()
        for (phase <- new EmitUsedSymbolsPlugin(this).components) {
          phasesSet += phase
        }
      }
    }

    val testSourceFile = new File("test/jvm/io/fsq/buildgen/plugin/used/test/SampleFileForPluginTests.scala")
    val testSource = new BatchSourceFile(
      testSourceFile.getName,
      Source.fromFile(testSourceFile.getPath).toArray
    )
    val runner = new global.Run()
    runner.compileSources(List(testSource))

    val outputFile = new File(outputDir, testSourceFile.getName)
    val outputJson = json.readValue[EmitUsedSymbolsPluginOutput](outputFile)

    val expectedJson = EmitUsedSymbolsPluginOutput(
      source = testSourceFile.getName,
      imports = Vector(
        "io.fsq.common.scala.Lists.Implicits._",
        "io.fsq.common.scala.TryO"
      ),
      fullyQualifiedNames = Vector(
        "io.fsq.common.scala.Identity",
        "io.fsq.common.scala.LazyLocal"
      )
    )

    Assert.assertEquals(
      "emitted source file name doesn't match expected",
      expectedJson.source,
      outputJson.source
    )
    Assert.assertArrayEquals(
      "emitted imports don't match expected",
      expectedJson.imports.sorted.toArray: Array[Object],
      outputJson.imports.sorted.toArray: Array[Object]
    )
    Assert.assertArrayEquals(
      "emitted names don't match expected",
      expectedJson.fullyQualifiedNames.sorted.toArray: Array[Object],
      outputJson.fullyQualifiedNames.sorted.toArray: Array[Object]
    )
  }
}
