// Copyright 2012 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.rogue.index

import io.fsq.field.Field
import scala.collection.immutable.ListMap

trait UntypedMongoIndex {
  def asListMap: ListMap[String, Any]

  override def toString(): String = {
    asListMap
      .map({
        case (fieldName, modifier) => s"$fieldName:$modifier"
      })
      .mkString(", ")
  }
}

object DefaultUntypedMongoIndex {
  def apply[MetaRecord](
    indexEntries: Seq[(String, Any)]
  ): DefaultUntypedMongoIndex = {
    val indexMap = {
      val builder = ListMap.newBuilder[String, Any]
      builder ++= indexEntries
      builder.result()
    }
    DefaultUntypedMongoIndex(indexMap)
  }
}

case class DefaultUntypedMongoIndex(
  override val asListMap: ListMap[String, Any]
) extends UntypedMongoIndex

trait TypedMongoIndex[MetaRecord] extends UntypedMongoIndex {
  def asTypedListMap: ListMap[String, IndexModifier]
  def meta: MetaRecord

  override def asListMap: ListMap[String, Any] = asTypedListMap.map({
    case (fieldName, modifier) => (fieldName, modifier.value)
  })
}

object MongoIndex {
  class Builder[MetaRecord](meta: MetaRecord) {
    def index(
      f1: MetaRecord => Field[_, MetaRecord],
      m1: IndexModifier
    ): MongoIndex[MetaRecord] = {
      MongoIndex(
        meta,
        ListMap(
          (f1(meta).name, m1)
        )
      )
    }

    def index(
      f1: MetaRecord => Field[_, MetaRecord],
      m1: IndexModifier,
      f2: MetaRecord => Field[_, MetaRecord],
      m2: IndexModifier
    ): MongoIndex[MetaRecord] = {
      MongoIndex(
        meta,
        ListMap(
          (f1(meta).name, m1),
          (f2(meta).name, m2)
        )
      )
    }

    def index(
      f1: MetaRecord => Field[_, MetaRecord],
      m1: IndexModifier,
      f2: MetaRecord => Field[_, MetaRecord],
      m2: IndexModifier,
      f3: MetaRecord => Field[_, MetaRecord],
      m3: IndexModifier
    ): MongoIndex[MetaRecord] = {
      MongoIndex(
        meta,
        ListMap(
          (f1(meta).name, m1),
          (f2(meta).name, m2),
          (f3(meta).name, m3)
        )
      )
    }

    def index(
      f1: MetaRecord => Field[_, MetaRecord],
      m1: IndexModifier,
      f2: MetaRecord => Field[_, MetaRecord],
      m2: IndexModifier,
      f3: MetaRecord => Field[_, MetaRecord],
      m3: IndexModifier,
      f4: MetaRecord => Field[_, MetaRecord],
      m4: IndexModifier
    ): MongoIndex[MetaRecord] = {
      MongoIndex(
        meta,
        ListMap(
          (f1(meta).name, m1),
          (f2(meta).name, m2),
          (f3(meta).name, m3),
          (f4(meta).name, m4)
        )
      )
    }

    def index(
      f1: MetaRecord => Field[_, MetaRecord],
      m1: IndexModifier,
      f2: MetaRecord => Field[_, MetaRecord],
      m2: IndexModifier,
      f3: MetaRecord => Field[_, MetaRecord],
      m3: IndexModifier,
      f4: MetaRecord => Field[_, MetaRecord],
      m4: IndexModifier,
      f5: MetaRecord => Field[_, MetaRecord],
      m5: IndexModifier
    ): MongoIndex[MetaRecord] = {
      MongoIndex(
        meta,
        ListMap(
          (f1(meta).name, m1),
          (f2(meta).name, m2),
          (f3(meta).name, m3),
          (f4(meta).name, m4),
          (f5(meta).name, m5)
        )
      )
    }

    def index(
      f1: MetaRecord => Field[_, MetaRecord],
      m1: IndexModifier,
      f2: MetaRecord => Field[_, MetaRecord],
      m2: IndexModifier,
      f3: MetaRecord => Field[_, MetaRecord],
      m3: IndexModifier,
      f4: MetaRecord => Field[_, MetaRecord],
      m4: IndexModifier,
      f5: MetaRecord => Field[_, MetaRecord],
      m5: IndexModifier,
      f6: MetaRecord => Field[_, MetaRecord],
      m6: IndexModifier
    ): MongoIndex[MetaRecord] = {
      MongoIndex(
        meta,
        ListMap(
          (f1(meta).name, m1),
          (f2(meta).name, m2),
          (f3(meta).name, m3),
          (f4(meta).name, m4),
          (f5(meta).name, m5),
          (f6(meta).name, m6)
        )
      )
    }
  }

  def builder[MetaRecord](meta: MetaRecord): Builder[MetaRecord] = new Builder(meta)
}

case class MongoIndex[MetaRecord](
  override val meta: MetaRecord,
  override val asTypedListMap: ListMap[String, IndexModifier]
) extends TypedMongoIndex[MetaRecord] {
  override lazy val asListMap: ListMap[String, Any] = asTypedListMap.map({
    case (fieldName, modifier) => (fieldName, modifier.value)
  })
}
