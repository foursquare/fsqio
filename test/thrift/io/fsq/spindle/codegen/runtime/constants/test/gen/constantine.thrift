// Copyright 2013 Foursquare Labs Inc. All Rights Reserved.

namespace java io.fsq.spindle.codegen.runtime.constants.test.gen

const string SIMPLE = "hello"
const bool BOOL = true
const double DOUBLE = 3.2
const map<string, string> MAPCONST = {"hello": "world", "wisconsin": "badgers"}
const list<double> LISTCONST = [1.2, 2.1, 1.1]

enum Foo {
  bar = 0
  froo = 1
}
const string REFERS = SIMPLE
const Foo MYNUM = Foo.bar
const map<string,string> CRAZY = {
  "foo": "hello",
  "bar": REFERS
}
const map<string, Foo> CRAZYENUMS = {
  "foo": Foo.bar, "bar": Foo.froo
}
