// Copyright 2022 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.rogue.lift.test

import com.mongodb.MongoClient
import com.mongodb.client.{MongoCollection, MongoDatabase}
import io.fsq.rogue.adapter.lift.LiftMongoCollectionFactory
import io.fsq.rogue.connection.MongoClientManager
import org.junit.{Assert, Test}
import org.mockito.Mockito.mock

class LiftMongoCollectionFactoryTest {
  val clientManagerMock = mock(classOf[MongoClientManager[MongoClient, MongoDatabase, MongoCollection]])
  val liftCollectionFactory = new LiftMongoCollectionFactory(clientManagerMock)

  @Test
  def testGetShardKeyNameFromRecord(): Unit = {
    // should always return None
    val shardKeyOpt = liftCollectionFactory.getShardKeyNameFromRecord(TestModel.createRecord)
    Assert.assertEquals(None, shardKeyOpt)
  }
}
