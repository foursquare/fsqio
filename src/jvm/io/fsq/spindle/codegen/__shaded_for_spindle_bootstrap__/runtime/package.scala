// Copyright 2012 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.spindle.__shaded_for_spindle_bootstrap__

package object runtime {
  trait Tagged[U]
  type Id[T, U] = T with Tagged[U]
}
