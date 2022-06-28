// Copyright 2014 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.spindle.rogue

import io.fsq.field.Field
import io.fsq.rogue.{
  AbstractListModifyField,
  AbstractListQueryField,
  AbstractModifyField,
  AbstractQueryField,
  DummyField,
  SelectableDummyField
}
import io.fsq.spindle.common.thrift.bson.TBSONObjectProtocol
import io.fsq.spindle.runtime.{CompanionProvider, Enum, EnumIntField, EnumStringField, MetaRecord, Record}
import org.apache.thrift.TBase
import org.bson.BSONObject

class SpindleEnumIntQueryField[M, E <: Enum[E]](field: Field[E, M] with EnumIntField)
  extends AbstractQueryField[E, E, Int, M](field) {
  override def valueToDB(e: E): Int = e.id
}

class SpindleEnumIntListQueryField[M, E <: Enum[E]](field: Field[Seq[E], M] with EnumIntField)
  extends AbstractListQueryField[E, E, Int, M, Seq](field) {

  override def at(i: Int): DummyField[E, M] with EnumIntField = {
    new DummyField[E, M](field.name + "." + i.toString, field.owner) with EnumIntField
  }

  override def valueToDB(e: E): Int = e.id
}

class SpindleEnumIntSetQueryField[M, E <: Enum[E]](field: Field[Set[E], M] with EnumIntField)
  extends AbstractListQueryField[E, E, Int, M, Set](field) {
  override def valueToDB(e: E): Int = e.id
}

class SpindleEnumIntModifyField[M, E <: Enum[E]](field: Field[E, M] with EnumIntField)
  extends AbstractModifyField[E, Int, M](field) {
  override def valueToDB(e: E): Int = e.id
}

class SpindleEnumIntListModifyField[M, E <: Enum[E]](field: Field[Seq[E], M] with EnumIntField)
  extends AbstractListModifyField[E, Int, M, Seq](field) {
  override def valueToDB(e: E): Int = e.id
}

class SpindleEnumIntSetModifyField[M, E <: Enum[E]](field: Field[Set[E], M] with EnumIntField)
  extends AbstractListModifyField[E, Int, M, Set](field) {
  override def valueToDB(e: E): Int = e.id
}

class SpindleEnumStringQueryField[M, E <: Enum[E]](field: Field[E, M] with EnumStringField)
  extends AbstractQueryField[E, E, String, M](field) {
  override def valueToDB(e: E): String = e.stringValue
}

class SpindleEnumStringListQueryField[M, E <: Enum[E]](field: Field[Seq[E], M] with EnumStringField)
  extends AbstractListQueryField[E, E, String, M, Seq](field) {

  override def at(i: Int): DummyField[E, M] with EnumStringField = {
    new DummyField[E, M](field.name + "." + i.toString, field.owner) with EnumStringField
  }

  override def valueToDB(e: E): String = e.stringValue
}

class SpindleEnumStringSetQueryField[M, E <: Enum[E]](field: Field[Set[E], M] with EnumStringField)
  extends AbstractListQueryField[E, E, String, M, Set](field) {
  override def valueToDB(e: E): String = e.stringValue
}

class SpindleEnumStringModifyField[M, E <: Enum[E]](field: Field[E, M] with EnumStringField)
  extends AbstractModifyField[E, String, M](field) {
  override def valueToDB(e: E): String = e.stringValue
}

class SpindleEnumStringListModifyField[M, E <: Enum[E]](field: Field[Seq[E], M] with EnumStringField)
  extends AbstractListModifyField[E, String, M, Seq](field) {
  override def valueToDB(e: E): String = e.stringValue
}

class SpindleEnumStringSetModifyField[M, E <: Enum[E]](field: Field[Set[E], M] with EnumStringField)
  extends AbstractListModifyField[E, String, M, Set](field) {
  override def valueToDB(e: E): String = e.stringValue
}

abstract class SpindleEmbeddedRecordQueryFieldHelper[C, F1, F2] {
  def field(subfield: C => F1): F2
  def select(subfield: C => F1): F2 = field(subfield)
}

abstract class SpindleEmbeddedRecordListQueryFieldHelper[C, F1, F2, F3] {
  def field(subfield: C => F1): F2
  def select(subfield: C => F1): F3
}

class SpindleEmbeddedRecordQueryField[
  R <: Record[_],
  MM <: MetaRecord[_, _]
](
  f: Field[R, MM]
) extends AbstractQueryField[R, R, BSONObject, MM](f) {

  override def valueToDB(b: R): BSONObject = {
    val factory = new TBSONObjectProtocol.WriterFactoryForDBObject
    val protocol = factory.getProtocol
    b.asInstanceOf[TBase[_, _]].write(protocol)
    protocol.getOutput
  }

  def sub[
    RR <: Record[RR],
    V
  ](
    implicit
    ev: R <:< Record[RR],
    d: CompanionProvider[RR]
  ) = new SpindleEmbeddedRecordQueryFieldHelper[d.CompanionT, Field[V, d.CompanionT], SelectableDummyField[V, MM]] {
    override def field(subfield: d.CompanionT => Field[V, d.CompanionT]): SelectableDummyField[V, MM] = {
      new SelectableDummyField[V, MM](f.name + "." + subfield(d.provide).name, f.owner)
    }
    def enumIntField(
      subfield: d.CompanionT => Field[V, d.CompanionT] with EnumIntField
    ): SelectableDummyField[V, MM] with EnumIntField = {
      new SelectableDummyField[V, MM](f.name + "." + subfield(d.provide).name, f.owner) with EnumIntField
    }
  }

  def unsafeField[V](name: String): SelectableDummyField[V, MM] = {
    new SelectableDummyField[V, MM](f.name + "." + name, f.owner)
  }
}

class SpindleEmbeddedRecordModifyField[
  R <: Record[_],
  MM <: MetaRecord[_, _]
](
  f: Field[R, MM]
) extends AbstractModifyField[R, BSONObject, MM](f) {

  override def valueToDB(b: R): BSONObject = {
    val factory = new TBSONObjectProtocol.WriterFactoryForDBObject
    val protocol = factory.getProtocol
    b.asInstanceOf[TBase[_, _]].write(protocol)
    protocol.getOutput
  }
}

class SpindleEmbeddedRecordListQueryField[
  R <: Record[_],
  MM <: MetaRecord[_, _]
](
  f: Field[Seq[R], MM]
) extends AbstractListQueryField[R, R, BSONObject, MM, Seq](f) {
  override def valueToDB(b: R): BSONObject = {
    val factory = new TBSONObjectProtocol.WriterFactoryForDBObject
    val protocol = factory.getProtocol
    b.asInstanceOf[TBase[_, _]].write(protocol)
    protocol.getOutput
  }

  def sub[
    RR <: Record[RR],
    V
  ](
    implicit
    ev: R <:< Record[RR],
    d: CompanionProvider[RR]
  ) = new SpindleEmbeddedRecordListQueryFieldHelper[
    d.CompanionT,
    Field[V, d.CompanionT],
    SelectableDummyField[V, MM],
    SelectableDummyField[Seq[Option[V]], MM]
  ] {
    override def field(subfield: d.CompanionT => Field[V, d.CompanionT]): SelectableDummyField[V, MM] = {
      new SelectableDummyField[V, MM](f.name + "." + subfield(d.provide).name, f.owner)
    }
    override def select(subfield: d.CompanionT => Field[V, d.CompanionT]): SelectableDummyField[Seq[Option[V]], MM] = {
      new SelectableDummyField[Seq[Option[V]], MM](f.name + "." + subfield(d.provide).name, f.owner)
    }
  }

  def unsafeField[V](name: String): SelectableDummyField[Seq[V], MM] = {
    new SelectableDummyField[Seq[V], MM](f.name + "." + name, f.owner)
  }
}

class SpindleEmbeddedRecordListModifyField[
  R <: Record[_],
  MM <: MetaRecord[_, _]
](
  f: Field[Seq[R], MM]
) extends AbstractListModifyField[R, BSONObject, MM, Seq](f) {

  override def valueToDB(b: R): BSONObject = {
    val factory = new TBSONObjectProtocol.WriterFactoryForDBObject
    val protocol = factory.getProtocol
    b.asInstanceOf[TBase[_, _]].write(protocol)
    protocol.getOutput
  }
}
