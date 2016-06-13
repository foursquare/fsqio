// Copyright 2013 Foursquare Labs Inc. All Rights Reserved.

namespace java io.fsq.spindle.runtime.enums.gen

enum TestEnum {
  Foo = 0 (owner="jliszka")
  Bar = 1
} (
  owner = "blackmad"
)

enum OldTestEnum {
  Zero = 0 (string_value="zero")
  One = 1 (string_value="one")
}

enum NewTestEnum {
  Zero = 0 (string_value="zero")
  One = 1 (string_value="one")
  Two = 2 (string_value="two")
}

struct StructWithOldEnumField {
  1: optional OldTestEnum anEnum
  2: optional list<OldTestEnum> anEnumList
  3: optional OldTestEnum anEnumAsString (serialize_as="string")
  4: optional list<OldTestEnum> anEnumListAsString (serialize_as="string")
} (
  preserve_unknown_fields="1"
)


struct StructWithNewEnumField {
  1: optional NewTestEnum anEnum
  2: optional list<NewTestEnum> anEnumList
  3: optional NewTestEnum anEnumAsString (serialize_as="string")
  4: optional list<NewTestEnum> anEnumListAsString (serialize_as="string")
} (
  preserve_unknown_fields="1"
)
