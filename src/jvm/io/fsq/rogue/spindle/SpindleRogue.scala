// Copyright 2014 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.rogue.spindle

import io.fsq.field.Field
import io.fsq.rogue.{BSONType, Rogue}
import io.fsq.spindle.runtime.{Enum, MetaRecord, Record}

trait SpindleRogue {
  implicit def enumFieldToSpindleEnumQueryField[M <: MetaRecord[_, _], F <: Enum[F]](f: Field[F, M]): SpindleEnumQueryField[M, F] =
    new SpindleEnumQueryField(f)
  implicit def enumListFieldToSpindleEnumListQueryField[M <: MetaRecord[_, _], F <: Enum[F]](f: Field[Seq[F], M]): SpindleEnumListQueryField[M, F] =
    new SpindleEnumListQueryField(f)
  implicit def enumFieldToSpindleEnumModifyField[M <: MetaRecord[_, _], F <: Enum[F]](f: Field[F, M]): SpindleEnumModifyField[M, F] =
    new SpindleEnumModifyField(f)  
  implicit def enumFieldToSpindleEnumListModifyField[M <: MetaRecord[_, _], F <: Enum[F]](f: Field[Seq[F], M]): SpindleEnumListModifyField[M, F] =
    new SpindleEnumListModifyField(f)  

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
    private val serializer = new SpindleRogueWriteSerializer
    override def asBSONObject(v: R): AnyRef = serializer.toDBObject(v)
  }

  object _SpindleRecordIsBSONType extends SpindleRecordIsBSONType[Nothing]

  implicit def SpindleRecordIsBSONType[R <: Record[R]]: BSONType[R] = _SpindleRecordIsBSONType.asInstanceOf[BSONType[R]]
}

object SpindleRogue extends Rogue with SpindleRogue
