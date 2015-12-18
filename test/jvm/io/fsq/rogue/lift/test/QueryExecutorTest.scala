// Copyright 2011 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.rogue.lift.test

import io.fsq.rogue.{InitialState, Query, RogueException}
import io.fsq.rogue.MongoHelpers.AndCondition
import io.fsq.rogue.lift.{LiftAdapter, ObjectIdKey}
import net.liftweb.mongodb.record.{MongoMetaRecord, MongoRecord}
import org.junit._
import org.specs2.matcher.JUnitMustMatchers

class LegacyQueryExecutorTest extends JUnitMustMatchers {

  class Dummy extends MongoRecord[Dummy] with ObjectIdKey[Dummy] {
    def meta = Dummy
  }

  object Dummy extends Dummy with MongoMetaRecord[Dummy] {
  }

  @Test
  def testExeptionInRunCommandIsDecorated {
    val query = Query[Dummy.type, Dummy, InitialState](
      Dummy, "Dummy", None, None, None, None, None, AndCondition(Nil, None), None, None, None)
    (LiftAdapter.runCommand(() => "hello", query){
      throw new RuntimeException("bang")
      "hi"
    }) must throwA[RogueException]
  }

}
