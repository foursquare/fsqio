// Copyright 2013 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.rogue.spindle

import com.mongodb.{BulkWriteResult, DBObject, DefaultDBDecoder, WriteConcern}
import io.fsq.rogue.{MongoHelpers, MongoJavaDriverAdapter, Query, QueryExecutor, QueryHelpers, QueryOptimizer}
import io.fsq.rogue.MongoHelpers.{AndCondition, MongoBuilder, MongoSelect}
import io.fsq.spindle.common.thrift.bson.TBSONObjectProtocol
import io.fsq.spindle.runtime.{UntypedMetaRecord, UntypedRecord}
import java.io.InputStream

trait SpindleQueryExecutor extends QueryExecutor[UntypedMetaRecord, UntypedRecord] {
  def dbCollectionFactory: SpindleDBCollectionFactory
}

class SpindleDatabaseService(val dbCollectionFactory: SpindleDBCollectionFactory) extends SpindleQueryExecutor {
  override def readSerializer[M <: UntypedMetaRecord, R](meta: M, select: Option[MongoSelect[M, R]]):
      SpindleRogueReadSerializer[M, R] = {
    new SpindleRogueReadSerializer(meta, select)
  }

  override def writeSerializer(record: UntypedRecord): SpindleRogueWriteSerializer = new SpindleRogueWriteSerializer
  override def defaultWriteConcern: WriteConcern = WriteConcern.SAFE

  // allow this to be overridden to substitute alternative deserialization methods
  def newBsonStreamDecoder(): (UntypedMetaRecord, InputStream) => SpindleDBObject = {
    val decoder = new DefaultDBDecoder()
    val protocolFactory = new TBSONObjectProtocol.ReaderFactory
    val protocol: TBSONObjectProtocol = protocolFactory.getProtocol

    (meta: UntypedMetaRecord, is: InputStream) => {
      val record = meta.createUntypedRawRecord
      protocol.reset()
      val dbo: DBObject = decoder.decode(is, null)
      protocol.setSource(dbo)
      record.read(protocol)
      SpindleMongoDBObject(record, dbo)
    }
  }

  override val adapter: MongoJavaDriverAdapter[UntypedMetaRecord, UntypedRecord] = new MongoJavaDriverAdapter(
    dbCollectionFactory,
    (meta: UntypedMetaRecord) => SpindleDBDecoderFactory(meta, newBsonStreamDecoder())
  )
  override val optimizer = new QueryOptimizer

  override def save[R <: UntypedRecord](record: R, writeConcern: WriteConcern = defaultWriteConcern): R = {
    if (record.meta.annotations.contains("nosave"))
      throw new IllegalArgumentException("Cannot save a %s record".format(record.meta.recordName))
    super.save(record, writeConcern)
  }

  override def insert[R <: UntypedRecord](record: R, writeConcern: WriteConcern = defaultWriteConcern): R = {
    if (record.meta.annotations.contains("nosave"))
      throw new IllegalArgumentException("Cannot insert a %s record".format(record.meta.recordName))
    super.insert(record, writeConcern)
  }

  def bulk[M <: UntypedMetaRecord, R <: UntypedRecord](
      clauses: Seq[BulkOperation[M, R]],
      ordered: Boolean = false,
      writeConcern: WriteConcern = WriteConcern.NORMAL
    ): Option[BulkWriteResult] = {
    clauses.headOption.map(firstClause => {
      val meta = firstClause match {
        case BulkInsertOne(record) => {
          record.meta
        }
        case BulkRemoveOne(query) => {
          query.meta
        }
        case BulkRemove(query) => {
          query.meta
        }
        case BulkReplaceOne(query, record, upsert) => {
          query.meta
        }
        case BulkUpdateOne(query, upsert) => {
          query.query.meta
        }
        case BulkUpdate(query, upsert) => {
          query.query.meta
        }
      }

      val collectionName = SpindleHelpers.getCollection(meta)
      val coll = dbCollectionFactory.getDBCollectionFromMeta(meta)

      val transformer = QueryHelpers.transformer
      val validator = QueryHelpers.validator

      val builder = if (ordered) {
        coll.initializeOrderedBulkOperation()
      } else {
        coll.initializeUnorderedBulkOperation()
      }

      val descriptionFuncBuilder = Vector.newBuilder[() => String]
      descriptionFuncBuilder.sizeHint(clauses.size)

      for {
        clause <- clauses
      } {
        clause match {
          case BulkInsertOne(record) => {
            val s = writeSerializer(record)
            val dbo = s.toDBObject(record)
            builder.insert(dbo)

            descriptionFuncBuilder += {
              () => dbo.toString
            }
          }
          case BulkRemoveOne(query) => {
            val queryClause = transformer.transformQuery(query)
            validator.validateQuery(queryClause, dbCollectionFactory.getIndexes(queryClause))
            builder.find(queryClause.asDBObject).removeOne()

            descriptionFuncBuilder += {
              () => MongoHelpers.MongoBuilder.buildConditionString("BulkRemoveOne", collectionName, queryClause)
            }
          }
          case BulkRemove(query) => {
            val queryClause = transformer.transformQuery(query)
            validator.validateQuery(queryClause, dbCollectionFactory.getIndexes(queryClause))
            builder.find(queryClause.asDBObject).remove()

            descriptionFuncBuilder += {
              () => MongoHelpers.MongoBuilder.buildConditionString("BulkRemove", collectionName, queryClause)
            }
          }
          case BulkReplaceOne(query, record, upsert) => {
            val queryClause = transformer.transformQuery(query)
            validator.validateQuery(queryClause, dbCollectionFactory.getIndexes(queryClause))
            val s = writeSerializer(record)
            val dbo = s.toDBObject(record)
            if (upsert) {
              builder.find(queryClause.asDBObject).upsert().replaceOne(dbo)
            } else {
              builder.find(queryClause.asDBObject).replaceOne(dbo)
            }

            // TODO: Add the replacement DBO string to the descriptionFunc
            descriptionFuncBuilder += {
              () => MongoHelpers.MongoBuilder.buildConditionString("BulkReplaceOne", collectionName, queryClause)
            }
          }
          case BulkUpdateOne(query, upsert) => {
            val modClause = transformer.transformModify(query)
            validator.validateModify(modClause, dbCollectionFactory.getIndexes(modClause.query))
            if (!modClause.mod.clauses.isEmpty) {
              val q = MongoBuilder.buildCondition(modClause.query.condition)
              val m = MongoBuilder.buildModify(modClause.mod)
              if (upsert) {
                builder.find(q).upsert().updateOne(m)
              } else {
                builder.find(q).updateOne(m)
              }

              descriptionFuncBuilder += {
                () => MongoHelpers.MongoBuilder.buildModifyString(query.query.collectionName, modClause, upsert = upsert, multi = false)
              }
            }


          }
          case BulkUpdate(query, upsert) => {
            val modClause = transformer.transformModify(query)
            validator.validateModify(modClause, dbCollectionFactory.getIndexes(modClause.query))
            if (!modClause.mod.clauses.isEmpty) {
              val q = MongoBuilder.buildCondition(modClause.query.condition)
              val m = MongoBuilder.buildModify(modClause.mod)
              if (upsert) {
                builder.find(q).upsert().update(m)
              } else {
                builder.find(q).update(m)
              }
            }

            descriptionFuncBuilder += {
              () => MongoHelpers.MongoBuilder.buildModifyString(query.query.collectionName, modClause, upsert = upsert, multi = true)
            }
          }
        }
      }

      val descriptionFunc: () => String = () => {
        descriptionFuncBuilder.result().map(df => {
          df()
        }).mkString("\n")
      }

      val fakeQueryForLogging = {
        Query(
          meta = meta.asInstanceOf[M],
          collectionName = collectionName,
          lim = None,
          sk = None,
          maxScan = None,
          comment = Some("bulk"),
          hint = None,
          condition = AndCondition(Nil, None),
          order = None,
          select = None,
          readPreference = None
        )
      }

      val result = adapter.runCommand(descriptionFunc, fakeQueryForLogging) {
        builder.execute(writeConcern)
      }

      result
    })
  }
}
