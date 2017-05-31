// Copyright 2016 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.rogue.adapter

import com.mongodb.{ReadPreference, WriteConcern}
import io.fsq.rogue.Query
import io.fsq.rogue.index.UntypedMongoIndex
import org.bson.codecs.configuration.CodecRegistry


trait MongoCollectionFactory[
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

  def getMongoCollectionFromRecord[R <: Record](
    record: R,
    readPreferenceOpt: Option[ReadPreference] = None,
    writeConcernOpt: Option[WriteConcern] = None
  ): MongoCollection[Document]

  def getInstanceNameFromQuery[M <: MetaRecord](query: Query[M, _, _]): String

  def getInstanceNameFromRecord[R <: Record](record: R): String

  def getIndexes[M <: MetaRecord](query: Query[M, _, _]): Option[Seq[UntypedMongoIndex]]
}
