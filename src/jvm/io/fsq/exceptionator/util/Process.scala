// Copyright 2012 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.exceptionator.util
import com.twitter.util.{Future, FuturePool}
import java.io.{BufferedReader, File, IOException, InputStreamReader}
import java.util.concurrent.Executors
import scala.collection.JavaConverters._

object Process {
  val processFuturePool = FuturePool(Executors.newFixedThreadPool(10))

  def readerIterator(reader: BufferedReader) = new Iterator[String] {
    var lineRead: Boolean = false
    var line: String = null

    def hasNext = {
      if (!lineRead) {
        try {
          lineRead = true
          line = reader.readLine()
        } catch {
          case _: IOException => line = null
        }
      }
      line != null
    }

    def next: String = {
      lineRead = false
      line
    }
  }

  def apply(command: List[String], wd: File): Future[ProcessResult] = {
    val pb = new ProcessBuilder(command.asJava).directory(wd)
    val p = pb.start()
    val errStream = new BufferedReader(new InputStreamReader(p.getErrorStream))
    val outStream = new BufferedReader(new InputStreamReader(p.getInputStream))

    val out = Process.processFuturePool(readerIterator(outStream).toList)
    val err = Process.processFuturePool(readerIterator(errStream).toList)
    Future.collect(List(out, err)).flatMap(s => {
      outStream.close()
      errStream.close()
      Process.processFuturePool(p.waitFor).map(_ match {
        case 0 => ProcessResult(0, s(0), s(1))
        case code => throw new IOException("Process failed: %s in %s returned %d with:\n%s".format(
          command.mkString(" "),
          wd.toString,
          code,
          s(1).mkString))
      })
    })
  }
}


case class ProcessResult(code: Int, out: List[String], err: List[String])
