// Copyright 2012 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.spindle

package object runtime {
  trait Tagged[U]
  type Id[T, U] = T with Tagged[U]
}
