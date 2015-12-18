// Copyright 2012 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.exceptionator.model

import _root_.io.fsq.exceptionator.model.io.UserFilterView
import _root_.io.fsq.rogue._
import _root_.io.fsq.rogue.index.{Asc, IndexedRecord}
import _root_.io.fsq.rogue.lift.LiftRogue._
import net.liftweb.json._
import net.liftweb.mongodb.record.{MongoMetaRecord, MongoRecord}
import net.liftweb.mongodb.record.field.{MongoListField, ObjectIdPk}
import net.liftweb.record.field._
import org.joda.time.{DateTime, DateTimeZone}


// TODO make a bucket for the filter to keep track of rate?
// A bit hard because we have to loop back and add it.

object TriggerType extends Enumeration {
  // Don't remove
  val NullTrigger = Value("null")
  // Always alert if this comes in
  val AlwaysTrigger = Value("always")
  // Never alert (i just want the bucket/disable)
  val NeverTrigger = Value("never")
  // Alert if this causes a stack to reach a pwr of 2
  val PowerOfTwoTrigger = Value("pwr2")
  // Alert if a match, but only once every P seconds
  val PeriodicTrigger = Value("timed")
  // Alert if rate exceeds level L at least C times within P
  val ThresholdTrigger = Value("threshold")
}

object FilterType extends Enumeration {
  // Don't remove
  val NullFilter = Value("null")
  // Always alert if this comes in
  val KeywordFilter = Value("kw")
  // Alert if this causes a stack to reach a pwr of 2
  val BucketFilter = Value("b")
}

// Record holding configuration for a user's filter
class UserFilterRecord extends MongoRecord[UserFilterRecord] with ObjectIdPk[UserFilterRecord] with UserFilterView {
  def meta = UserFilterRecord

  def createTime = new DateTime(id.value.getTimestamp * 1000L, DateTimeZone.UTC).toDate

  object name extends StringField(this, 255) {
    override def name = "n"
  }

  object lastMatched extends LongField(this) {
    override def name = "lm"
  }

  object userId extends StringField(this, 255) {
    override def name = "u"
  }

  // List of other emails to cc
  object cc extends MongoListField[UserFilterRecord, String](this)

  // User can specify a mute period
  object muteUntil extends OptionalLongField(this) {
    override def name = "mute"
  }

  // Message Criteria
  object filterType extends EnumNameField(this, FilterType) {
    override def name = "ft"
  }

  object criteria extends MongoListField[UserFilterRecord, String](this) {
    override def name = "c"
  }

  // Trigger criteria
  object triggerType extends EnumNameField(this, TriggerType) {
    override def name = "tt"
  }

  // Span period in minutes (timed, threshold)
  object triggerPeriod extends OptionalIntField(this) {
    override def name = "p"
  }

  // Count (threshold)
  object thresholdCount extends OptionalIntField(this) {
    override def name = "tc"
  }

  // Level (threshold)
  object thresholdLevel extends OptionalIntField(this) {
    override def name = "tl"
  }

  def displayName: String = {
    name.valueBox.filterNot(_ == "").getOrElse(id.toString)
  }

  def doc = asJValue
}

object UserFilterRecord
  extends UserFilterRecord
  with MongoMetaRecord[UserFilterRecord]
  with IndexedRecord[UserFilterRecord] {
  override def collectionName = "user_filters"

  override val mongoIndexList = Vector(
    UserFilterRecord.index(_.id, Asc),
    UserFilterRecord.index(_.userId, Asc))
}
