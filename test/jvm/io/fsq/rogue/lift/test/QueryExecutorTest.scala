// Copyright 2011 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.rogue.lift.test

import io.fsq.rogue.{InitialState, Query, RogueException}
import io.fsq.rogue.MongoHelpers.AndCondition
import io.fsq.rogue.lift.{LiftAdapter, ObjectIdKey}
import net.liftweb.mongodb.record.{MongoMetaRecord, MongoRecord}
import org.junit.{Ignore, Test}
import org.specs2.matcher.JUnitMustMatchers

class LegacyQueryExecutorTest extends JUnitMustMatchers {

  class Dummy extends MongoRecord[Dummy] with ObjectIdKey[Dummy] {
    def meta = Dummy
  }

  object Dummy extends Dummy with MongoMetaRecord[Dummy] {
  }

  @Test @Ignore("TODO(mateo): The public version of lift 2.6.2 causes an infinite recursion. FIXME")
  // Test ignored because it is broken when using OSS version of lift.
  def testExceptionInRunCommandIsDecorated {
    val query = Query[Dummy.type, Dummy, InitialState](
      Dummy, "Dummy", None, None, None, None, None, AndCondition(Nil, None), None, None, None)
    (LiftAdapter.runCommand(() => "hello", query){
      throw new RuntimeException("bang")
      "hi"
    }) must throwA[RogueException]
  }

}
