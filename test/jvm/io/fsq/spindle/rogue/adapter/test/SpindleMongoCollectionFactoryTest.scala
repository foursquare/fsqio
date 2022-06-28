// Copyright 2022 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.spindle.rogue.adapter.test

import com.mongodb.MongoClient
import com.mongodb.client.{MongoCollection, MongoDatabase}
import io.fsq.rogue.connection.MongoClientManager
import io.fsq.spindle.rogue.adapter.SpindleMongoCollectionFactory
import io.fsq.spindle.rogue.testlib.gen.{InnerShardKeyStruct, TestShardKeyStruct, TestStruct}
import org.bson.types.ObjectId
import org.junit.{Assert, Test}
import org.mockito.Mockito.mock

class SpindleMongoCollectionFactoryTest {
  val clientManagerMock = mock(classOf[MongoClientManager[MongoClient, MongoDatabase, MongoCollection]])
  val spindleCollectionFactory = new SpindleMongoCollectionFactory(clientManagerMock)

  @Test
  def testGetShardKeyNameFromRecordWithShardKey {
    val inner = InnerShardKeyStruct.newBuilder.id(new ObjectId()).result()
    val record = TestShardKeyStruct.newBuilder
      .id(new ObjectId())
      .innerStruct(inner)
      .result()
    val shardKeyOpt = spindleCollectionFactory.getShardKeyNameFromRecord(record)
    Assert.assertEquals(Some("i._id"), shardKeyOpt)
  }

  @Test
  def testGetShardKeyNameFromRecordNoShardKey {
    val record = TestStruct.newBuilder.id(1).result()
    val shardKeyOpt = spindleCollectionFactory.getShardKeyNameFromRecord(record)
    Assert.assertEquals(None, shardKeyOpt)
  }
}
