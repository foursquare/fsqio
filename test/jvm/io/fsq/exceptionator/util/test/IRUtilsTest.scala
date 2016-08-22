// Copyright 2012 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.exceptionator.util.test

import io.fsq.exceptionator.util.RollingRank
import org.junit.{Assert => A, Test}

class IRUtilsTest {
  @Test
  def testRollingRankWindowing(): Unit = {
    val windowSize = 3
    val rr = new RollingRank("test1", "\\s+", None, None, windowSize)
    rr.rank("a a b")  // Slot 0
    rr.rank("b b c")  // Slot 1
    rr.rank("c c d")  // Slot 2
    rr.rank("d d e")  // Slot 0
    println(rr.docFrequency)
    println(rr.window.mkString(","))
    A.assertEquals(None, Option(rr.docFrequency.get("a")))
    // Note that tf-idf doesn't count the number of times
    // a term appears occurs in the corpus,
    // just the number of times a document containing the term
    // at least once occurs in the corpus
    A.assertEquals(Some(1), Option(rr.docFrequency.get("b")).map(_.get))
    A.assertEquals(Some(2), Option(rr.docFrequency.get("c")).map(_.get))
    A.assertEquals(Some(2), Option(rr.docFrequency.get("d")).map(_.get))
    rr.window.foreach(terms => {
      rr.prune(terms)
      println(rr.docFrequency)
    })
    A.assertEquals(0, rr.docFrequency.size)
  }
}
