// Copyright 2013 Foursquare Labs Inc. All Rights Reserved.

// Test typedef
typedef i32 t00
typedef i32 t01()
typedef i32 t02(a="b")
typedef i32 t03 (a = "b", c = "d")

// Test enum
enum e00{}
enum e01 {}
enum e02{}()
enum e03{}(a="b")
enum e04{}(a="b", c="d")
enum e05{v00}
enum e06{v00=3}
enum e07{v00()}
enum e08{v00=3(a="b")}
enum e09{v00(a="b",c="d")}
enum e10{a;b=7;c}
enum e11{a,b=7,c}
enum e12 {
  a
  b=7
  c
}

// Test union
union u00{}
union u01{}()
union u02{}(a="b")

// Test exception
exception ex00{}
exception ex01{}()
exception ex02{}(a="b")
