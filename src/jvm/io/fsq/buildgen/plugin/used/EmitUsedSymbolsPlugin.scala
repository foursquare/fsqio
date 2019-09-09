// Copyright 2011 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.buildgen.plugin.used

import java.io.{FileWriter, PrintWriter, Writer}
import scala.io.Source
import scala.tools.nsc.{Global, Phase}
import scala.tools.nsc.plugins.{Plugin, PluginComponent}

object EmitUsedSymbolsPlugin {
  val name = "emit-used-symbols"
  val description = "Emit imported or used fully qualified from source"
  val rootSymbolPrefix = "_root_."
  val rootSymbolPrefixLength = rootSymbolPrefix.length
}

class EmitUsedSymbolsPlugin(override val global: Global) extends Plugin {
  override val name = EmitUsedSymbolsPlugin.name
  override val description = EmitUsedSymbolsPlugin.description
  override val components = List[PluginComponent](EmitUsedSymbolsPluginComponent)

  private object EmitUsedSymbolsPluginComponent extends PluginComponent {
    import global._
    import global.definitions._

    override val global = EmitUsedSymbolsPlugin.this.global
    override val phaseName = EmitUsedSymbolsPlugin.name
    override val runsAfter = List("parser")
    override val runsBefore = List("namer")

    private val whitelist = Option(System.getProperty("io.fsq.buildgen.plugin.used.whitelist"))
      .map(whitelistPath => {
        Source.fromFile(whitelistPath).getLines().toSet
      })
      .getOrElse({
        println("io.fsq.buildgen.plugin.used.whitelist not specified, will not gather fully qualified names")
        Set.empty[String]
      })

    class UsedSymbolTraverser(
      unit: CompilationUnit,
      whitelist: Set[String],
      outputWriter: Writer
    ) extends Traverser {
      def gatherImports(tree: Tree): Seq[String] = tree match {
        case Import(pkg, selectors) => {
          selectors.map(symbol => s"$pkg.${symbol.name}")
        }
        case _: ImplDef => Nil
        case _ => {
          tree.children.flatMap(gatherImports)
        }
      }

      def gatherFQNames(tree: Tree, pid: String): Seq[String] = tree match {
        case s: Select => {
          val symbol = s.toString
          if (symbol != pid) {
            if (whitelist.contains(symbol)) {
              Vector(symbol)
            } else if (symbol.startsWith(EmitUsedSymbolsPlugin.rootSymbolPrefix)) {
              val unprefixedSymbol = symbol.substring(EmitUsedSymbolsPlugin.rootSymbolPrefixLength)
              if (whitelist.contains(unprefixedSymbol)) {
                Vector(unprefixedSymbol)
              } else {
                Vector.empty
              }
            } else {
              Vector.empty
            }
          } else {
            Vector.empty
          }
        }
        case Import(_, _) => Vector.empty
        case _ => {
          tree.children.flatMap(x => gatherFQNames(x, pid))
        }
      }

      override def traverse(tree: Tree): Unit = tree match {
        case PackageDef(pid, stats) => {
          val imports = gatherImports(tree).distinct
          val importsJson = imports.map(x => "\"" + x + "\"").mkString(",")
          val fqNames = gatherFQNames(tree, pid.toString).distinct
          val fqNamesJson = fqNames.map(x => "\"" + x + "\"").mkString(",")
          println("IMPORTS" + importsJson)
          println("FQNAMES" + fqNamesJson)
          outputWriter.write(s"""
            {
              "source": "${unit.source.path}",
              "imports": [$importsJson],
              "fully_qualified_names": [$fqNamesJson]
            }
          """)
        }
      }
    }

    override def newPhase(prev: Phase): StdPhase = new StdPhase(prev) {
      override def name: String = EmitUsedSymbolsPlugin.name
      override def description: String = EmitUsedSymbolsPlugin.description
      override def apply(unit: CompilationUnit): Unit = {
        val outputWriter = Option(System.getProperty("io.fsq.buildgen.plugin.used.outputDir"))
          .map(outputDir => {
            val pathSafeSource = unit.source.path.replaceAllLiterally("/", ".")
            new FileWriter(s"$outputDir/$pathSafeSource")
          })
          .getOrElse({
            println("io.fsq.buildgen.plugin.used.outputDir not specified, writing to stdout")
            new PrintWriter(System.out)
          })

        val traverser = new UsedSymbolTraverser(unit, whitelist, outputWriter)
        traverser.traverse(unit.body)
        outputWriter.close()
      }
    }
  }
}
