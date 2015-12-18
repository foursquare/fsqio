// Copyright 2013 Foursquare Labs Inc. All Rights Reserved.

service s00{}
service s01{}()
service s02{}(a="b")
service s03 {}
service s04 {}
service s05 extends s00{}
service s06 { i32 m00() }
service s07 { oneway i32 m00() }
service s08 { void m00() }
service s09 { i32 m00() throws() }
service s10 { i32 m00(1:i32 a00) }
service s11 { i32 m00(1:i32 a00, 2:i32 a01) }
service s12 { i32 m00(1:i32 a00, 2:i32 a01) throws(3:i32 a02) }
service s13 { i32 m00(1:i32 a00, 2:i32 a01) throws(3:i32 a02, 4:i32 a03) }
service s14 { i32 m00(1:i32 a00, 2:i32 a01) throws(3:i32 a02, 4:i32 a03) (a="b") }
