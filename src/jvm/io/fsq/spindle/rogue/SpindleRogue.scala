// Copyright 2014 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.spindle.rogue

import io.fsq.field.Field
import io.fsq.rogue.{BSONType, Rogue}
import io.fsq.spindle.rogue.query.SpindleRogueSerializer
import io.fsq.spindle.runtime.{Enum, EnumIntField, EnumStringField, MetaRecord, Record}

trait SpindleRogue {
  // EnumIntField: Query (base, list, & set)
  implicit def enumFieldToSpindleEnumIntQueryField[M <: MetaRecord[_, _], F <: Enum[F]](
    f: Field[F, M] with EnumIntField
  ): SpindleEnumIntQueryField[M, F] =
    new SpindleEnumIntQueryField(f)

  implicit def enumListFieldToSpindleEnumIntListQueryField[M <: MetaRecord[_, _], F <: Enum[F]](
    f: Field[Seq[F], M] with EnumIntField
  ): SpindleEnumIntListQueryField[M, F] =
    new SpindleEnumIntListQueryField(f)

  implicit def enumSetFieldToSpindleEnumIntSetQueryField[M <: MetaRecord[_, _], F <: Enum[F]](
    f: Field[Set[F], M] with EnumIntField
  ): SpindleEnumIntSetQueryField[M, F] =
    new SpindleEnumIntSetQueryField(f)

  // EnumIntField: Modify (base, list, & set)
  implicit def enumFieldToSpindleEnumIntModifyField[M <: MetaRecord[_, _], F <: Enum[F]](
    f: Field[F, M] with EnumIntField
  ): SpindleEnumIntModifyField[M, F] =
    new SpindleEnumIntModifyField(f)

  implicit def enumFieldToSpindleEnumIntListModifyField[M <: MetaRecord[_, _], F <: Enum[F]](
    f: Field[Seq[F], M] with EnumIntField
  ): SpindleEnumIntListModifyField[M, F] =
    new SpindleEnumIntListModifyField(f)

  implicit def enumFieldToSpindleEnumIntSetModifyField[M <: MetaRecord[_, _], F <: Enum[F]](
    f: Field[Set[F], M] with EnumIntField
  ): SpindleEnumIntSetModifyField[M, F] =
    new SpindleEnumIntSetModifyField(f)

  // EnumStringField: Query (base, list, & set)
  implicit def enumFieldToSpindleEnumStringQueryField[M <: MetaRecord[_, _], F <: Enum[F]](
    f: Field[F, M] with EnumStringField
  ): SpindleEnumStringQueryField[M, F] =
    new SpindleEnumStringQueryField(f)

  implicit def enumListFieldToSpindleEnumStringListQueryField[M <: MetaRecord[_, _], F <: Enum[F]](
    f: Field[Seq[F], M] with EnumStringField
  ): SpindleEnumStringListQueryField[M, F] =
    new SpindleEnumStringListQueryField(f)

  implicit def enumSetFieldToSpindleEnumStringSetQueryField[M <: MetaRecord[_, _], F <: Enum[F]](
    f: Field[Set[F], M] with EnumStringField
  ): SpindleEnumStringSetQueryField[M, F] =
    new SpindleEnumStringSetQueryField(f)

  // EnumStringField: Modify (base, list, & set)
  implicit def enumFieldToSpindleEnumStringModifyField[M <: MetaRecord[_, _], F <: Enum[F]](
    f: Field[F, M] with EnumStringField
  ): SpindleEnumStringModifyField[M, F] =
    new SpindleEnumStringModifyField(f)

  implicit def enumFieldToSpindleEnumStringListModifyField[M <: MetaRecord[_, _], F <: Enum[F]](
    f: Field[Seq[F], M] with EnumStringField
  ): SpindleEnumStringListModifyField[M, F] =
    new SpindleEnumStringListModifyField(f)

  implicit def enumFieldToSpindleEnumStringSetModifyField[M <: MetaRecord[_, _], F <: Enum[F]](
    f: Field[Set[F], M] with EnumStringField
  ): SpindleEnumStringSetModifyField[M, F] =
    new SpindleEnumStringSetModifyField(f)

  implicit def embeddedFieldToSpindleEmbeddedRecordQueryField[
    R <: Record[_],
    MM <: MetaRecord[_, _]
  ](
    f: Field[R, MM]
  ): SpindleEmbeddedRecordQueryField[R, MM] = new SpindleEmbeddedRecordQueryField(f)

  implicit def embeddedFieldToSpindleEmbeddedRecordModifyField[
    R <: Record[_],
    MM <: MetaRecord[_, _]
  ](
    f: Field[R, MM]
  ): SpindleEmbeddedRecordModifyField[R, MM] = new SpindleEmbeddedRecordModifyField(f)

  implicit def embeddedListFieldToSpindleEmbeddedRecordListQueryField[
    R <: Record[_],
    MM <: MetaRecord[_, _]
  ](
    f: Field[Seq[R], MM]
  ): SpindleEmbeddedRecordListQueryField[R, MM] = new SpindleEmbeddedRecordListQueryField(f)

  implicit def embeddedListFieldToSpindleEmbeddedRecordListModifyField[
    R <: Record[_],
    MM <: MetaRecord[_, _]
  ](
    f: Field[Seq[R], MM]
  ): SpindleEmbeddedRecordListModifyField[R, MM] = new SpindleEmbeddedRecordListModifyField(f)

  class SpindleRecordIsBSONType[R <: Record[R]] extends BSONType[R] {
    private val serializer = new SpindleRogueSerializer
    override def asBSONObject(v: R): AnyRef = serializer.writeToDocument(v)
  }

  object _SpindleRecordIsBSONType extends SpindleRecordIsBSONType[Nothing]

  implicit def SpindleRecordIsBSONType[R <: Record[R]]: BSONType[R] = _SpindleRecordIsBSONType.asInstanceOf[BSONType[R]]
}

object SpindleRogue extends Rogue with SpindleRogue
