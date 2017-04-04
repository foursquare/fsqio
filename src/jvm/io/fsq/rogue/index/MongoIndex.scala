// Copyright 2012 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.rogue.index

import io.fsq.field.Field
import scala.collection.immutable.ListMap

trait UntypedMongoIndex {
  def asListMap: ListMap[String, Any]

  override def toString() =
    asListMap.map(fld => "%s:%s".format(fld._1, fld._2)).mkString(", ")
}

trait MongoIndex[R] extends UntypedMongoIndex {
  def asTypedListMap: ListMap[String, IndexModifier]

  override def asListMap: ListMap[String, Any] = asTypedListMap.map({
    case (key, modifier) => (key, modifier.value)
  })
}

case class MongoIndex1[R](
    f1: Field[_, R],
    m1: IndexModifier
) extends MongoIndex[R] {
  override def asTypedListMap: ListMap[String, IndexModifier] = ListMap((f1.name, m1))
}

case class MongoIndex2[R](
    f1: Field[_, R],
    m1: IndexModifier,
    f2: Field[_, R],
    m2: IndexModifier
) extends MongoIndex[R] {
  override def asTypedListMap: ListMap[String, IndexModifier] = {
    ListMap((f1.name, m1), (f2.name, m2))
  }
}

case class MongoIndex3[R](
    f1: Field[_, R],
    m1: IndexModifier,
    f2: Field[_, R],
    m2: IndexModifier,
    f3: Field[_, R],
    m3: IndexModifier
) extends MongoIndex[R] {
  override def asTypedListMap: ListMap[String, IndexModifier] = {
    ListMap((f1.name, m1), (f2.name, m2), (f3.name, m3))
  }
}

case class MongoIndex4[R](
    f1: Field[_, R],
    m1: IndexModifier,
    f2: Field[_, R],
    m2: IndexModifier,
    f3: Field[_, R],
    m3: IndexModifier,
    f4: Field[_, R],
    m4: IndexModifier
) extends MongoIndex[R] {
  override def asTypedListMap: ListMap[String, IndexModifier] = {
    ListMap(
        (f1.name, m1),
        (f2.name, m2),
        (f3.name, m3),
        (f4.name, m4))
  }
}

case class MongoIndex5[R](
    f1: Field[_, R],
    m1: IndexModifier,
    f2: Field[_, R],
    m2: IndexModifier,
    f3: Field[_, R],
    m3: IndexModifier,
    f4: Field[_, R],
    m4: IndexModifier,
    f5: Field[_, R],
    m5: IndexModifier
) extends MongoIndex[R] {
  override def asTypedListMap: ListMap[String, IndexModifier] = {
    ListMap(
        (f1.name, m1),
        (f2.name, m2),
        (f3.name, m3),
        (f4.name, m4),
        (f5.name, m5))
  }
}

case class MongoIndex6[R](
    f1: Field[_, R],
    m1: IndexModifier,
    f2: Field[_, R],
    m2: IndexModifier,
    f3: Field[_, R],
    m3: IndexModifier,
    f4: Field[_, R],
    m4: IndexModifier,
    f5: Field[_, R],
    m5: IndexModifier,
    f6: Field[_, R],
    m6: IndexModifier
) extends MongoIndex[R] {
  override def asTypedListMap: ListMap[String, IndexModifier] = {
    ListMap(
        (f1.name, m1),
        (f2.name, m2),
        (f3.name, m3),
        (f4.name, m4),
        (f5.name, m5),
        (f6.name, m6))
  }
}

case class IndexBuilder[M](rec: M) {
  def index(
      f1: M => Field[_, M],
      m1: IndexModifier
  ): MongoIndex1[M] =
    MongoIndex1[M](f1(rec), m1)

  def index(
      f1: M => Field[_, M],
      m1: IndexModifier,
      f2: M => Field[_, M],
      m2: IndexModifier
  ): MongoIndex2[M] =
    MongoIndex2[M](f1(rec), m1, f2(rec), m2)

  def index(
      f1: M => Field[_, M],
      m1: IndexModifier,
      f2: M => Field[_, M],
      m2: IndexModifier,
      f3: M => Field[_, M],
      m3: IndexModifier
  ): MongoIndex3[M] =
    MongoIndex3[M](f1(rec), m1, f2(rec), m2, f3(rec), m3)

  def index(
      f1: M => Field[_, M],
      m1: IndexModifier,
      f2: M => Field[_, M],
      m2: IndexModifier,
      f3: M => Field[_, M],
      m3: IndexModifier,
      f4: M => Field[_, M],
      m4: IndexModifier
  ): MongoIndex4[M] =
    MongoIndex4[M](f1(rec), m1, f2(rec), m2, f3(rec), m3, f4(rec), m4)

  def index(
      f1: M => Field[_, M],
      m1: IndexModifier,
      f2: M => Field[_, M],
      m2: IndexModifier,
      f3: M => Field[_, M],
      m3: IndexModifier,
      f4: M => Field[_, M],
      m4: IndexModifier,
      f5: M => Field[_, M],
      m5: IndexModifier
  ): MongoIndex5[M] =
    MongoIndex5[M](f1(rec), m1, f2(rec), m2, f3(rec), m3, f4(rec), m4, f5(rec), m5)

  def index(
      f1: M => Field[_, M],
      m1: IndexModifier,
      f2: M => Field[_, M],
      m2: IndexModifier,
      f3: M => Field[_, M],
      m3: IndexModifier,
      f4: M => Field[_, M],
      m4: IndexModifier,
      f5: M => Field[_, M],
      m5: IndexModifier,
      f6: M => Field[_, M],
      m6: IndexModifier
  ): MongoIndex6[M] =
    MongoIndex6[M](f1(rec), m1, f2(rec), m2, f3(rec), m3, f4(rec), m4, f5(rec), m5, f6(rec), m6)
}
