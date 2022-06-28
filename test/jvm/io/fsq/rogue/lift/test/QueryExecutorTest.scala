// Copyright 2011 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.rogue.lift.test

import com.mongodb.WriteConcern
import io.fsq.rogue.{InitialState, MongoHelpers, Query, RogueException}
import io.fsq.rogue.adapter.{BlockingMongoClientAdapter, BlockingResult}
import io.fsq.rogue.adapter.lift.LiftMongoCollectionFactory
import io.fsq.rogue.lift.ObjectIdKey
import io.fsq.rogue.lift.testlib.{LiftMongoTest, RogueTestMongoIdentifier}
import io.fsq.rogue.query.QueryExecutor
import io.fsq.rogue.util.{DefaultQueryLogger, DefaultQueryUtilities}
import net.liftweb.mongodb.record.{MongoMetaRecord, MongoRecord}
import net.liftweb.util.ConnectionIdentifier
import org.junit.{Assert, Test}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{spy, when}

class QueryExecutorTest extends LiftMongoTest {

  class Dummy extends MongoRecord[Dummy] with ObjectIdKey[Dummy] {
    def meta = Dummy
  }

  object Dummy extends Dummy with MongoMetaRecord[Dummy] {
    override def connectionIdentifier: ConnectionIdentifier = RogueTestMongoIdentifier
  }

  @Test
  def testExceptionInRunCommandIsDecorated(): Unit = {
    val underlyingException = new Exception("oops")

    val queryExecutor = {
      val collectionFactory = new LiftMongoCollectionFactory(blockingClientManager)

      val clientAdapter = {
        val queryUtilities = {
          val logger = new DefaultQueryLogger[BlockingResult] {
            // have to subclass and override due to the call-by-name params here
            override def onExecuteQuery[T](
              query: Query[_, _, _],
              instanceName: String,
              msg: => String,
              f: => BlockingResult[T]
            ): BlockingResult[T] = throw underlyingException
          }

          val spied = spy(new DefaultQueryUtilities[BlockingResult])
          when(spied.logger).thenReturn(logger)
          spied
        }

        new BlockingMongoClientAdapter(collectionFactory, queryUtilities)
      }

      new QueryExecutor(
        clientAdapter,
        LiftMongoTest.queryOptimizer,
        LiftMongoTest.serializer
      ) {
        override def defaultWriteConcern: WriteConcern = WriteConcern.W1
      }
    }

    val query = Query[Dummy.type, Dummy, InitialState](
      Dummy,
      "Dummy",
      None,
      None,
      None,
      None,
      MongoHelpers.AndCondition(Nil, None),
      None,
      None,
      None
    )

    try {
      queryExecutor.fetch(query)
      Assert.fail("Expected to see a RogueException, but nothing was thrown.")
    } catch {
      case re: RogueException => {
        Assert.assertEquals(underlyingException, re.getCause)
      }
    }
  }

}
