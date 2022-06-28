// Copyright 2017 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.spindle.rogue.query.test

import com.mongodb.BasicDBObject
import io.fsq.common.testing.matchers.FoursquareMatchers
import io.fsq.field.OptionalField
import io.fsq.rogue.Rogue
import io.fsq.spindle.common.thrift.bson.TBSONObjectProtocol
import io.fsq.spindle.rogue.{SpindleQuery => Q, SpindleRogue}
import io.fsq.spindle.rogue.query.SpindleRogueSerializer
import io.fsq.spindle.rogue.query.testlib.gen.{SerdeTestRecord, SerdeTestRecordMeta}
import io.fsq.spindle.runtime.FieldDescriptor
import org.bson.types.ObjectId
import org.hamcrest.MatcherAssert
import org.junit.{Assert, Test}
import scala.math.max
import scala.util.Random

class SpindleRogueSerializerTest extends Rogue with SpindleRogue {
  val random = new Random(1399660443)
  val writerProtocolFactory = new TBSONObjectProtocol.WriterFactoryForDBObject

  val serializer = new SpindleRogueSerializer

  def alphanumericString(length: Int): String = {
    new String(random.alphanumeric.take(length).toArray)
  }

  /** NOTE(jacob): This is the exact implementation used by serializer.writeToDocument,
    *    making the write tests a bit useless on the surface. We are not here to test
    *    TBSONObjectProtocol though, and this will at least guard against future changes
    *    to serializer behavior.
    */
  def dbObjectFromRecord(testRecord: SerdeTestRecord): BasicDBObject = {
    val protocol = writerProtocolFactory.getProtocol
    testRecord.write(protocol)
    protocol.getOutput.asInstanceOf[BasicDBObject]
  }

  def fullReadFromDocument(dbObject: BasicDBObject): SerdeTestRecord = {
    serializer.readFromDocument(SerdeTestRecord, None)(dbObject)
  }

  def newEmptyRecord: SerdeTestRecord = {
    SerdeTestRecord.newBuilder.id(new ObjectId).result()
  }

  def newTestRecord(nestingDepth: Int): SerdeTestRecord = {
    val builder = {
      SerdeTestRecord.newBuilder
        .id(new ObjectId)
        .stringField(alphanumericString(8))
        .mapField(
          Map(
            alphanumericString(4) -> random.nextInt,
            alphanumericString(4) -> random.nextInt
          )
        )
        .stringListField(
          Vector.tabulate(5)(_ => alphanumericString(8))
        )
    }

    if (nestingDepth > 0) {
      builder
        .subrecordField(newTestRecord(nestingDepth - 1))
        .subrecordMapField(
          Vector.tabulate(5)(_ => alphanumericString(8) -> newTestRecord(nestingDepth - 1)).toMap
        )
        .subrecordListField(
          Vector.tabulate(5)(_ => newTestRecord(nestingDepth - 1))
        )
    }

    builder.result()
  }

  @Test
  def fullReadTest(): Unit = {
    val emptyRecord = newEmptyRecord
    Assert.assertEquals(emptyRecord, fullReadFromDocument(dbObjectFromRecord(emptyRecord)))

    for (nestingDepth <- 0 to 3) {
      val testRecord = newTestRecord(nestingDepth)
      Assert.assertEquals(testRecord, fullReadFromDocument(dbObjectFromRecord(testRecord)))
    }
  }

  // NOTE(jacob): Although the serializer supports it, spindle rogue overall lacks support
  //    for selects on map fields so they are not tested here.
  @Test
  def selectReadTest(): Unit = {
    val testRecords = newEmptyRecord +: Vector.tabulate(3)(newTestRecord(_))

    type FieldType = OptionalField[Any, SerdeTestRecordMeta]
      with FieldDescriptor[Any, SerdeTestRecord, SerdeTestRecordMeta]
    val typedFields = SerdeTestRecord.fields.asInstanceOf[Seq[FieldType]]

    for {
      testRecord <- testRecords
      testDbObject = dbObjectFromRecord(testRecord)
      field <- typedFields
    } {

      // no nesting
      Assert.assertEquals(
        field.getter(testRecord),
        serializer.readFromDocument(
          SerdeTestRecord,
          Q(SerdeTestRecord)
            .select(
              _ => field
            )
            .select
        )(
          testDbObject
        )
      )

      // single nesting
      Assert.assertEquals(
        testRecord.subrecordFieldOption.flatMap(
          field.getter(_)
        ),
        serializer.readFromDocument(
          SerdeTestRecord,
          Q(SerdeTestRecord)
            .select(
              _.subrecordField.sub.select(_ => field)
            )
            .select
        )(
          testDbObject
        )
      )

      Assert.assertEquals(
        testRecord.subrecordListFieldOption.map(
          _.map(
            field.getter(_)
          )
        ),
        serializer.readFromDocument(
          SerdeTestRecord,
          Q(SerdeTestRecord)
            .select(
              _.subrecordListField.sub.select(_ => field)
            )
            .select
        )(
          testDbObject
        )
      )

      // double nesting
      Assert.assertEquals(
        testRecord.subrecordFieldOption.flatMap(
          _.subrecordFieldOption.flatMap(
            field.getter(_)
          )
        ),
        serializer.readFromDocument(
          SerdeTestRecord,
          Q(SerdeTestRecord)
            .select(
              _.subrecordField.sub.select(_.subrecordField).sub.select(_ => field)
            )
            .select
        )(
          testDbObject
        )
      )

      Assert.assertEquals(
        testRecord.subrecordFieldOption.flatMap(
          _.subrecordListFieldOption.map(
            _.map(
              field.getter(_)
            )
          )
        ),
        serializer.readFromDocument(
          SerdeTestRecord,
          Q(SerdeTestRecord)
            .select(
              _.subrecordField.sub.select(_.subrecordListField).sub.select(_ => field)
            )
            .select
        )(
          testDbObject
        )
      )

      Assert.assertEquals(
        testRecord.subrecordListFieldOption.map(
          _.map(
            _.subrecordFieldOption.flatMap(
              field.getter(_)
            )
          )
        ),
        serializer.readFromDocument(
          SerdeTestRecord,
          Q(SerdeTestRecord)
            .select(
              _.subrecordListField.sub.select(_.subrecordField.sub.select(_ => field))
            )
            .select
        )(
          testDbObject
        )
      )

      Assert.assertEquals(
        testRecord.subrecordListFieldOption.map(
          _.map(
            _.subrecordListFieldOption.map(
              _.map(
                field.getter(_)
              )
            )
          )
        ),
        serializer.readFromDocument(
          SerdeTestRecord,
          Q(SerdeTestRecord)
            .select(
              _.subrecordListField.sub.select(_.subrecordListField.sub.select(_ => field))
            )
            .select
        )(
          testDbObject
        )
      )
    }

    // tupled selects
    for {
      testRecord <- testRecords
      testDbObject = dbObjectFromRecord(testRecord)
      tupleSize <- 2 to max(typedFields.size, 10) // Query supports a max of 10 selected fields
      selectedFieldsSet <- typedFields.toSet.subsets(tupleSize)
    } {
      val selectedFields = selectedFieldsSet.toArray

      // If ever there were a case for macros in unit tests...
      val tupleResult = tupleSize match {
        case 2 => {
          serializer.readFromDocument(
            SerdeTestRecord,
            Q(SerdeTestRecord)
              .select(
                _ => selectedFields(0),
                _ => selectedFields(1)
              )
              .select
          )(
            testDbObject
          )
        }

        case 3 => {
          serializer.readFromDocument(
            SerdeTestRecord,
            Q(SerdeTestRecord)
              .select(
                _ => selectedFields(0),
                _ => selectedFields(1),
                _ => selectedFields(2)
              )
              .select
          )(
            testDbObject
          )
        }

        case 4 => {
          serializer.readFromDocument(
            SerdeTestRecord,
            Q(SerdeTestRecord)
              .select(
                _ => selectedFields(0),
                _ => selectedFields(1),
                _ => selectedFields(2),
                _ => selectedFields(3)
              )
              .select
          )(
            testDbObject
          )
        }

        case 5 => {
          serializer.readFromDocument(
            SerdeTestRecord,
            Q(SerdeTestRecord)
              .select(
                _ => selectedFields(0),
                _ => selectedFields(1),
                _ => selectedFields(2),
                _ => selectedFields(3),
                _ => selectedFields(4)
              )
              .select
          )(
            testDbObject
          )
        }

        case 6 => {
          serializer.readFromDocument(
            SerdeTestRecord,
            Q(SerdeTestRecord)
              .select(
                _ => selectedFields(0),
                _ => selectedFields(1),
                _ => selectedFields(2),
                _ => selectedFields(3),
                _ => selectedFields(4),
                _ => selectedFields(5)
              )
              .select
          )(
            testDbObject
          )
        }

        case 7 => {
          serializer.readFromDocument(
            SerdeTestRecord,
            Q(SerdeTestRecord)
              .select(
                _ => selectedFields(0),
                _ => selectedFields(1),
                _ => selectedFields(2),
                _ => selectedFields(3),
                _ => selectedFields(4),
                _ => selectedFields(5),
                _ => selectedFields(6)
              )
              .select
          )(
            testDbObject
          )
        }

        case 8 => {
          serializer.readFromDocument(
            SerdeTestRecord,
            Q(SerdeTestRecord)
              .select(
                _ => selectedFields(0),
                _ => selectedFields(1),
                _ => selectedFields(2),
                _ => selectedFields(3),
                _ => selectedFields(4),
                _ => selectedFields(5),
                _ => selectedFields(6),
                _ => selectedFields(7)
              )
              .select
          )(
            testDbObject
          )
        }

        case 9 => {
          serializer.readFromDocument(
            SerdeTestRecord,
            Q(SerdeTestRecord)
              .select(
                _ => selectedFields(0),
                _ => selectedFields(1),
                _ => selectedFields(2),
                _ => selectedFields(3),
                _ => selectedFields(4),
                _ => selectedFields(5),
                _ => selectedFields(6),
                _ => selectedFields(7),
                _ => selectedFields(8)
              )
              .select
          )(
            testDbObject
          )
        }

        case 10 => {
          serializer.readFromDocument(
            SerdeTestRecord,
            Q(SerdeTestRecord)
              .select(
                _ => selectedFields(0),
                _ => selectedFields(1),
                _ => selectedFields(2),
                _ => selectedFields(3),
                _ => selectedFields(4),
                _ => selectedFields(5),
                _ => selectedFields(6),
                _ => selectedFields(7),
                _ => selectedFields(8),
                _ => selectedFields(9)
              )
              .select
          )(
            testDbObject
          )
        }
      }

      MatcherAssert.assertThat(
        tupleResult.productIterator.toVector.asInstanceOf[Vector[Option[Any]]],
        FoursquareMatchers.equalsCollection(selectedFields.map(_.getter(testRecord)))
      )
    }
  }

  @Test
  def writeTest(): Unit = {
    val emptyRecord = newEmptyRecord
    Assert.assertEquals(dbObjectFromRecord(emptyRecord), serializer.writeToDocument(emptyRecord))

    for (nestingDepth <- 0 to 3) {
      val testRecord = newTestRecord(nestingDepth)
      Assert.assertEquals(dbObjectFromRecord(testRecord), serializer.writeToDocument(testRecord))
    }
  }
}
