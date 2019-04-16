// Copyright 2017 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.twofishes.indexer.mongo

import io.fsq.rogue.Rogue
import io.fsq.rogue.adapter.BlockingResult
import io.fsq.spindle.rogue.{SpindleQuery, SpindleRogue}

object RogueImplicits extends Rogue with SpindleRogue with BlockingResult.Implicits {
  val Q: SpindleQuery.type = SpindleQuery
}
