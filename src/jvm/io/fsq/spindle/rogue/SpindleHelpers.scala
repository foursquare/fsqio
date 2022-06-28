// Copyright 2013 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.spindle.rogue

import io.fsq.spindle.runtime.UntypedMetaRecord

object SpindleHelpers {
  def getIdentifier(meta: UntypedMetaRecord): String = {
    meta.annotations.get("mongo_identifier").getOrElse {
      throw new Exception("Add a mongo_identifier annotation to the Thrift definition for this class.")
    }
  }

  def getCollection(meta: UntypedMetaRecord): String = {
    meta.annotations.get("mongo_collection").getOrElse {
      throw new Exception("Add a mongo_collection annotation to the Thrift definition for this class.")
    }
  }
}
