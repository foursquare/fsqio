// Copyright 2017 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.rogue.query.test

import com.twitter.util.{Await, Future}
import io.fsq.common.concurrent.Futures
import io.fsq.field.{OptionalField, RequiredField}
import io.fsq.rogue.{InitialState, Query, QueryOptimizer, Rogue}
import io.fsq.rogue.MongoHelpers.AndCondition
import io.fsq.rogue.adapter.{AsyncMongoClientAdapter, BlockingMongoClientAdapter, BlockingResult}
import io.fsq.rogue.adapter.callback.twitter.TwitterFutureMongoCallbackFactory
import io.fsq.rogue.connection.MongoIdentifier
import io.fsq.rogue.connection.testlib.RogueMongoTest
import io.fsq.rogue.query.QueryExecutor
import io.fsq.rogue.query.testlib.{TrivialORMMetaRecord, TrivialORMMongoCollectionFactory, TrivialORMRecord,
    TrivialORMRogueSerializer}
import org.bson.Document
import org.bson.types.ObjectId
import org.junit.{Before, Ignore}
import org.specs2.matcher.{JUnitMustMatchers, MatchersImplicits}
import scala.collection.JavaConverters.{iterableAsScalaIterableConverter, mapAsJavaMapConverter,
    mapAsScalaMapConverter, seqAsJavaListConverter}


case class SimpleRecord(
  id: ObjectId = new ObjectId,
  boolean: Option[Boolean] = None,
  int: Option[Int] = None,
  long: Option[Long] = None,
  double: Option[Double] = None,
  string: Option[String] = None,
  vector: Option[Vector[Int]] = None,
  map: Option[Map[String, Int]] = None
) extends TrivialORMRecord {
  override type Self = SimpleRecord
  override def meta = SimpleRecord
}

object SimpleRecord extends TrivialORMMetaRecord[SimpleRecord] {

  val id = new RequiredField[ObjectId, SimpleRecord.type] {
    override val owner = SimpleRecord
    override val name = "_id"
    override def defaultValue: ObjectId = new ObjectId
  }

  val boolean = new OptionalField[Boolean, SimpleRecord.type] {
    override val owner = SimpleRecord
    override val name = "boolean"
  }

  val int = new OptionalField[Int, SimpleRecord.type] {
    override val owner = SimpleRecord
    override val name = "int"
  }

  val long = new OptionalField[Long, SimpleRecord.type] {
    override val owner = SimpleRecord
    override val name = "long"
  }

  val double = new OptionalField[Double, SimpleRecord.type] {
    override val owner = SimpleRecord
    override val name = "double"
  }

  val string = new OptionalField[String, SimpleRecord.type] {
    override val owner = SimpleRecord
    override val name = "string"
  }

  val vector = new OptionalField[Vector[Int], SimpleRecord.type] {
    override val owner = SimpleRecord
    override val name = "vector"
  }

  val map = new OptionalField[Map[String, Int], SimpleRecord.type] {
    override val owner = SimpleRecord
    override val name = "map"
  }

  override val collectionName = "test_records"

  override val mongoIdentifier = MongoIdentifier("test")

  override def fromDocument(document: Document): SimpleRecord = {
    SimpleRecord(
      document.getObjectId(id.name),
      Option(document.getBoolean(boolean.name).asInstanceOf[Boolean]),
      Option(document.getInteger(int.name).asInstanceOf[Int]),
      Option(document.getLong(long.name).asInstanceOf[Long]),
      Option(document.getDouble(double.name).asInstanceOf[Double]),
      Option(document.getString(string.name)),
      Option(document.get(vector.name, classOf[java.util.List[Int]])).map(_.asScala.toVector),
      Option(document.get(map.name, classOf[Document])).map(_.asScala.toMap.asInstanceOf[Map[String, Int]])
    )
  }

  override def toDocument(record: SimpleRecord): Document = {
    val document = new Document

    document.append(id.name, record.id)
    record.boolean.foreach(document.append(boolean.name, _))
    record.int.foreach(document.append(int.name, _))
    record.long.foreach(document.append(long.name, _))
    record.double.foreach(document.append(double.name, _))
    record.string.foreach(document.append(string.name, _))
    record.vector.foreach(vectorVal => document.append(vector.name, vectorVal.asJava))
    record.map.foreach(mapVal => {
      document.append(map.name, new Document(mapVal.asInstanceOf[Map[String, Object]].asJava))
    })

    document
  }
}

object TrivialORMQueryTest {

  val dbName = "test"

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
class TrivialORMQueryTest extends RogueMongoTest
  with JUnitMustMatchers
  with MatchersImplicits
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
      asyncMongoClient,
      TrivialORMQueryTest.dbName
    )
    blockingClientManager.defineDb(
      SimpleRecord.mongoIdentifier,
      blockingMongoClient,
      TrivialORMQueryTest.dbName
    )
  }

  @Ignore
  def canBuildQuery: Unit = {
    metaRecordToQuery(SimpleRecord).toString must_== """db.test_records.find({ })"""
    SimpleRecord.where(_.int eqs 1).toString must_== """db.test_records.find({ "int" : 1})"""
  }

  @Ignore
  def testAsyncCount: Unit = {
    val numInserts = 10
    val insertFuture = Futures.groupedCollect(1 to numInserts, numInserts)(_ => {
      asyncQueryExecutor.insert(SimpleRecord())
    })

    val idSelect = SimpleRecord.select(_.id)
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

  @Ignore
  def testBlockingCount: Unit = {
    val numInserts = 10
    for (_ <- 1 to numInserts) {
      blockingQueryExecutor.insert(SimpleRecord())
    }

    val idSelect = SimpleRecord.select(_.id)
    blockingQueryExecutor.count(idSelect).unwrap must_== 10
    blockingQueryExecutor.count(idSelect.limit(3)).unwrap must_== 3
    blockingQueryExecutor.count(idSelect.limit(15)).unwrap must_== 10
    blockingQueryExecutor.count(idSelect.skip(5)).unwrap must_== 5
    blockingQueryExecutor.count(idSelect.skip(12)).unwrap must_== 0
    blockingQueryExecutor.count(idSelect.skip(3).limit(5)).unwrap must_== 5
    blockingQueryExecutor.count(idSelect.skip(8).limit(4)).unwrap must_== 2
  }

  private def newTestRecord(recordIndex: Int): SimpleRecord = SimpleRecord(
    new ObjectId,
    Some(false),
    Some(recordIndex % 3),
    Some(6L),
    Some(8.5),
    Some("hello"),
    Some(Vector(recordIndex % 2, recordIndex % 4)),
    Some(Map("modThree" -> recordIndex % 3))
  )

  @Ignore
  def testAsyncDistinct: Unit = {
    val numInserts = 10
    val insertFuture = Futures.groupedCollect(1 to numInserts, numInserts)(i => {
      asyncQueryExecutor.insert(newTestRecord(i))
    })

    val staticId = new ObjectId
    val staticIdTestFuture = asyncQueryExecutor.insert(SimpleRecord(staticId)).flatMap(_ => {
      Future.join(
        asyncQueryExecutor.distinct(SimpleRecord.where(_.id eqs staticId))(_.id).map(_ must_== Seq(staticId)),
        asyncQueryExecutor.distinct(SimpleRecord.where(_.id eqs staticId))(_.boolean).map(_ must_== Seq.empty)
      )
    })

    val allFieldTestFuture = insertFuture.flatMap(_ => {
      Future.join(
        asyncQueryExecutor.distinct(SimpleRecord.where(_.id neqs staticId))(_.id).map(_.size must_== numInserts),
        asyncQueryExecutor.distinct(SimpleRecord)(_.boolean).map(_ must_== Seq(false)),
        asyncQueryExecutor.distinct(SimpleRecord)(_.int).map(_ must containTheSameElementsAs(Seq(0, 1, 2))),
        asyncQueryExecutor.distinct(SimpleRecord)(_.long).map(_ must_== Seq(6L)),
        asyncQueryExecutor.distinct(SimpleRecord)(_.double).map(_ must_== Seq(8.5)),
        asyncQueryExecutor.distinct(SimpleRecord)(_.string).map(_ must_== Seq("hello")),
        asyncQueryExecutor.distinct(SimpleRecord)(_.vector).map(_ must containTheSameElementsAs(Seq(0, 1, 2, 3))),
        asyncQueryExecutor.distinct(SimpleRecord)(
          _.map,
          _.asInstanceOf[java.util.Map[String, Int]].asScala.toMap
        ).map(_ must containTheSameElementsAs(Seq(
          Map("modThree" -> 0),
          Map("modThree" -> 1),
          Map("modThree" -> 2)
        )))
      )
    })

    Await.result(Future.join(staticIdTestFuture, allFieldTestFuture))
  }

  @Ignore
  def testBlockingDistinct: Unit = {
    val numInserts = 10
    for (i <- 1 to numInserts) {
      blockingQueryExecutor.insert(newTestRecord(i))
    }

    val staticId = new ObjectId
    blockingQueryExecutor.insert(SimpleRecord(staticId))
    blockingQueryExecutor.distinct(SimpleRecord.where(_.id eqs staticId))(_.id).unwrap must_== Seq(staticId)
    blockingQueryExecutor.distinct(SimpleRecord.where(_.id eqs staticId))(_.boolean).unwrap must_== Seq.empty

    blockingQueryExecutor.distinct(SimpleRecord.where(_.id neqs staticId))(_.id).unwrap.size must_== numInserts
    blockingQueryExecutor.distinct(SimpleRecord)(_.boolean).unwrap must_== Seq(false)
    blockingQueryExecutor.distinct(SimpleRecord)(_.int).unwrap must containTheSameElementsAs(Seq(0, 1, 2))
    blockingQueryExecutor.distinct(SimpleRecord)(_.long).unwrap must_== Seq(6L)
    blockingQueryExecutor.distinct(SimpleRecord)(_.double).unwrap must_== Seq(8.5)
    blockingQueryExecutor.distinct(SimpleRecord)(_.string).unwrap must_== Seq("hello")
    blockingQueryExecutor.distinct(SimpleRecord)(_.vector).unwrap must containTheSameElementsAs(Seq(0, 1, 2, 3))
    blockingQueryExecutor.distinct(SimpleRecord)(
      _.map,
      _.asInstanceOf[java.util.Map[String, Int]].asScala.toMap
    ).unwrap must containTheSameElementsAs(Seq(
      Map("modThree" -> 0),
      Map("modThree" -> 1),
      Map("modThree" -> 2)
    ))
  }

  @Ignore
  def testAsyncCountDistinct: Unit = {
    val numInserts = 10
    val insertFuture = Futures.groupedCollect(1 to numInserts, numInserts)(i => {
      asyncQueryExecutor.insert(newTestRecord(i))
    })

    val staticId = new ObjectId
    val staticIdTestFuture = asyncQueryExecutor.insert(SimpleRecord(staticId)).flatMap(_ => {
      Future.join(
        asyncQueryExecutor.countDistinct(SimpleRecord.where(_.id eqs staticId))(_.id).map(_ must_== 1),
        asyncQueryExecutor.countDistinct(SimpleRecord.where(_.id eqs staticId))(_.boolean).map(_ must_== 0)
      )
    })

    val allFieldTestFuture = insertFuture.flatMap(_ => {
      Future.join(
        asyncQueryExecutor.countDistinct(SimpleRecord.where(_.id neqs staticId))(_.id).map(_ must_== numInserts),
        asyncQueryExecutor.countDistinct(SimpleRecord)(_.boolean).map(_ must_== 1),
        asyncQueryExecutor.countDistinct(SimpleRecord)(_.int).map(_ must_== 3),
        asyncQueryExecutor.countDistinct(SimpleRecord)(_.long).map(_ must_== 1),
        asyncQueryExecutor.countDistinct(SimpleRecord)(_.double).map(_ must_== 1),
        asyncQueryExecutor.countDistinct(SimpleRecord)(_.string).map(_ must_== 1),
        asyncQueryExecutor.countDistinct(SimpleRecord)(_.vector).map(_ must_== 4),
        asyncQueryExecutor.countDistinct(SimpleRecord)(_.map).map(_ must_== 3)
      )
    })

    Await.result(Future.join(staticIdTestFuture, allFieldTestFuture))
  }

  @Ignore
  def testBlockingCountDistinct: Unit = {
    val numInserts = 10
    for (i <- 1 to numInserts) {
      blockingQueryExecutor.insert(newTestRecord(i))
    }

    val staticId = new ObjectId
    blockingQueryExecutor.insert(SimpleRecord(staticId))
    blockingQueryExecutor.countDistinct(SimpleRecord.where(_.id eqs staticId))(_.id).unwrap must_== 1
    blockingQueryExecutor.countDistinct(SimpleRecord.where(_.id eqs staticId))(_.boolean).unwrap must_== 0

    blockingQueryExecutor.countDistinct(SimpleRecord.where(_.id neqs staticId))(_.id).unwrap must_== numInserts
    blockingQueryExecutor.countDistinct(SimpleRecord)(_.boolean).unwrap must_== 1
    blockingQueryExecutor.countDistinct(SimpleRecord)(_.int).unwrap must_== 3
    blockingQueryExecutor.countDistinct(SimpleRecord)(_.long).unwrap must_== 1
    blockingQueryExecutor.countDistinct(SimpleRecord)(_.double).unwrap must_== 1
    blockingQueryExecutor.countDistinct(SimpleRecord)(_.string).unwrap must_== 1
    blockingQueryExecutor.countDistinct(SimpleRecord)(_.vector).unwrap must_== 4
    blockingQueryExecutor.countDistinct(SimpleRecord)(_.map).unwrap must_== 3
  }

  @Ignore
  def testAsyncFetch: Unit = {
    val numInserts = 10
    val insertedFuture = Futures.groupedCollect(1 to numInserts, numInserts)(i => {
      asyncQueryExecutor.insert(newTestRecord(i))
    })

    val basicTestFuture = insertedFuture.flatMap(inserted => {
      val filteredInts = Set(0, 1)
      val filteredRecords = inserted.filter(_.int.map(filteredInts.contains(_)).getOrElse(false))

      Future.join(
        asyncQueryExecutor.fetch(SimpleRecord).map(_ must containTheSameElementsAs(inserted)),
        asyncQueryExecutor.fetch(SimpleRecord.where(_.id eqs inserted.head.id)).map(_ must_== Seq(inserted.head)),
        asyncQueryExecutor.fetch(SimpleRecord.where(_.id eqs new ObjectId)).map(_ must beEmpty),
        asyncQueryExecutor.fetch(
          SimpleRecord.where(_.int in filteredInts)
        ).map(_ must containTheSameElementsAs(filteredRecords))
      )
    })

    val emptyRecord = SimpleRecord()
    val emptyTestFuture = for {
      _ <- basicTestFuture
      _ <- asyncQueryExecutor.insert(emptyRecord)
      fetched <- asyncQueryExecutor.fetch(SimpleRecord.where(_.id eqs emptyRecord.id))
    } yield {
      fetched must_== Seq(emptyRecord)
    }

    Await.result(emptyTestFuture)
  }

  @Ignore
  def testBlockingFetch: Unit = {
    val numInserts = 10
    val inserted = for (i <- 1 to numInserts) yield {
      blockingQueryExecutor.insert(newTestRecord(i)).unwrap
    }

    blockingQueryExecutor.fetch(SimpleRecord).unwrap must containTheSameElementsAs(inserted)
    blockingQueryExecutor.fetch(SimpleRecord.where(_.id eqs inserted.head.id)).unwrap must_== Seq(inserted.head)
    blockingQueryExecutor.fetch(SimpleRecord.where(_.id eqs new ObjectId)).unwrap must beEmpty

    val filteredInts = Set(0, 1)
    val filteredRecords = inserted.filter(_.int.map(filteredInts.contains(_)).getOrElse(false))
    blockingQueryExecutor.fetch(
      SimpleRecord.where(_.int in filteredInts)
    ).unwrap must containTheSameElementsAs(filteredRecords)

    val emptyRecord = SimpleRecord()
    blockingQueryExecutor.insert(emptyRecord)
    blockingQueryExecutor.fetch(SimpleRecord.where(_.id eqs emptyRecord.id)).unwrap must_== Seq(emptyRecord)
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
