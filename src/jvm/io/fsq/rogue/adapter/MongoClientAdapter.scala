// Copyright 2016 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.rogue.adapter

import com.mongodb.{Block, MongoNamespace, ReadPreference, WriteConcern}
import com.mongodb.client.model.{CountOptions, UpdateOptions}
import io.fsq.rogue.{ModifyQuery, Query}
import io.fsq.rogue.MongoHelpers.{MongoBuilder => LegacyMongoBuilder}
import io.fsq.rogue.util.QueryUtilities
import org.bson.{BsonDocument, BsonDocumentReader, BsonValue}
import org.bson.codecs.DecoderContext
import org.bson.conversions.Bson


object MongoClientAdapter {
  // NOTE(jacob): This restriction is technically unnecessary, we could also define some
  //    additional helpers and converters on MongoCollectionFactory to handle the
  //    operations these allow (ex.- get a field for a record or view a record as Bson).
  //    For now though, all built-in document types in the mongo driver fit this typedef
  //    and I see no good reason to add complexity where none is needed.
  type BaseDocument[BaseValue] = Bson with java.util.Map[String, BaseValue]
}

/** TODO(jacob): All of the collection methods implemented here should get rid of the
  *     option to send down a read preference, and just use the one on the query.
  */
abstract class MongoClientAdapter[
  MongoCollection[_],
  DocumentValue,
  Document <: MongoClientAdapter.BaseDocument[DocumentValue],
  MetaRecord,
  Record,
  Result[_]
](
  collectionFactory: MongoCollectionFactory[MongoCollection, DocumentValue, Document, MetaRecord, Record],
  val queryHelpers: QueryUtilities[Result]
) {

  /** Wrap a result for a no-op query. */
  def wrapResult[T](value: => T): Result[T]

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

  protected def countImpl(
    collection: MongoCollection[Document]
  )(
    filter: Bson,
    options: CountOptions
  ): Result[Long]

  protected def distinctImpl[T](
    resultAccessor: => T, // call by name
    accumulator: Block[BsonValue]
  )(
    collection: MongoCollection[Document]
  )(
    fieldName: String,
    filter: Bson
  ): Result[T]

  protected def findImpl[T](
    resultAccessor: => T, // call by name
    accumulator: Block[Document]
  )(
    collection: MongoCollection[Document]
  )(
    filter: Bson
  )(
    modifiers: Bson,
    batchSizeOpt: Option[Int] = None,
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

  def count[
    M <: MetaRecord
  ](
    query: Query[M, _, _],
    readPreferenceOpt: Option[ReadPreference]
  ): Result[Long] = {
    val queryClause = queryHelpers.transformer.transformQuery(query)
    queryHelpers.validator.validateQuery(queryClause, collectionFactory.getIndexes(queryClause))
    val collection = collectionFactory.getMongoCollectionFromQuery(query, readPreferenceOpt)
    val descriptionFunc = () => LegacyMongoBuilder.buildConditionString("count", query.collectionName, queryClause)
    // TODO(jacob): This cast will always succeed, but it should be removed once there is a
    //    version of LegacyMongoBuilder that speaks the new CRUD api.
    val filter = LegacyMongoBuilder.buildCondition(queryClause.condition).asInstanceOf[Bson]
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
    accumulator: Block[BsonValue]
  )(
    query: Query[M, _, _],
    fieldName: String,
    readPreferenceOpt: Option[ReadPreference]
  ): Result[T] = {
    val queryClause = queryHelpers.transformer.transformQuery(query)
    queryHelpers.validator.validateQuery(queryClause, collectionFactory.getIndexes(queryClause))
    val collection = collectionFactory.getMongoCollectionFromQuery(query, readPreferenceOpt)
    val descriptionFunc = () => LegacyMongoBuilder.buildConditionString("distinct", query.collectionName, queryClause)
    // TODO(jacob): This cast will always succeed, but it should be removed once there is a
    //    version of LegacyMongoBuilder that speaks the new CRUD api.
    val filter = LegacyMongoBuilder.buildCondition(queryClause.condition).asInstanceOf[Bson]

    runCommand(descriptionFunc, queryClause) {
      distinctImpl(resultAccessor, accumulator)(collection)(fieldName, filter)
    }
  }

  def countDistinct[M <: MetaRecord](
    query: Query[M, _, _],
    fieldName: String,
    readPreferenceOpt: Option[ReadPreference]
  ): Result[Long] = {
    var count = 0L
    val counter = new Block[BsonValue] {
      override def apply(value: BsonValue): Unit = {
        count += 1
      }
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

    val appender = new Block[BsonValue] {
      override def apply(value: BsonValue): Unit = {
        container.put("value", value)
        val document = documentCodec.decode(
          new BsonDocumentReader(container),
          DecoderContext.builder.build()
        )
        fieldsBuilder += resultTransformer(document.get("value"))
      }
    }

    distinctRunner(fieldsBuilder.result(): Seq[FieldType], appender)(query, fieldName, readPreferenceOpt)
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
    records.headOption.map(record => {
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
    }).getOrElse(wrapResult(records))
  }

  // NOTE(jacob): For better or for worse, the globally configured batch size takes
  //    precedence over whatever is passed down to this method: batchSizeOpt is only
  //    applied if the global config is unset.
  private def queryRunner[M <: MetaRecord, T](
    resultAccessor: => T, // call by name
    accumulator: Block[Document]
  )(
    operation: String,
    query: Query[M, _, _],
    batchSizeOpt: Option[Int],
    readPreferenceOpt: Option[ReadPreference],
    setMaxTimeMS: Boolean = false
  ): Result[T] = {
    val queryClause = queryHelpers.transformer.transformQuery(query)
    queryHelpers.validator.validateQuery(queryClause, collectionFactory.getIndexes(queryClause))
    // TODO(jacob): We should just use the read preference on the query itself.
    val queryReadPreferenceOpt = readPreferenceOpt.orElse(queryClause.readPreference)
    val collection = collectionFactory.getMongoCollectionFromQuery(query, queryReadPreferenceOpt)
    val descriptionFunc = () => LegacyMongoBuilder.buildQueryString(operation, query.collectionName, queryClause)

    // TODO(jacob): These casts will always succeed, but should be removed once there is a
    //    version of LegacyMongoBuilder that speaks the new CRUD api.
    val filter = LegacyMongoBuilder.buildCondition(queryClause.condition).asInstanceOf[Bson]

    val maxTimeMSOpt = {
      if (setMaxTimeMS) {
        queryHelpers.config.maxTimeMSOpt(collectionFactory.getInstanceNameFromQuery(queryClause))
      } else {
        None
      }
    }

    runCommand(descriptionFunc, queryClause) {
      findImpl(resultAccessor, accumulator)(collection)(filter)(
        modifiers = MongoBuilder.buildQueryModifiers(queryClause),
        batchSizeOpt = queryHelpers.config.cursorBatchSize.getOrElse(batchSizeOpt),
        limitOpt = queryClause.lim,
        skipOpt = queryClause.sk,
        sortOpt = queryClause.order.map(LegacyMongoBuilder.buildOrder(_).asInstanceOf[Bson]),
        projectionOpt = queryClause.select.map(LegacyMongoBuilder.buildSelect(_).asInstanceOf[Bson]),
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
    val accumulator = new Block[Document] {
      override def apply(value: Document): Unit = singleResultProcessor(value)
    }

    queryRunner(resultAccessor, accumulator)("find", query, batchSizeOpt, readPreferenceOpt, setMaxTimeMS = true)
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
    queryHelpers.validator.validateQuery(queryClause, collectionFactory.getIndexes(queryClause))
    val collection = collectionFactory.getMongoCollectionFromQuery(query, writeConcernOpt = writeConcernOpt)
    val descriptionFunc = () => LegacyMongoBuilder.buildConditionString("remove", query.collectionName, queryClause)
    // TODO(jacob): This cast will always succeed, but it should be removed once there is a
    //    version of LegacyMongoBuilder that speaks the new CRUD api.
    val filter = LegacyMongoBuilder.buildCondition(queryClause.condition).asInstanceOf[Bson]

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
      wrapResult(0L)

    } else {
      queryHelpers.validator.validateModify(modifyClause, collectionFactory.getIndexes(modifyClause.query))
      val collection = collectionFactory.getMongoCollectionFromQuery(
        modifyQuery.query,
        writeConcernOpt = writeConcernOpt
      )
      val descriptionFunc = () => LegacyMongoBuilder.buildModifyString(
        modifyQuery.query.collectionName,
        modifyClause,
        upsert = upsert,
        multi = multi
      )
      // TODO(jacob): These casts will always succeed, but should be removed once there is a
      //    version of LegacyMongoBuilder that speaks the new CRUD api.
      val filter = LegacyMongoBuilder.buildCondition(modifyClause.query.condition).asInstanceOf[Bson]
      val update = LegacyMongoBuilder.buildModify(modifyClause.mod).asInstanceOf[Bson]
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
}
