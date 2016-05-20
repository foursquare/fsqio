// Copyright 2013 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.exceptionator.actions.concrete

import io.fsq.common.logging.Logger
import io.fsq.exceptionator.actions.{IndexActions, UserFilterActions}
import io.fsq.exceptionator.model.UserFilterRecord
import io.fsq.exceptionator.model.io.UserFilterView
import io.fsq.rogue.lift.LiftRogue._
import net.liftweb.common.{Failure, Full}
import net.liftweb.json._
import org.bson.types.ObjectId

class ConcreteUserFilterActions extends UserFilterActions with IndexActions with Logger {

  def ensureIndexes {
    Vector(UserFilterRecord).foreach(metaRecord => {
        metaRecord.mongoIndexList.foreach(i =>
          metaRecord.createIndex(JObject(i.asListMap.map(fld => JField(fld._1, JInt(fld._2.toString.toInt))).toList)))
    })
  }

  def getAll(userIdOpt: Option[String] = None): List[UserFilterView] = {
    UserFilterRecord
      .whereOpt(userIdOpt)(_.userId eqs _)
      .fetch(2000)
  }

  def save(jsonString: String, userId: String): Option[UserFilterView] = {
    UserFilterRecord.fromJValue(JsonParser.parse(jsonString)).flatMap(rec => {
      if (rec.userId.value != userId) {
        Failure("provided user %s doesn't match authenticated user %s".format(rec.userId, userId))
      } else {
        rec.save(true)
        Full(rec)
      }
    }) match {
      case f: Failure => {
        logger.error("Failed to convert %s to a UserFilterRecord: %s".format(jsonString, f.msg))
        None
      }
      case result => result.toOption
    }
  }

  def get(id: String): Option[UserFilterView] = {
    val oidOpt = try {
      Some(new ObjectId(id))
    } catch {
      case _: IllegalArgumentException => None
    }
    oidOpt.flatMap(oid => UserFilterRecord.where(_.id eqs oid).fetch.headOption)
  }

  def remove(id: String, userId: Option[String]) {
    // TODO: check incoming user
    val oidOpt = try {
      Some(new ObjectId(id))
    } catch {
      case _: IllegalArgumentException => None
    }

    oidOpt.map(oid => UserFilterRecord.where(_.id eqs oid).bulkDelete_!!!())
  }
}
