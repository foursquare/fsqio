// Copyright 2012 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.exceptionator.actions

import io.fsq.exceptionator.model.io.UserFilterView

trait HasUserFilterActions {
  def userFilterActions: UserFilterActions
}

trait UserFilterActions {
  def getAll(userId: Option[String] = None): List[UserFilterView]
  def get(id: String): Option[UserFilterView]
  def remove(id: String, userId: Option[String])
  def save(jsonString: String, userId: String): Option[UserFilterView]
}
