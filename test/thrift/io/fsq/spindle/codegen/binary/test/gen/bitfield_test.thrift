// Copyright 2013 Foursquare Labs Inc. All Rights Reserved.

namespace java io.fsq.spindle.codegen.binary.test.gen

struct ChildStruct7 {
  1: optional bool member1
  2: optional bool member2
  3: optional bool member3
  4: optional bool member4
  5: optional bool member5
  6: optional bool member6
  7: optional bool member7
}

struct ChildStruct16 {
  1: optional bool member1
  2: optional bool member2
  3: optional bool member3
  4: optional bool member4
  5: optional bool member5
  6: optional bool member6
  7: optional bool member7
  8: optional bool member8
  9: optional bool member9
  10: optional bool member10
  11: optional bool member11
  12: optional bool member12
  13: optional bool member13
  14: optional bool member14
  15: optional bool member15
  16: optional bool member16
}

struct ChildStruct32 {
  1: optional bool member1
  2: optional bool member2
  3: optional bool member3
  4: optional bool member4
  5: optional bool member5
  6: optional bool member6
  7: optional bool member7
  8: optional bool member8
  9: optional bool member9
  10: optional bool member10
  11: optional bool member11
  12: optional bool member12
  13: optional bool member13
  14: optional bool member14
  15: optional bool member15
  16: optional bool member16
  17: optional bool member17
  18: optional bool member18
  19: optional bool member19
  20: optional bool member20
  21: optional bool member21
  22: optional bool member22
  23: optional bool member23
  24: optional bool member24
  25: optional bool member25
  26: optional bool member26
  27: optional bool member27
  28: optional bool member28
  29: optional bool member29
  30: optional bool member30
  31: optional bool member31
  32: optional bool member32
}


struct ChildStruct64 {
  1: optional bool member1
  2: optional bool member2
  3: optional bool member3
  4: optional bool member4
  5: optional bool member5
  6: optional bool member6
  7: optional bool member7
  8: optional bool member8
  9: optional bool member9
  10: optional bool member10
  11: optional bool member11
  12: optional bool member12
  13: optional bool member13
  14: optional bool member14
  15: optional bool member15
  16: optional bool member16
  17: optional bool member17
  18: optional bool member18
  19: optional bool member19
  20: optional bool member20
  21: optional bool member21
  22: optional bool member22
  23: optional bool member23
  24: optional bool member24
  25: optional bool member25
  26: optional bool member26
  27: optional bool member27
  28: optional bool member28
  29: optional bool member29
  30: optional bool member30
  31: optional bool member31
  32: optional bool member32
  33: optional bool member33
  34: optional bool member34
  35: optional bool member35
  36: optional bool member36
  37: optional bool member37
  38: optional bool member38
  39: optional bool member39
  40: optional bool member40
  41: optional bool member41
  42: optional bool member42
  43: optional bool member43
  44: optional bool member44
  45: optional bool member45
  46: optional bool member46
  47: optional bool member47
  48: optional bool member48
  49: optional bool member49
  50: optional bool member50
  51: optional bool member51
  52: optional bool member52
  53: optional bool member53
  54: optional bool member54
  55: optional bool member55
  56: optional bool member56
  57: optional bool member57
  58: optional bool member58
  59: optional bool member59
  60: optional bool member60
  61: optional bool member61
  62: optional bool member62
  63: optional bool member63
  64: optional bool member64
}

struct ParentStruct {
  1: optional i32 s7As32 (bitfield_struct="ChildStruct7")
  2: optional i64 s7As64 (bitfield_struct="ChildStruct7")
  3: optional i32 s16As32 (bitfield_struct="ChildStruct16")
  4: optional i32 s7As32NoSet (bitfield_struct_no_setbits="ChildStruct7")
  5: optional i64 s7As64NoSet (bitfield_struct_no_setbits="ChildStruct7")
  6: optional i32 s32As32NoSet (bitfield_struct_no_setbits="ChildStruct32")
  7: optional i64 s64As64NoSet (bitfield_struct_no_setbits="ChildStruct64")
}

