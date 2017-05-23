// Copyright 2017 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.rogue.query.test

import com.mongodb.{ErrorCategory, MongoBulkWriteException, MongoWriteException, WriteConcern}
import com.twitter.util.{Await, Future}
import io.fsq.common.concurrent.Futures
import io.fsq.common.scala.Identity._
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
import org.junit.{Before, Test}
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
  val asyncQueryExecutor = new QueryExecutor(asyncClientAdapter, queryOptimizer, serializer) {
    override def defaultWriteConcern: WriteConcern = WriteConcern.W1
  }

  val blockingCollectionFactory = new TrivialORMMongoCollectionFactory(blockingClientManager)
  val blockingClientAdapter = new BlockingMongoClientAdapter(blockingCollectionFactory)
  val blockingQueryExecutor = new QueryExecutor(blockingClientAdapter, queryOptimizer, serializer) {
    override def defaultWriteConcern: WriteConcern = WriteConcern.W1
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

  @Test
  def canBuildQuery: Unit = {
    metaRecordToQuery(SimpleRecord).toString must_== """db.test_records.find({ })"""
    SimpleRecord.where(_.int eqs 1).toString must_== """db.test_records.find({ "int" : 1})"""
  }

  def testSingleAsyncInsert(record: SimpleRecord): Future[Unit] = {
    for {
      _ <- asyncQueryExecutor.insert(record)
      foundOpt <- asyncQueryExecutor.fetchOne(SimpleRecord.where(_.id eqs record.id))
    } yield {
      foundOpt must_== Some(record)
    }
  }

  @Test
  def testAsyncInsert: Unit = {
    val duplicateId = new ObjectId

    val testFutures = Future.join(
      testSingleAsyncInsert(SimpleRecord()),
      testSingleAsyncInsert(SimpleRecord(duplicateId)),
      testSingleAsyncInsert(newTestRecord(1))
    )

    val duplicateTestFuture = testFutures.flatMap(_ => {
      asyncQueryExecutor.insert(SimpleRecord(duplicateId))
        .map(_ => throw new Exception("Expected insertion failure on duplicate id"))
        .handle({
          case mwe: MongoWriteException => mwe.getError.getCategory match {
            case ErrorCategory.DUPLICATE_KEY => ()
            case ErrorCategory.EXECUTION_TIMEOUT | ErrorCategory.UNCATEGORIZED => throw mwe
          }
        })
    })

    Await.result(duplicateTestFuture)
  }

  def testSingleBlockingInsert(record: SimpleRecord): Unit = {
    blockingQueryExecutor.insert(record)
    blockingQueryExecutor.fetchOne(
      SimpleRecord.where(_.id eqs record.id)
    ).unwrap must_== Some(record)
  }

  @Test
  def testBlockingInsert: Unit = {
    val duplicateId = new ObjectId

    testSingleBlockingInsert(SimpleRecord())
    testSingleBlockingInsert(SimpleRecord(duplicateId))
    testSingleBlockingInsert(newTestRecord(1))

    try {
      blockingQueryExecutor.insert(SimpleRecord(duplicateId))
      throw new Exception("Expected insertion failure on duplicate id")
    } catch {
      case mwe: MongoWriteException => mwe.getError.getCategory match {
        case ErrorCategory.DUPLICATE_KEY => ()
        case ErrorCategory.EXECUTION_TIMEOUT | ErrorCategory.UNCATEGORIZED => throw mwe
      }
    }
  }

  def testSingleAsyncInsertAll(records: Seq[SimpleRecord]): Future[Unit] = {
    for {
      _ <- asyncQueryExecutor.insertAll(records)
      found <- asyncQueryExecutor.fetch(SimpleRecord.where(_.id in records.map(_.id)))
    } yield {
      found must_== records
    }
  }

  def testSingleAsyncDuplicateInsertAll(
    records: Seq[SimpleRecord],
    testFuture: () => Future[Unit] = () => Future.Unit
  ): Future[Unit] = {
    asyncQueryExecutor.insertAll(records)
      .map(_ => throw new Exception("Expected insertion failure on duplicate id"))
      .handle({
        case mbwe: MongoBulkWriteException => {
          mbwe.getWriteErrors.asScala.map(_.getCategory) match {
            case Seq(ErrorCategory.DUPLICATE_KEY) => ()
            case _ => throw mbwe
          }
        }
      }).flatMap(_ => testFuture())
  }

  /** NOTE(jacob): The following includes tests which specify behavior around how bulk
    *     writes handle duplicate keys. They can then serve as a canary during an upgrade
    *     should the underlying driver behavior change.
    */
  @Test
  def testAsyncInsertAll: Unit = {
    val emptyInsertFuture = for {
      _ <- asyncQueryExecutor.insertAll(Seq.empty[SimpleRecord])
      count <- asyncQueryExecutor.count(SimpleRecord)
    } yield {
      count must_== 0
    }

    val records = Seq(
      newTestRecord(0),
      newTestRecord(1),
      newTestRecord(2)
    )
    val testFutures = emptyInsertFuture.flatMap(_ => Future.join(
      testSingleAsyncInsertAll(Seq(newTestRecord(0))),
      testSingleAsyncInsertAll(records)
    ))

    val duplicate = SimpleRecord(new ObjectId)
    val duplicateTestFutures = testFutures.flatMap(_ => Future.join(
      testSingleAsyncDuplicateInsertAll(Seq(SimpleRecord(records(0).id))),
      testSingleAsyncDuplicateInsertAll(
        Seq(duplicate, duplicate),
        () => asyncQueryExecutor.count(SimpleRecord.where(_.id eqs duplicate.id)).map(_ must_== 1)
      )
    ))

    val others = Seq.tabulate(3)(_ => SimpleRecord())
    val duplicateBehavioralTestFutures = duplicateTestFutures.flatMap(_ => Future.join(
      testSingleAsyncDuplicateInsertAll(
        Seq(others(0), duplicate, duplicate),
        () => asyncQueryExecutor.fetchOne(SimpleRecord.where(_.id eqs others(0).id)).map(_ must_== Some(others(0)))
      ),
      testSingleAsyncDuplicateInsertAll(
        Seq(duplicate, others(1), duplicate),
        () => asyncQueryExecutor.fetchOne(SimpleRecord.where(_.id eqs others(1).id)).map(_ must_== None)
      ),
      testSingleAsyncDuplicateInsertAll(
        Seq(duplicate, duplicate, others(2)),
        () => asyncQueryExecutor.fetchOne(SimpleRecord.where(_.id eqs others(2).id)).map(_ must_== None)
      )
    ))

    Await.result(duplicateBehavioralTestFutures)
  }

  def testSingleBlockingInsertAll(records: Seq[SimpleRecord]): Unit = {
    blockingQueryExecutor.insertAll(records)
    blockingQueryExecutor.fetch(
      SimpleRecord.where(_.id in records.map(_.id))
    ).unwrap must_== records
  }

  def testSingleBlockingDuplicateInsertAll(
    records: Seq[SimpleRecord],
    test: () => Unit = () => ()
  ): Unit = {
    try {
      blockingQueryExecutor.insertAll(records)
    } catch {
      case mbwe: MongoBulkWriteException => {
        mbwe.getWriteErrors.asScala.map(_.getCategory) match {
          case Seq(ErrorCategory.DUPLICATE_KEY) => ()
          case _ => throw mbwe
        }
      }
    }
    test()
  }

  /** NOTE(jacob): The following includes tests which specify behavior around how bulk
    *     writes handle duplicate keys. They can then serve as a canary during an upgrade
    *     should the underlying driver behavior change.
    */
  @Test
  def testBlockingInsertAll: Unit = {
    blockingQueryExecutor.insertAll(Seq.empty[SimpleRecord])
    blockingQueryExecutor.count(SimpleRecord).unwrap must_== 0

    val records = Seq(
      newTestRecord(0),
      newTestRecord(1),
      newTestRecord(2)
    )
    testSingleBlockingInsertAll(Seq(newTestRecord(0)))
    testSingleBlockingInsertAll(records)

    val duplicate = SimpleRecord(new ObjectId)
    testSingleBlockingDuplicateInsertAll(Seq(SimpleRecord(records(0).id)))
    testSingleBlockingDuplicateInsertAll(
      Seq(duplicate, duplicate),
      () => blockingQueryExecutor.count(SimpleRecord.where(_.id eqs duplicate.id)).unwrap must_== 1
    )

    val others = Seq.tabulate(3)(_ => SimpleRecord())
    testSingleBlockingDuplicateInsertAll(
      Seq(others(0), duplicate, duplicate),
      () => blockingQueryExecutor.fetchOne(SimpleRecord.where(_.id eqs others(0).id)).unwrap must_== Some(others(0))
    )
    testSingleBlockingDuplicateInsertAll(
      Seq(duplicate, others(1), duplicate),
      () => blockingQueryExecutor.fetchOne(SimpleRecord.where(_.id eqs others(1).id)).unwrap must_== None
    )
    testSingleBlockingDuplicateInsertAll(
      Seq(duplicate, duplicate, others(2)),
      () => blockingQueryExecutor.fetchOne(SimpleRecord.where(_.id eqs others(2).id)).unwrap must_== None
    )
  }

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  // TODO(jacob): These fetch/fetchOne/foreach tests are basically all doing the same
  //    thing, cut down on the logic duplication here.
  @Test
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

  @Test
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

  @Test
  def testAsyncFetchOne: Unit = {
    val numInserts = 10
    val insertedFuture = Futures.groupedCollect(1 to numInserts, numInserts)(i => {
      asyncQueryExecutor.insert(newTestRecord(i))
    })

    val basicTestFuture = insertedFuture.flatMap(inserted => {
      val filteredInts = Set(0, 1)
      val filteredRecords = inserted.filter(_.int.map(filteredInts.contains(_)).getOrElse(false))

      Future.join(
        asyncQueryExecutor.fetchOne(SimpleRecord).map(_.get must beOneOf(inserted: _*)),
        asyncQueryExecutor.fetchOne(SimpleRecord.where(_.id eqs inserted.head.id)).map(_ must_== Some(inserted.head)),
        asyncQueryExecutor.fetchOne(SimpleRecord.where(_.id eqs inserted.last.id)).map(_ must_== Some(inserted.last)),
        asyncQueryExecutor.fetchOne(SimpleRecord.where(_.id eqs new ObjectId)).map(_ must_== None)
      )
    })

    val emptyRecord = SimpleRecord()
    val emptyTestFuture = for {
      _ <- basicTestFuture
      _ <- asyncQueryExecutor.insert(emptyRecord)
      fetched <- asyncQueryExecutor.fetchOne(SimpleRecord.where(_.id eqs emptyRecord.id))
    } yield {
      fetched must_== Some(emptyRecord)
    }

    Await.result(emptyTestFuture)
  }

  @Test
  def testBlockingFetchOne: Unit = {
    val numInserts = 10
    val inserted = for (i <- 1 to numInserts) yield {
      blockingQueryExecutor.insert(newTestRecord(i)).unwrap
    }

    blockingQueryExecutor.fetchOne(SimpleRecord).unwrap.get must beOneOf(inserted: _*)
    blockingQueryExecutor.fetchOne(SimpleRecord.where(_.id eqs inserted.head.id)).unwrap must_== Some(inserted.head)
    blockingQueryExecutor.fetchOne(SimpleRecord.where(_.id eqs inserted.last.id)).unwrap must_== Some(inserted.last)
    blockingQueryExecutor.fetchOne(SimpleRecord.where(_.id eqs new ObjectId)).unwrap must_== None

    val emptyRecord = SimpleRecord()
    blockingQueryExecutor.insert(emptyRecord)
    blockingQueryExecutor.fetchOne(SimpleRecord.where(_.id eqs emptyRecord.id)).unwrap must_== Some(emptyRecord)
  }

  def testSingleAsyncForeachQuery(
    query: Query[SimpleRecord.type, SimpleRecord, _],
    expected: Seq[SimpleRecord]
  ): Future[Unit] = {
    val accumulator = Vector.newBuilder[SimpleRecord]
    asyncQueryExecutor.foreach(query)(accumulator += _).map(_ => {
      accumulator.result() must containTheSameElementsAs(expected)
    })
  }

  @Test
  def testAsyncForeach: Unit = {
    val numInserts = 10
    val insertedFuture = Futures.groupedCollect(1 to numInserts, numInserts)(i => {
      asyncQueryExecutor.insert(newTestRecord(i))
    })

    val basicTestFuture = insertedFuture.flatMap(inserted => {
      val filteredInts = Set(0, 1)
      val filteredRecords = inserted.filter(_.int.map(filteredInts.contains(_)).getOrElse(false))

      Future.join(
        testSingleAsyncForeachQuery(SimpleRecord, inserted),
        testSingleAsyncForeachQuery(SimpleRecord.where(_.id eqs inserted.head.id), Seq(inserted.head)),
        testSingleAsyncForeachQuery(SimpleRecord.where(_.id eqs new ObjectId), Seq.empty),
        testSingleAsyncForeachQuery(SimpleRecord.where(_.int in filteredInts), filteredRecords)
      )
    })

    val emptyRecord = SimpleRecord()
    val emptyTestFuture = for {
      _ <- basicTestFuture
      _ <- asyncQueryExecutor.insert(emptyRecord)
      _ <- testSingleAsyncForeachQuery(SimpleRecord.where(_.id eqs emptyRecord.id), Seq(emptyRecord))
    } yield ()

    Await.result(emptyTestFuture)
  }

  def testSingleBlockingForeachQuery(
    query: Query[SimpleRecord.type, SimpleRecord, _],
    expected: Seq[SimpleRecord]
  ): Unit = {
    val accumulator = Vector.newBuilder[SimpleRecord]
    blockingQueryExecutor.foreach(query)(accumulator += _)
    accumulator.result() must containTheSameElementsAs(expected)
  }

  @Test
  def testBlockingForeach: Unit = {
    val numInserts = 10
    val inserted = for (i <- 1 to numInserts) yield {
      blockingQueryExecutor.insert(newTestRecord(i)).unwrap
    }

    testSingleBlockingForeachQuery(SimpleRecord, inserted)
    testSingleBlockingForeachQuery(SimpleRecord.where(_.id eqs inserted.head.id), Seq(inserted.head))
    testSingleBlockingForeachQuery(SimpleRecord.where(_.id eqs new ObjectId), Seq.empty)

    val filteredInts = Set(0, 1)
    val filteredRecords = inserted.filter(_.int.map(filteredInts.contains(_)).getOrElse(false))
    testSingleBlockingForeachQuery(SimpleRecord.where(_.int in filteredInts), filteredRecords)

    val emptyRecord = SimpleRecord()
    blockingQueryExecutor.insert(emptyRecord)
    testSingleBlockingForeachQuery(SimpleRecord.where(_.id eqs emptyRecord.id), Seq(emptyRecord))
  }

  @Test
  def testAsyncFetchBatch: Unit = {
    val numInserts = 20
    val evenBatchSize = 5
    val oddBatchSize = 7
    val insertedFuture = Futures.groupedCollect(1 to numInserts, numInserts)(i => {
      asyncQueryExecutor.insert(newTestRecord(i))
    })

    val testFuture = insertedFuture.flatMap(inserted => {
      Future.join(
        asyncQueryExecutor.fetchBatch(
          SimpleRecord,
          evenBatchSize
        )(
          _.map(_.id)
        ).map(_ must containTheSameElementsAs(inserted.map(_.id))),

        asyncQueryExecutor.fetchBatch(
          SimpleRecord,
          oddBatchSize
        )(
          _.map(_.id)
        ).map(_ must containTheSameElementsAs(inserted.map(_.id))),

        asyncQueryExecutor.fetchBatch(
          SimpleRecord.where(_.id eqs inserted.head.id),
          evenBatchSize
        )(
          _.map(_.id)
        ).map(_ must_== Seq(inserted.head.id)),

        asyncQueryExecutor.fetchBatch(
          SimpleRecord.where(_.id eqs new ObjectId),
          evenBatchSize
        )(
          _.map(_.id)
        ).map(_ must beEmpty)
      )
    })

    Await.result(testFuture)
  }

  @Test
  def testBlockingFetchBatch: Unit = {
    val numInserts = 20
    val evenBatchSize = 5
    val oddBatchSize = 7
    val inserted = for (i <- 1 to numInserts) yield {
      blockingQueryExecutor.insert(newTestRecord(i)).unwrap
    }

    blockingQueryExecutor.fetchBatch(
      SimpleRecord,
      evenBatchSize
    )(
      _.map(_.id)
    ).unwrap must containTheSameElementsAs(inserted.map(_.id))

    blockingQueryExecutor.fetchBatch(
      SimpleRecord,
      oddBatchSize
    )(
      _.map(_.id)
    ).unwrap must containTheSameElementsAs(inserted.map(_.id))

    blockingQueryExecutor.fetchBatch(
      SimpleRecord.where(_.id eqs inserted.head.id),
      evenBatchSize
    )(
      _.map(_.id)
    ).unwrap must_== Seq(inserted.head.id)

    blockingQueryExecutor.fetchBatch(
      SimpleRecord.where(_.id eqs new ObjectId),
      evenBatchSize
    )(
      _.map(_.id)
    ).unwrap must beEmpty
  }
}
