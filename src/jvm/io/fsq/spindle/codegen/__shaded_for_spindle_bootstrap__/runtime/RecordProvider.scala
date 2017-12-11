// Copyright 2012 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.spindle.__shaded_for_spindle_bootstrap__.runtime

import org.apache.thrift.{TBase, TFieldIdEnum}

trait RecordProvider[R <: TBase[_ <: TBase[_, _], _ <: TFieldIdEnum]] {
  def createRecord: R
}
