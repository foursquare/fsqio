// Copyright 2013 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.spindle.__shaded_for_spindle_bootstrap__.runtime

object RuntimeHelpers {
  def reportError(e: Throwable): Unit = errorHooks.reportError(e)
  def preserveUnknownFields(record: Record[_]): Boolean = configHooks.preserveUnknownFields(record)


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

    def missingAlternateObj[
        F,
        R <: Record[R],
        M <: MetaRecord[R, M]
    ](
        record: R,
        field: ForeignKeyFieldDescriptor[F, R, M],
        fieldValue: Option[F]
    ): Option[AnyRef]

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

    override def missingAlternateObj[
        F,
        R <: Record[R],
        M <: MetaRecord[R, M]
    ](
        record: R,
        field: ForeignKeyFieldDescriptor[F, R, M],
        fieldValue: Option[F]
    ): Option[AnyRef] = None

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
