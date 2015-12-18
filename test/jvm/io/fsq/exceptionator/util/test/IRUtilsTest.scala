// Copyright 2012 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.exceptionator.util.test

import io.fsq.exceptionator.util.RollingRank
import org.junit.Test
import org.specs._

class IRUtilsTest extends SpecsMatchers {
  @Test
  def testRollingRankWindowing {
    val windowSize = 3
    val rr = new RollingRank("test1", "\\s+", None, None, windowSize)
    rr.rank("a a b")  // Slot 0
    rr.rank("b b c")  // Slot 1
    rr.rank("c c d")  // Slot 2
    rr.rank("d d e")  // Slot 0
    println(rr.docFrequency)
    println(rr.window.mkString(","))
    Option(rr.docFrequency.get("a")) must_== None
    // Note that tf-idf doesn't count the number of times
    // a term appears occurs in the corpus,
    // just the number of times a document containing the term
    // at least once occurs in the corpus
    Option(rr.docFrequency.get("b")).map(_.get) must_== Some(1)
    Option(rr.docFrequency.get("c")).map(_.get) must_== Some(2)
    Option(rr.docFrequency.get("d")).map(_.get) must_== Some(2)
    rr.window.foreach(terms => {
      rr.prune(terms)
      println(rr.docFrequency)
    })
    rr.docFrequency.size must_== 0
  }
}
