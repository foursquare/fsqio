// Copyright 2014 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.spindle.runtime

import org.apache.thrift.TBase
import org.apache.thrift.protocol.{TField, TProtocol, TProtocolUtil, TType}

// A single unknown field, as read from the wire.
case class UnknownField(tfield: TField, value: UValue) {
  // Must override because TField's equals method has the wrong signature. Sigh.
  override def equals(other: Any) = other match {
    case o: UnknownField => tfield.name == o.tfield.name &&
                            tfield.id == o.tfield.id &&
                            tfield.`type` == o.tfield.`type` &&
                            value == o.value
    case _ => false
  }
}


// Unknown fields, encountered on the wire during deserialization from the specified protocol.
// We stash that data away in this hidden data structure inside the record, so we can serialize it out
// again later. This allows us to roundtrip through an older version of a record without data loss.
// Unknown fields may be serialized either inline or in a blob, depending on whether we have
// enough information to do so. See comments on UnknownFieldsBlob for details.
//
// - inputProtocolName: The protocol the unknown fields are being read from.
// - stashList: Unknown fields are stashed here until we need to write them out.
//
// Note: UnknownFields don't participate in equality of the records that contain them.
// However we do require UnknownFields to have well-defined equality for tests, which is
// why stashList is a parameter of the case class.
case class UnknownFields(rec: TBase[_, _] with Record[_], inputProtocolName: String, private var stashList: List[UnknownField] = Nil) {
  private def stash(uf: UnknownField) { stashList = uf :: stashList }

  val retiredIds = rec.meta.annotations.getAll("retired_ids").flatMap(_.split(',')).map(_.toShort).toSet
  val retiredWireNames = rec.meta.annotations.getAll("retired_wire_names").flatMap(_.split(',')).toSet

  def write(oprot: TProtocol) {
    val outputProtocolName = TProtocolInfo.getProtocolName(oprot)
    // We can write inline if both protocols are robust, or if they are the same protocol.
    if (outputProtocolName == inputProtocolName ||
        TProtocolInfo.isRobust(inputProtocolName) && TProtocolInfo.isRobust(outputProtocolName)) {
      writeInline(oprot)
    } else {  // Write as a blob.
      val blob = UnknownFieldsBlob.toBlob(this)
      blob.write(oprot)
    }
  }

  // Read a field whose wire name/id is unknown to rec.
  def readUnknownField(iprot: TProtocol, wireTField: TField, rec: TBase[_, _]) {
    if (retiredIds(wireTField.id) || retiredWireNames(wireTField.name)) {
      // skip the field
      TProtocolUtil.skip(iprot, wireTField.`type`)
    } else if (wireTField.id == UnknownFieldsBlob.magicField.id || wireTField.name == UnknownFieldsBlob.magicField.name) {
      val blob = UnknownFieldsBlob.fromMagicField(iprot)
      blob.read(rec)
    } else {
      // This is a regular, inline field that rec doesn't know about, so stash it.
      readInline(iprot, wireTField)
    }
  }

  // A special case for when we see an unknown value in a known enum field.
  def addUnknownEnumValue(fieldName: String, fieldId: Short, enumValue: Int) {
    stash(UnknownField(new TField(fieldName, TType.I32, fieldId), I32UValue(enumValue)))
  }

  def writeInline(oprot: TProtocol) {
    stashList.reverse foreach {
      field: UnknownField => {
        try {
          oprot.writeFieldBegin(field.tfield)
          field.value.write(oprot)
          oprot.writeFieldEnd()
        } catch {
          case e: Exception => {
            val id = rec match {
              case r: SemitypedHasPrimaryKey[_] => " (%s)".format(r.primaryKey.toString)
              case _ => ""
            }

            RuntimeHelpers.reportError(new RuntimeException(
              "Failed to stash field %s (%s) in %s record%s: %s".format(
                field.tfield.name,
                field.value.getClass.getSimpleName,
                rec.meta.recordName,
                id,
                e.getMessage
              ),
              e
            ))
          }
        }
      }
    }
  }

  def readInline(iprot: TProtocol, tfield: TField) {
    val fieldVal: UValue = UValue.read(rec, iprot, tfield.`type`)
    stash(UnknownField(tfield, fieldVal))
  }
}
