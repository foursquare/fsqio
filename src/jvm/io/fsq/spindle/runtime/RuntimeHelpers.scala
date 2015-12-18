// Copyright 2013 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.spindle.runtime

object RuntimeHelpers {
  def reportError(e: Throwable): Unit = errorHooks.reportError(e)
  def preserveUnknownFields(record: Record[_]): Boolean = configHooks.preserveUnknownFields(record)

  // TODO: We can clean up a bunch of this *Seq duplication if
  // ForeignKeyField and ForeignKeySeqField can be unified some.
  trait ForeignKeyHooks {
    def missingKey[
        F,
        R <: Record[R],
        M <: MetaRecord[R, M],
        FR <: Record[FR] with HasPrimaryKey[F, FR]
    ](
        record: R,
        field: ForeignKeyFieldDescriptor[F, R, M],
        foreignMeta: MetaRecord[FR, _]
    ): Option[FR]

    def missingKeySeq[
        F,
        R <: Record[R],
        M <: MetaRecord[R, M],
        FR <: Record[FR] with HasPrimaryKey[F, FR]
    ](
        record: R,
        field: ForeignKeySeqFieldDescriptor[F, R, M],
        foreignMeta: MetaRecord[FR, _]
    ): Seq[FR]

    def missingObj[
        F,
        R <: Record[R],
        M <: MetaRecord[R, M],
        FR <: Record[FR] with HasPrimaryKey[F, FR]
    ](
        record: R,
        field: ForeignKeyFieldDescriptor[F, R, M],
        foreignMeta: MetaRecord[FR, _],
        fieldValue: F
    ): Option[FR]

    def missingObjSeq[
        F,
        R <: Record[R],
        M <: MetaRecord[R, M],
        FR <: Record[FR] with HasPrimaryKey[F, FR]
    ](
        record: R,
        field: ForeignKeySeqFieldDescriptor[F, R, M],
        foreignMeta: MetaRecord[FR, _],
        fieldValue: Seq[F]
    ): Seq[FR]

    def missingAlternateObj[
        F,
        R <: Record[R],
        M <: MetaRecord[R, M]
    ](
        record: R,
        field: ForeignKeyFieldDescriptor[F, R, M],
        fieldValue: Option[F]
    ): Option[AnyRef]

    def missingAlternateObjSeq[
        F,
        R <: Record[R],
        M <: MetaRecord[R, M]
    ](
        record: R,
        field: ForeignKeySeqFieldDescriptor[F, R, M],
        fieldValue: Seq[F]
    ): Seq[AnyRef]

    def mismatchedInstanceType[
        F,
        R <: Record[R],
        M <: MetaRecord[R, M],
        FR <: Record[FR] with HasPrimaryKey[F, FR]
    ](
        record: R,
        field: ForeignKeyFieldDescriptor[F, R, M],
        foreignMeta: MetaRecord[FR, _],
        fieldValue: F,
        obj: AnyRef
    ): Option[FR]

    def mismatchedInstanceTypeSeq[
        F,
        R <: Record[R],
        M <: MetaRecord[R, M],
        FR <: Record[FR] with HasPrimaryKey[F, FR]
    ](
        record: R,
        field: ForeignKeySeqFieldDescriptor[F, R, M],
        foreignMeta: MetaRecord[FR, _],
        fieldValue: Seq[F],
        obj: Seq[AnyRef]
    ): Option[FR]

    def mismatchedPrimaryKey[
        F,
        R <: Record[R],
        M <: MetaRecord[R, M],
        FR <: Record[FR] with HasPrimaryKey[F, FR]
    ](
        record: R,
        field: ForeignKeyFieldDescriptor[F, R, M],
        foreignMeta: MetaRecord[FR, _],
        fieldValue: F,
        foreignRecord: FR
    ): Option[FR]

    def mismatchedPrimaryKeySeq[
        F,
        R <: Record[R],
        M <: MetaRecord[R, M],
        FR <: Record[FR] with HasPrimaryKey[F, FR]
    ](
        record: R,
        field: ForeignKeySeqFieldDescriptor[F, R, M],
        foreignMeta: MetaRecord[FR, _],
        fieldValue: Seq[F],
        foreignRecord: FR
    ): Option[FR]
  }

  class DefaultForeignKeyHooks extends ForeignKeyHooks {
    override def missingKey[
        F,
        R <: Record[R],
        M <: MetaRecord[R, M],
        FR <: Record[FR] with HasPrimaryKey[F, FR]
    ](
        record: R,
        field: ForeignKeyFieldDescriptor[F, R, M],
        foreignMeta: MetaRecord[FR, _]
    ): Option[FR] = None

    override def missingKeySeq[
        F,
        R <: Record[R],
        M <: MetaRecord[R, M],
        FR <: Record[FR] with HasPrimaryKey[F, FR]
    ](
        record: R,
        field: ForeignKeySeqFieldDescriptor[F, R, M],
        foreignMeta: MetaRecord[FR, _]
    ): Seq[FR] = Vector.empty

    override def missingObj[
        F,
        R <: Record[R],
        M <: MetaRecord[R, M],
        FR <: Record[FR] with HasPrimaryKey[F, FR]
    ](
        record: R,
        field: ForeignKeyFieldDescriptor[F, R, M],
        foreignMeta: MetaRecord[FR, _],
        fieldValue: F
    ): Option[FR] = None

    override def missingObjSeq[
        F,
        R <: Record[R],
        M <: MetaRecord[R, M],
        FR <: Record[FR] with HasPrimaryKey[F, FR]
    ](
        record: R,
        field: ForeignKeySeqFieldDescriptor[F, R, M],
        foreignMeta: MetaRecord[FR, _],
        fieldValue: Seq[F]
    ): Seq[FR] = Vector.empty

    override def missingAlternateObj[
        F,
        R <: Record[R],
        M <: MetaRecord[R, M]
    ](
        record: R,
        field: ForeignKeyFieldDescriptor[F, R, M],
        fieldValue: Option[F]
    ): Option[AnyRef] = None

    override def missingAlternateObjSeq[
        F,
        R <: Record[R],
        M <: MetaRecord[R, M]
    ](
        record: R,
        field: ForeignKeySeqFieldDescriptor[F, R, M],
        fieldValue: Seq[F]
    ): Seq[AnyRef] = Vector.empty

    override def mismatchedInstanceType[
        F,
        R <: Record[R],
        M <: MetaRecord[R, M],
        FR <: Record[FR] with HasPrimaryKey[F, FR]
    ](
        record: R,
        field: ForeignKeyFieldDescriptor[F, R, M],
        foreignMeta: MetaRecord[FR, _],
        fieldValue: F,
        obj: AnyRef
    ): Option[FR] = None

    override def mismatchedInstanceTypeSeq[
        F,
        R <: Record[R],
        M <: MetaRecord[R, M],
        FR <: Record[FR] with HasPrimaryKey[F, FR]
    ](
        record: R,
        field: ForeignKeySeqFieldDescriptor[F, R, M],
        foreignMeta: MetaRecord[FR, _],
        fieldValue: Seq[F],
        obj: Seq[AnyRef]
    ): Option[FR] = None

    override def mismatchedPrimaryKey[
        F,
        R <: Record[R],
        M <: MetaRecord[R, M],
        FR <: Record[FR] with HasPrimaryKey[F, FR]
    ](
        record: R,
        field: ForeignKeyFieldDescriptor[F, R, M],
        foreignMeta: MetaRecord[FR, _],
        fieldValue: F,
        foreignRecord: FR
    ): Option[FR] = None

    override def mismatchedPrimaryKeySeq[
        F,
        R <: Record[R],
        M <: MetaRecord[R, M],
        FR <: Record[FR] with HasPrimaryKey[F, FR]
    ](
        record: R,
        field: ForeignKeySeqFieldDescriptor[F, R, M],
        foreignMeta: MetaRecord[FR, _],
        fieldValue: Seq[F],
        foreignRecord: FR
    ): Option[FR] = None
  }

  trait ConfigHooks {
    def preserveUnknownFields(record: Record[_]): Boolean
  }

  trait ErrorHooks {
    def reportError(e: Throwable): Unit
  }

  object NoopForeignKeyHooks extends DefaultForeignKeyHooks

  object ThrowErrorHooks extends ErrorHooks {
    override def reportError(e: Throwable): Unit = throw e
  }

  object DefaultConfigHooks extends ConfigHooks {
    override def preserveUnknownFields(record: Record[_]): Boolean = {
      record.meta.annotations.get("preserve_unknown_fields").isDefined
    }
  }

  var fkHooks: ForeignKeyHooks = NoopForeignKeyHooks
  var errorHooks: ErrorHooks = ThrowErrorHooks
  var configHooks: ConfigHooks = DefaultConfigHooks
}
