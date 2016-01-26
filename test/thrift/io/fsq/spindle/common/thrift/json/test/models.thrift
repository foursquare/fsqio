namespace java io.fsq.spindle.common.thrift.json.test.gen

struct Simple {
  1: optional string myString
  2: optional bool myBool
  3: optional i32 myInt
  4: optional list<string> myStrings
}

struct IntHolder {
  1: optional i32 i
}

struct IntHolderList {
  1: optional list<IntHolder> intHolders
}

struct SimpleHolderList {
  1: optional list<Simple> simpleHolders
}

struct Complex {
  1: optional list<i32> iList
  2: optional Simple simple
  3: optional list<Simple> simpleList
  4: optional IntHolderList intHolderList
  5: optional SimpleHolderList simpleHolderList
  6: optional map<string, IntHolder> intHolderMap
}

struct MapTest {
  1: optional map<string, string> stringMap
  2: optional map<string, i32> intMap
  3: optional map<string, Simple> simpleMap
  4: optional map<string, Complex> complexMap
}

struct ListOfLists {
  1: optional list<list<string>> listHolders
}

struct ListOfListsOfLists {
  1: optional list<list<list<i32>>> listHolders
}

struct MapOfLists {
  1: optional map<string, list<string>> mapHolder
}
