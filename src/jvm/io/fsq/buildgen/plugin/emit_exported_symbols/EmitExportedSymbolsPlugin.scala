
// Copyright 2011 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.buildgen.plugin.emit_exported_symbols

import scala.reflect.internal.Flags
import scala.tools.nsc.{Global, Phase}
import scala.tools.nsc.plugins.{Plugin, PluginComponent}


class EmitExportedSymbolsPlugin(val global: Global) extends Plugin {
  import global._

  val name = "emit-exported-symbols"
  val description = "Emit symbols importable from this source"
  val components = List[PluginComponent](EmitExportedSymbolsPluginComponent)
  val outputDir = System.getProperty("io.fsq.buildgen.plugin.emit_exported_symbols.outputDir")

  private object EmitExportedSymbolsPluginComponent extends PluginComponent {
    import global._
    import global.definitions._

    val global = EmitExportedSymbolsPlugin.this.global

    override val runsAfter = List("parser")
    override val runsBefore = List("namer")

    val phaseName = EmitExportedSymbolsPlugin.this.name

    override def newPhase(prev: Phase): StdPhase = new StdPhase(prev) {
      override def name = EmitExportedSymbolsPlugin.this.name
      override def description = EmitExportedSymbolsPlugin.this.description
      override def apply(unit: global.CompilationUnit): Unit = {
        new ExportedSymbolTraverser(unit).traverse(unit.body)
      }
    }

    class ExportedSymbolTraverser(unit: CompilationUnit) extends Traverser {
      val pathSafeSource = unit.source.path.replaceAllLiterally("/", ".")
      val outputPath = outputDir + "/" + pathSafeSource

      private def isPrivate(md: MemberDef) = {
        (md.mods.flags & Flags.PRIVATE.toLong) != 0
      }

      private def traversePackageSymbols(tree: Tree): Seq[String] = {
        tree match {
          case md: MemberDef if isPrivate(md) => Seq.empty

          case pd: PackageDef => {
            val childNames = pd.children.flatMap(traversePackageSymbols)
            val fqChildNames = childNames.map(pd.pid + "." + _)
            Vector(pd.pid.toString) ++ fqChildNames
          }

          case md: ModuleDef => {
            val childNames = md.children.flatMap(traversePackageSymbols)
            md.name.toString match {
              case "package" => childNames
              case other => Vector(md.name.toString) ++ childNames.map(other + "." + _)
            }
          }

          case cd: ClassDef => {
            Vector(cd.name.toString)
          }

          case dd: ValOrDefDef => {
            if (dd.name.toString != "<init>") {
              Vector(dd.name.toString)
            } else {
              Seq.empty
            }
          }

          case td: TypeDef => {
            Vector(td.name.toString)
          }

          case _ => tree.children.flatMap(traversePackageSymbols)
        }
      }

      override def traverse(tree: Tree): Unit = tree match {
        case PackageDef(pid, stats) => {
          val symbols = traversePackageSymbols(tree).toSet
          val symbolsJson = symbols.map(x => "\"%s\"".format(x)).mkString(",")
          val packageName = pid
          println("Writing JSON to output file: %s".format(outputPath))
          val outputFile = new java.io.FileWriter(outputPath)
          outputFile.write("""
            {
              "package": "%s",
              "source": "%s",
              "symbols": [%s]
            }
          """.format(packageName, unit.source.path, symbolsJson))
          outputFile.close()
        }
      }
    }
  }
}

