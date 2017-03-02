// Copyright 2017 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.rogue.query.test

import com.mongodb.{ConnectionString, MongoClient => BlockingMongoClient, MongoClientURI}
import com.mongodb.async.client.{MongoClients => AsyncMongoClients}
import io.fsq.field.OptionalField
import io.fsq.rogue.{InitialState, Query, QueryOptimizer, Rogue}
import io.fsq.rogue.MongoHelpers.AndCondition
import io.fsq.rogue.adapter.{AsyncMongoClientAdapter, BlockingMongoClientAdapter}
import io.fsq.rogue.adapter.callback.twitter.TwitterFutureMongoCallbackFactory
import io.fsq.rogue.connection.testlib.MongoTest
import io.fsq.rogue.query.QueryExecutor
import io.fsq.rogue.query.testlib.{TrivialORMMetaRecord, TrivialORMMongoCollectionFactory, TrivialORMRecord,
    TrivialORMRogueSerializer}
import net.liftweb.util.ConnectionIdentifier
import org.bson.Document
import org.junit.{Assert, Before, Test}


case class SimpleRecord(a: Int, b: String) extends TrivialORMRecord {
  override type Self = SimpleRecord
  override def meta = SimpleRecord
}

object SimpleRecord extends TrivialORMMetaRecord[SimpleRecord] {

  val a = new OptionalField[Int, SimpleRecord.type] {
    override val owner = SimpleRecord
    override val name = "a"
  }

  val b = new OptionalField[String, SimpleRecord.type] {
    override val owner = SimpleRecord
    override val name = "b"
  }

  override val collectionName = "test_records"

  override val connectionIdentifier = new ConnectionIdentifier {
    override def jndiName: String = "test"
    override def toString: String = jndiName
  }

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
  val asyncMongoClient = AsyncMongoClients.create(new ConnectionString(mongoAddress))
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
class TrivialORMQueryTest extends MongoTest with TrivialORMQueryTest.Implicits {

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
      SimpleRecord.connectionIdentifier,
      TrivialORMQueryTest.asyncMongoClient,
      TrivialORMQueryTest.dbName
    )
    blockingClientManager.defineDb(
      SimpleRecord.connectionIdentifier,
      TrivialORMQueryTest.blockingMongoClient,
      TrivialORMQueryTest.dbName
    )
  }

  @Test
  def canBuildQuery: Unit = {
    Assert.assertEquals(
      metaRecordToQuery(SimpleRecord).toString,
      """db.test_records.find({ })"""
    )
    Assert.assertEquals(
      SimpleRecord.where(_.a eqs 1).toString,
      """db.test_records.find({ "a" : 1})"""
    )
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
