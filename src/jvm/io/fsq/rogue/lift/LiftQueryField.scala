// Copyright 2014 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.rogue.lift

import com.mongodb.DBObject
import io.fsq.field.Field
import io.fsq.rogue.{AbstractListModifyField, AbstractListQueryField, SelectableDummyField}

class CaseClassQueryField[V, M](val field: Field[V, M]) {
  def unsafeField[F](name: String): SelectableDummyField[F, M] = {
    new SelectableDummyField[F, M](field.name + "." + name, field.owner)
  }
}

class CaseClassListQueryField[V, M](field: Field[List[V], M])
    extends AbstractListQueryField[V, V, DBObject, M, List](field) {
  override def valueToDB(v: V) = LiftQueryHelpers.asDBObject(v)

  def unsafeField[F](name: String): SelectableDummyField[List[F], M] =
    new SelectableDummyField[List[F], M](field.name + "." + name, field.owner)
}

class CaseClassListModifyField[V, M](field: Field[List[V], M])
    extends AbstractListModifyField[V, DBObject, M, List](field) {
  override def valueToDB(v: V) = LiftQueryHelpers.asDBObject(v)
}

