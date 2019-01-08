// Copyright 2019 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.twofishes.indexer.util

import io.fsq.common.scala.Identity._
import io.fsq.common.scala.Lists.Implicits._

object JaroWinkler {
  // Returns a double from 0 to 1 (0 is totally different, 1 is exactly the same)
  // Only looks at letters and ignores case.
  // Based on https://github.com/rockymadden/stringmetric
  def score(s1: String, s2: String): Double = {
    // b is longer string
    val List(a, b) = List(s1, s2)
      .map(_.collect({ case c if c.isLetter => c.toLower }))
      .sortBy(_.length)
    if (a.isEmpty) {
      0d
    } else if (a =? b) {
      1d
    } else {
      val aLen = a.length
      val bLen = b.length
      val window = math.abs(bLen / 2 - 1)
      // matches is kind of like a.map(b.indexOf).filter(_ > -1)
      // but it uses a sliding window in b to match letters in a, and each letter
      // in b can only be used once.
      val matches = (Vector[Int]() /: a.indices)((seen, i) => {
        seen ++ ((i - window) to (i + window)).find(ii => {
          b.isDefinedAt(ii) && a(i) =? b(ii) && !seen.has(ii)
        })
      })

      if (matches.isEmpty) {
        0d
      } else {
        // number of letters in common
        val ms = matches.size.toDouble
        // transposition score
        val ts = matches.zip(matches.sorted).count({ case (i, j) => b(i) !=? b(j) }) / 2
        val jaro = ((ms / aLen) + (ms / bLen) + ((ms - ts) / ms)) / 3
        // jaro winkler
        val prefixLen = if (b.startsWith(a)) aLen else a.zip(b).indexWhere({ case (c1, c2) => c1 !=? c2 })
        jaro + (math.min(prefixLen, 4) * 0.1 * (1 - jaro))
      }
    }
  }
}
