// Copyright 2017 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.rogue.query.test

import com.mongodb.{ErrorCategory, MongoBulkWriteException, MongoCommandException, MongoWriteException, WriteConcern}
import com.mongodb.bulk.{BulkWriteResult, BulkWriteUpsert}
import com.mongodb.client.{MongoCollection => BlockingMongoCollection, MongoDatabase}
import com.mongodb.client.model.CountOptions
import com.mongodb.reactivestreams.client.{MongoCollection => AsyncMongoCollection}
import com.twitter.util.{Await, Duration, Future}
import io.fsq.common.concurrent.Futures
import io.fsq.common.scala.Lists.Implicits._
import io.fsq.field.{OptionalField, RequiredField}
import io.fsq.rogue.{
  BulkInsertOne,
  BulkRemove,
  BulkRemoveOne,
  BulkReplaceOne,
  BulkUpdateMany,
  BulkUpdateOne,
  InitialState,
  Iter,
  Query,
  QueryOptimizer,
  Rogue,
  RogueException
}
import io.fsq.rogue.MongoHelpers.AndCondition
import io.fsq.rogue.adapter.{BlockingMongoClientAdapter, BlockingResult}
import io.fsq.rogue.adapter.twitter.{AsyncMongoClientAdapter, TwitterAsyncUtil}
import io.fsq.rogue.connection.MongoIdentifier
import io.fsq.rogue.connection.testlib.RogueMongoTest
import io.fsq.rogue.index.{Asc, Desc, MongoIndex}
import io.fsq.rogue.query.QueryExecutor
import io.fsq.rogue.query.testlib.{
  TrivialORMMetaRecord,
  TrivialORMMongoCollectionFactory,
  TrivialORMRecord,
  TrivialORMRogueSerializer
}
import io.fsq.rogue.util.{DefaultQueryLogger, DefaultQueryUtilities, QueryLogger}
import java.util.{ArrayList, List => JavaList}
import java.util.concurrent.{CyclicBarrier, TimeUnit}
import org.bson.{BsonObjectId, Document}
import org.bson.conversions.Bson
import org.bson.types.ObjectId
import org.junit.{Assert, Before, Test}
import org.specs2.matcher.{JUnitMustMatchers, MatchersImplicits}
import scala.collection.JavaConverters._
import scala.math.min

object SerializeUtil {
  def nestedFieldValueFromDocument(document: Document): Map[String, Map[String, ObjectId]] = {
    document.asScala.toMap
      .asInstanceOf[Map[String, Object]]
      .map({
        case (key, value) => (key, value.asInstanceOf[Document].asScala.toMap.asInstanceOf[Map[String, ObjectId]])
      })
  }

  def nestedFieldValueToDocument(nestedMapValue: Map[String, Map[String, ObjectId]]): Document = {
    val document = new Document
    nestedMapValue.foreach({
      case (key, value) =>
        document.append(key, new Document(value.asInstanceOf[Map[String, Object]].asJava))
    })
    document
  }
}

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
  override def meta: SimpleRecord.type = SimpleRecord
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

case class OptionalNestedIdRecord(id: Option[Map[String, Map[String, ObjectId]]] = None, int: Option[Int] = None)
  extends TrivialORMRecord {
  override type Self = OptionalNestedIdRecord
  override def meta: OptionalNestedIdRecord.type = OptionalNestedIdRecord
}

object OptionalNestedIdRecord extends TrivialORMMetaRecord[OptionalNestedIdRecord] {

  val id = new OptionalField[ObjectId, OptionalNestedIdRecord.type] {
    override val owner = OptionalNestedIdRecord
    override val name = "_id"
  }

  val int = new OptionalField[Int, OptionalNestedIdRecord.type] {
    override val owner = OptionalNestedIdRecord
    override val name = "int"
  }

  override val collectionName = "test_optional_nested_id_records"

  override val mongoIdentifier = MongoIdentifier("test")

  override def fromDocument(document: Document): OptionalNestedIdRecord = {
    OptionalNestedIdRecord(
      Option(document.get(id.name, classOf[Document])).map(SerializeUtil.nestedFieldValueFromDocument(_)),
      Option(document.getInteger(int.name).asInstanceOf[Int])
    )
  }

  override def toDocument(record: OptionalNestedIdRecord): Document = {
    val document = new Document
    record.int.foreach(document.append(int.name, _))
    record.id.foreach({ value =>
      document.append(id.name, SerializeUtil.nestedFieldValueToDocument(value))
    })
    document
  }
}

case class OptionalIdRecord(
  id: Option[ObjectId] = None,
  int: Option[Int] = None,
  nestedMap: Option[Map[String, Map[String, ObjectId]]] = None
) extends TrivialORMRecord {
  override type Self = OptionalIdRecord
  override def meta: OptionalIdRecord.type = OptionalIdRecord
}

object OptionalIdRecord extends TrivialORMMetaRecord[OptionalIdRecord] {

  val id = new OptionalField[ObjectId, OptionalIdRecord.type] {
    override val owner = OptionalIdRecord
    override val name = "_id"
  }

  val int = new OptionalField[Int, OptionalIdRecord.type] {
    override val owner = OptionalIdRecord
    override val name = "int"
  }

  val nestedMap = new OptionalField[Map[String, Map[String, ObjectId]], OptionalIdRecord.type] {
    override val owner = OptionalIdRecord
    override val name = "nested_map"
  }

  override val collectionName = "test_optional_records"

  override val mongoIdentifier = MongoIdentifier("test")

  override def fromDocument(document: Document): OptionalIdRecord = {
    OptionalIdRecord(
      Option(document.getObjectId(id.name)),
      Option(document.getInteger(int.name).asInstanceOf[Int]),
      Option(document.get(nestedMap.name, classOf[Document])).map(SerializeUtil.nestedFieldValueFromDocument(_))
    )
  }

  override def toDocument(record: OptionalIdRecord): Document = {
    val document = new Document
    record.id.foreach(document.append(id.name, _))
    record.int.foreach(document.append(int.name, _))
    record.nestedMap.foreach({ value =>
      document.append(nestedMap.name, SerializeUtil.nestedFieldValueToDocument(value))
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
class TrivialORMQueryTest
  extends RogueMongoTest
  with JUnitMustMatchers
  with MatchersImplicits
  with BlockingResult.Implicits
  with TrivialORMQueryTest.Implicits {

  val queryOptimizer = new QueryOptimizer
  val serializer = new TrivialORMRogueSerializer

  val asyncCollectionFactory = new TrivialORMMongoCollectionFactory(asyncClientManager)
  val asyncClientAdapter = new AsyncMongoClientAdapter(
    asyncCollectionFactory,
    new DefaultQueryUtilities[Future]
  )
  val asyncQueryExecutor = new QueryExecutor(asyncClientAdapter, queryOptimizer, serializer) {
    override def defaultWriteConcern: WriteConcern = WriteConcern.W1
  }

  val blockingCollectionFactory = new TrivialORMMongoCollectionFactory(blockingClientManager)
  val blockingClientAdapter = new BlockingMongoClientAdapter(
    blockingCollectionFactory,
    new DefaultQueryUtilities[BlockingResult]
  )
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
      () => asyncMongoClient,
      TrivialORMQueryTest.dbName
    )
    blockingClientManager.defineDb(
      SimpleRecord.mongoIdentifier,
      () => blockingMongoClient,
      TrivialORMQueryTest.dbName
    )
  }

  @Test
  def canBuildQuery: Unit = {
    metaRecordToQuery(SimpleRecord).toString must_== """db.test_records.find({})"""
    SimpleRecord.where(_.int eqs 1).toString must_== """db.test_records.find({"int": 1})"""
  }

  /** Ensure correct throwable encoding -- we catch and encode Exceptions as
    * RogueExceptions, but any Errors are left untouched.
    */
  @Test
  def testRogueExceptionEncoding: Unit = {
    var toThrow: Throwable = new IllegalArgumentException

    val testAsyncClientAdapter = new AsyncMongoClientAdapter(
      asyncCollectionFactory,
      new DefaultQueryUtilities[Future]
    ) {
      override protected def countImpl(
        collection: AsyncMongoCollection[Document]
      )(
        filter: Bson,
        options: CountOptions
      ): Future[Long] = {
        TwitterAsyncUtil
          .optResult[java.lang.Long](
            collection.countDocuments(filter, options)
          )
          .map(
            javaLong => throw toThrow
          )
      }
    }

    val testAsyncQueryExecutor = new QueryExecutor(
      testAsyncClientAdapter,
      queryOptimizer,
      serializer
    )

    val testBlockingClientAdapter = new BlockingMongoClientAdapter(
      blockingCollectionFactory,
      new DefaultQueryUtilities[BlockingResult]
    ) {
      override protected def countImpl(
        collection: BlockingMongoCollection[Document]
      )(
        filter: Bson,
        options: CountOptions
      ): BlockingResult[Long] = {
        throw toThrow
      }
    }

    val testBlockingQueryExecutor = new QueryExecutor(
      testBlockingClientAdapter,
      queryOptimizer,
      serializer
    )

    Await.result(testAsyncQueryExecutor.count(SimpleRecord), Duration.fromSeconds(5)) must throwA[RogueException]
    testBlockingQueryExecutor.count(SimpleRecord) must throwA[RogueException]

    toThrow = new Error

    Await.result(testAsyncQueryExecutor.count(SimpleRecord), Duration.fromSeconds(5)) must throwA[Error]
    testBlockingQueryExecutor.count(SimpleRecord) must throwA[Error]
  }

  /** Ensure correct logging of asynchronous queries -- this test will fail if logging is
    * run immediately upon invocation, as opposed to being asynchronously triggered upon
    * completion.
    */
  @Test
  def testAsyncLogging(): Unit = {
    val barrier = new CyclicBarrier(2)
    var countRun = false

    val testQueryLogger = new DefaultQueryLogger[Future] {
      override def log(
        query: Query[_, _, _],
        instanceName: String,
        msg: => String,
        timeMillis: Long
      ): Unit = {
        countRun must_== true
      }
    }
    val testQueryUtilities = new DefaultQueryUtilities[Future] {
      override val logger: QueryLogger[Future] = testQueryLogger
    }

    val testClientAdapter = new AsyncMongoClientAdapter(
      asyncCollectionFactory,
      testQueryUtilities
    ) {
      override protected def countImpl(
        collection: AsyncMongoCollection[Document]
      )(
        filter: Bson,
        options: CountOptions
      ): Future[Long] = {
        TwitterAsyncUtil
          .optResult(
            collection.countDocuments(filter, options)
          )
          .map(javaLongOpt => {
            barrier.await(1, TimeUnit.SECONDS)
            countRun = true
            javaLongOpt.map(l => l: Long).getOrElse(0)
          })
      }
    }

    val testQueryExecutor = new QueryExecutor(testClientAdapter, queryOptimizer, serializer)

    val countFuture = try {
      testQueryExecutor.count(SimpleRecord)
    } finally {
      barrier.await(1, TimeUnit.SECONDS)
    }
    Await.result(countFuture, Duration.fromSeconds(5))
  }

  def testSingleAsyncSave(record: SimpleRecord): Future[Unit] = {
    for {
      _ <- asyncQueryExecutor.save(record)
      foundOpt <- asyncQueryExecutor.fetchOne(SimpleRecord.where(_.id eqs record.id))
    } yield {
      foundOpt must_== Some(record)
    }
  }

  @Test
  def testAsyncSave(): Unit = {
    val duplicateId = new ObjectId
    val noIdInt = 24601
    val noIdRecord = OptionalIdRecord(int = Some(noIdInt), nestedMap = Some(Map("a" -> Map("b" -> new ObjectId()))))

    val testFutures = Future.join(
      testSingleAsyncSave(SimpleRecord()),
      testSingleAsyncSave(SimpleRecord(duplicateId)),
      testSingleAsyncSave(newTestRecord(1))
    )

    // Save existing record with modification
    val duplicateTestFuture = for {
      _ <- testFutures
      _ <- testSingleAsyncSave(SimpleRecord(duplicateId, int = Some(5)))
      _ <- asyncQueryExecutor.count(SimpleRecord).map(_ must_== 3)
    } yield ()

    val noIdRecordTestFuture = for {
      // record was given id
      _ <- asyncQueryExecutor.save(noIdRecord)
      _ <- asyncQueryExecutor.fetchOne(OptionalIdRecord.where(_.int eqs noIdInt)).map(_.flatMap(_.id) must_!= None)
      // second save with no id will insert a new copy
      _ <- asyncQueryExecutor.save(noIdRecord)
      _ <- asyncQueryExecutor.countDistinct(OptionalIdRecord)(_.id).map(_ must_== 2)
    } yield ()

    val allTestFutures = Future.join(
      duplicateTestFuture,
      noIdRecordTestFuture
    )

    Await.result(allTestFutures, Duration.fromSeconds(5))
  }

  def testSingleBlockingSave(record: SimpleRecord): Unit = {
    blockingQueryExecutor.save(record)
    blockingQueryExecutor
      .fetchOne(
        SimpleRecord.where(_.id eqs record.id)
      )
      .unwrap must_== Some(record)
  }

  @Test
  def testSaveWithShardKey(): Unit = {
    val executor = buildBlockingExecutorWithOptionalShardKey(Some("nested_map.a.b"))
    val id = new ObjectId()
    val record = OptionalIdRecord(Some(id), int = None, nestedMap = Some(Map("a" -> Map("b" -> new ObjectId()))))
    executor.save(record)
    executor
      .fetchOne(
        OptionalIdRecord.where(_.id eqs id)
      )
      .unwrap must_== Some(record)
  }

  @Test
  def testSaveMultipleWithSameShardKeySameId(): Unit = {
    val executor = buildBlockingExecutorWithOptionalShardKey(Some("int"))
    val id = new ObjectId()
    val shardKeyValue = 3
    val idRecord = SimpleRecord(id = id, int = Some(shardKeyValue))
    executor.save(idRecord)
    executor.count(SimpleRecord).unwrap must_== 1
    executor
      .fetchOne(
        SimpleRecord.where(_.id eqs id)
      )
      .unwrap must_== Some(idRecord)

    val updatedRecord = SimpleRecord(id = id, int = Some(shardKeyValue), string = Some("abc"))
    executor.save(updatedRecord)
    executor.count(SimpleRecord).unwrap must_== 1
    executor
      .fetchOne(
        SimpleRecord.where(_.id eqs id)
      )
      .unwrap must_== Some(updatedRecord)
  }

  @Test
  def testSaveMultipleWithSameShardKeyDifferentId(): Unit = {
    val executor = buildBlockingExecutorWithOptionalShardKey(Some("int"))
    val shardKeyValue = 3
    val record1 = SimpleRecord(id = new ObjectId(), int = Some(shardKeyValue))
    executor.save(record1)
    executor.count(SimpleRecord).unwrap must_== 1
    executor
      .fetchOne(
        SimpleRecord.where(_.id eqs record1.id)
      )
      .unwrap must_== Some(record1)

    val record2 = SimpleRecord(id = new ObjectId(), int = Some(shardKeyValue), string = Some("abc"))
    executor.save(record2)
    executor.count(SimpleRecord).unwrap must_== 2
    executor
      .fetchOne(
        SimpleRecord.where(_.id eqs record1.id)
      )
      .unwrap must_== Some(record1)
    executor
      .fetchOne(
        SimpleRecord.where(_.id eqs record2.id)
      )
      .unwrap must_== Some(record2)
  }

  @Test(expected = classOf[MongoWriteException])
  def testSaveMultipleWithDifferentShardKeySameId(): Unit = {
    val executor = buildBlockingExecutorWithOptionalShardKey(Some("int"))
    val id = new ObjectId()
    val record1ShardKeyValue = 3
    val record1 = SimpleRecord(id = id, int = Some(record1ShardKeyValue))
    executor.save(record1)
    executor
      .fetchOne(
        SimpleRecord.where(_.id eqs id)
      )
      .unwrap must_== Some(record1)

    val record2 = SimpleRecord(id = id, int = Some(4), string = Some("abc"))
    // will attempt to insert a new record with same id as initial record since shard key is different, so duplicate
    // key error is thrown
    executor.save(record2)
  }

  @Test
  def testSaveMultipleWithDifferentShardKeyNoId(): Unit = {
    val executor = buildBlockingExecutorWithOptionalShardKey(Some("int"))
    val record1ShardKeyValue = 3
    val record1 = OptionalIdRecord(id = None, int = Some(record1ShardKeyValue))
    executor.count(OptionalIdRecord).unwrap must_== 0
    executor.save(record1)
    executor.count(OptionalIdRecord).unwrap must_== 1

    val record2ShardKeyValue = 4
    val record2 = OptionalIdRecord(id = None, int = Some(record2ShardKeyValue))
    executor.save(record2)
    executor.count(OptionalIdRecord).unwrap must_== 2
    val results = executor
      .fetch(
        OptionalIdRecord.where(_.int in Vector(record1ShardKeyValue, record2ShardKeyValue))
      )
      .unwrap

    results.size must_== 2
    results.map(_.flatMap(_.id) must_!= None)
  }

  @Test
  def testBlockingSave(): Unit = {
    val duplicateId = new ObjectId
    val noIdInt = 24601
    val noIdRecord = OptionalIdRecord(int = Some(noIdInt))

    testSingleBlockingSave(SimpleRecord())
    testSingleBlockingSave(SimpleRecord(duplicateId))
    testSingleBlockingSave(newTestRecord(1))

    // Save existing record with modification
    testSingleBlockingSave(SimpleRecord(duplicateId, int = Some(5)))
    blockingQueryExecutor.count(SimpleRecord).unwrap must_== 3

    // record was given id
    blockingQueryExecutor.save(noIdRecord)
    blockingQueryExecutor.fetchOne(OptionalIdRecord.where(_.int eqs noIdInt)).flatMap(_.id) must_!= None
    // second save with no id will insert a new copy
    blockingQueryExecutor.save(noIdRecord)
    blockingQueryExecutor.countDistinct(OptionalIdRecord)(_.id).unwrap must_== 2
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
  def testAsyncInsert(): Unit = {
    val duplicateId = new ObjectId

    val testFutures = Future.join(
      testSingleAsyncInsert(SimpleRecord()),
      testSingleAsyncInsert(SimpleRecord(duplicateId)),
      testSingleAsyncInsert(newTestRecord(1))
    )

    val duplicateTestFuture = testFutures.flatMap(_ => {
      asyncQueryExecutor
        .insert(SimpleRecord(duplicateId))
        .map(_ => throw new Exception("Expected insertion failure on duplicate id"))
        .handle({
          case mwe: MongoWriteException =>
            mwe.getError.getCategory match {
              case ErrorCategory.DUPLICATE_KEY => ()
              case ErrorCategory.EXECUTION_TIMEOUT | ErrorCategory.UNCATEGORIZED => throw mwe
            }
        })
    })

    Await.result(duplicateTestFuture, Duration.fromSeconds(5))
  }

  def testSingleBlockingInsert(record: SimpleRecord): Unit = {
    blockingQueryExecutor.insert(record)
    blockingQueryExecutor
      .fetchOne(
        SimpleRecord.where(_.id eqs record.id)
      )
      .unwrap must_== Some(record)
  }

  @Test
  def testBlockingInsert(): Unit = {
    val duplicateId = new ObjectId

    testSingleBlockingInsert(SimpleRecord())
    testSingleBlockingInsert(SimpleRecord(duplicateId))
    testSingleBlockingInsert(newTestRecord(1))

    try {
      blockingQueryExecutor.insert(SimpleRecord(duplicateId))
      throw new Exception("Expected insertion failure on duplicate id")
    } catch {
      case mwe: MongoWriteException =>
        mwe.getError.getCategory match {
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
    testFuture: () => Future[Unit]
  ): Future[Unit] = {
    asyncQueryExecutor
      .insertAll(records)
      .map(_ => throw new Exception("Expected insertion failure on duplicate id"))
      .handle({
        case mbwe: MongoBulkWriteException => {
          mbwe.getWriteErrors.asScala.map(_.getCategory) match {
            case Seq(ErrorCategory.DUPLICATE_KEY) => ()
            case _ => throw mbwe
          }
        }
      })
      .flatMap(_ => testFuture())
  }

  /** NOTE(jacob): The following includes tests which specify behavior around how bulk
    *     writes handle duplicate keys. They can then serve as a canary during an upgrade
    *     should the underlying driver behavior change.
    */
  @Test
  def testAsyncInsertAll(): Unit = {
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
    val testFutures = emptyInsertFuture.flatMap(
      _ =>
        Future.join(
          testSingleAsyncInsertAll(Seq(newTestRecord(0))),
          testSingleAsyncInsertAll(records)
        )
    )

    val duplicate = SimpleRecord(new ObjectId)
    val duplicateTestFutures = testFutures.flatMap(
      _ =>
        Future.join(
          testSingleAsyncDuplicateInsertAll(Seq(SimpleRecord(records(0).id)), () => Future.Unit),
          testSingleAsyncDuplicateInsertAll(
            Seq(duplicate, duplicate),
            () => asyncQueryExecutor.count(SimpleRecord.where(_.id eqs duplicate.id)).map(_ must_== 1)
          )
        )
    )

    val others = Seq.tabulate(3)(_ => SimpleRecord())
    val duplicateBehavioralTestFutures = duplicateTestFutures.flatMap(
      _ =>
        Future.join(
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
        )
    )

    Await.result(duplicateBehavioralTestFutures, Duration.fromSeconds(5))
  }

  def testSingleBlockingInsertAll(records: Seq[SimpleRecord]): Unit = {
    blockingQueryExecutor.insertAll(records)
    blockingQueryExecutor
      .fetch(
        SimpleRecord.where(_.id in records.map(_.id))
      )
      .unwrap must_== records
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
  def testBlockingInsertAll(): Unit = {
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
  def testAsyncCount(): Unit = {
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

    Await.result(testFuture, Duration.fromSeconds(10))
  }

  @Test
  def testBlockingCount(): Unit = {
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
  def testAsyncDistinct(): Unit = {
    val numInserts = 10
    val insertFuture = Futures.groupedCollect(1 to numInserts, numInserts)(i => {
      asyncQueryExecutor.insert(newTestRecord(i))
    })

    val staticId = new ObjectId
    val staticIdTestFuture = asyncQueryExecutor
      .insert(SimpleRecord(staticId))
      .flatMap(_ => {
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
        asyncQueryExecutor
          .distinct(SimpleRecord)(
            _.map,
            _.asInstanceOf[java.util.Map[String, Int]].asScala.toMap
          )
          .map(
            _ must containTheSameElementsAs(
              Seq(
                Map("modThree" -> 0),
                Map("modThree" -> 1),
                Map("modThree" -> 2)
              )
            )
          )
      )
    })

    Await.result(Future.join(staticIdTestFuture, allFieldTestFuture), Duration.fromSeconds(5))
  }

  @Test
  def testBlockingDistinct(): Unit = {
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
    blockingQueryExecutor
      .distinct(SimpleRecord)(
        _.map,
        _.asInstanceOf[java.util.Map[String, Int]].asScala.toMap
      )
      .unwrap must containTheSameElementsAs(
      Seq(
        Map("modThree" -> 0),
        Map("modThree" -> 1),
        Map("modThree" -> 2)
      )
    )
  }

  @Test
  def testAsyncCountDistinct(): Unit = {
    val numInserts = 10
    val insertFuture = Futures.groupedCollect(1 to numInserts, numInserts)(i => {
      asyncQueryExecutor.insert(newTestRecord(i))
    })

    val staticId = new ObjectId
    val staticIdTestFuture = asyncQueryExecutor
      .insert(SimpleRecord(staticId))
      .flatMap(_ => {
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

    Await.result(Future.join(staticIdTestFuture, allFieldTestFuture), Duration.fromSeconds(5))
  }

  @Test
  def testBlockingExplain(): Unit = {
    val staticId = new ObjectId
    blockingQueryExecutor.insert(SimpleRecord(staticId))
    val explainDoc = blockingQueryExecutor.explain(SimpleRecord.where(_.id eqs staticId)).unwrap
    explainDoc.get("executionStats").asInstanceOf[Document].get("nReturned") must_== 1
  }

  @Test
  def testAsyncExplain(): Unit = {
    val staticId = new ObjectId
    val staticIdTestFuture = asyncQueryExecutor
      .insert(SimpleRecord(staticId))
      .flatMap(_ => {
        val explainDocF = asyncQueryExecutor.explain(SimpleRecord.where(_.id eqs staticId))
        explainDocF.map(_.get("executionStats").asInstanceOf[Document].get("nReturned")).map(_ must_== 1)
      })

    Await.result(staticIdTestFuture, Duration.fromSeconds(5))
  }

  @Test
  def testBlockingCountDistinct(): Unit = {
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
  def testAsyncFetch(): Unit = {
    val numInserts = 10
    val insertedFuture = Futures.groupedCollect(1 to numInserts, numInserts)(i => {
      asyncQueryExecutor.insert(newTestRecord(i))
    })

    val basicTestFuture = insertedFuture.flatMap(inserted => {
      val filteredInts = Set(0, 1)
      val filteredRecords = inserted.filter(_.int.map(filteredInts.has(_)).getOrElse(false))

      Future.join(
        asyncQueryExecutor.fetch(SimpleRecord).map(_ must containTheSameElementsAs(inserted)),
        asyncQueryExecutor.fetch(SimpleRecord.where(_.id eqs inserted.head.id)).map(_ must_== Seq(inserted.head)),
        asyncQueryExecutor.fetch(SimpleRecord.where(_.id eqs new ObjectId)).map(_ must beEmpty),
        asyncQueryExecutor
          .fetch(
            SimpleRecord.where(_.int in filteredInts)
          )
          .map(_ must containTheSameElementsAs(filteredRecords))
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

    Await.result(emptyTestFuture, Duration.fromSeconds(10))
  }

  @Test
  def testBlockingFetch(): Unit = {
    val numInserts = 10
    val inserted = for (i <- 1 to numInserts) yield {
      blockingQueryExecutor.insert(newTestRecord(i)).unwrap
    }

    blockingQueryExecutor.fetch(SimpleRecord).unwrap must containTheSameElementsAs(inserted)
    blockingQueryExecutor.fetch(SimpleRecord.where(_.id eqs inserted.head.id)).unwrap must_== Seq(inserted.head)
    blockingQueryExecutor.fetch(SimpleRecord.where(_.id eqs new ObjectId)).unwrap must beEmpty

    val filteredInts = Set(0, 1)
    val filteredRecords = inserted.filter(_.int.map(filteredInts.has(_)).getOrElse(false))
    blockingQueryExecutor
      .fetch(
        SimpleRecord.where(_.int in filteredInts)
      )
      .unwrap must containTheSameElementsAs(filteredRecords)

    val emptyRecord = SimpleRecord()
    blockingQueryExecutor.insert(emptyRecord)
    blockingQueryExecutor.fetch(SimpleRecord.where(_.id eqs emptyRecord.id)).unwrap must_== Seq(emptyRecord)
  }

  @Test
  def testAsyncFetchOne(): Unit = {
    val numInserts = 10
    val insertedFuture = Futures.groupedCollect(1 to numInserts, numInserts)(i => {
      asyncQueryExecutor.insert(newTestRecord(i))
    })

    val basicTestFuture = insertedFuture.flatMap(inserted => {
      val filteredInts = Set(0, 1)
      val filteredRecords = inserted.filter(_.int.map(filteredInts.has(_)).getOrElse(false))

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

    Await.result(emptyTestFuture, Duration.fromSeconds(5))
  }

  @Test
  def testBlockingFetchOne(): Unit = {
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
    asyncQueryExecutor
      .foreach(query)(accumulator += _)
      .map(_ => {
        accumulator.result() must containTheSameElementsAs(expected)
      })
  }

  @Test
  def testAsyncForeach(): Unit = {
    val numInserts = 10
    val insertedFuture = Futures.groupedCollect(1 to numInserts, numInserts)(i => {
      asyncQueryExecutor.insert(newTestRecord(i))
    })

    val basicTestFuture = insertedFuture.flatMap(inserted => {
      val filteredInts = Set(0, 1)
      val filteredRecords = inserted.filter(_.int.map(filteredInts.has(_)).getOrElse(false))

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

    Await.result(emptyTestFuture, Duration.fromSeconds(5))
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
  def testBlockingForeach(): Unit = {
    val numInserts = 10
    val inserted = for (i <- 1 to numInserts) yield {
      blockingQueryExecutor.insert(newTestRecord(i)).unwrap
    }

    testSingleBlockingForeachQuery(SimpleRecord, inserted)
    testSingleBlockingForeachQuery(SimpleRecord.where(_.id eqs inserted.head.id), Seq(inserted.head))
    testSingleBlockingForeachQuery(SimpleRecord.where(_.id eqs new ObjectId), Seq.empty)

    val filteredInts = Set(0, 1)
    val filteredRecords = inserted.filter(_.int.map(filteredInts.has(_)).getOrElse(false))
    testSingleBlockingForeachQuery(SimpleRecord.where(_.int in filteredInts), filteredRecords)

    val emptyRecord = SimpleRecord()
    blockingQueryExecutor.insert(emptyRecord)
    testSingleBlockingForeachQuery(SimpleRecord.where(_.id eqs emptyRecord.id), Seq(emptyRecord))
  }

  @Test
  def testAsyncFetchBatch(): Unit = {
    val numInserts = 20
    val evenBatchSize = 5
    val oddBatchSize = 7
    val insertedFuture = Futures.groupedCollect(1 to numInserts, numInserts)(i => {
      asyncQueryExecutor.insert(newTestRecord(i))
    })

    val testFuture = insertedFuture.flatMap(inserted => {
      Future.join(
        asyncQueryExecutor
          .fetchBatch(
            SimpleRecord,
            evenBatchSize
          )(
            _.map(_.id)
          )
          .map(_ must containTheSameElementsAs(inserted.map(_.id))),
        asyncQueryExecutor
          .fetchBatch(
            SimpleRecord,
            oddBatchSize
          )(
            _.map(_.id)
          )
          .map(_ must containTheSameElementsAs(inserted.map(_.id))),
        asyncQueryExecutor
          .fetchBatch(
            SimpleRecord.where(_.id eqs inserted.head.id),
            evenBatchSize
          )(
            _.map(_.id)
          )
          .map(_ must_== Seq(inserted.head.id)),
        asyncQueryExecutor
          .fetchBatch(
            SimpleRecord.where(_.id eqs new ObjectId),
            evenBatchSize
          )(
            _.map(_.id)
          )
          .map(_ must beEmpty)
      )
    })

    Await.result(testFuture, Duration.fromSeconds(5))
  }

  @Test
  def testBlockingFetchBatch(): Unit = {
    val numInserts = 20
    val evenBatchSize = 5
    val oddBatchSize = 7
    val inserted = for (i <- 1 to numInserts) yield {
      blockingQueryExecutor.insert(newTestRecord(i)).unwrap
    }

    blockingQueryExecutor
      .fetchBatch(
        SimpleRecord,
        evenBatchSize
      )(
        _.map(_.id)
      )
      .unwrap must containTheSameElementsAs(inserted.map(_.id))

    blockingQueryExecutor
      .fetchBatch(
        SimpleRecord,
        oddBatchSize
      )(
        _.map(_.id)
      )
      .unwrap must containTheSameElementsAs(inserted.map(_.id))

    blockingQueryExecutor
      .fetchBatch(
        SimpleRecord.where(_.id eqs inserted.head.id),
        evenBatchSize
      )(
        _.map(_.id)
      )
      .unwrap must_== Seq(inserted.head.id)

    blockingQueryExecutor
      .fetchBatch(
        SimpleRecord.where(_.id eqs new ObjectId),
        evenBatchSize
      )(
        _.map(_.id)
      )
      .unwrap must beEmpty
  }

  @Test
  def testAsyncRemove(): Unit = {
    val emptyRecord = SimpleRecord()
    val fullRecord1 = newTestRecord(1)
    val modifiedFullRecord1 = fullRecord1.copy(int = Some(5))
    val fullRecord2 = newTestRecord(2)
    val modifiedFullRecord2 = fullRecord2.copy(int = None)

    val testFutures = Future.join(
      asyncQueryExecutor.remove(SimpleRecord()).map(_ must_== 0),
      for {
        _ <- asyncQueryExecutor.insert(emptyRecord)
        _ <- asyncQueryExecutor.remove(emptyRecord).map(_ must_== 1)
        _ <- asyncQueryExecutor.count(SimpleRecord.where(_.id eqs emptyRecord.id)).map(_ must_== 0)
      } yield (),
      // We just pass the serialized record as the query filter to the driver, thus removes
      // with modified fields do nothing, unless the field is deleted entirely as in the
      // case of modifiedFullRecord2 below.
      for {
        _ <- asyncQueryExecutor.insert(fullRecord1)
        _ <- asyncQueryExecutor.remove(modifiedFullRecord1).map(_ must_== 0)
        _ <- asyncQueryExecutor.count(SimpleRecord.where(_.id eqs fullRecord1.id)).map(_ must_== 1)
        _ <- asyncQueryExecutor.remove(fullRecord1).map(_ must_== 1)
        _ <- asyncQueryExecutor.count(SimpleRecord.where(_.id eqs fullRecord1.id)).map(_ must_== 0)
      } yield (),
      for {
        _ <- asyncQueryExecutor.insert(fullRecord2)
        _ <- asyncQueryExecutor.remove(modifiedFullRecord2).map(_ must_== 1)
        _ <- asyncQueryExecutor.count(SimpleRecord.where(_.id eqs fullRecord2.id)).map(_ must_== 0)
      } yield ()
    )

    Await.result(testFutures, Duration.fromSeconds(5))
  }

  @Test
  def testBlockingRemove: Unit = {
    val emptyRecord = SimpleRecord()
    val fullRecord1 = newTestRecord(1)
    val modifiedFullRecord1 = fullRecord1.copy(int = Some(5))
    val fullRecord2 = newTestRecord(2)
    val modifiedFullRecord2 = fullRecord2.copy(int = None)

    blockingQueryExecutor.remove(SimpleRecord()).unwrap must_== 0

    blockingQueryExecutor.insert(emptyRecord)
    blockingQueryExecutor.remove(emptyRecord).unwrap must_== 1
    blockingQueryExecutor.count(SimpleRecord.where(_.id eqs emptyRecord.id)).unwrap must_== 0

    // We just pass the serialized record as the query filter to the driver, thus removes
    // with modified fields do nothing, unless the field is deleted entirely as in the
    // case of modifiedFullRecord2 below.
    blockingQueryExecutor.insert(fullRecord1)
    blockingQueryExecutor.remove(modifiedFullRecord1).unwrap must_== 0
    blockingQueryExecutor.count(SimpleRecord.where(_.id eqs fullRecord1.id)).unwrap must_== 1
    blockingQueryExecutor.remove(fullRecord1).unwrap must_== 1
    blockingQueryExecutor.count(SimpleRecord.where(_.id eqs fullRecord1.id)).unwrap must_== 0

    blockingQueryExecutor.insert(fullRecord2)
    blockingQueryExecutor.remove(modifiedFullRecord2).unwrap must_== 1
    blockingQueryExecutor.count(SimpleRecord.where(_.id eqs fullRecord2.id)).unwrap must_== 0
  }

  @Test
  def testAsyncBulkDelete(): Unit = {
    val emptyRecord = SimpleRecord()
    val testRecords = Seq.tabulate(5)(newTestRecord)
    val testRecordIds = testRecords.map(_.id)

    val testFutures = for {
      _ <- asyncQueryExecutor.bulkDelete_!!(SimpleRecord).map(_ must_== 0)
      _ <- Future.join(
        for {
          _ <- asyncQueryExecutor.insert(emptyRecord)
          _ <- asyncQueryExecutor.bulkDelete_!!(SimpleRecord.where(_.id eqs emptyRecord.id)).map(_ must_== 1)
          _ <- asyncQueryExecutor.count(SimpleRecord.where(_.id eqs emptyRecord.id)).map(_ must_== 0)
        } yield (),
        for {
          _ <- asyncQueryExecutor.insertAll(testRecords)
          _ <- asyncQueryExecutor.bulkDelete_!!(SimpleRecord.where(_.id eqs testRecords(0).id)).map(_ must_== 1)
          _ <- asyncQueryExecutor.count(SimpleRecord.where(_.id in testRecordIds)).map(_ must_== 4)
          _ <- asyncQueryExecutor.bulkDelete_!!(SimpleRecord.where(_.int eqs 1)).map(_ must_== 2)
          _ <- asyncQueryExecutor.count(SimpleRecord.where(_.id in testRecordIds)).map(_ must_== 2)
          _ <- asyncQueryExecutor.bulkDelete_!!(SimpleRecord.where(_.int eqs 1)).map(_ must_== 0)
          _ <- asyncQueryExecutor.count(SimpleRecord.where(_.id in testRecordIds)).map(_ must_== 2)
        } yield ()
      )
      _ <- for {
        _ <- asyncQueryExecutor.bulkDelete_!!(SimpleRecord).map(_ must_== 2)
        _ <- asyncQueryExecutor.count(SimpleRecord).map(_ must_== 0)
      } yield ()
    } yield ()

    Await.result(testFutures, Duration.fromSeconds(60))
  }

  @Test
  def testBlockingBulkDelete(): Unit = {
    val emptyRecord = SimpleRecord()
    val testRecords = Seq.tabulate(5)(newTestRecord)
    val testRecordIds = testRecords.map(_.id)

    blockingQueryExecutor.bulkDelete_!!(SimpleRecord).unwrap must_== 0

    blockingQueryExecutor.insert(emptyRecord)
    blockingQueryExecutor.bulkDelete_!!(SimpleRecord.where(_.id eqs emptyRecord.id)).unwrap must_== 1
    blockingQueryExecutor.count(SimpleRecord.where(_.id eqs emptyRecord.id)).unwrap must_== 0

    blockingQueryExecutor.insertAll(testRecords)
    blockingQueryExecutor.bulkDelete_!!(SimpleRecord.where(_.id eqs testRecords(0).id)).unwrap must_== 1
    blockingQueryExecutor.count(SimpleRecord.where(_.id in testRecordIds)).unwrap must_== 4
    blockingQueryExecutor.bulkDelete_!!(SimpleRecord.where(_.int eqs 1)).unwrap must_== 2
    blockingQueryExecutor.count(SimpleRecord.where(_.id in testRecordIds)).unwrap must_== 2
    blockingQueryExecutor.bulkDelete_!!(SimpleRecord.where(_.int eqs 1)).unwrap must_== 0
    blockingQueryExecutor.count(SimpleRecord.where(_.id in testRecordIds)).unwrap must_== 2

    blockingQueryExecutor.bulkDelete_!!(SimpleRecord).unwrap must_== 2
    blockingQueryExecutor.count(SimpleRecord).unwrap must_== 0
  }

  @Test
  def testAsyncUpdateOne(): Unit = {
    val testRecord = newTestRecord(0)

    val serialTestFutures = Future.join(
      asyncQueryExecutor.insert(newTestRecord(1)),
      for {
        // update on non-existant record
        _ <- asyncQueryExecutor
          .updateOne(
            SimpleRecord.where(_.id eqs testRecord.id).modify(_.int setTo testRecord.int)
          )
          .map(_ must_== 0L)
        // no-op update
        _ <- asyncQueryExecutor.insert(testRecord)
        _ <- asyncQueryExecutor
          .updateOne(
            SimpleRecord.where(_.id eqs testRecord.id).modify(_.int setTo testRecord.int)
          )
          .map(_ must_== 0L)
        // update on existing record
        _ <- asyncQueryExecutor
          .updateOne(
            SimpleRecord.where(_.id eqs testRecord.id).modify(_.int setTo testRecord.int.map(_ + 10))
          )
          .map(_ must_== 1L)
        _ <- asyncQueryExecutor
          .fetchOne(
            SimpleRecord.where(_.id eqs testRecord.id)
          )
          .map(_ must_== Some(testRecord.copy(int = testRecord.int.map(_ + 10))))
      } yield ()
    )

    val testFutures = serialTestFutures.flatMap(_ => {
      Future.join(
        // updates fail on modifying _id
        asyncQueryExecutor
          .updateOne(SimpleRecord.where(_.id eqs testRecord.id).modify(_.id setTo new ObjectId))
          .map(_ => throw new Exception("Expected update failure when modifying _id field"))
          .handle({
            case rogueException: RogueException =>
              Option(rogueException.getCause) match {
                case Some(mwe: MongoWriteException) =>
                  mwe.getError.getCategory match {
                    case ErrorCategory.UNCATEGORIZED => ()
                    case ErrorCategory.DUPLICATE_KEY | ErrorCategory.EXECUTION_TIMEOUT => throw rogueException
                  }
                case _ => throw rogueException
              }
          }),
        // match multiple records, but only modify one
        asyncQueryExecutor
          .updateOne(
            SimpleRecord.modify(_.boolean setTo true)
          )
          .map(_ must_== 1L)
      )
    })

    Await.result(testFutures, Duration.fromSeconds(5))
  }

  @Test
  def testBlockingUpdateOne(): Unit = {
    val testRecord = newTestRecord(0)

    // update on non-existant record
    blockingQueryExecutor
      .updateOne(
        SimpleRecord.where(_.id eqs testRecord.id).modify(_.int setTo testRecord.int)
      )
      .unwrap must_== 0L

    // no-op update
    blockingQueryExecutor.insert(testRecord)
    blockingQueryExecutor
      .updateOne(
        SimpleRecord.where(_.id eqs testRecord.id).modify(_.int setTo testRecord.int)
      )
      .unwrap must_== 0L

    // update on existing record
    blockingQueryExecutor
      .updateOne(
        SimpleRecord.where(_.id eqs testRecord.id).modify(_.int setTo testRecord.int.map(_ + 10))
      )
      .unwrap must_== 1L
    blockingQueryExecutor
      .fetchOne(
        SimpleRecord.where(_.id eqs testRecord.id)
      )
      .unwrap must_== Some(testRecord.copy(int = testRecord.int.map(_ + 10)))

    // match multiple records, but only modify one
    blockingQueryExecutor.insert(newTestRecord(1))
    blockingQueryExecutor
      .updateOne(
        SimpleRecord.modify(_.boolean setTo true)
      )
      .unwrap must_== 1L

    // updates fail on modifying _id
    try {
      blockingQueryExecutor.updateOne(
        SimpleRecord.where(_.id eqs testRecord.id).modify(_.id setTo new ObjectId)
      )
      throw new Exception("Expected update failure when modifying _id field")
    } catch {
      case rogueException: RogueException =>
        Option(rogueException.getCause) match {
          case Some(mwe: MongoWriteException) =>
            mwe.getError.getCategory match {
              case ErrorCategory.UNCATEGORIZED => ()
              case ErrorCategory.DUPLICATE_KEY | ErrorCategory.EXECUTION_TIMEOUT => throw rogueException
            }
          case _ => throw rogueException
        }
    }
  }

  @Test
  def testAsyncUpdateMany(): Unit = {
    val testRecord = newTestRecord(0)

    val serialTestFutures = Future.join(
      asyncQueryExecutor.insert(newTestRecord(1)),
      for {
        // update on non-existant record
        _ <- asyncQueryExecutor
          .updateMany(
            SimpleRecord.where(_.id eqs testRecord.id).modify(_.int setTo testRecord.int)
          )
          .map(_ must_== 0L)
        // no-op update
        _ <- asyncQueryExecutor.insert(testRecord)
        _ <- asyncQueryExecutor
          .updateMany(
            SimpleRecord.where(_.id eqs testRecord.id).modify(_.int setTo testRecord.int)
          )
          .map(_ must_== 0L)
        // update on existing record
        _ <- asyncQueryExecutor
          .updateMany(
            SimpleRecord.where(_.id eqs testRecord.id).modify(_.int setTo testRecord.int.map(_ + 10))
          )
          .map(_ must_== 1L)
        _ <- asyncQueryExecutor
          .fetchOne(
            SimpleRecord.where(_.id eqs testRecord.id)
          )
          .map(_ must_== Some(testRecord.copy(int = testRecord.int.map(_ + 10))))
      } yield ()
    )

    val testFutures = serialTestFutures.flatMap(_ => {
      Future.join(
        // updates fail on modifying _id
        asyncQueryExecutor
          .updateMany(SimpleRecord.where(_.id eqs testRecord.id).modify(_.id setTo new ObjectId))
          .map(_ => throw new Exception("Expected update failure when modifying _id field"))
          .handle({
            case rogueException: RogueException =>
              Option(rogueException.getCause) match {
                case Some(mwe: MongoWriteException) =>
                  mwe.getError.getCategory match {
                    case ErrorCategory.UNCATEGORIZED => ()
                    case ErrorCategory.DUPLICATE_KEY | ErrorCategory.EXECUTION_TIMEOUT => throw rogueException
                  }
                case _ => throw rogueException
              }
          }),
        // update multiple records
        asyncQueryExecutor
          .updateMany(
            SimpleRecord.modify(_.boolean setTo true)
          )
          .map(_ must_== 2L)
      )
    })

    Await.result(testFutures, Duration.fromSeconds(5))
  }

  @Test
  def testBlockingUpdateMany(): Unit = {
    val testRecord = newTestRecord(0)

    // update on non-existant record
    blockingQueryExecutor
      .updateMany(
        SimpleRecord.where(_.id eqs testRecord.id).modify(_.int setTo testRecord.int)
      )
      .unwrap must_== 0L

    // no-op update
    blockingQueryExecutor.insert(testRecord)
    blockingQueryExecutor
      .updateMany(
        SimpleRecord.where(_.id eqs testRecord.id).modify(_.int setTo testRecord.int)
      )
      .unwrap must_== 0L

    // update on existing record
    blockingQueryExecutor
      .updateMany(
        SimpleRecord.where(_.id eqs testRecord.id).modify(_.int setTo testRecord.int.map(_ + 10))
      )
      .unwrap must_== 1L
    blockingQueryExecutor
      .fetchOne(
        SimpleRecord.where(_.id eqs testRecord.id)
      )
      .unwrap must_== Some(testRecord.copy(int = testRecord.int.map(_ + 10)))

    // update multiple records
    blockingQueryExecutor.insert(newTestRecord(1))
    blockingQueryExecutor
      .updateMany(
        SimpleRecord.modify(_.boolean setTo true)
      )
      .unwrap must_== 2L

    // updates fail on modifying _id
    try {
      blockingQueryExecutor.updateMany(
        SimpleRecord.where(_.id eqs testRecord.id).modify(_.id setTo new ObjectId)
      )
      throw new Exception("Expected update failure when modifying _id field")
    } catch {
      case rogueException: RogueException =>
        Option(rogueException.getCause) match {
          case Some(mwe: MongoWriteException) =>
            mwe.getError.getCategory match {
              case ErrorCategory.UNCATEGORIZED => ()
              case ErrorCategory.DUPLICATE_KEY | ErrorCategory.EXECUTION_TIMEOUT => throw rogueException
            }
          case _ => throw rogueException
        }
    }
  }

  @Test
  def testAsyncUpsertOne(): Unit = {
    val testRecord = newTestRecord(0)

    val serialTestFutures = Future.join(
      asyncQueryExecutor.insert(newTestRecord(1)),
      for {
        // insert new record with only id/modified fields
        _ <- asyncQueryExecutor
          .upsertOne(
            SimpleRecord.where(_.id eqs testRecord.id).modify(_.int setTo testRecord.int)
          )
          .map(_ must_== 0L)
        _ <- asyncQueryExecutor
          .fetchOne(
            SimpleRecord.where(_.id eqs testRecord.id)
          )
          .map(_ must_== Some(SimpleRecord(id = testRecord.id, int = testRecord.int)))
        // no-op upsert
        _ <- asyncQueryExecutor
          .upsertOne(
            SimpleRecord.where(_.id eqs testRecord.id).modify(_.int setTo testRecord.int)
          )
          .map(_ must_== 0L)
        // modify on existing record
        _ <- asyncQueryExecutor
          .upsertOne(
            SimpleRecord.where(_.id eqs testRecord.id).modify(_.int setTo testRecord.int.map(_ + 10))
          )
          .map(_ must_== 1L)
        _ <- asyncQueryExecutor
          .fetchOne(
            SimpleRecord.where(_.id eqs testRecord.id)
          )
          .map(_ must_== Some(SimpleRecord(id = testRecord.id, int = testRecord.int.map(_ + 10))))
      } yield ()
    )

    val testFutures = serialTestFutures.flatMap(_ => {
      Future.join(
        // updates fail on modifying _id
        asyncQueryExecutor
          .upsertOne(SimpleRecord.where(_.id eqs testRecord.id).modify(_.id setTo new ObjectId))
          .map(_ => throw new Exception("Expected update failure when modifying _id field"))
          .handle({
            case rogueException: RogueException =>
              Option(rogueException.getCause) match {
                case Some(mwe: MongoWriteException) =>
                  mwe.getError.getCategory match {
                    case ErrorCategory.UNCATEGORIZED => ()
                    case ErrorCategory.DUPLICATE_KEY | ErrorCategory.EXECUTION_TIMEOUT => throw rogueException
                  }
                case _ => throw rogueException
              }
          }),
        // match multiple records, but only modify one
        asyncQueryExecutor
          .upsertOne(
            SimpleRecord.modify(_.boolean setTo true)
          )
          .map(_ must_== 1L)
      )
    })

    Await.result(testFutures, Duration.fromSeconds(5))
  }

  @Test
  def testBlockingUpsertOne(): Unit = {
    val testRecord = newTestRecord(0)

    // insert new record with only id/modified fields
    blockingQueryExecutor
      .upsertOne(
        SimpleRecord.where(_.id eqs testRecord.id).modify(_.int setTo testRecord.int)
      )
      .unwrap must_== 0L
    blockingQueryExecutor
      .fetchOne(
        SimpleRecord.where(_.id eqs testRecord.id)
      )
      .unwrap must_== Some(SimpleRecord(id = testRecord.id, int = testRecord.int))

    // no-op upsert
    blockingQueryExecutor
      .upsertOne(
        SimpleRecord.where(_.id eqs testRecord.id).modify(_.int setTo testRecord.int)
      )
      .unwrap must_== 0L

    // modify on existing record
    blockingQueryExecutor
      .upsertOne(
        SimpleRecord.where(_.id eqs testRecord.id).modify(_.int setTo testRecord.int.map(_ + 10))
      )
      .unwrap must_== 1L
    blockingQueryExecutor
      .fetchOne(
        SimpleRecord.where(_.id eqs testRecord.id)
      )
      .unwrap must_== Some(SimpleRecord(id = testRecord.id, int = testRecord.int.map(_ + 10)))

    // match multiple records, but only modify one
    blockingQueryExecutor.insert(newTestRecord(1))
    blockingQueryExecutor
      .upsertOne(
        SimpleRecord.modify(_.boolean setTo true)
      )
      .unwrap must_== 1L

    // updates fail on modifying _id
    try {
      blockingQueryExecutor.upsertOne(
        SimpleRecord.where(_.id eqs testRecord.id).modify(_.id setTo new ObjectId)
      )
      throw new Exception("Expected update failure when modifying _id field")
    } catch {
      case rogueException: RogueException =>
        Option(rogueException.getCause) match {
          case Some(mwe: MongoWriteException) =>
            mwe.getError.getCategory match {
              case ErrorCategory.UNCATEGORIZED => ()
              case ErrorCategory.DUPLICATE_KEY | ErrorCategory.EXECUTION_TIMEOUT => throw rogueException
            }
          case _ => throw rogueException
        }
    }
  }

  @Test
  def testAsyncFindAndUpdateOne(): Unit = {
    val testRecord = newTestRecord(0)
    val updatedTestRecord = testRecord.copy(int = testRecord.int.map(_ + 10))
    val otherRecord = newTestRecord(1)
    val returnNewTestRecord = newTestRecord(2)
    val updatedReturnNewTestRecord = returnNewTestRecord.copy(string = returnNewTestRecord.string.map(_ + " there"))

    val serialTestFutures = Future.join(
      asyncQueryExecutor.insert(otherRecord),
      for {
        // update on non-existant record
        _ <- asyncQueryExecutor
          .findAndUpdateOne(
            SimpleRecord.where(_.id eqs testRecord.id).findAndModify(_.int setTo testRecord.int)
          )
          .map(_ must_== None)
        // no-op update
        _ <- asyncQueryExecutor.insert(testRecord)
        _ <- asyncQueryExecutor
          .findAndUpdateOne(
            SimpleRecord.where(_.id eqs testRecord.id).findAndModify(_.int setTo testRecord.int)
          )
          .map(_ must_== Some(testRecord))
        // update on existing record
        _ <- asyncQueryExecutor
          .findAndUpdateOne(
            SimpleRecord.where(_.id eqs testRecord.id).findAndModify(_.int setTo updatedTestRecord.int)
          )
          .map(_ must_== Some(testRecord))
        _ <- asyncQueryExecutor
          .fetchOne(
            SimpleRecord.where(_.id eqs testRecord.id)
          )
          .map(_ must_== Some(updatedTestRecord))
      } yield (),
      for {
        // test returnNew = true
        _ <- asyncQueryExecutor.insert(returnNewTestRecord)
        _ <- asyncQueryExecutor
          .findAndUpdateOne(
            SimpleRecord
              .where(_.id eqs returnNewTestRecord.id)
              .findAndModify(_.string setTo updatedReturnNewTestRecord.string),
            returnNew = true
          )
          .map(_ must_== Some(updatedReturnNewTestRecord))
      } yield ()
    )

    val testFutures = serialTestFutures.flatMap(_ => {
      Future.join(
        // updates fail on modifying _id
        asyncQueryExecutor
          .findAndUpdateOne(SimpleRecord.where(_.id eqs testRecord.id).findAndModify(_.id setTo new ObjectId))
          .map(_ => throw new Exception("Expected update failure when modifying _id field"))
          .handle({
            case rogueException: RogueException =>
              Option(rogueException.getCause) match {
                case Some(mce: MongoCommandException) =>
                  mce.getErrorCode match {
                    case 66 => ()
                    case _ => throw rogueException
                  }
                case _ => throw rogueException
              }
          }),
        // match multiple records, but only modify one
        for {
          _ <- asyncQueryExecutor
            .findAndUpdateOne(
              SimpleRecord.findAndModify(_.boolean setTo true)
            )
            .map(
              _ must beOneOf(
                Some(updatedTestRecord),
                Some(otherRecord),
                Some(updatedReturnNewTestRecord)
              )
            )
          _ <- asyncQueryExecutor.count(SimpleRecord.where(_.boolean eqs true)).map(_ must_== 1)
        } yield ()
      )
    })

    Await.result(testFutures, Duration.fromSeconds(10))
  }

  @Test
  def testBlockingFindAndUpdateOne(): Unit = {
    val testRecord = newTestRecord(0)
    val updatedTestRecord = testRecord.copy(int = testRecord.int.map(_ + 10))
    val otherRecord = newTestRecord(1)
    val returnNewTestRecord = newTestRecord(2)
    val updatedReturnNewTestRecord = returnNewTestRecord.copy(string = returnNewTestRecord.string.map(_ + " there"))

    // update on non-existant record
    blockingQueryExecutor
      .findAndUpdateOne(
        SimpleRecord.where(_.id eqs testRecord.id).findAndModify(_.int setTo testRecord.int)
      )
      .unwrap must_== None

    // no-op update
    blockingQueryExecutor.insert(testRecord)
    blockingQueryExecutor
      .findAndUpdateOne(
        SimpleRecord.where(_.id eqs testRecord.id).findAndModify(_.int setTo testRecord.int)
      )
      .unwrap must_== Some(testRecord)

    // update on existing record
    blockingQueryExecutor
      .findAndUpdateOne(
        SimpleRecord.where(_.id eqs testRecord.id).findAndModify(_.int setTo updatedTestRecord.int)
      )
      .unwrap must_== Some(testRecord)
    blockingQueryExecutor
      .fetchOne(
        SimpleRecord.where(_.id eqs testRecord.id)
      )
      .unwrap must_== Some(updatedTestRecord)

    // test returnNew = true
    blockingQueryExecutor.insert(returnNewTestRecord)
    blockingQueryExecutor
      .findAndUpdateOne(
        SimpleRecord
          .where(_.id eqs returnNewTestRecord.id)
          .findAndModify(_.string setTo updatedReturnNewTestRecord.string),
        returnNew = true
      )
      .unwrap must_== Some(updatedReturnNewTestRecord)

    // match multiple records, but only modify one
    blockingQueryExecutor.insert(otherRecord)
    blockingQueryExecutor
      .findAndUpdateOne(
        SimpleRecord.findAndModify(_.boolean setTo true)
      )
      .unwrap must beOneOf(
      Some(updatedTestRecord),
      Some(otherRecord),
      Some(updatedReturnNewTestRecord)
    )
    blockingQueryExecutor.count(SimpleRecord.where(_.boolean eqs true)).unwrap must_== 1

    // updates fail on modifying _id
    try {
      blockingQueryExecutor.findAndUpdateOne(
        SimpleRecord.where(_.id eqs testRecord.id).findAndModify(_.id setTo new ObjectId)
      )
      throw new Exception("Expected update failure when modifying _id field")
    } catch {
      case rogueException: RogueException =>
        Option(rogueException.getCause) match {
          case Some(mce: MongoCommandException) =>
            mce.getErrorCode match {
              case 66 => ()
              case _ => throw rogueException
            }
          case _ => throw rogueException
        }
    }
  }

  @Test
  def testAsyncFindAndUpsertOne(): Unit = {
    val testRecord = newTestRecord(0)
    val updatedTestRecord = testRecord.copy(int = testRecord.int.map(_ + 10))
    val otherRecord = newTestRecord(1)
    val returnNewTestRecord = newTestRecord(2)
    val updatedReturnNewTestRecord = returnNewTestRecord.copy(string = returnNewTestRecord.string.map(_ + " there"))

    val serialTestFutures = Future.join(
      asyncQueryExecutor.insert(otherRecord),
      for {
        // insert new record with only id/modified fields
        _ <- asyncQueryExecutor
          .findAndUpsertOne(
            SimpleRecord.where(_.id eqs testRecord.id).findAndModify(_.int setTo testRecord.int)
          )
          .map(_ must_== None)
        _ <- asyncQueryExecutor
          .fetchOne(
            SimpleRecord.where(_.id eqs testRecord.id)
          )
          .map(_ must_== Some(SimpleRecord(id = testRecord.id, int = testRecord.int)))
        // no-op update
        _ <- asyncQueryExecutor.save(testRecord)
        _ <- asyncQueryExecutor
          .findAndUpsertOne(
            SimpleRecord.where(_.id eqs testRecord.id).findAndModify(_.int setTo testRecord.int)
          )
          .map(_ must_== Some(testRecord))
        // update on existing record
        _ <- asyncQueryExecutor
          .findAndUpsertOne(
            SimpleRecord.where(_.id eqs testRecord.id).findAndModify(_.int setTo updatedTestRecord.int)
          )
          .map(_ must_== Some(testRecord))
        _ <- asyncQueryExecutor
          .fetchOne(
            SimpleRecord.where(_.id eqs testRecord.id)
          )
          .map(_ must_== Some(updatedTestRecord))
      } yield (),
      for {
        // test returnNew = true
        _ <- asyncQueryExecutor.insert(returnNewTestRecord)
        _ <- asyncQueryExecutor
          .findAndUpsertOne(
            SimpleRecord
              .where(_.id eqs returnNewTestRecord.id)
              .findAndModify(_.string setTo updatedReturnNewTestRecord.string),
            returnNew = true
          )
          .map(_ must_== Some(updatedReturnNewTestRecord))
      } yield ()
    )

    val testFutures = serialTestFutures.flatMap(_ => {
      Future.join(
        // updates fail on modifying _id
        asyncQueryExecutor
          .findAndUpsertOne(SimpleRecord.where(_.id eqs testRecord.id).findAndModify(_.id setTo new ObjectId))
          .map(_ => throw new Exception("Expected update failure when modifying _id field"))
          .handle({
            case rogueException: RogueException =>
              Option(rogueException.getCause) match {
                case Some(mce: MongoCommandException) =>
                  mce.getErrorCode match {
                    case 66 => ()
                    case _ => throw rogueException
                  }
                case _ => throw rogueException
              }
          }),
        // match multiple records, but only modify one
        for {
          _ <- asyncQueryExecutor
            .findAndUpsertOne(
              SimpleRecord.findAndModify(_.boolean setTo true)
            )
            .map(
              _ must beOneOf(
                Some(updatedTestRecord),
                Some(otherRecord),
                Some(updatedReturnNewTestRecord)
              )
            )
          _ <- asyncQueryExecutor.count(SimpleRecord.where(_.boolean eqs true)).map(_ must_== 1L)
        } yield ()
      )
    })

    Await.result(testFutures, Duration.fromSeconds(5))
  }

  @Test
  def testBlockingFindAndUpsertOne(): Unit = {
    val testRecord = newTestRecord(0)
    val updatedTestRecord = testRecord.copy(int = testRecord.int.map(_ + 10))
    val otherRecord = newTestRecord(1)
    val returnNewTestRecord = newTestRecord(2)
    val updatedReturnNewTestRecord = returnNewTestRecord.copy(string = returnNewTestRecord.string.map(_ + " there"))

    // insert new record with only id/modified fields
    blockingQueryExecutor
      .findAndUpsertOne(
        SimpleRecord.where(_.id eqs testRecord.id).findAndModify(_.int setTo testRecord.int)
      )
      .unwrap must_== None
    blockingQueryExecutor
      .fetchOne(
        SimpleRecord.where(_.id eqs testRecord.id)
      )
      .unwrap must_== Some(SimpleRecord(id = testRecord.id, int = testRecord.int))

    // no-op update
    blockingQueryExecutor.save(testRecord)
    blockingQueryExecutor
      .findAndUpsertOne(
        SimpleRecord.where(_.id eqs testRecord.id).findAndModify(_.int setTo testRecord.int)
      )
      .unwrap must_== Some(testRecord)

    // update on existing record
    blockingQueryExecutor
      .findAndUpsertOne(
        SimpleRecord.where(_.id eqs testRecord.id).findAndModify(_.int setTo updatedTestRecord.int)
      )
      .unwrap must_== Some(testRecord)
    blockingQueryExecutor
      .fetchOne(
        SimpleRecord.where(_.id eqs testRecord.id)
      )
      .unwrap must_== Some(updatedTestRecord)

    // test returnNew = true
    blockingQueryExecutor.insert(returnNewTestRecord)
    blockingQueryExecutor
      .findAndUpsertOne(
        SimpleRecord
          .where(_.id eqs returnNewTestRecord.id)
          .findAndModify(_.string setTo updatedReturnNewTestRecord.string),
        returnNew = true
      )
      .unwrap must_== Some(updatedReturnNewTestRecord)

    // match multiple records, but only modify one
    blockingQueryExecutor.insert(otherRecord)
    blockingQueryExecutor
      .findAndUpsertOne(
        SimpleRecord.findAndModify(_.boolean setTo true)
      )
      .unwrap must beOneOf(
      Some(updatedTestRecord),
      Some(otherRecord),
      Some(updatedReturnNewTestRecord)
    )
    blockingQueryExecutor.count(SimpleRecord.where(_.boolean eqs true)).unwrap must_== 1L

    // updates fail on modifying _id
    try {
      blockingQueryExecutor.findAndUpsertOne(
        SimpleRecord.where(_.id eqs testRecord.id).findAndModify(_.id setTo new ObjectId)
      )
      throw new Exception("Expected update failure when modifying _id field")
    } catch {
      case rogueException: RogueException =>
        Option(rogueException.getCause) match {
          case Some(mce: MongoCommandException) =>
            mce.getErrorCode match {
              case 66 => ()
              case _ => throw rogueException
            }
          case _ => throw rogueException
        }
    }
  }

  @Test
  def testAsyncFindAndDeleteOne(): Unit = {
    val testRecords = Array(
      newTestRecord(0),
      newTestRecord(1),
      newTestRecord(2)
    )

    val testFuture = for {
      // delete with no stored records
      _ <- asyncQueryExecutor.findAndDeleteOne(SimpleRecord).map(_ must_== None)
      // delete single record
      _ <- asyncQueryExecutor.insertAll(testRecords)
      _ <- asyncQueryExecutor
        .findAndDeleteOne(
          SimpleRecord.where(_.id eqs testRecords(0).id)
        )
        .map(_ must_== Some(testRecords(0)))
      _ <- asyncQueryExecutor.count(SimpleRecord.where(_.id eqs testRecords(0).id)).map(_ must_== 0)
      // delete query does not match
      _ <- asyncQueryExecutor.findAndDeleteOne(SimpleRecord.where(_.id eqs testRecords(0).id)).map(_ must_== None)
      _ <- asyncQueryExecutor.count(SimpleRecord).map(_ must_== 2)
      // match multiple records but only delete one
      _ <- asyncQueryExecutor
        .findAndDeleteOne(
          SimpleRecord
        )
        .map(_ must beOneOf(Some(testRecords(1)), Some(testRecords(2))))
      _ <- asyncQueryExecutor.count(SimpleRecord).map(_ must_== 1)
    } yield ()

    Await.result(testFuture, Duration.fromSeconds(90))
  }

  @Test
  def testBlockingFindAndDeleteOne(): Unit = {
    val testRecords = Array(
      newTestRecord(0),
      newTestRecord(1),
      newTestRecord(2)
    )

    // delete with no stored records
    blockingQueryExecutor.findAndDeleteOne(SimpleRecord).unwrap must_== None
    // delete single record
    blockingQueryExecutor.insertAll(testRecords)
    blockingQueryExecutor
      .findAndDeleteOne(
        SimpleRecord.where(_.id eqs testRecords(0).id)
      )
      .unwrap must_== Some(testRecords(0))
    blockingQueryExecutor.count(SimpleRecord.where(_.id eqs testRecords(0).id)).unwrap must_== 0
    // delete query does not match
    blockingQueryExecutor.findAndDeleteOne(SimpleRecord.where(_.id eqs testRecords(0).id)).unwrap must_== None
    blockingQueryExecutor.count(SimpleRecord).unwrap must_== 2
    // match multiple records but only delete one
    blockingQueryExecutor
      .findAndDeleteOne(
        SimpleRecord
      )
      .unwrap must beOneOf(Some(testRecords(1)), Some(testRecords(2)))
    blockingQueryExecutor.count(SimpleRecord).unwrap must_== 1
  }

  def testSingleAsyncIterate[T](
    query: Query[SimpleRecord.type, SimpleRecord, _],
    initial: T
  )(
    expectedResult: T,
    expectedVisited: Int
  )(
    handler: (T, Iter.Event[SimpleRecord]) => Iter.Command[T]
  ): Future[Unit] = {
    @volatile var visited = 0
    asyncQueryExecutor
      .iterate(query, initial)({
        case (cumulative, event) => {
          event match {
            case Iter.OnNext(_) => visited += 1
            case Iter.OnComplete | Iter.OnError(_) => ()
          }
          handler(cumulative, event)
        }
      })
      .map(result => {
        result must_== expectedResult
        visited must_== expectedVisited
      })
  }

  @Test
  def testAsyncIterate(): Unit = {
    val numInserts = 10
    val testRecords = Seq.tabulate(numInserts)(newTestRecord)

    val filterInts = Set(0, 1)
    val filteredRecords = testRecords.filter(_.int.map(filterInts.has(_)).getOrElse(false))

    // NOTE(jacob): These numbers are dependent upon the behavior of newTestRecord.
    val shortCircuitCount = 3
    val visitedMin = 4
    val shortCircuitVisited = visitedMin
    val shortCircuitFiltered = filteredRecords.take(shortCircuitCount)

    val testFuture = asyncQueryExecutor
      .insertAll(testRecords)
      .flatMap(
        _ =>
          Future.join(
            // no matching records
            testSingleAsyncIterate(SimpleRecord.where(_.id eqs new ObjectId), 0)(0, 0) {
              case (count, Iter.OnNext(record)) => Iter.Continue(count + 1)
              case (count, Iter.OnComplete) => Iter.Return(count)
              case (_, Iter.OnError(e)) => throw e
            },
            // single matching record
            testSingleAsyncIterate(SimpleRecord.where(_.id eqs testRecords.head.id), 0)(1, 1) {
              case (count, Iter.OnNext(record)) => Iter.Continue(count + 1)
              case (count, Iter.OnComplete) => Iter.Return(count)
              case (_, Iter.OnError(e)) => throw e
            },
            // all records match
            testSingleAsyncIterate(SimpleRecord, 0)(numInserts, numInserts) {
              case (count, Iter.OnNext(record)) => Iter.Continue(count + 1)
              case (count, Iter.OnComplete) => Iter.Return(count)
              case (_, Iter.OnError(e)) => throw e
            },
            // filter via query vs iterator
            testSingleAsyncIterate(
              SimpleRecord.where(_.int in filterInts).orderAsc(_.id),
              Seq.empty[SimpleRecord]
            )(
              filteredRecords,
              filteredRecords.size
            ) {
              case (matched, Iter.OnNext(record)) => Iter.Continue(matched :+ record)
              case (matched, Iter.OnComplete) => Iter.Return(matched.sortBy(_.id))
              case (_, Iter.OnError(e)) => throw e
            },
            testSingleAsyncIterate(
              SimpleRecord,
              Seq.empty[SimpleRecord]
            )(
              filteredRecords,
              numInserts
            ) {
              case (matched, Iter.OnNext(record)) => {
                if (record.int.map(filterInts.has(_)).getOrElse(false)) {
                  Iter.Continue(matched :+ record)
                } else {
                  Iter.Continue(matched)
                }
              }
              case (matched, Iter.OnComplete) => Iter.Return(matched.sortBy(_.id))
              case (_, Iter.OnError(e)) => throw e
            },
            // iterator filter + short circuit
            testSingleAsyncIterate(
              SimpleRecord.orderAsc(_.id),
              Seq.empty[SimpleRecord]
            )(
              filteredRecords.take(shortCircuitCount),
              shortCircuitVisited
            ) {
              case (matched, Iter.OnNext(record)) => {
                if (record.int.map(filterInts.has(_)).getOrElse(false)) {
                  val newMatched = matched :+ record
                  if (newMatched.size >= shortCircuitCount) {
                    Iter.Return(newMatched)
                  } else {
                    Iter.Continue(newMatched)
                  }
                } else {
                  Iter.Continue(matched)
                }
              }
              case (matched, Iter.OnComplete) => Iter.Return(matched)
              case (_, Iter.OnError(e)) => throw e
            },
            // reverse ordering
            testSingleAsyncIterate(
              SimpleRecord.orderDesc(_.id),
              Seq.empty[SimpleRecord]
            )(
              testRecords.reverse,
              numInserts
            ) {
              case (matched, Iter.OnNext(record)) => Iter.Continue(matched :+ record)
              case (matched, Iter.OnComplete) => Iter.Return(matched)
              case (_, Iter.OnError(e)) => throw e
            }
          )
      )

    Await.result(testFuture, Duration.fromSeconds(30))
  }

  def testSingleBlockingIterate[T](
    query: Query[SimpleRecord.type, SimpleRecord, _],
    initial: T
  )(
    expectedResult: T,
    expectedVisited: Int
  )(
    handler: (T, Iter.Event[SimpleRecord]) => Iter.Command[T]
  ): Unit = {
    var visited = 0
    val result = blockingQueryExecutor
      .iterate(query, initial)({
        case (cumulative, event) => {
          event match {
            case Iter.OnNext(_) => visited += 1
            case Iter.OnComplete | Iter.OnError(_) => ()
          }
          handler(cumulative, event)
        }
      })
      .unwrap
    result must_== expectedResult
    visited must_== expectedVisited
  }

  @Test
  def testBlockingIterate(): Unit = {
    val numInserts = 10
    val testRecords = Seq.tabulate(numInserts)(newTestRecord)
    blockingQueryExecutor.insertAll(testRecords)

    val filterInts = Set(0, 1)
    val filteredRecords = testRecords.filter(_.int.map(filterInts.has(_)).getOrElse(false))

    // NOTE(jacob): These numbers are dependent upon the behavior of newTestRecord.
    val shortCircuitCount = 3
    val visitedMin = 4
    val shortCircuitVisited = visitedMin
    val shortCircuitFiltered = filteredRecords.take(shortCircuitCount)

    // no matching records
    testSingleBlockingIterate(SimpleRecord.where(_.id eqs new ObjectId), 0)(0, 0) {
      case (count, Iter.OnNext(record)) => Iter.Continue(count + 1)
      case (count, Iter.OnComplete) => Iter.Return(count)
      case (_, Iter.OnError(e)) => throw e
    }

    // single matching record
    testSingleBlockingIterate(SimpleRecord.where(_.id eqs testRecords.head.id), 0)(1, 1) {
      case (count, Iter.OnNext(record)) => Iter.Continue(count + 1)
      case (count, Iter.OnComplete) => Iter.Return(count)
      case (_, Iter.OnError(e)) => throw e
    }

    // all records match
    testSingleBlockingIterate(SimpleRecord, 0)(numInserts, numInserts) {
      case (count, Iter.OnNext(record)) => Iter.Continue(count + 1)
      case (count, Iter.OnComplete) => Iter.Return(count)
      case (_, Iter.OnError(e)) => throw e
    }

    // filter via query vs iterator
    testSingleBlockingIterate(
      SimpleRecord.where(_.int in filterInts).orderAsc(_.id),
      Seq.empty[SimpleRecord]
    )(
      filteredRecords,
      filteredRecords.size
    ) {
      case (matched, Iter.OnNext(record)) => Iter.Continue(matched :+ record)
      case (matched, Iter.OnComplete) => Iter.Return(matched.sortBy(_.id))
      case (_, Iter.OnError(e)) => throw e
    }
    testSingleBlockingIterate(
      SimpleRecord,
      Seq.empty[SimpleRecord]
    )(
      filteredRecords,
      numInserts
    ) {
      case (matched, Iter.OnNext(record)) => {
        if (record.int.map(filterInts.has(_)).getOrElse(false)) {
          Iter.Continue(matched :+ record)
        } else {
          Iter.Continue(matched)
        }
      }
      case (matched, Iter.OnComplete) => Iter.Return(matched.sortBy(_.id))
      case (_, Iter.OnError(e)) => throw e
    }

    // iterator filter + short circuit
    testSingleBlockingIterate(
      SimpleRecord.orderAsc(_.id),
      Seq.empty[SimpleRecord]
    )(
      filteredRecords.take(shortCircuitCount),
      shortCircuitVisited
    ) {
      case (matched, Iter.OnNext(record)) => {
        if (record.int.map(filterInts.has(_)).getOrElse(false)) {
          val newMatched = matched :+ record
          if (newMatched.size >= shortCircuitCount) {
            Iter.Return(newMatched)
          } else {
            Iter.Continue(newMatched)
          }
        } else {
          Iter.Continue(matched)
        }
      }
      case (matched, Iter.OnComplete) => Iter.Return(matched)
      case (_, Iter.OnError(e)) => throw e
    }

    // reverse ordering
    testSingleBlockingIterate(
      SimpleRecord.orderDesc(_.id),
      Seq.empty[SimpleRecord]
    )(
      testRecords.reverse,
      numInserts
    ) {
      case (matched, Iter.OnNext(record)) => Iter.Continue(matched :+ record)
      case (matched, Iter.OnComplete) => Iter.Return(matched)
      case (_, Iter.OnError(e)) => throw e
    }
  }

  def assertsForCreateIndexesTest(listedIndexes: Seq[Document]): Unit = {
    val indexMap = listedIndexes.toMapByKey(_.getString("name"))
    val intIndex = indexMap.getOrElse("int_1", throw new RuntimeException("Expected int_1 index"))
    Assert.assertEquals(
      """{"int": 1}""",
      intIndex.get("key", classOf[Document]).toJson
    )
    val booleanLongIndex =
      indexMap.getOrElse("boolean_1_long_-1", throw new RuntimeException("Expected boolean_1_long_-1 index"))
    Assert.assertEquals(
      """{"boolean": 1, "long": -1}""",
      booleanLongIndex.get("key", classOf[Document]).toJson
    )
  }

  @Test
  def testBlockingCreateIndexes(): Unit = {
    val coll = blockingCollectionFactory.getMongoCollectionFromMetaRecord(SimpleRecord)
    blockingQueryExecutor.createIndexes(SimpleRecord)(
      MongoIndex.builder(SimpleRecord).index(_.int, Asc),
      MongoIndex.builder(SimpleRecord).index(_.boolean, Asc, _.long, Desc)
    )
    val listedIndexes = coll.listIndexes().asScala.toVector
    assertsForCreateIndexesTest(listedIndexes)
  }

  @Test
  def testAsyncCreateIndexes(): Unit = {
    val coll = asyncCollectionFactory.getMongoCollectionFromMetaRecord(SimpleRecord)
    val testFuture = for {
      _ <- asyncQueryExecutor.createIndexes(SimpleRecord)(
        MongoIndex.builder(SimpleRecord).index(_.int, Asc),
        MongoIndex.builder(SimpleRecord).index(_.boolean, Asc, _.long, Desc)
      )
      listedIndexes <- TwitterAsyncUtil.seqResult(coll.listIndexes())
    } yield {
      assertsForCreateIndexesTest(listedIndexes)
    }

    Await.result(testFuture, Duration.fromSeconds(5))
  }

  // TODO(jacob): There are a couple issues with these bulk operation tests:
  //    1. Like most of tests here, there is a lot of very similar or flat out redundant
  //      code that should be abstracted out of otherwise deduplicated.
  //    2. We should have more thorough tests mixing different bulk operations in the same
  //      query.

  @Test
  def testBulkOperationOrdering(): Unit = {
    val testRecord = newTestRecord(0)

    try {
      blockingQueryExecutor.bulk(
        Vector(
          BulkInsertOne(SimpleRecord, testRecord),
          BulkRemove(SimpleRecord),
          BulkInsertOne(SimpleRecord, testRecord)
        ),
        ordered = false
      )
      throw new Exception("Expected insertion failure on duplicate id")
    } catch {
      case rogueException: RogueException =>
        Option(rogueException.getCause) match {
          case Some(mbwe: MongoBulkWriteException) =>
            mbwe.getWriteErrors.asScala.map(_.getCategory) match {
              case Seq(ErrorCategory.DUPLICATE_KEY) => ()
              case _ => throw mbwe
            }
          case _ => throw rogueException
        }
    }

    blockingQueryExecutor.remove(testRecord)

    blockingQueryExecutor
      .bulk(
        Vector(
          BulkInsertOne(SimpleRecord, testRecord),
          BulkRemove(SimpleRecord),
          BulkInsertOne(SimpleRecord, testRecord)
        ),
        ordered = true
      )
      .unwrap must_== Some(
      BulkWriteResult.acknowledged(2, 0, 1, 0, new ArrayList[BulkWriteUpsert])
    )
  }

  def bulkInsertResult(insertedCount: Int): Option[BulkWriteResult] = {
    Some(
      BulkWriteResult.acknowledged(
        insertedCount,
        0,
        0,
        0,
        new ArrayList[BulkWriteUpsert]
      )
    )
  }

  def testSingleAsyncBulkInsertOne(records: Seq[SimpleRecord]): Future[Unit] = {
    for {
      _ <- asyncQueryExecutor
        .bulk(records.map(BulkInsertOne(SimpleRecord, _)))
        .map(
          _ must_== records.headOption.flatMap(_ => bulkInsertResult(records.size))
        )
      found <- asyncQueryExecutor.fetch(SimpleRecord.where(_.id in records.map(_.id)))
    } yield {
      found must containTheSameElementsAs(records)
    }
  }

  @Test
  def testAsyncBulkOperationInsertOne(): Unit = {
    val duplicateId = new ObjectId

    val insertFutures = Future.join(
      testSingleAsyncBulkInsertOne(
        Seq.empty
      ),
      testSingleAsyncBulkInsertOne(
        Seq(
          SimpleRecord()
        )
      ),
      testSingleAsyncBulkInsertOne(
        Seq(
          SimpleRecord(),
          SimpleRecord(duplicateId),
          newTestRecord(1)
        )
      )
    )

    val duplicateTestFuture = insertFutures.flatMap(_ => {
      asyncQueryExecutor
        .bulk(
          Vector(BulkInsertOne(SimpleRecord, SimpleRecord(duplicateId)))
        )
        .map(_ => throw new Exception("Expected insertion failure on duplicate id"))
        .handle({
          case rogueException: RogueException =>
            Option(rogueException.getCause) match {
              case Some(mbwe: MongoBulkWriteException) =>
                mbwe.getWriteErrors.asScala.map(_.getCategory) match {
                  case Seq(ErrorCategory.DUPLICATE_KEY) => ()
                  case _ => throw mbwe
                }
              case _ => throw rogueException
            }
        })
    })

    Await.result(duplicateTestFuture, Duration.fromSeconds(5))
  }

  def testSingleBlockingBulkInsertOne(records: Seq[SimpleRecord]): Unit = {
    blockingQueryExecutor
      .bulk(
        records.map(BulkInsertOne(SimpleRecord, _))
      )
      .unwrap must_== records.headOption.flatMap(_ => bulkInsertResult(records.size))
    blockingQueryExecutor
      .fetch(
        SimpleRecord.where(_.id in records.map(_.id))
      )
      .unwrap must containTheSameElementsAs(records)
  }

  @Test
  def testBlockingBulkOperationInsertOne(): Unit = {
    val duplicateId = new ObjectId

    testSingleBlockingBulkInsertOne(
      Seq.empty
    )
    testSingleBlockingBulkInsertOne(
      Seq(
        SimpleRecord()
      )
    )
    testSingleBlockingBulkInsertOne(
      Seq(
        SimpleRecord(),
        SimpleRecord(duplicateId),
        newTestRecord(1)
      )
    )

    try {
      blockingQueryExecutor.bulk(Vector(BulkInsertOne(SimpleRecord, SimpleRecord(duplicateId))))
      throw new Exception("Expected insertion failure on duplicate id")
    } catch {
      case rogueException: RogueException =>
        Option(rogueException.getCause) match {
          case Some(mbwe: MongoBulkWriteException) =>
            mbwe.getWriteErrors.asScala.map(_.getCategory) match {
              case Seq(ErrorCategory.DUPLICATE_KEY) => ()
              case _ => throw mbwe
            }
          case _ => throw rogueException
        }
    }
  }

  def bulkRemoveResult(removedCount: Int): Option[BulkWriteResult] = {
    Some(
      BulkWriteResult.acknowledged(
        0,
        0,
        removedCount,
        0,
        new ArrayList[BulkWriteUpsert]
      )
    )
  }

  @Test
  def testAsyncBulkOperationRemoveOne(): Unit = {
    val testRecords = Array(
      newTestRecord(0),
      newTestRecord(1),
      newTestRecord(2)
    )

    val testFuture = for {
      // delete with no stored records
      _ <- asyncQueryExecutor
        .bulk(
          Vector(BulkRemoveOne(SimpleRecord))
        )
        .map(_ must_== bulkRemoveResult(0))

      // delete single record
      _ <- asyncQueryExecutor.insertAll(testRecords)
      _ <- asyncQueryExecutor
        .bulk(
          Vector(BulkRemoveOne(SimpleRecord.where(_.id eqs testRecords(0).id)))
        )
        .map(_ must_== bulkRemoveResult(1))
      _ <- asyncQueryExecutor.count(SimpleRecord.where(_.id eqs testRecords(0).id)).map(_ must_== 0)

      // delete query does not match
      _ <- asyncQueryExecutor
        .bulk(
          Vector(BulkRemoveOne(SimpleRecord.where(_.id eqs testRecords(0).id)))
        )
        .map(_ must_== bulkRemoveResult(0))
      _ <- asyncQueryExecutor.count(SimpleRecord).map(_ must_== 2)

      // match multiple records but only delete one
      _ <- asyncQueryExecutor
        .bulk(
          Vector(BulkRemoveOne(SimpleRecord))
        )
        .map(_ must_== bulkRemoveResult(1))
      _ <- asyncQueryExecutor.count(SimpleRecord).map(_ must_== 1)
    } yield ()

    Await.result(testFuture, Duration.fromSeconds(5))
  }

  @Test
  def testBlockingBulkOperationRemoveOne(): Unit = {
    val testRecords = Array(
      newTestRecord(0),
      newTestRecord(1),
      newTestRecord(2)
    )

    // delete with no stored records
    blockingQueryExecutor
      .bulk(
        Vector(BulkRemoveOne(SimpleRecord))
      )
      .unwrap must_== bulkRemoveResult(0)

    // delete single record
    blockingQueryExecutor.insertAll(testRecords)
    blockingQueryExecutor
      .bulk(
        Vector(BulkRemoveOne(SimpleRecord.where(_.id eqs testRecords(0).id)))
      )
      .unwrap must_== bulkRemoveResult(1)
    blockingQueryExecutor.count(SimpleRecord.where(_.id eqs testRecords(0).id)).unwrap must_== 0

    // delete query does not match
    blockingQueryExecutor
      .bulk(
        Vector(BulkRemoveOne(SimpleRecord.where(_.id eqs testRecords(0).id)))
      )
      .unwrap must_== bulkRemoveResult(0)
    blockingQueryExecutor.count(SimpleRecord).unwrap must_== 2

    // match multiple records but only delete one
    blockingQueryExecutor
      .bulk(
        Vector(BulkRemoveOne(SimpleRecord))
      )
      .unwrap must_== bulkRemoveResult(1)
    blockingQueryExecutor.count(SimpleRecord).unwrap must_== 1
  }

  @Test
  def testAsyncBulkOperationRemove(): Unit = {
    val emptyRecord = SimpleRecord()
    val testRecords = Seq.tabulate(5)(newTestRecord)
    val testRecordIds = testRecords.map(_.id)

    val testFutures = for {
      // no matching records
      _ <- asyncQueryExecutor
        .bulk(
          Vector(BulkRemove(SimpleRecord))
        )
        .map(_ must_== bulkRemoveResult(0))

      _ <- Future.join(
        for {
          // empty record
          _ <- asyncQueryExecutor.insert(emptyRecord)
          _ <- asyncQueryExecutor
            .bulk(
              Vector(BulkRemove(SimpleRecord.where(_.id eqs emptyRecord.id)))
            )
            .map(_ must_== bulkRemoveResult(1))
          _ <- asyncQueryExecutor.count(SimpleRecord.where(_.id eqs emptyRecord.id)).map(_ must_== 0)
        } yield (),
        for {
          _ <- asyncQueryExecutor.insertAll(testRecords)

          // remove single record
          _ <- asyncQueryExecutor
            .bulk(
              Vector(BulkRemove(SimpleRecord.where(_.id eqs testRecords(0).id)))
            )
            .map(_ must_== bulkRemoveResult(1))
          _ <- asyncQueryExecutor.count(SimpleRecord.where(_.id in testRecordIds)).map(_ must_== 4)

          // remove multiple records
          _ <- asyncQueryExecutor
            .bulk(
              Vector(BulkRemove(SimpleRecord.where(_.int eqs 1)))
            )
            .map(_ must_== bulkRemoveResult(2))
          _ <- asyncQueryExecutor.count(SimpleRecord.where(_.id in testRecordIds)).map(_ must_== 2)

          // re-run of previous remove shouldn't delete anything
          _ <- asyncQueryExecutor
            .bulk(
              Vector(BulkRemove(SimpleRecord.where(_.int eqs 1)))
            )
            .map(_ must_== bulkRemoveResult(0))
          _ <- asyncQueryExecutor.count(SimpleRecord.where(_.id in testRecordIds)).map(_ must_== 2)
        } yield ()
      )

      // remove everything
      _ <- for {
        _ <- asyncQueryExecutor
          .bulk(
            Vector(BulkRemove(SimpleRecord))
          )
          .map(_ must_== bulkRemoveResult(2))
        _ <- asyncQueryExecutor.count(SimpleRecord).map(_ must_== 0)
      } yield ()
    } yield ()

    Await.result(testFutures, Duration.fromSeconds(5))
  }

  @Test
  def testBlockingBulkOperationRemove(): Unit = {
    val emptyRecord = SimpleRecord()
    val testRecords = Seq.tabulate(5)(newTestRecord)
    val testRecordIds = testRecords.map(_.id)

    // no matching records
    blockingQueryExecutor
      .bulk(
        Vector(BulkRemove(SimpleRecord))
      )
      .unwrap must_== bulkRemoveResult(0)

    // empty record
    blockingQueryExecutor.insert(emptyRecord)
    blockingQueryExecutor
      .bulk(
        Vector(BulkRemove(SimpleRecord.where(_.id eqs emptyRecord.id)))
      )
      .unwrap must_== bulkRemoveResult(1)
    blockingQueryExecutor.count(SimpleRecord.where(_.id eqs emptyRecord.id)).unwrap must_== 0

    blockingQueryExecutor.insertAll(testRecords)

    // remove single record
    blockingQueryExecutor
      .bulk(
        Vector(BulkRemove(SimpleRecord.where(_.id eqs testRecords(0).id)))
      )
      .unwrap must_== bulkRemoveResult(1)
    blockingQueryExecutor.count(SimpleRecord.where(_.id in testRecordIds)).unwrap must_== 4

    // remove multiple records
    blockingQueryExecutor
      .bulk(
        Vector(BulkRemove(SimpleRecord.where(_.int eqs 1)))
      )
      .unwrap must_== bulkRemoveResult(2)
    blockingQueryExecutor.count(SimpleRecord.where(_.id in testRecordIds)).unwrap must_== 2

    // re-run of previous remove shouldn't delete anything
    blockingQueryExecutor
      .bulk(
        Vector(BulkRemove(SimpleRecord.where(_.int eqs 1)))
      )
      .unwrap must_== bulkRemoveResult(0)
    blockingQueryExecutor.count(SimpleRecord.where(_.id in testRecordIds)).unwrap must_== 2

    // remove everything
    blockingQueryExecutor
      .bulk(
        Vector(BulkRemove(SimpleRecord))
      )
      .unwrap must_== bulkRemoveResult(2)
    blockingQueryExecutor.count(SimpleRecord).unwrap must_== 0
  }

  def bulkUpdateResult(
    matchedCount: Int,
    modifiedCount: Int,
    upserts: JavaList[BulkWriteUpsert] = new ArrayList[BulkWriteUpsert]
  ): Option[BulkWriteResult] = {
    Some(
      BulkWriteResult.acknowledged(
        0,
        matchedCount,
        0,
        modifiedCount: Integer,
        upserts
      )
    )
  }

  @Test
  def testAsyncBulkOperationReplaceOne(): Unit = {
    val testRecords = Seq.tabulate(4)(i => OptionalIdRecord(id = Some(new ObjectId), int = Some(i)))

    val serialTestFutures = for {
      // replace on non-existant record
      _ <- asyncQueryExecutor
        .bulk(
          Vector(
            BulkReplaceOne(
              OptionalIdRecord,
              testRecords(0),
              upsert = false
            )
          )
        )
        .map(_ must_== bulkUpdateResult(0, 0))

      // upserts
      _ <- asyncQueryExecutor
        .bulk(
          testRecords
            .take(3)
            .map(record => {
              BulkReplaceOne(
                OptionalIdRecord.where(_.id eqs record.id.get),
                record,
                upsert = true
              )
            }),
          ordered = true
        )
        .map(
          _ must_== bulkUpdateResult(
            0,
            0,
            testRecords
              .take(3)
              .zipWithIndex
              .map({
                case (record, i) => new BulkWriteUpsert(i, new BsonObjectId(record.id.get))
              })
              .asJava
          )
        )
      _ <- asyncQueryExecutor.count(OptionalIdRecord).map(_ must_== 3)
    } yield ()

    val replacement = OptionalIdRecord(int = Some(9001))
    val allTestFutures = serialTestFutures.flatMap(_ => {
      Future.join(
        // match one and replace one
        asyncQueryExecutor
          .bulk(
            Vector(
              BulkReplaceOne(
                OptionalIdRecord.where(_.id eqs testRecords(0).id.get),
                replacement,
                upsert = false
              )
            )
          )
          .flatMap(bulkWriteResult => {
            bulkWriteResult must_== bulkUpdateResult(1, 1)
            asyncQueryExecutor
              .fetchOne(
                OptionalIdRecord.where(_.id eqs testRecords(0).id.get)
              )
              .map(_ must_== Some(OptionalIdRecord(id = testRecords(0).id, int = replacement.int)))
          }),
        // match multiple and replace one
        asyncQueryExecutor
          .bulk(
            Vector(
              BulkReplaceOne(
                OptionalIdRecord.where(_.id neqs testRecords(0).id.get),
                OptionalIdRecord(int = Some(2048)),
                upsert = false
              )
            )
          )
          .map(
            _ must_== bulkUpdateResult(1, 1)
          )
      )
    })

    Await.result(allTestFutures, Duration.fromSeconds(5))
  }

  @Test
  def testBlockingBulkOperationReplaceOne(): Unit = {
    val testRecords = Seq.tabulate(4)(i => OptionalIdRecord(id = Some(new ObjectId), int = Some(i)))

    // replace on non-existant record
    blockingQueryExecutor
      .bulk(
        Vector(
          BulkReplaceOne(
            OptionalIdRecord,
            testRecords(0),
            upsert = false
          )
        )
      )
      .unwrap must_== bulkUpdateResult(0, 0)

    // upserts
    blockingQueryExecutor
      .bulk(
        testRecords
          .take(3)
          .map(record => {
            BulkReplaceOne(
              OptionalIdRecord.where(_.id eqs record.id.get),
              record,
              upsert = true
            )
          }),
        ordered = true
      )
      .unwrap must_== bulkUpdateResult(
      0,
      0,
      testRecords
        .take(3)
        .zipWithIndex
        .map({
          case (record, i) => new BulkWriteUpsert(i, new BsonObjectId(record.id.get))
        })
        .asJava
    )
    blockingQueryExecutor.count(OptionalIdRecord).unwrap must_== 3

    // match one and replace one
    val replacement = OptionalIdRecord(int = Some(9001))
    blockingQueryExecutor
      .bulk(
        Vector(
          BulkReplaceOne(
            OptionalIdRecord.where(_.id eqs testRecords(0).id.get),
            replacement,
            upsert = false
          )
        )
      )
      .unwrap must_== bulkUpdateResult(1, 1)
    blockingQueryExecutor
      .fetchOne(
        OptionalIdRecord.where(_.id eqs testRecords(0).id.get)
      )
      .unwrap must_== Some(OptionalIdRecord(id = testRecords(0).id, int = replacement.int))

    // match multiple and replace one
    blockingQueryExecutor
      .bulk(
        Vector(
          BulkReplaceOne(
            OptionalIdRecord.where(_.int neqs testRecords(0).int.get),
            OptionalIdRecord(int = Some(2048)),
            upsert = false
          )
        )
      )
      .unwrap must_== bulkUpdateResult(1, 1)
  }

  @Test
  def testAsyncBulkOperationUpdateOne(): Unit = {
    val testRecord = newTestRecord(0)
    val extraRecord = newTestRecord(1)

    val serialTestFutures = Future.join(
      // upsert
      asyncQueryExecutor
        .bulk(
          Vector(
            BulkUpdateOne(
              SimpleRecord.where(_.id eqs extraRecord.id).modify(_.int setTo extraRecord.int),
              upsert = true
            )
          )
        )
        .flatMap(bulkWriteResult => {
          val expectedUpsert = new BulkWriteUpsert(0, new BsonObjectId(extraRecord.id))
          bulkWriteResult must_== bulkUpdateResult(0, 0, Vector(expectedUpsert).asJava)
          asyncQueryExecutor
            .fetchOne(
              SimpleRecord.where(_.id eqs extraRecord.id)
            )
            .map(_ must_== Some(SimpleRecord(id = extraRecord.id, int = extraRecord.int)))
        }),
      for {
        // update on non-existant record
        _ <- asyncQueryExecutor
          .bulk(
            Vector(
              BulkUpdateOne(
                SimpleRecord.where(_.id eqs testRecord.id).modify(_.int setTo testRecord.int),
                upsert = false
              )
            )
          )
          .map(_ must_== bulkUpdateResult(0, 0))

        // no-op update
        _ <- asyncQueryExecutor.insert(testRecord)
        _ <- asyncQueryExecutor
          .bulk(
            Vector(
              BulkUpdateOne(
                SimpleRecord.where(_.id eqs testRecord.id).modify(_.int setTo testRecord.int),
                upsert = false
              )
            )
          )
          .map(_ must_== bulkUpdateResult(1, 0))

        // update on existing record
        _ <- asyncQueryExecutor
          .bulk(
            Vector(
              BulkUpdateOne(
                SimpleRecord.where(_.id eqs testRecord.id).modify(_.int setTo testRecord.int.map(_ + 10)),
                upsert = false
              )
            )
          )
          .map(_ must_== bulkUpdateResult(1, 1))
        _ <- asyncQueryExecutor
          .fetchOne(
            SimpleRecord.where(_.id eqs testRecord.id)
          )
          .map(_ must_== Some(testRecord.copy(int = testRecord.int.map(_ + 10))))
      } yield ()
    )

    val testFutures = serialTestFutures.flatMap(_ => {
      Future.join(
        // updates fail on modifying _id
        asyncQueryExecutor
          .bulk(
            Vector(
              BulkUpdateOne(
                SimpleRecord.where(_.id eqs testRecord.id).modify(_.id setTo new ObjectId),
                upsert = false
              )
            )
          )
          .map(_ => throw new Exception("Expected update failure when modifying _id field"))
          .handle({
            case rogueException: RogueException =>
              Option(rogueException.getCause) match {
                case Some(mbwe: MongoBulkWriteException) =>
                  mbwe.getWriteErrors.asScala.map(_.getCategory) match {
                    case Seq(ErrorCategory.UNCATEGORIZED) => ()
                    case _ => throw rogueException
                  }
                case _ => throw rogueException
              }
          }),
        // match multiple records, but only modify one
        asyncQueryExecutor
          .bulk(
            Vector(BulkUpdateOne(SimpleRecord.modify(_.boolean setTo true), upsert = false))
          )
          .map(_ must_== bulkUpdateResult(1, 1))
      )
    })

    Await.result(testFutures, Duration.fromSeconds(5))
  }

  @Test
  def testBlockingBulkOperationUpdateOne(): Unit = {
    val testRecord = newTestRecord(0)
    val extraRecord = newTestRecord(1)

    // upsert
    val expectedUpsert = new BulkWriteUpsert(0, new BsonObjectId(extraRecord.id))
    blockingQueryExecutor
      .bulk(
        Vector(
          BulkUpdateOne(
            SimpleRecord.where(_.id eqs extraRecord.id).modify(_.int setTo extraRecord.int),
            upsert = true
          )
        )
      )
      .unwrap must_== bulkUpdateResult(0, 0, Vector(expectedUpsert).asJava)
    blockingQueryExecutor
      .fetchOne(
        SimpleRecord.where(_.id eqs extraRecord.id)
      )
      .unwrap must_== Some(SimpleRecord(id = extraRecord.id, int = extraRecord.int))

    // update on non-existant record
    blockingQueryExecutor
      .bulk(
        Vector(
          BulkUpdateOne(
            SimpleRecord.where(_.id eqs testRecord.id).modify(_.int setTo testRecord.int),
            upsert = false
          )
        )
      )
      .unwrap must_== bulkUpdateResult(0, 0)

    // no-op update
    blockingQueryExecutor.insert(testRecord)
    blockingQueryExecutor
      .bulk(
        Vector(
          BulkUpdateOne(
            SimpleRecord.where(_.id eqs testRecord.id).modify(_.int setTo testRecord.int),
            upsert = false
          )
        )
      )
      .unwrap must_== bulkUpdateResult(1, 0)

    // update on existing record
    blockingQueryExecutor
      .bulk(
        Vector(
          BulkUpdateOne(
            SimpleRecord.where(_.id eqs testRecord.id).modify(_.int setTo testRecord.int.map(_ + 10)),
            upsert = false
          )
        )
      )
      .unwrap must_== bulkUpdateResult(1, 1)
    blockingQueryExecutor
      .fetchOne(
        SimpleRecord.where(_.id eqs testRecord.id)
      )
      .unwrap must_== Some(testRecord.copy(int = testRecord.int.map(_ + 10)))

    // updates fail on modifying _id
    try {
      blockingQueryExecutor.bulk(
        Vector(
          BulkUpdateOne(
            SimpleRecord.where(_.id eqs testRecord.id).modify(_.id setTo new ObjectId),
            upsert = false
          )
        )
      )
      throw new Exception("Expected update failure when modifying _id field")
    } catch {
      case rogueException: RogueException =>
        Option(rogueException.getCause) match {
          case Some(mbwe: MongoBulkWriteException) =>
            mbwe.getWriteErrors.asScala.map(_.getCategory) match {
              case Seq(ErrorCategory.UNCATEGORIZED) => ()
              case _ => throw rogueException
            }
          case _ => throw rogueException
        }
    }

    // match multiple records, but only modify one
    blockingQueryExecutor
      .bulk(
        Vector(BulkUpdateOne(SimpleRecord.modify(_.boolean setTo true), upsert = false))
      )
      .unwrap must_== bulkUpdateResult(1, 1)
  }

  @Test
  def testAsyncBulkOperationUpdateMany(): Unit = {
    val testRecord = newTestRecord(0)
    val extraRecord = newTestRecord(1)

    val serialTestFutures = Future.join(
      // upsert
      asyncQueryExecutor
        .bulk(
          Vector(
            BulkUpdateMany(
              SimpleRecord.where(_.id eqs extraRecord.id).modify(_.int setTo extraRecord.int),
              upsert = true
            )
          )
        )
        .flatMap(bulkWriteResult => {
          val expectedUpsert = new BulkWriteUpsert(0, new BsonObjectId(extraRecord.id))
          bulkWriteResult must_== bulkUpdateResult(0, 0, Vector(expectedUpsert).asJava)
          asyncQueryExecutor
            .fetchOne(
              SimpleRecord.where(_.id eqs extraRecord.id)
            )
            .map(_ must_== Some(SimpleRecord(id = extraRecord.id, int = extraRecord.int)))
        }),
      for {
        // update on non-existant record
        _ <- asyncQueryExecutor
          .bulk(
            Vector(
              BulkUpdateMany(
                SimpleRecord.where(_.id eqs testRecord.id).modify(_.int setTo testRecord.int),
                upsert = false
              )
            )
          )
          .map(_ must_== bulkUpdateResult(0, 0))

        // no-op update
        _ <- asyncQueryExecutor.insert(testRecord)
        _ <- asyncQueryExecutor
          .bulk(
            Vector(
              BulkUpdateMany(
                SimpleRecord.where(_.id eqs testRecord.id).modify(_.int setTo testRecord.int),
                upsert = false
              )
            )
          )
          .map(_ must_== bulkUpdateResult(1, 0))

        // update on existing record
        _ <- asyncQueryExecutor
          .bulk(
            Vector(
              BulkUpdateMany(
                SimpleRecord.where(_.id eqs testRecord.id).modify(_.int setTo testRecord.int.map(_ + 10)),
                upsert = false
              )
            )
          )
          .map(_ must_== bulkUpdateResult(1, 1))
        _ <- asyncQueryExecutor
          .fetchOne(
            SimpleRecord.where(_.id eqs testRecord.id)
          )
          .map(_ must_== Some(testRecord.copy(int = testRecord.int.map(_ + 10))))
      } yield ()
    )

    val testFutures = serialTestFutures.flatMap(_ => {
      Future.join(
        // updates fail on modifying _id
        asyncQueryExecutor
          .bulk(
            Vector(
              BulkUpdateMany(
                SimpleRecord.where(_.id eqs testRecord.id).modify(_.id setTo new ObjectId),
                upsert = false
              )
            )
          )
          .map(_ => throw new Exception("Expected update failure when modifying _id field"))
          .handle({
            case rogueException: RogueException =>
              Option(rogueException.getCause) match {
                case Some(mbwe: MongoBulkWriteException) =>
                  mbwe.getWriteErrors.asScala.map(_.getCategory) match {
                    case Seq(ErrorCategory.UNCATEGORIZED) => ()
                    case _ => throw rogueException
                  }
                case _ => throw rogueException
              }
          }),
        // update multiple records
        asyncQueryExecutor
          .bulk(
            Vector(BulkUpdateMany(SimpleRecord.modify(_.boolean setTo true), upsert = false))
          )
          .map(_ must_== bulkUpdateResult(2, 2))
      )
    })

    Await.result(testFutures, Duration.fromSeconds(5))
  }

  @Test
  def testBlockingBulkOperationUpdateMany(): Unit = {
    val testRecord = newTestRecord(0)
    val extraRecord = newTestRecord(1)

    // upsert
    val expectedUpsert = new BulkWriteUpsert(0, new BsonObjectId(extraRecord.id))
    blockingQueryExecutor
      .bulk(
        Vector(
          BulkUpdateMany(
            SimpleRecord.where(_.id eqs extraRecord.id).modify(_.int setTo extraRecord.int),
            upsert = true
          )
        )
      )
      .unwrap must_== bulkUpdateResult(0, 0, Vector(expectedUpsert).asJava)
    blockingQueryExecutor
      .fetchOne(
        SimpleRecord.where(_.id eqs extraRecord.id)
      )
      .unwrap must_== Some(SimpleRecord(id = extraRecord.id, int = extraRecord.int))

    // update on non-existant record
    blockingQueryExecutor
      .bulk(
        Vector(
          BulkUpdateMany(
            SimpleRecord.where(_.id eqs testRecord.id).modify(_.int setTo testRecord.int),
            upsert = false
          )
        )
      )
      .unwrap must_== bulkUpdateResult(0, 0)

    // no-op update
    blockingQueryExecutor.insert(testRecord)
    blockingQueryExecutor
      .bulk(
        Vector(
          BulkUpdateMany(
            SimpleRecord.where(_.id eqs testRecord.id).modify(_.int setTo testRecord.int),
            upsert = false
          )
        )
      )
      .unwrap must_== bulkUpdateResult(1, 0)

    // update on existing record
    blockingQueryExecutor
      .bulk(
        Vector(
          BulkUpdateMany(
            SimpleRecord.where(_.id eqs testRecord.id).modify(_.int setTo testRecord.int.map(_ + 10)),
            upsert = false
          )
        )
      )
      .unwrap must_== bulkUpdateResult(1, 1)
    blockingQueryExecutor
      .fetchOne(
        SimpleRecord.where(_.id eqs testRecord.id)
      )
      .unwrap must_== Some(testRecord.copy(int = testRecord.int.map(_ + 10)))

    // updates fail on modifying _id
    try {
      blockingQueryExecutor.bulk(
        Vector(
          BulkUpdateMany(
            SimpleRecord.where(_.id eqs testRecord.id).modify(_.id setTo new ObjectId),
            upsert = false
          )
        )
      )
      throw new Exception("Expected update failure when modifying _id field")
    } catch {
      case rogueException: RogueException =>
        Option(rogueException.getCause) match {
          case Some(mbwe: MongoBulkWriteException) =>
            mbwe.getWriteErrors.asScala.map(_.getCategory) match {
              case Seq(ErrorCategory.UNCATEGORIZED) => ()
              case _ => throw rogueException
            }
          case _ => throw rogueException
        }
    }

    // update multiple records
    blockingQueryExecutor
      .bulk(
        Vector(BulkUpdateMany(SimpleRecord.modify(_.boolean setTo true), upsert = false))
      )
      .unwrap must_== bulkUpdateResult(2, 2)
  }

  @Test
  def testParseShardKey(): Unit = {
    val expected = 2
    val record = OptionalIdRecord(int = Some(expected))
    val shardKeyValue = blockingClientAdapter.parseShardKeyValue(OptionalIdRecord.toDocument(record), Vector("int"))
    Assert.assertEquals(expected, shardKeyValue)
  }

  @Test
  def testParseShardKeyNested(): Unit = {
    val expected = new ObjectId()
    val idRecord = OptionalIdRecord(
      Some(new ObjectId()),
      int = None,
      nestedMap = Some(Map("a" -> Map("1" -> new ObjectId(), "2" -> expected)))
    )
    val shardKeyValue =
      blockingClientAdapter.parseShardKeyValue(OptionalIdRecord.toDocument(idRecord), Vector("nested_map", "a", "2"))
    Assert.assertEquals(expected, shardKeyValue)
  }

  private def buildBlockingExecutorWithOptionalShardKey(
    shardKeyOpt: Option[String]
  ): QueryExecutor[
    MongoDatabase,
    BlockingMongoCollection,
    Object,
    Document,
    TrivialORMMetaRecord[_],
    TrivialORMRecord,
    BlockingResult
  ] = {
    val collectionFactory = new TrivialORMMongoCollectionFactory(blockingClientManager) {
      override def getShardKeyNameFromRecord[R <: TrivialORMRecord](record: R): Option[String] = shardKeyOpt
    }
    val adapter = new BlockingMongoClientAdapter(
      collectionFactory,
      new DefaultQueryUtilities[BlockingResult]
    )
    new QueryExecutor(adapter, queryOptimizer, serializer) {
      override def defaultWriteConcern: WriteConcern = WriteConcern.W1
    }
  }
}
