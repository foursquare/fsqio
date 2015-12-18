// Copyright 2013 Foursquare Labs Inc. All Rights Reserved.

typedef i32 myint

// Test optional spacing
const i32 c00=0
const i32 c01 =0
const i32 c02= 0
const i32 c03 = 0

// Test base types
const string c04=0
const binary c05=0
const bool c06=0
const byte c07=0
const i16 c08=0
const i32 c09=0
const i64 c10=0
const double c11=0

// Test base types with type annotations
const string() c12=0
const string(a="b") c13=0
const string(a="b",c="d") c14=0

// Test container types
const list<i32>c15=[]
const list<i32>c16=[]
const list< 	 i32>c17=[]
const list<i32	 	>c18=[]
const list<		i32	 	>c19=[]
const list<i32> cpp_type "foo" c20=[]
const set<i32> c21=[]
const set cpp_type "foo" <i32>c22=[]
const map<i32, i32> c23={}
const map<i32,i32> c24={}
const map< i32,i32> c25={}
const map< i32,i32 > c26={}
const map< i32 ,i32 > c27={}
const map< i32 , i32 > c28={}
const map cpp_type "foo" <i32, i32> c29={}

// Test typeref
const myint c30 = 0
