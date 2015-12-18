// Copyright 2014 Foursquare Labs Inc. All Rights Reserved.

namespace java io.fsq.spindle.codegen.test.gen

service AService {
  void voidMethod()
  oneway void oneWayVoidMethod()
  i32 add(1: i32 x, 2: i32 y)
  void send(1: string request)
}
