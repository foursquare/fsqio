// Copyright 2013 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.spindle.codegen.binary

import io.fsq.spindle.__shaded_for_spindle_bootstrap__.runtime.Annotations
import io.fsq.spindle.codegen.parser.{ParserException, ThriftParser}
import io.fsq.spindle.codegen.runtime.{BitfieldRef, CodegenException, EnhancedTypeRef, EnhancedTypes, ProgramSource,
    ScalaProgram, Scope, TypeDeclaration, TypeDeclarationResolver, TypeReference}
import java.io.{File, PrintWriter}
import org.fusesource.scalate.{RenderContext, TemplateEngine}
import scala.annotation.tailrec
import scopt.OptionParser

// TODO: This used to be from a magical sbt plugin. Figure out something
// clever for pants?
object Info {
  val version: String = "3.0.1"
}

case class ThriftCodegenConfig(
  input: Seq[File] = Nil,
  template: String = "",  // will be filled in since this is a required option
  javaTemplate: Option[String] = None,
  includes: Option[Seq[File]] = None,
  namespaceOut: Option[File] = None,
  workingDir: Option[File] = None,
  extension: Option[String] = None,
  allowReload: Boolean = false
)

object ThriftCodegenConfig {
  def parse(args: Array[String]): Option[ThriftCodegenConfig] = {
    val parser = new OptionParser[ThriftCodegenConfig]("scala-thrift-codegen") {
      opt[String]("template")
        .required()
        .text("path to scala template to generate from")
        .action((v, c) => c.copy(template = v))

      opt[String]("java_template")
        .text("path to java template to generate from")
        .action((v, c) => c.copy(javaTemplate = Some(v)))

      opt[String]("thrift_include")
        .text("thrift include directives are resolved relative to these paths (and the including file's directory)")
        .validate(v => {
          val files = v.split(":").map(name => new File(name).getAbsoluteFile)
          val nonexisting = files.filter(!_.exists)
          if (nonexisting.nonEmpty) {
            failure("Thrift included file(s) " + nonexisting.mkString(":") + "do(es) not exists.")
          } else
            success
        })
        .action((v, c) => {
          val files = v.split(":").map(name => new File(name).getAbsoluteFile)
          c.copy(includes = Some(files))
        })

      opt[File]("namespace_out")
        .text("Root of the output namespace hierarchy. We add the intermediate directory structure there as needed. "
          + "Required when compiling multiple files.")
        .action((v, c) => c.copy(namespaceOut = Some(v)))

      opt[File]("working_dir")
        .text("Root of the working directory used to store intermediate Scalate files.")
        .action((v, c) => c.copy(workingDir = Some(v)))

      opt[String]("extension")
        .text("the extension the files should have")
        .action((v, c) => c.copy(extension = Some(v)))

      opt[Boolean]("allow_reload")
        .text("allow reloading of codegen templates")
        .action((_, c) => c.copy(allowReload = true))

      arg[File]("thrift_file(s)")
        .optional()
        .maxOccurs(Int.MaxValue)
        .validate(f => if (f.exists) success else failure(s"Input file ${f.getName} does not exist."))
        .action((v, c) => c.copy(input = c.input :+ v))
    }

    parser.parse(args, ThriftCodegenConfig())
  }
}

object ThriftCodegen {
  def main(_args: Array[String]): Unit = {
    val args = ThriftCodegenConfig.parse(_args).getOrElse({
      sys.exit(1)
    })

    try {
      args.javaTemplate.foreach(javaTemplate => {
        compile(
          javaTemplate,
          args.input,
          args.includes.getOrElse(Nil),
          args.namespaceOut,
          args.workingDir,
          "java",
          args.allowReload)
      })
      compile(
        args.template,
        args.input,
        args.includes.getOrElse(Nil),
        args.namespaceOut,
        args.workingDir,
        args.extension.getOrElse("scala"),
        args.allowReload)
    } catch {
      case e: CodegenException =>
        println("Codegen error:\n%s".format(e.getMessage))
        sys.exit(1)
    }
  }

  def compile(
      templatePath: String,
      inputFiles: Seq[File],
      includePaths: Seq[File],
      namespaceOutputPath: Option[File],
      workingDirPath: Option[File],
      extension: String,
      allowReload: Boolean
  ): Unit = {
    val (sourcesToCompile, typeDeclarations, enhancedTypes) = inputInfoForCompiler(inputFiles, includePaths)

    val engine = new TemplateEngine(Nil, "") {
      override protected def createRenderContext(uri: String, out: PrintWriter): RenderContext = {
        val renderContext = super.createRenderContext(uri, out)
        renderContext.numberFormat.setGroupingUsed(false)
        renderContext
      }
    }
    engine.workingDirectory = workingDirPath.getOrElse(new File(".scalate.d/"))
    engine.escapeMarkup = false
    engine.allowReload = allowReload

    try {
      for (source <- sourcesToCompile) {
        val program =
          try {
            ScalaProgram(source, typeDeclarations, enhancedTypes)
          } catch {
            case e: CodegenException =>
              throw new CodegenException("Error generating code for file %s:\n%s".format(source.file.toString, e.getMessage))
          }

        extension match {

          case _ => {}
        }

        //val extraPath = pkg.map(_.split('.').mkString(File.separator, File.separator, "")).getOrElse("")
        val out = (extension, namespaceOutputPath) match {
          case ("js", _) if (program.jsPackage.isEmpty) => {
            throw new IllegalStateException("%s does not have a js namespace defined!".format(source.baseName))
          }
          case ("js", Some(nsOut)) => {
            val prefix = "%s%s.".format(File.separator, program.jsPackage.getOrElse(""))
            val dir = nsOut.getAbsoluteFile.toString
            new File(dir).mkdirs()
            val file = dir + prefix + source.baseName + ".js"
            val outFile = new File(file)
            new PrintWriter(outFile, "UTF-8")
          }
          case ("scala", Some(nsOut)) => {
            val prefix = program.pkg.map(_.split('.').mkString(File.separator, File.separator, "")).getOrElse("")
            val outputDir = nsOut.getAbsoluteFile.toString + prefix
            new File(outputDir).mkdirs()
            val outputFile = new File(outputDir + File.separator + source.baseName + ".scala")
            new PrintWriter(outputFile, "UTF-8")
          }
          case ("java", Some(nsOut)) => {
            val prefix = program.pkg.map(_.split('.').mkString(File.separator, File.separator, "")).getOrElse("")
            val outputDir = nsOut.getAbsoluteFile.toString + prefix
            new File(outputDir).mkdirs()
            val outputFile = new File(outputDir + File.separator + "java_" + source.baseName + ".java")
            new PrintWriter(outputFile, "UTF-8")
          }
          case (a, b) => {
            new PrintWriter(System.out, true)
          }
        }
        val args =
          Map(
            "program" -> program,
            "source" -> source,
            "templatePath" -> templatePath,
            "version" -> Info.version)
        try {
          engine.layout(templatePath, out, args)
        } catch {
          case e: CodegenException =>
            throw new CodegenException("Error generating code for file %s:\n%s".format(source.file.toString, e.getMessage))
        } finally {
          out.flush
        }
      }
    } finally {
      engine.shutdown()
    }
  }

  def inputInfoForCompiler(inputFiles: Seq[File], includePaths: Seq[File]):
      (Seq[ProgramSource], Map[ProgramSource, Map[String, TypeDeclaration]], EnhancedTypes) = {
    val enhancedTypes = (ref: TypeReference, annots: Annotations, scope: Scope) => {
      if (annots.contains("enhanced_types")) {
        annots.get("enhanced_types").map(value => EnhancedTypeRef(value, ref))
      } else if (annots.contains("bitfield_struct")) {
        for (structName <- annots.get("bitfield_struct")) yield {
          val typeDeclaration = scope.getOrElse(structName,
            throw new CodegenException("Could not find struct referenced in bitfield annotation with name: %s".format(structName)))
          BitfieldRef(typeDeclaration.name, ref, true)
        }
      } else if (annots.contains("bitfield_struct_no_setbits")) {
        for (structName <- annots.get("bitfield_struct_no_setbits")) yield {
          val typeDeclaration = scope.getOrElse(structName,
            throw new CodegenException("Could not find struct referenced in bitfield annotation with name: %s".format(structName)))
          BitfieldRef(typeDeclaration.name, ref, false)
        }
      } else {
        Some(ref)
      }
    }

    val declarationResolver = new TypeDeclarationResolver(enhancedTypes)
    val sources = parsePrograms(inputFiles, includePaths)
    val typeDeclarations = declarationResolver.resolveAllTypeDeclarations(sources)
    (sources, typeDeclarations, enhancedTypes)
 }

  def parsePrograms(toParse: Seq[File], includePaths: Seq[File]): Seq[ProgramSource] = {
    try {
      recursiveParsePrograms(parsed = Seq.empty, toParse, includePaths)
    } catch {
      case e: ParserException =>
        throw new CodegenException("Error parsing file %s:\n%s".format(e.file, e.message))
    }
  }

  @tailrec
  final def recursiveParsePrograms(
      parsed: Seq[ProgramSource],
      _toParse: Seq[File],
      includePaths: Seq[File]
  ): Seq[ProgramSource] = {
    if (_toParse.isEmpty) {
      parsed
    } else {
      val toParse = _toParse.distinct
      val programs = ThriftParser.parsePrograms(toParse)

      // Sanity check
      if (toParse.size != programs.size) {
        throw new CodegenException("Expected %d but only %d files parsed.".format(toParse.size, programs.size))
      }

      val newlyParsed =
        for ((program, file) <- programs.zip(toParse)) yield {
          val includes = program.includes.map(_.path)
          val includedFiles = includes.map(path => resolveInclude(path, includePaths, file))
          ProgramSource(file, program, includedFiles)
        }

      val justIncluded = newlyParsed.flatMap(_.includedFiles).distinct
      val allParsed = parsed ++ newlyParsed
      val newToParse = justIncluded.filterNot(allParsed.map(_.file).toSet)
      recursiveParsePrograms(allParsed, newToParse, includePaths)
    }
  }

  private def resolveInclude(relativePath: String, includedPaths: Seq[File], file: File): File = {
    val absolutePaths = includedPaths.map(base => new File(base, relativePath))
    absolutePaths.find(_.exists).getOrElse {
      throw new CodegenException("Unresolvable include \"" + relativePath + "\" in file \"" + file + "\"")
    }
  }
}
