// Copyright 2017 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.twofishes.indexer.mongo

import com.mongodb.{BasicDBObject, ErrorCategory, MongoClient, MongoClientURI, MongoWriteException}
import com.mongodb.client.{MongoCollection, MongoDatabase}
import io.fsq.common.scala.Identity._
import io.fsq.common.scala.Lists.Implicits._
import io.fsq.rogue.QueryOptimizer
import io.fsq.rogue.adapter.{BlockingMongoClientAdapter, BlockingResult}
import io.fsq.rogue.connection.{BlockingMongoClientManager, DefaultMongoIdentifier}
import io.fsq.rogue.query.QueryExecutor
import io.fsq.rogue.util.DefaultQueryUtilities
import io.fsq.spindle.rogue.SpindleHelpers
import io.fsq.spindle.rogue.adapter.SpindleMongoCollectionFactory
import io.fsq.spindle.rogue.query.SpindleRogueSerializer
import io.fsq.spindle.runtime.{UntypedMetaRecord, UntypedRecord}
import io.fsq.twofishes.indexer.mongo.RogueImplicits._
import io.fsq.twofishes.indexer.util.{BoundingBox, DisplayName, GeocodeRecord}
import io.fsq.twofishes.model.gen.{ThriftGeocodeRecord, ThriftNameIndex}
import io.fsq.twofishes.util.StoredFeatureId
import org.bson.{BsonDocument, BsonInt32}
import org.bson.types.ObjectId
import scala.collection.JavaConverters._

// Singleton is gross, but convenient :D
object IndexerQueryExecutor {
  type ExecutorT = QueryExecutor[
    MongoDatabase,
    MongoCollection,
    Object,
    BasicDBObject,
    UntypedMetaRecord,
    UntypedRecord,
    BlockingResult
  ]

  private val mongoIdentifier = new DefaultMongoIdentifier("geocoder")

  private lazy val clientManager = {
    val manager = new BlockingMongoClientManager
    Runtime
      .getRuntime()
      .addShutdownHook(new Thread() {
        override def run(): Unit = {
          manager.closeAll()
        }
      })
    manager
  }

  /*
   SOMETHING TO KNOW BEFORE USING THIS EXECUTOR:
   If you're running executor.count(MyModel), make sure to annotate the left hand side as a Long.
   For example,
     val count: Long = executor.count(MyModel)
   Results from the executor are wrapped in a BlockingResult[T], which is unwrapped via an implicit.
   If you don't annotate the LHS, it assumes the type BlockingResult, which will cause issues with
   string formatting (i.e. attempt to print the BlockingResult instead of the underlying value).
   This mostly affects db.count's, since a common pattern in the indexer is to get a count of total
   objects for use in a progress display.
   */
  lazy val instance: ExecutorT = {
    val mongoClient = Option(System.getProperty("mongodb.server")) match {
      case Some(address) => new MongoClient(new MongoClientURI(s"mongodb://$address"))
      case None => new MongoClient() // Connect on default host and port
    }

    clientManager.defineDb(mongoIdentifier, () => mongoClient, "geocoder")

    ensureMongoVersionOrEmitHelpfulErrorMessage()

    val collectionFactory = new SpindleMongoCollectionFactory(clientManager)
    val queryUtilities = new DefaultQueryUtilities[BlockingResult]()
    val clientAdapter = new BlockingMongoClientAdapter(collectionFactory, queryUtilities)

    val optimizer = new QueryOptimizer
    val serializer = new SpindleRogueSerializer
    new QueryExecutor(clientAdapter, optimizer, serializer)
  }

  val RequiredMongoMajorVersion = 3

  // This can probably be removed in the future, but for now maybe people will find this
  // check/warning helpful, as opposed to getting halfway through the index build and getting
  // some error that's lost in the logs.
  private def ensureMongoVersionOrEmitHelpfulErrorMessage(): Unit = {
    def withErrorBanner(f: => Unit): Unit = {
      val rule = List.fill(50)('=').mkString
      val banner = s"${rule} ERROR ${rule}"
      println(banner)
      f
      println(banner)
    }
    try {
      val db = clientManager.getDbOrThrow(mongoIdentifier)
      val resultDoc = db.runCommand(new BsonDocument("buildinfo", new BsonInt32(1)))
      val versionString = resultDoc.getString("version")
      val majorVersionInt = versionString.takeWhile(_ !=? '.').toInt
      if (majorVersionInt < RequiredMongoMajorVersion) {
        withErrorBanner({
          println(s"It looks like you're using a Mongo server of version < 3.x! (Got version: ${versionString})")
          println("The indexer build now requires a mongod >= 3.x. Please upgrade your server and try again.")
        })
        System.exit(1)
      }
    } catch {
      case e: Exception => {
        withErrorBanner({
          println("Error while getting MongoDB version info: " + e)
          e.printStackTrace()
        })
        System.exit(1)
      }
    }
  }

  def dropCollection(meta: UntypedMetaRecord): Unit = {
    clientManager.useCollection(
      new DefaultMongoIdentifier(SpindleHelpers.getIdentifier(meta)),
      SpindleHelpers.getCollection(meta),
      classOf[BasicDBObject]
    )(coll => {
      coll.drop()
    })
  }
}

class RogueMongoGeocodeStorageService extends GeocodeStorageWriteService {
  val executor = IndexerQueryExecutor.instance

  def insert(record: GeocodeRecord): Unit = {
    insert(List(record))
  }

  def insert(record: List[GeocodeRecord]): Unit = {
    record.foreach(r => {
      // Geonames data may contain duplicate features (especially postal codes)
      // Silently swallow duplicate key exceptions and move on
      try {
        executor.insert(r)
      } catch {
        case mwe: MongoWriteException if mwe.getError.getCategory =? ErrorCategory.DUPLICATE_KEY =>
      }
    })
  }

  def setRecordNames(id: StoredFeatureId, names: List[DisplayName]) = {
    executor.updateOne(
      Q(ThriftGeocodeRecord)
        .where(_.id eqs id.longId)
        .modify(_.displayNames setTo names)
    )
  }

  def addBoundingBoxToRecord(bbox: BoundingBox, id: StoredFeatureId) = {
    executor.updateOne(
      Q(ThriftGeocodeRecord)
        .where(_.id eqs id.longId)
        .modify(_.boundingBox setTo bbox)
    )
  }

  def addNameToRecord(name: DisplayName, id: StoredFeatureId) = {
    executor.updateOne(
      Q(ThriftGeocodeRecord)
        .where(_.id eqs id.longId)
        .modify(_.displayNames addToSet name)
    )
  }

  def addNameIndex(name: NameIndex) = {
    executor.insert(name)
  }

  def addNameIndexes(names: List[NameIndex]) = {
    names.foreach(n => executor.insert(n))
  }

  def addPolygonToRecord(id: StoredFeatureId, polyId: ObjectId) = {
    executor.updateOne(
      Q(ThriftGeocodeRecord)
        .where(_.id eqs id.longId)
        .modify(_.hasPoly setTo true)
        .modify(_.polyId setTo polyId)
    )
  }

  def addSlugToRecord(id: StoredFeatureId, slug: String) = {
    executor.updateOne(
      Q(ThriftGeocodeRecord)
        .where(_.id eqs id.longId)
        .modify(_.slug setTo slug)
    )
  }

  def getById(id: StoredFeatureId): Iterator[GeocodeRecord] = {
    executor
      .fetch(
        Q(ThriftGeocodeRecord)
          .where(_.id eqs id.longId)
      )
      .map(new GeocodeRecord(_))
      .toIterator
  }

  def getNameIndexByIdLangAndName(id: StoredFeatureId, lang: String, name: String): Iterator[NameIndex] = {
    executor
      .fetch(
        Q(ThriftNameIndex)
          .scan(_.fid eqs id.longId)
          .scan(_.lang eqs lang)
          .scan(_.name eqs name)
      )
      .map(new NameIndex(_))
      .toIterator
  }

  def updateFlagsOnNameIndexByIdLangAndName(id: StoredFeatureId, lang: String, name: String, flags: Int) = {
    executor.updateMany(
      Q(ThriftNameIndex)
        .scan(_.fid eqs id.longId)
        .scan(_.lang eqs lang)
        .scan(_.name eqs name)
        .modify(_.flags setTo flags)
    )
  }
}
