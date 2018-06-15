// Copyright 2017 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.spindle.rogue.query

import com.mongodb.BasicDBObject
import io.fsq.common.scala.Identity._
import io.fsq.rogue.MongoHelpers.MongoSelect
import io.fsq.rogue.query.RogueSerializer
import io.fsq.spindle.common.thrift.bson.TBSONObjectProtocol
import io.fsq.spindle.runtime.{UntypedFieldDescriptor, UntypedMetaRecord, UntypedRecord}

class SpindleRogueSerializer extends RogueSerializer[UntypedMetaRecord, UntypedRecord, BasicDBObject] {

  // TODO(jacob): Instead of caching factories, just throw the protocols in a ThreadLocal
  val readerProtocolFactory = new TBSONObjectProtocol.ReaderFactory
  val writerProtocolFactory = new TBSONObjectProtocol.WriterFactoryForDBObject

  private def getFieldValueFromRecord(
    meta: UntypedMetaRecord,
    record: Any,
    fieldName: String
  ): Option[Any] = {
    meta.untypedFields
      .find(_.name =? fieldName)
      .getOrElse(throw new Exception(s"Meta record does not have a definition for field $fieldName"))
      .unsafeGetterOption(record)
  }

  private def getFieldValueFromAny(source: Option[Any], fieldName: String): Option[Any] = source.flatMap({
    case map: Map[_, _] => map.find(_._1.toString =? fieldName).map(_._2)
    case list: Seq[_] =>
      Some(list.map(elem => {
        val elemOpt = elem match {
          case opt: Option[_] => opt
          case nonOpt => Some(nonOpt)
        }
        getFieldValueFromAny(elemOpt, fieldName)
      }))
    case record: UntypedRecord => getFieldValueFromRecord(record.meta, record, fieldName)
    case other =>
      throw new Exception(
        s"Rogue bug: unepected object type encountered during spindle deserialization - ${other.getClass.getName}"
      )
  })

  private def readFullUntypedRecordFromDocument[M <: UntypedMetaRecord](
    meta: M,
    document: BasicDBObject
  ): UntypedRecord = {
    val record = meta.createUntypedRawRecord
    val protocol = readerProtocolFactory.getProtocol
    protocol.setSource(document)
    record.read(protocol)
    record
  }

  override def readFromDocument[M <: UntypedMetaRecord, R](
    meta: M,
    selectOpt: Option[MongoSelect[M, R]]
  )(
    document: BasicDBObject
  ): R = {
    val intermediateRecord = readFullUntypedRecordFromDocument(meta, document).asInstanceOf[R]

    selectOpt match {
      case Some(MongoSelect(fields, transformer)) => {
        val selectedFields = fields.map(
          field =>
            field.field match {
              case descriptor: UntypedFieldDescriptor => {
                field.valueOrDefault(descriptor.unsafeGetterOption(intermediateRecord))
              }

              case subrecordField => {
                val Array(rootFieldName, subPath @ _*) = subrecordField.name.split('.').filter(_ !=? "$")
                val rootValueOpt = getFieldValueFromRecord(subrecordField.owner, intermediateRecord, rootFieldName)
                val valueOpt = subPath.foldLeft(rootValueOpt)(getFieldValueFromAny)
                field.valueOrDefault(valueOpt)
              }
            }
        )

        transformer(selectedFields)
      }

      case None => intermediateRecord
    }
  }

  override def writeToDocument[R <: UntypedRecord](record: R): BasicDBObject = {
    val protocol = writerProtocolFactory.getProtocol
    record.write(protocol)
    protocol.getOutput.asInstanceOf[BasicDBObject]
  }
}
