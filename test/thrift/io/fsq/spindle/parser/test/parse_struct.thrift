// Copyright 2013 Foursquare Labs Inc. All Rights Reserved.

// Test spacing
struct S00{}
struct S01 {}
struct S02 {};

// Test annotations
struct S03{}()
struct S04{}(a="b")
struct S05{}(a="b",c="d")

// Test xsdAl
struct S06 xsdAll{}

// Test fields
struct S07{1:list<i32>f00}
struct S08{1 :list<i32>f00}
struct S09{1: list<i32>f00}
struct S10{1: list<i32> f00}
struct S11{1: list<i32> f00;}
struct S12{1: list<i32> f00,}
struct S13{1:optional list<i32> f00}
struct S14{1: optional list<i32> f00}
struct S15{1: optional list<i32> f00 =[]}
struct S16{1: optional list<i32> f00 xsdOptional}
struct S17{1: optional list<i32> f00 xsdNillable}
struct S18{1: optional list<i32> f00 xsd_attributes{1:i32 f01}}
struct S19{1:i32 f00;2:i32 f01;}
struct S20{1:i32 f00,2:i32 f01,}
struct S21{
  1:i32 f00;2:i32 f01;}
struct S22{
  1:i32 f00
  2:i32 f01}
struct S23{
  1:i32 f00
  2:i32 f01
}
