// Copyright 2013 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.spindle.codegen.binary

import io.fsq.spindle.__shaded_for_spindle_bootstrap__.runtime.Annotations
import io.fsq.spindle.codegen.parser.{ParserException, ThriftParser}
import io.fsq.spindle.codegen.runtime.{BitfieldRef, CodegenException, EnhancedTypeRef, EnhancedTypes, ProgramSource,
    ScalaProgram, Scope, TypeDeclaration, TypeDeclarationResolver, TypeReference}
import java.io.{File, PrintWriter}
import org.clapper.argot.ArgotConverters._
import org.clapper.argot.ArgotParser
import org.fusesource.scalate.{RenderContext, TemplateEngine}
import scala.annotation.tailrec

// TODO: This used to be from a magical sbt plugin. Figure out something
// clever for pants?
object Info {
  val version: String = "3.0.1"
}

object ThriftCodegen {
  def main(_args: Array[String]): Unit = {
    val args = new CodegenArgs(_args)
    try {
      args.javaTemplate.value.foreach(javaTemplate => {
        compile(
          javaTemplate,
          args.input.value,
          args.includes.value.getOrElse(Nil),
          args.namespaceOut.value,
          args.workingDir.value,
          "java",
          args.allowReload.value.getOrElse(false))
      })
      compile(
        args.template.value.get,
        args.input.value,
        args.includes.value.getOrElse(Nil),
        args.namespaceOut.value,
        args.workingDir.value,
        args.extension.value.getOrElse("scala"),
        args.allowReload.value.getOrElse(false))
    } catch {
      case e: CodegenException =>
        println("Codegen error:\n%s".format(e.getMessage))
        System.exit(1)
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

  class CodegenArgs(args: Array[String]) {
    val parser = new ArgotParser("scala-thrift-codegen", preUsage=Some("scala-thrift-codegen: Version 0.1"))

    val template = parser.option[String](List("template"), "/path/to/template", "path to scala template to generate from")

    val javaTemplate = parser.option[String](List("java_template"), "/path/to/template", "path to java template to generate from")

    val includes = parser.option[Seq[File]](List("thrift_include"), "dir1:dir2:...",
        "thrift include directives are resolved relative to these paths (and the including file's directory)") { (s, _) =>
      val files = s.split(':').map(parseFile)
      val nonexisting = files.filter(!_.exists)
      if (nonexisting.nonEmpty)
        parser.usage("Thrift included file(s) " + nonexisting.mkString(":") + "do(es) not exists.")
      files
    }

    val namespaceOut = parser.option[File](List("namespace_out"), "/path/to/output/dir",
        "Root of the output namespace hierarchy. We add the intermediate directory structure there as needed. " +
        "Required when compiling multiple files.") { (s, _) => parseFile(s) }

    val workingDir = parser.option[File](List("working_dir"), "/path/to/working/dir",
        "Root of the working directory used to store intermediate Scalate files.") { (s, _) => parseFile(s) }

    val input = parser.multiParameter[File]("thrift_file(s)", "Thrift files to codegen from.", optional=true) { (s, _) =>
      val file = parseFile(s)
      if (!file.exists)
        parser.usage("Input file \""+file+"\" does not exist.")
      file
    }

    val extension = parser.option[String](List("extension"), "js|scala", "the extension the files should have")

    val allowReload = parser.flag[Boolean](List("allow_reload"), "allow reloading of codegen templates")

    parser.parse(args)
    if (template.value.isEmpty) {
      parser.usage("Missing '--template' parameter.")
    }

    def parseFile(s: String): File = new File(s).getAbsoluteFile
  }
}
