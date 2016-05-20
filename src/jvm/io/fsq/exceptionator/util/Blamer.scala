// Copyright 2012 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.exceptionator.util

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap
import com.twitter.util.Future
import io.fsq.common.logging.Logger
import java.io.File
import org.joda.time.DateTime
import scala.util.matching.Regex

object Blame {
  def apply(filePath: String, lineNum: Int): Blame = Blame("", "", "", filePath, "", lineNum)
}

case class Blame(author: String, mail: String, date: String, filePath: String, lineString: String, lineNum: Int) {
  def isValid = author.length > 0 && mail.length > 0 && mail != "not.committed.yet"
}

trait Blamer {
  def blame(tag: String, fileName: String, line: Int, hints: Set[String]): Future[Option[Blame]]
}


class ConcreteBlamer extends Blamer with Logger {
  val blamer = Config.opt(_.getString("git.repo"))
    .map(r => new File(r))
    .find(f => f.exists && f.isDirectory)
    .map(rf => new GitBlamer(rf)).getOrElse({
      logger.warning("git.repo isn't configured or is misconfigured.  Blame support disabled.")
      new NilBlamer
    })

  def blame(tag: String, fileName: String, line: Int, hints: Set[String]): Future[Option[Blame]] = blamer.blame(tag, fileName, line, hints)

}


class NilBlamer extends Blamer {
  def blame(tag: String, fileName: String, line: Int, hints: Set[String]): Future[Option[Blame]] =
    Future.value(None)
}

class GitBlamer(val root: File) extends Blamer with Logger {

  val maxTags = 4
  val rootSub = if(root.toString.endsWith("/")) root.toString.length else root.toString.length + 1
  val recentTags =
    new ConcurrentLinkedHashMap.Builder[String, Map[String, List[String]]].maximumWeightedCapacity(maxTags).build()

  def retry(
    process: () => Future[ProcessResult],
    fixAttempt: () => Future[ProcessResult]): Future[ProcessResult] = {

    process().rescue { case e => {
      fixAttempt().flatMap { (r: ProcessResult) =>
         process()
       } onFailure { e =>
        Future.exception[ProcessResult](e)
       }
    }}
  }

  def fetch(tag: String): Future[ProcessResult] = {
    Process(List("git", "fetch", "origin", "+refs/heads/*:refs/remotes/origin/*", "refs/tags/*:refs/tags/*"), root)
  }

  def findFile(tag: String, name: String, hints: Set[String]): Future[Option[String]] = {
    Option(recentTags.get(tag))
      .map(fileMap => Future.value(fileMap.get(name)
      .getOrElse(Nil))).getOrElse({
        val result = retry(() => Process(List("git", "ls-files", "--with-tree=" + tag), root), () => fetch(tag))
        val rescued: Future[ProcessResult] = result.rescue { case e => {
          Future.value(ProcessResult(0, Nil, Nil))
        }}

        rescued.map(r => {
          val fileMap: Map[String, List[String]] = r.out.map(f => new File(f).getName -> f)
            .groupBy(_._1)
            .map { case (key, pairs) => key -> pairs.map(_._2).toList }
          recentTags.put(tag, fileMap)
          fileMap.get(name).getOrElse(Nil)
        })
    }).map(_ match {
      case Nil => None
      case candidates =>
        Some(candidates.maxBy(_.split("/").toSet.intersect(hints).size))
    })
  }
  def blame(tag: String, fileName: String, line: Int, hints: Set[String]): Future[Option[Blame]] = {
    val Author = new Regex("""^author (.*)$""")
    val Mail = new Regex("""^author-mail <(.*)>$""")
    val Date = new Regex("""^committer-time (.*)$""")
    val Summary = new Regex("""^summary (.*)$""")
    val LineString = new Regex("^\t(.*)$")
    def updateBlame(line: String, b: Blame) = line match {
      case Author(a) => b.copy(author=a)
      case Date(a) => b.copy(date=new DateTime(a.toLong * 1000L).toString)
      case Mail(m) => b.copy(mail=m)
      case LineString(l) => b.copy(lineString=l)
      case _ => b
    }

    findFile(tag, fileName, hints).flatMap { fileOpt => fileOpt.map(f => {
      val gblame = retry(
        () => Process(List("git", "blame", tag, "-w", "--porcelain", "-L%d,%d".format(line, line), "--", f), root),
        () => fetch(tag))
      gblame.map(pr => Some(pr.out.foldLeft(Blame(f, line)){ (b, l) => updateBlame(l, b) }))
      }).getOrElse(Future.value[Option[Blame]](None))
    }
  }
}
