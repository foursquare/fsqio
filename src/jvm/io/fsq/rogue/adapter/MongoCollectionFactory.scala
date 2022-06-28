// Copyright 2016 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.rogue.adapter

import com.mongodb.{ReadPreference, WriteConcern}
import io.fsq.rogue.Query
import io.fsq.rogue.index.UntypedMongoIndex
import org.bson.codecs.configuration.CodecRegistry

trait MongoCollectionFactory[
  MongoDatabase,
  MongoCollection[_],
  DocumentValue,
  Document <: MongoClientAdapter.BaseDocument[DocumentValue],
  MetaRecord,
  Record
] {

  def documentClass: Class[Document]

  def documentToString(document: Document): String

  def getCodecRegistryFromQuery[M <: MetaRecord](
    query: Query[M, _, _]
  ): CodecRegistry

  // TODO(jacob): We should get rid of the option to send down a read preference here and
  //    just use the one on the query.
  def getMongoCollectionFromQuery[M <: MetaRecord](
    query: Query[M, _, _],
    readPreferenceOpt: Option[ReadPreference] = None,
    writeConcernOpt: Option[WriteConcern] = None
  ): MongoCollection[Document]

  def getMongoCollectionFromMetaRecord[M <: MetaRecord](
    meta: M,
    readPreferenceOpt: Option[ReadPreference] = None,
    writeConcernOpt: Option[WriteConcern] = None
  ): MongoCollection[Document]

  /** Given a record, use the metadata to parse and convert the shard key name to the wire name used in MongoDB. Note
    * this method is only supported in Thrift and will return `None` if called with Lift.
    *
    * @param record record to parse
    * @tparam R represents either a Thrift UntypedRecord or Lift MongoRecord
    * @return an option containing the shard key field name in MongoDB or `None` if no shard key is specified; e.g.,
    *         Some("_id.u").
    */
  def getShardKeyNameFromRecord[R <: Record](record: R): Option[String]

  def getMongoCollectionFromRecord[R <: Record](
    record: R,
    readPreferenceOpt: Option[ReadPreference] = None,
    writeConcernOpt: Option[WriteConcern] = None
  ): MongoCollection[Document]

  def getMongoDatabaseFromQuery[M <: MetaRecord](query: Query[M, _, _]): MongoDatabase

  def getInstanceNameFromQuery[M <: MetaRecord](query: Query[M, _, _]): String

  def getInstanceNameFromRecord[R <: Record](record: R): String

  // TODO(jacob): The Option here is a bit superfluous, just return a possibly empty Seq.
  def getIndexes(meta: MetaRecord): Option[Seq[UntypedMongoIndex]]
}
