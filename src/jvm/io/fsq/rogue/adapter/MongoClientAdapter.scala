// Copyright 2016 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.rogue.adapter

import com.mongodb.{MongoNamespace, ReadPreference, WriteConcern}
import com.mongodb.bulk.BulkWriteResult
import com.mongodb.client.model.{
  BulkWriteOptions,
  CountOptions,
  DeleteManyModel,
  DeleteOneModel,
  FindOneAndDeleteOptions,
  FindOneAndUpdateOptions,
  IndexModel,
  InsertOneModel,
  ReplaceOneModel,
  ReplaceOptions,
  ReturnDocument,
  UpdateManyModel,
  UpdateOneModel,
  UpdateOptions,
  WriteModel
}
import io.fsq.rogue.{
  BulkInsertOne,
  BulkModifyQueryOperation,
  BulkOperation,
  BulkQueryOperation,
  BulkRemove,
  BulkRemoveOne,
  BulkReplaceOne,
  BulkUpdateMany,
  BulkUpdateOne,
  FindAndModifyQuery,
  Iter,
  ModifyQuery,
  Query
}
import io.fsq.rogue.MongoHelpers.{AndCondition, MongoBuilder, MongoModify}
import io.fsq.rogue.index.UntypedMongoIndex
import io.fsq.rogue.util.QueryUtilities
import java.util.{ArrayList, List => JavaList}
import java.util.concurrent.TimeUnit
import org.bson.{BsonDocument, BsonDocumentReader, BsonInt32, BsonString, BsonValue}
import org.bson.codecs.DecoderContext
import org.bson.conversions.Bson
import scala.util.{Success, Try}

object MongoClientAdapter {
  // NOTE(jacob): This restriction is technically unnecessary, we could also define some
  //    additional helpers and converters on MongoCollectionFactory to handle the
  //    operations these allow (ex.- get a field for a record or view a record as Bson).
  //    For now though, all built-in document types in the mongo driver fit this typedef
  //    and I see no good reason to add complexity where none is needed.
  type BaseDocument[BaseValue] = Bson with java.util.Map[String, BaseValue]
}

trait AdapterUtil[DocumentValue, Document <: MongoClientAdapter.BaseDocument[DocumentValue]] {

  /** Given a MongoDB document and MongoDB shard key field name, recursively parses and returns the raw MongoDB shard
    * key value.
    *
    * @param document raw MongoDB document to parse
    * @param shardKeyFieldNames sequence representing each component of the shard key field name; e.g. if the shard key
    *                           is a nested field such as `_id.u`, passed in argument would be `Seq["_id", "u"]`
    * @return MongoDB DocumentValue of the shard key field
    */
  def parseShardKeyValue(document: Document, shardKeyFieldNames: Seq[String]): DocumentValue = {
    val valueOpt = Option(document.get(shardKeyFieldNames(0)))
    valueOpt
      .map(value => {
        shardKeyFieldNames.drop(1) match {
          case Seq() => value
          case remainingKeys => parseShardKeyValue(value.asInstanceOf[Document], remainingKeys)
        }
      })
      .getOrElse(document)
      .asInstanceOf[DocumentValue]
  }

}

/** TODO(jacob): All of the collection methods implemented here should get rid of the
  *     option to send down a read preference, and just use the one on the query.
  */
abstract class MongoClientAdapter[
  MongoDatabase,
  MongoCollection[_],
  DocumentValue,
  Document <: MongoClientAdapter.BaseDocument[DocumentValue],
  MetaRecord,
  Record,
  Result[_]
](
  collectionFactory: MongoCollectionFactory[
    MongoDatabase,
    MongoCollection,
    DocumentValue,
    Document,
    MetaRecord,
    Record
  ],
  val queryHelpers: QueryUtilities[Result]
) extends AdapterUtil[DocumentValue, Document] {

  /** The type of cursor used by find query processors. This is FindIterable[Document]
    * for both adapters, but they are different types.
    */
  type Cursor

  /** Wrap a result for a no-op query. May throw given Failure, depending on implementation. */
  def wrapResult[T](value: Try[T]): Result[T]

  /* TODO(jacob): Can we move this to a better place? It needs access to the
   *    implementation of MongoCollection used, so currently our options are either
   *    MongoClientAdapter or MongoClientManager. Perhaps we want to abstract out some
   *    kind of utility helper?
   */
  protected def getCollectionNamespace(collection: MongoCollection[Document]): MongoNamespace

  /* NOTE(jacob): We have to retry upserts that fail with duplicate key exceptions, see
   * https://jira.mongodb.org/browse/SERVER-14322
   */
  protected def upsertWithDuplicateKeyRetry[T](upsert: => Result[T]): Result[T]

  protected def runCommand[M <: MetaRecord, T](
    descriptionFunc: () => String,
    query: Query[M, _, _]
  )(
    f: => Result[T]
  ): Result[T]

  protected def createIndexesImpl(
    collection: MongoCollection[Document]
  )(
    indexes: Seq[IndexModel]
  ): Result[Seq[String]]

  protected def countImpl(
    collection: MongoCollection[Document]
  )(
    filter: Bson,
    options: CountOptions
  ): Result[Long]

  protected def distinctImpl[T](
    resultAccessor: => T, // call by name
    accumulator: BsonValue => Unit
  )(
    collection: MongoCollection[Document]
  )(
    fieldName: String,
    filter: Bson
  ): Result[T]

  /** A constructor for exhaustive cursor processors used in find queries. Essentially
    * just calls cursor.forEach.
    */
  protected def forEachProcessor[T](
    resultAccessor: => T, // call by name
    accumulator: Document => Unit
  )(
    cursor: Cursor
  ): Result[T]

  /** A constructor for iterative cursor processors used in find queries. This uses the
    * lower level cursor abstraction to allow short-circuiting consumption of the entire
    * cursor.
    */
  protected def iterateProcessor[R, T](
    initialIterState: T,
    deserializer: Document => R,
    handler: (T, Iter.Event[R]) => Iter.Command[T]
  )(
    cursor: Cursor
  ): Result[T]

  protected def findImpl[T](
    processor: Cursor => Result[T]
  )(
    collection: MongoCollection[Document]
  )(
    filter: Bson
  )(
    batchSizeOpt: Option[Int] = None,
    commentOpt: Option[String] = None,
    hintOpt: Option[Bson],
    limitOpt: Option[Int] = None,
    skipOpt: Option[Int] = None,
    sortOpt: Option[Bson] = None,
    projectionOpt: Option[Bson] = None,
    maxTimeMSOpt: Option[Long] = None
  ): Result[T]

  protected def insertImpl[R <: Record](
    collection: MongoCollection[Document]
  )(
    record: R,
    document: Document
  ): Result[R]

  protected def insertAllImpl[R <: Record](
    collection: MongoCollection[Document]
  )(
    records: Seq[R],
    documents: Seq[Document]
  ): Result[Seq[R]]

  protected def replaceOneImpl[R <: Record](
    collection: MongoCollection[Document]
  )(
    record: R,
    filter: Bson,
    document: Document,
    options: ReplaceOptions
  ): Result[R]

  protected def removeImpl[R <: Record](
    collection: MongoCollection[Document]
  )(
    record: R,
    document: Document
  ): Result[Long]

  protected def deleteImpl(
    collection: MongoCollection[Document]
  )(
    filter: Bson
  ): Result[Long]

  protected def updateOneImpl(
    collection: MongoCollection[Document]
  )(
    filter: Bson,
    update: Bson,
    options: UpdateOptions
  ): Result[Long]

  protected def updateManyImpl(
    collection: MongoCollection[Document]
  )(
    filter: Bson,
    update: Bson,
    options: UpdateOptions
  ): Result[Long]

  protected def findOneAndUpdateImpl[R](
    deserializer: Document => R
  )(
    collection: MongoCollection[Document]
  )(
    filter: Bson,
    update: Bson,
    options: FindOneAndUpdateOptions
  ): Result[Option[R]]

  protected def findOneAndDeleteImpl[R](
    deserializer: Document => R
  )(
    collection: MongoCollection[Document]
  )(
    filter: Bson,
    options: FindOneAndDeleteOptions
  ): Result[Option[R]]

  protected def bulkWriteImpl(
    collection: MongoCollection[Document]
  )(
    requests: JavaList[WriteModel[Document]],
    options: BulkWriteOptions
  ): Result[Option[BulkWriteResult]]

  private def convertMongoIndexToBson(index: UntypedMongoIndex): Bson = {
    val indexDocument = new BsonDocument
    index.asListMap.foreach({
      case (key, value: Int) => indexDocument.append(key, new BsonInt32(value))
      case (key, value: String) => indexDocument.append(key, new BsonString(value))
      case (key, value) => throw new RuntimeException(s"Unrecognized value type for MongoIndex: $value")
    })
    indexDocument
  }

  def createIndexes[M <: MetaRecord](
    metaRecord: M
  )(
    indexes: UntypedMongoIndex*
  ): Result[Seq[String]] = {
    val collection = collectionFactory.getMongoCollectionFromMetaRecord(metaRecord)
    val indexModels = indexes.map(index => {
      new IndexModel(convertMongoIndexToBson(index))
    })
    createIndexesImpl(collection)(indexModels)
  }

  def count[
    M <: MetaRecord
  ](
    query: Query[M, _, _],
    readPreferenceOpt: Option[ReadPreference]
  ): Result[Long] = {
    val queryClause = queryHelpers.transformer.transformQuery(query)
    queryHelpers.validator.validateQuery(
      queryClause,
      collectionFactory.getIndexes(queryClause.meta)
    )
    val collection = collectionFactory.getMongoCollectionFromQuery(query, readPreferenceOpt)
    val descriptionFunc = () => MongoBuilder.buildConditionString("count", query.collectionName, queryClause)
    // TODO(jacob): This cast will always succeed, but it should be removed once there is a
    //    version of MongoBuilder that speaks the new CRUD api.
    val filter = MongoBuilder.buildCondition(queryClause.condition).asInstanceOf[Bson]
    val options = {
      new CountOptions()
        .limit(queryClause.lim.getOrElse(0))
        .skip(queryClause.sk.getOrElse(0))
    }

    runCommand(descriptionFunc, queryClause) {
      countImpl(collection)(filter, options)
    }
  }

  private def distinctRunner[M <: MetaRecord, T](
    resultAccessor: => T, // call by name
    accumulator: BsonValue => Unit
  )(
    query: Query[M, _, _],
    fieldName: String,
    readPreferenceOpt: Option[ReadPreference]
  ): Result[T] = {
    val queryClause = queryHelpers.transformer.transformQuery(query)
    queryHelpers.validator.validateQuery(
      queryClause,
      collectionFactory.getIndexes(queryClause.meta)
    )
    val collection = collectionFactory.getMongoCollectionFromQuery(query, readPreferenceOpt)
    val descriptionFunc = () => MongoBuilder.buildConditionString("distinct", query.collectionName, queryClause)
    // TODO(jacob): This cast will always succeed, but it should be removed once there is a
    //    version of MongoBuilder that speaks the new CRUD api.
    val filter = MongoBuilder.buildCondition(queryClause.condition).asInstanceOf[Bson]

    runCommand(descriptionFunc, queryClause) {
      distinctImpl(resultAccessor, accumulator)(collection)(fieldName, filter)
    }
  }

  def countDistinct[M <: MetaRecord](
    query: Query[M, _, _],
    fieldName: String,
    readPreferenceOpt: Option[ReadPreference]
  ): Result[Long] = {
    @volatile var count = 0L
    def counter(value: BsonValue): Unit = {
      count += 1
    }

    distinctRunner(count, counter)(query, fieldName, readPreferenceOpt)
  }

  /* TODO(jacob): Do some profiling of different strategies to remove the intermediate
   *    serializations to BsonDocument/DBObject here. Some possible options:
   *
   *    1. We stick with the existing logic in DBCollection, as implemented here.
   *
   *    2. We define a custom CodecProvider mapping query types to a Codec class to use
   *       for them. This feels hacky and could be pretty onerous to maintain: as far as I
   *       can tell there is no way to "type erase" a Class object, so for example we
   *       would have to maintain separate mappings for Array[Int] and Array[String].
   *
   *    3. We register custom Codecs for some scala types (such as primitives). We likely
   *       wouldn't want to do this for everything, for the same reason as in 2.
   *       Primitives in particular are promising if we can avoid having to autobox them
   *       as objects, as would happen if we just mapped to mongo's ValueCodecs.
   *
   *    4. Some combination of 1, 2, and 3
   */
  def distinct[M <: MetaRecord, FieldType](
    query: Query[M, _, _],
    fieldName: String,
    resultTransformer: DocumentValue => FieldType,
    readPreferenceOpt: Option[ReadPreference]
  ): Result[Seq[FieldType]] = {
    val fieldsBuilder = Vector.newBuilder[FieldType]
    val container = new BsonDocument
    val documentCodec = collectionFactory.getCodecRegistryFromQuery(query).get(collectionFactory.documentClass)

    def appender(value: BsonValue): Unit = {
      container.put("value", value)
      val document = documentCodec.decode(
        new BsonDocumentReader(container),
        DecoderContext.builder.build()
      )
      fieldsBuilder += resultTransformer(document.get("value"))
    }

    distinctRunner(fieldsBuilder.result(): Seq[FieldType], appender)(query, fieldName, readPreferenceOpt)
  }

  def explainImpl[M <: MetaRecord](
    database: MongoDatabase,
    command: Bson,
    readPreference: ReadPreference
  ): Result[Document]

  def explain[M <: MetaRecord](
    query: Query[M, _, _],
    readPreferenceOpt: Option[ReadPreference]
  ): Result[Document] = {
    val queryClause = queryHelpers.transformer.transformQuery(query)
    queryHelpers.validator.validateQuery(
      queryClause,
      collectionFactory.getIndexes(queryClause.meta)
    )
    val database = collectionFactory.getMongoDatabaseFromQuery(query)
    val filter = MongoBuilder
      .buildCondition(queryClause.condition)
      .asInstanceOf[Bson]
      .toBsonDocument(collectionFactory.documentClass, collectionFactory.getCodecRegistryFromQuery(query))
    val command = new BsonDocument()
      .append("find", new BsonString(query.collectionName))
      .append("filter", filter)
    val params = new BsonDocument()
      .append("explain", command)

    // The mongo java driver defaults to primary read preference
    explainImpl(database, params, readPreferenceOpt.getOrElse(ReadPreference.primary()))
  }

  /** Saves a record by either calling replaceOne with upsert=true if the unique `_id` field exists in the given record,
    * otherwise calls insert to save as a new record.
    *
    * NOTE(ali): When calling replaceOne with upsert, MongoDB 4.2 requires us to specify the shard key so we add it
    * to the filter if it's not already included in the unique `_id` field.
    *
    * Release notes: https://docs.mongodb.com/manual/release-notes/4.2/#sharded-collections-and-replace-documents)
    * ReplaceOne with upsert docs: https://docs.mongodb.com/v4.2/reference/method/db.collection.replaceOne/#upsert
    * Insert docs: https://docs.mongodb.com/v4.2/reference/method/db.collection.insert/
    *
    * @param record rogue record to insert/replace
    * @param document serialized record as a MongoDB document
    * @param writeConcernOpt optional MongoDB write concern: https://docs.mongodb.com/manual/reference/write-concern/
    * @tparam R represents either a Thrift UntypedRecord or Lift MongoRecord
    * @return the newly inserted/replaced record wrapped in either an async or blocking `Result`
    */
  def save[R <: Record](
    record: R,
    document: Document,
    writeConcernOpt: Option[WriteConcern]
  ): Result[R] = {
    val collection: MongoCollection[Document] =
      collectionFactory.getMongoCollectionFromRecord(record, writeConcernOpt = writeConcernOpt)
    val collectionName: String = getCollectionNamespace(collection).getCollectionName
    val instanceName: String = collectionFactory.getInstanceNameFromRecord(record)

    val idFieldName = "_id"
    // NOTE(jacob): This emulates the legacy behavior of DBCollection: either a replace
    //    upsert by id or an insert if there is no id present.
    def run: Result[R] = {
      Option(document.get(idFieldName))
        .map(id => {
          val filter = collectionFactory.documentClass.newInstance
          filter.put(idFieldName, id)
          val shardKeyNameOpt: Option[String] = collectionFactory.getShardKeyNameFromRecord(record)
          shardKeyNameOpt.map(name => {
            // NOTE(ali): Upsert fails if shard key is added to the filter twice.
            if (!name.startsWith(idFieldName)) {
              val result: DocumentValue = parseShardKeyValue(document, name.split('.'))
              filter.put(name, result)
            }
          })
          val options = new ReplaceOptions().upsert(true)
          upsertWithDuplicateKeyRetry(
            replaceOneImpl(collection)(record, filter, document, options)
          )
        })
        .getOrElse({
          insertImpl(collection)(record, document)
        })
    }

    queryHelpers.logger.onExecuteWriteCommand(
      "save",
      collectionName,
      instanceName,
      collectionFactory.documentToString(document),
      run
    )
  }

  def insert[R <: Record](
    record: R,
    document: Document,
    writeConcernOpt: Option[WriteConcern]
  ): Result[R] = {
    val collection = collectionFactory.getMongoCollectionFromRecord(record, writeConcernOpt = writeConcernOpt)
    val collectionName = getCollectionNamespace(collection).getCollectionName
    val instanceName = collectionFactory.getInstanceNameFromRecord(record)
    queryHelpers.logger.onExecuteWriteCommand(
      "insert",
      collectionName,
      instanceName,
      collectionFactory.documentToString(document),
      insertImpl(collection)(record, document)
    )
  }

  def insertAll[R <: Record](
    records: Seq[R],
    documents: Seq[Document],
    writeConcernOpt: Option[WriteConcern]
  ): Result[Seq[R]] = {
    records.headOption
      .map(record => {
        val collection = collectionFactory.getMongoCollectionFromRecord(record, writeConcernOpt = writeConcernOpt)
        val collectionName = getCollectionNamespace(collection).getCollectionName
        val instanceName = collectionFactory.getInstanceNameFromRecord(record)
        queryHelpers.logger.onExecuteWriteCommand(
          "insert",
          collectionName,
          instanceName,
          documents.toIterator.map(collectionFactory.documentToString(_)).mkString("[", ",", "]"),
          insertAllImpl(collection)(records, documents)
        )
      })
      .getOrElse(wrapResult(Success(records)))
  }

  // NOTE(jacob): For better or for worse, the globally configured batch size takes
  //    precedence over whatever is passed down to this method: batchSizeOpt is only
  //    applied if the global config is unset.
  private def queryRunner[M <: MetaRecord, T](
    processor: Cursor => Result[T]
  )(
    operation: String,
    query: Query[M, _, _],
    batchSizeOpt: Option[Int],
    readPreferenceOpt: Option[ReadPreference],
    setMaxTimeMS: Boolean = false
  ): Result[T] = {
    val queryClause = queryHelpers.transformer.transformQuery(query)
    queryHelpers.validator.validateQuery(
      queryClause,
      collectionFactory.getIndexes(queryClause.meta)
    )
    // TODO(jacob): We should just use the read preference on the query itself.
    val queryReadPreferenceOpt = readPreferenceOpt.orElse(queryClause.readPreference)
    val collection = collectionFactory.getMongoCollectionFromQuery(query, queryReadPreferenceOpt)
    val descriptionFunc = () => MongoBuilder.buildQueryString(operation, query.collectionName, queryClause)

    // TODO(jacob): These casts will always succeed, but should be removed once there is a
    //    version of MongoBuilder that speaks the new CRUD api.
    val filter = MongoBuilder.buildCondition(queryClause.condition).asInstanceOf[Bson]

    val maxTimeMSOpt = {
      if (setMaxTimeMS) {
        queryHelpers.config.maxTimeMSOpt(collectionFactory.getInstanceNameFromQuery(queryClause))
      } else {
        None
      }
    }

    val queryHint: Option[Bson] = MongoBuilder.buildQueryHint(queryClause)

    runCommand(descriptionFunc, queryClause) {
      findImpl(processor)(collection)(filter)(
        batchSizeOpt = queryHelpers.config.cursorBatchSize.getOrElse(batchSizeOpt),
        commentOpt = query.comment,
        hintOpt = queryHint,
        limitOpt = queryClause.lim,
        skipOpt = queryClause.sk,
        sortOpt = queryClause.order.map(MongoBuilder.buildOrder(_).asInstanceOf[Bson]),
        projectionOpt = queryClause.select.map(MongoBuilder.buildSelect(_).asInstanceOf[Bson]),
        maxTimeMSOpt = maxTimeMSOpt
      )
    }
  }

  def query[M <: MetaRecord, T](
    resultAccessor: => T, // call by name
    singleResultProcessor: Document => Unit
  )(
    query: Query[M, _, _],
    batchSizeOpt: Option[Int],
    readPreferenceOpt: Option[ReadPreference]
  ): Result[T] = {
    queryRunner(forEachProcessor(resultAccessor, singleResultProcessor))(
      "find",
      query,
      batchSizeOpt,
      readPreferenceOpt,
      setMaxTimeMS = true
    )
  }

  def remove[R <: Record](
    record: R,
    document: Document,
    writeConcernOpt: Option[WriteConcern]
  ): Result[Long] = {
    val collection = collectionFactory.getMongoCollectionFromRecord(record, writeConcernOpt = writeConcernOpt)
    val collectionName = getCollectionNamespace(collection).getCollectionName
    val instanceName = collectionFactory.getInstanceNameFromRecord(record)
    queryHelpers.logger.onExecuteWriteCommand(
      "remove",
      collectionName,
      instanceName,
      collectionFactory.documentToString(document),
      removeImpl(collection)(record, document)
    )
  }

  def delete[M <: MetaRecord](
    query: Query[M, _, _],
    writeConcernOpt: Option[WriteConcern]
  ): Result[Long] = {
    val queryClause = queryHelpers.transformer.transformQuery(query)
    queryHelpers.validator.validateQuery(
      queryClause,
      collectionFactory.getIndexes(queryClause.meta)
    )
    val collection = collectionFactory.getMongoCollectionFromQuery(query, writeConcernOpt = writeConcernOpt)
    val descriptionFunc = () => MongoBuilder.buildConditionString("remove", query.collectionName, queryClause)
    // TODO(jacob): This cast will always succeed, but it should be removed once there is a
    //    version of MongoBuilder that speaks the new CRUD api.
    val filter = MongoBuilder.buildCondition(queryClause.condition).asInstanceOf[Bson]

    runCommand(descriptionFunc, queryClause) {
      deleteImpl(collection)(filter)
    }
  }

  def modify[M <: MetaRecord](
    modifyQuery: ModifyQuery[M, _],
    upsert: Boolean,
    multi: Boolean,
    writeConcernOpt: Option[WriteConcern]
  ): Result[Long] = {
    val modifyClause = queryHelpers.transformer.transformModify(modifyQuery)

    if (modifyClause.mod.clauses.isEmpty) {
      wrapResult(Success(0L))

    } else {
      queryHelpers.validator.validateModify(
        modifyClause,
        collectionFactory.getIndexes(modifyClause.query.meta)
      )
      val collection = collectionFactory.getMongoCollectionFromQuery(
        modifyQuery.query,
        writeConcernOpt = writeConcernOpt
      )
      val descriptionFunc = () =>
        MongoBuilder.buildModifyString(
          modifyQuery.query.collectionName,
          modifyClause,
          upsert = upsert,
          multi = multi
        )
      // TODO(jacob): These casts will always succeed, but should be removed once there is a
      //    version of MongoBuilder that speaks the new CRUD api.
      val filter = MongoBuilder.buildCondition(modifyClause.query.condition).asInstanceOf[Bson]
      val update = MongoBuilder.buildModify(modifyClause.mod).asInstanceOf[Bson]
      val options = {
        new UpdateOptions()
          .upsert(upsert)
      }

      def run: Result[Long] = runCommand(descriptionFunc, modifyClause.query) {
        if (multi) {
          updateManyImpl(collection)(filter, update, options)
        } else {
          updateOneImpl(collection)(filter, update, options)
        }
      }

      if (upsert) {
        upsertWithDuplicateKeyRetry(run)
      } else {
        run
      }
    }
  }

  def findOneAndUpdate[M <: MetaRecord, R](
    deserializer: Document => R
  )(
    findAndModify: FindAndModifyQuery[M, R],
    returnNew: Boolean,
    upsert: Boolean,
    writeConcernOpt: Option[WriteConcern]
  ): Result[Option[R]] = {
    val findAndModifyClause = queryHelpers.transformer.transformFindAndModify(findAndModify)

    // TODO(jacob): This preserves existing behavior, but callers should have some way of
    //    distinguishing "this query was empty and did not run" from "the query ran and
    //    did not match anything". We should probably return some sort of datatype that
    //    can encode that state.
    if (findAndModifyClause.mod.clauses.isEmpty) {
      wrapResult(Success(None))

    } else {
      queryHelpers.validator.validateFindAndModify(
        findAndModifyClause,
        collectionFactory.getIndexes(findAndModifyClause.query.meta)
      )
      val collection = collectionFactory.getMongoCollectionFromQuery(
        findAndModifyClause.query,
        writeConcernOpt = writeConcernOpt
      )
      val descriptionFunc = () =>
        MongoBuilder.buildFindAndModifyString(
          findAndModify.query.collectionName,
          findAndModifyClause,
          returnNew = returnNew,
          upsert = upsert,
          remove = false
        )

      // TODO(jacob): These casts will always succeed, but should be removed once there is a
      //    version of MongoBuilder that speaks the new CRUD api.
      val filter = MongoBuilder.buildCondition(findAndModifyClause.query.condition).asInstanceOf[Bson]
      val update = MongoBuilder.buildModify(findAndModifyClause.mod).asInstanceOf[Bson]

      val options = new FindOneAndUpdateOptions()
      queryHelpers.config
        .maxTimeMSOpt(collectionFactory.getInstanceNameFromQuery(findAndModifyClause.query))
        .foreach(
          options.maxTime(_, TimeUnit.MILLISECONDS)
        )
      findAndModifyClause.query.order.foreach(order => {
        options.sort(MongoBuilder.buildOrder(order).asInstanceOf[Bson])
      })
      findAndModifyClause.query.select.foreach(select => {
        options.projection(MongoBuilder.buildSelect(select).asInstanceOf[Bson])
      })
      if (returnNew) {
        options.returnDocument(ReturnDocument.AFTER)
      } else {
        options.returnDocument(ReturnDocument.BEFORE)
      }
      options.upsert(upsert)

      def run: Result[Option[R]] = runCommand(descriptionFunc, findAndModifyClause.query) {
        findOneAndUpdateImpl(deserializer)(collection)(filter, update, options)
      }

      if (upsert) {
        upsertWithDuplicateKeyRetry(run)
      } else {
        run
      }
    }
  }

  def findOneAndDelete[M <: MetaRecord, R](
    deserializer: Document => R
  )(
    query: Query[M, R, _],
    writeConcernOpt: Option[WriteConcern]
  ): Result[Option[R]] = {
    val queryClause = queryHelpers.transformer.transformQuery(query)
    queryHelpers.validator.validateQuery(
      queryClause,
      collectionFactory.getIndexes(queryClause.meta)
    )

    val collection = collectionFactory.getMongoCollectionFromQuery(query, writeConcernOpt = writeConcernOpt)
    val descriptionFunc = () =>
      MongoBuilder.buildFindAndModifyString(
        query.collectionName,
        FindAndModifyQuery(queryClause, MongoModify(Nil)),
        returnNew = false,
        upsert = false,
        remove = true
      )

    // TODO(jacob): These casts will always succeed, but should be removed once there is a
    //    version of MongoBuilder that speaks the new CRUD api.
    val filter = MongoBuilder.buildCondition(queryClause.condition).asInstanceOf[Bson]

    val options = new FindOneAndDeleteOptions()
    queryHelpers.config
      .maxTimeMSOpt(collectionFactory.getInstanceNameFromQuery(queryClause))
      .foreach(
        options.maxTime(_, TimeUnit.MILLISECONDS)
      )
    queryClause.order.foreach(order => {
      options.sort(MongoBuilder.buildOrder(order).asInstanceOf[Bson])
    })
    queryClause.select.foreach(select => {
      options.projection(MongoBuilder.buildSelect(select).asInstanceOf[Bson])
    })

    runCommand(descriptionFunc, queryClause) {
      findOneAndDeleteImpl(deserializer)(collection)(filter, options)
    }
  }

  def iterate[M <: MetaRecord, R, T](
    query: Query[M, R, _],
    initialIterState: T,
    deserializer: Document => R,
    readPreferenceOpt: Option[ReadPreference],
    batchSizeOpt: Option[Int]
  )(
    handler: (T, Iter.Event[R]) => Iter.Command[T]
  ): Result[T] = {
    queryRunner(iterateProcessor(initialIterState, deserializer, handler))(
      "find",
      query,
      batchSizeOpt,
      readPreferenceOpt
    )
  }

  def bulk[M <: MetaRecord, R <: Record](
    serializer: R => Document
  )(
    ops: Seq[BulkOperation[M, R]],
    ordered: Boolean = false,
    writeConcernOpt: Option[WriteConcern]
  ): Result[Option[BulkWriteResult]] = {
    ops.headOption
      .map(firstOp => {
        val numOps = ops.size
        val requests = new ArrayList[WriteModel[Document]](numOps)
        val descriptionBuilder = Vector.newBuilder[() => String]
        descriptionBuilder.sizeHint(numOps)

        ops.foreach({
          case BulkInsertOne(_, record) => {
            val document = serializer(record)
            requests.add(new InsertOneModel(document))
            descriptionBuilder += { () =>
              collectionFactory.documentToString(document)
            }
          }

          case queryOp: BulkQueryOperation[M, R] => {
            val queryClause = queryHelpers.transformer.transformQuery(queryOp.query)
            queryHelpers.validator.validateQuery(
              queryClause,
              collectionFactory.getIndexes(queryClause.meta)
            )
            descriptionBuilder += { () =>
              MongoBuilder.buildConditionString(
                queryOp.getClass.getSimpleName,
                queryOp.query.collectionName,
                queryClause
              )
            }
            // TODO(jacob): This cast will always succeed, but it should be removed once there is a
            //    version of MongoBuilder that speaks the new CRUD api.
            val filter = MongoBuilder.buildCondition(queryClause.condition).asInstanceOf[Bson]

            queryOp match {
              case _: BulkRemoveOne[M, R] => requests.add(new DeleteOneModel(filter))
              case _: BulkRemove[M, R] => requests.add(new DeleteManyModel(filter))
              case BulkReplaceOne(_, record, upsert) => {
                val document = serializer(record)
                val options = {
                  new ReplaceOptions()
                    .upsert(upsert)
                }
                requests.add(new ReplaceOneModel(filter, document, options))
              }
            }
          }

          case modifyOp: BulkModifyQueryOperation[M, R] => {
            val modifyClause = queryHelpers.transformer.transformModify(modifyOp.modifyQuery)

            if (modifyClause.mod.clauses.nonEmpty) {
              queryHelpers.validator.validateModify(
                modifyClause,
                collectionFactory.getIndexes(modifyClause.query.meta)
              )
              descriptionBuilder += { () =>
                MongoBuilder.buildModifyString(
                  modifyOp.modifyQuery.query.collectionName,
                  modifyClause,
                  upsert = modifyOp.upsert,
                  multi = modifyOp.multi
                )
              }
              // TODO(jacob): These casts will always succeed, but should be removed once there is a
              //    version of MongoBuilder that speaks the new CRUD api.
              val filter = MongoBuilder.buildCondition(modifyClause.query.condition).asInstanceOf[Bson]
              val update = MongoBuilder.buildModify(modifyClause.mod).asInstanceOf[Bson]
              val options = {
                new UpdateOptions()
                  .upsert(modifyOp.upsert)
              }

              modifyOp match {
                case _: BulkUpdateOne[M, R] => requests.add(new UpdateOneModel(filter, update, options))
                case _: BulkUpdateMany[M, R] => requests.add(new UpdateManyModel(filter, update, options))
              }
            }
          }
        })

        val collection = firstOp match {
          case BulkInsertOne(_, record) =>
            collectionFactory.getMongoCollectionFromRecord(
              record,
              writeConcernOpt = writeConcernOpt
            )

          case BulkQueryOperation(query) =>
            collectionFactory.getMongoCollectionFromQuery(
              query,
              writeConcernOpt = writeConcernOpt
            )

          case BulkModifyQueryOperation(modifyQuery, _) =>
            collectionFactory.getMongoCollectionFromQuery(
              modifyQuery.query,
              writeConcernOpt = writeConcernOpt
            )
        }

        val options = {
          new BulkWriteOptions()
            .ordered(ordered)
        }

        val descriptionFunc = () => {
          descriptionBuilder
            .result()
            .toIterator
            .map(_())
            .mkString("\n")
        }

        val fakeQueryForLogging = Query(
          meta = firstOp.metaRecord,
          collectionName = getCollectionNamespace(collection).getCollectionName,
          lim = None,
          sk = None,
          comment = Some("bulk"),
          hint = None,
          condition = AndCondition(Nil, None),
          order = None,
          select = None,
          readPreference = None
        )

        runCommand(descriptionFunc, fakeQueryForLogging) {
          bulkWriteImpl(collection)(requests, options)
        }
      })
      .getOrElse(
        wrapResult(Success(None))
      )
  }
}
