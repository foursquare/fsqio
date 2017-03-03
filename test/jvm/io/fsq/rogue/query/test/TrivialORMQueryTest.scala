// Copyright 2017 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.rogue.query.test

import com.mongodb.{ConnectionString, MongoClient => BlockingMongoClient, MongoClientURI}
import com.mongodb.async.client.{MongoClientSettings, MongoClients => AsyncMongoClients}
import com.mongodb.connection.ClusterSettings
import com.twitter.util.{Await, Future}
import io.fsq.common.concurrent.Futures
import io.fsq.field.{OptionalField, RequiredField}
import io.fsq.rogue.{InitialState, Query, QueryOptimizer, Rogue}
import io.fsq.rogue.MongoHelpers.AndCondition
import io.fsq.rogue.adapter.{AsyncMongoClientAdapter, BlockingMongoClientAdapter, BlockingResult}
import io.fsq.rogue.adapter.callback.twitter.TwitterFutureMongoCallbackFactory
import io.fsq.rogue.connection.MongoIdentifier
import io.fsq.rogue.connection.testlib.MongoTest
import io.fsq.rogue.query.QueryExecutor
import io.fsq.rogue.query.testlib.{TrivialORMMetaRecord, TrivialORMMongoCollectionFactory, TrivialORMRecord,
    TrivialORMRogueSerializer}
import org.bson.Document
import org.bson.types.ObjectId
import org.junit.{Before, Ignore, Test}
import org.specs2.matcher.JUnitMustMatchers


case class SimpleRecord(a: Int, b: String) extends TrivialORMRecord {
  override type Self = SimpleRecord
  override def meta = SimpleRecord
}

object SimpleRecord extends TrivialORMMetaRecord[SimpleRecord] {

  val _id = new RequiredField[ObjectId, SimpleRecord.type] {
    override val owner = SimpleRecord
    override val name = "_id"
    override def defaultValue: ObjectId = new ObjectId
  }

  val a = new OptionalField[Int, SimpleRecord.type] {
    override val owner = SimpleRecord
    override val name = "a"
  }

  val b = new OptionalField[String, SimpleRecord.type] {
    override val owner = SimpleRecord
    override val name = "b"
  }

  override val collectionName = "test_records"

  override val mongoIdentifier = MongoIdentifier("test")

  override def fromDocument(document: Document): SimpleRecord = {
    SimpleRecord(document.getInteger(a.name), document.getString(b.name))
  }

  override def toDocument(record: SimpleRecord): Document = {
    new Document()
      .append(a.name, record.a)
      .append(b.name, record.b)
  }
}

object TrivialORMQueryTest {

  val mongoAddress = {
    val address = Option(System.getProperty("default.mongodb.server")).getOrElse("mongodb://localhost")
    if (!address.startsWith("mongodb://")) {
      s"mongodb://$address"
    } else {
      address
    }
  }
  val dbName = "test"

  /* NOTE(jacob): For whatever reason, the default CodecRegistry used by the async client
   *    is exactly the same as the blocking client except for the fact that it omits the
   *    DBObjectCodecProvider, which we need until MongoBuilder has been rewritten to use
   *    something else.
   *
   * TODO(jacob): Remove the custom settings here once MongoBuilder no longer uses
   *    DBObject.
   */
  val connectionString = new ConnectionString(mongoAddress)
  val asyncMongoClientSettings = {
    MongoClientSettings.builder
      .codecRegistry(
        BlockingMongoClient.getDefaultCodecRegistry
      ).clusterSettings(
        ClusterSettings.builder
          .applyConnectionString(connectionString)
          .build()
      ).build()
  }
  val asyncMongoClient = AsyncMongoClients.create(asyncMongoClientSettings)
  val blockingMongoClient = new BlockingMongoClient(new MongoClientURI(mongoAddress))

  trait Implicits extends Rogue {
    implicit def metaRecordToQuery[M <: TrivialORMMetaRecord[R], R <: TrivialORMRecord](
      meta: M with TrivialORMMetaRecord[R]
    ): Query[M, R, InitialState] = {
      Query(
        meta = meta,
        collectionName = meta.collectionName,
        lim = None,
        sk = None,
        maxScan = None,
        comment = None,
        hint = None,
        condition = AndCondition(Nil, None),
        order = None,
        select = None,
        readPreference = None
      )
    }
  }
}

// TODO(jacob): Move basically everything in the rogue tests into here.
class TrivialORMQueryTest extends MongoTest
  with JUnitMustMatchers
  with BlockingResult.Implicits
  with TrivialORMQueryTest.Implicits {

  val queryOptimizer = new QueryOptimizer
  val serializer = new TrivialORMRogueSerializer

  val asyncCollectionFactory = new TrivialORMMongoCollectionFactory(asyncClientManager)
  val asyncClientAdapter = new AsyncMongoClientAdapter(asyncCollectionFactory, new TwitterFutureMongoCallbackFactory)
  val asyncQueryExecutor = new QueryExecutor(asyncClientAdapter, queryOptimizer, serializer)

  val blockingCollectionFactory = new TrivialORMMongoCollectionFactory(blockingClientManager)
  val blockingClientAdapter = new BlockingMongoClientAdapter(blockingCollectionFactory)
  val blockingQueryExecutor = new QueryExecutor(blockingClientAdapter, queryOptimizer, serializer)

  @Before
  override def initClientManagers(): Unit = {
    asyncClientManager.defineDb(
      SimpleRecord.mongoIdentifier,
      TrivialORMQueryTest.asyncMongoClient,
      TrivialORMQueryTest.dbName
    )
    blockingClientManager.defineDb(
      SimpleRecord.mongoIdentifier,
      TrivialORMQueryTest.blockingMongoClient,
      TrivialORMQueryTest.dbName
    )
  }

  @Test
  def canBuildQuery: Unit = {
    metaRecordToQuery(SimpleRecord).toString must_== """db.test_records.find({ })"""
    SimpleRecord.where(_.a eqs 1).toString must_== """db.test_records.find({ "a" : 1})"""
  }

  @Ignore("TODO(jacob): This hits an AccessControlException with our internal rogue hooks. Temporarily ignoring.")
  @Test
  def testAsyncCount: Unit = {
    val testRecord = SimpleRecord(5, "hi there!")
    val numInserts = 10
    val insertFuture = Futures.groupedCollect(1 to numInserts, numInserts)(_ => {
      asyncQueryExecutor.insert(testRecord)
    })

    val idSelect = SimpleRecord.select(_._id)
    val testFuture = insertFuture.flatMap(_ => {
      Future.join(
        asyncQueryExecutor.count(idSelect).map(_ must_== 10),
        asyncQueryExecutor.count(idSelect.limit(3)).map(_ must_== 3),
        asyncQueryExecutor.count(idSelect.limit(15)).map(_ must_== 10),
        asyncQueryExecutor.count(idSelect.skip(5)).map(_ must_== 5),
        asyncQueryExecutor.count(idSelect.skip(12)).map(_ must_== 0),
        asyncQueryExecutor.count(idSelect.skip(3).limit(5)).map(_ must_== 5),
        asyncQueryExecutor.count(idSelect.skip(8).limit(4)).map(_ must_== 2)
      )
    })
    Await.result(testFuture)
  }

  @Test
  def testBlockingCount: Unit = {
    val testRecord = SimpleRecord(5, "hi there!")
    val numInserts = 10
    for (_ <- 1 to numInserts) {
      blockingQueryExecutor.insert(testRecord)
    }

    val idSelect = SimpleRecord.select(_._id)
    blockingQueryExecutor.count(idSelect).unwrap must_== 10
    blockingQueryExecutor.count(idSelect.limit(3)).unwrap must_== 3
    blockingQueryExecutor.count(idSelect.limit(15)).unwrap must_== 10
    blockingQueryExecutor.count(idSelect.skip(5)).unwrap must_== 5
    blockingQueryExecutor.count(idSelect.skip(12)).unwrap must_== 0
    blockingQueryExecutor.count(idSelect.skip(3).limit(5)).unwrap must_== 5
    blockingQueryExecutor.count(idSelect.skip(8).limit(4)).unwrap must_== 2
  }

  // TODO(jacob): Uncomment and clean up these tests once their behavior is implemented.
  //
  // @Test
  // def canExecuteQuery: Unit = {
  //   executor.fetch(SimpleRecord.where(_.a eqs 1)) must_== Nil
  //   executor.count(SimpleRecord) must_== 0
  // }

  // @Test
  // def canUpsertAndGetResults: Unit = {
  //   executor.count(SimpleRecord) must_== 0

  //   executor.upsertOne(SimpleRecord.modify(_.a setTo 1).and(_.b setTo "foo"))

  //   executor.count(SimpleRecord) must_== 1

  //   val results = executor.fetch(SimpleRecord.where(_.a eqs 1))
  //   results.size must_== 1
  //   results(0).a must_== 1
  //   results(0).b must_== "foo"

  //   executor.fetch(SimpleRecord.where(_.a eqs 1).select(_.a)) must_== List(Some(1))
  //   executor.fetch(SimpleRecord.where(_.a eqs 1).select(_.b)) must_== List(Some("foo"))
  //   executor.fetch(SimpleRecord.where(_.a eqs 1).select(_.a, _.b)) must_== List((Some(1), Some("foo")))
  // }
}
