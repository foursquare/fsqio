// Copyright 2016 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.rogue.adapter

import com.mongodb.ReadPreference
import io.fsq.rogue.Query
import io.fsq.rogue.index.UntypedMongoIndex


trait MongoCollectionFactory[MongoCollection[_], Document, MetaRecord, Record] {

  def documentClass: Class[Document]

  // TODO(jacob): We should get rid of the option to send down a read preference here and
  //    just use the one on the query.
  def getMongoCollection[
    M <: MetaRecord
  ](
    query: Query[M, _, _],
    readPreferenceOpt: Option[ReadPreference]
  ): MongoCollection[Document]

  def getInstanceName[M <: MetaRecord](query: Query[M, _, _]): String

  def getIndexes[M <: MetaRecord](query: Query[M, _, _]): Option[Seq[UntypedMongoIndex]]
}
