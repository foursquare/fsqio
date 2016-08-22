// Copyright 2012 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.spindle.runtime.test

import io.fsq.spindle.codegen.binary.test.gen.{ChildStruct16, ChildStruct32, ChildStruct64, ChildStruct7, ParentStruct}
import io.fsq.spindle.runtime.BitFieldHelpers
import org.junit.{Assert => A, Test}

class BitFieldHelpersTest {
  @Test
  def testLongFlags(): Unit = {
    // Flag at place 1 is set true, flag at place 0 is set false, all others unset
    val sanityFlags = (3L << 32) | 2
    A.assertFalse(BitFieldHelpers.getLongIsSet(sanityFlags, 2))

    A.assertTrue(BitFieldHelpers.getLongIsSet(sanityFlags, 1))
    A.assertTrue(BitFieldHelpers.getLongValue(sanityFlags, 1))

    A.assertTrue(BitFieldHelpers.getLongIsSet(sanityFlags, 0))
    A.assertFalse(BitFieldHelpers.getLongValue(sanityFlags, 0))
  }

  @Test
  def toLong(): Unit = {
    A.assertEquals(s7, BitFieldHelpers.bitFieldToStruct(BitFieldHelpers.structToBitField(s7), s7.meta))
    A.assertEquals(s7, BitFieldHelpers.longBitFieldToStruct(BitFieldHelpers.structToLongBitField(s7), s7.meta))

    A.assertEquals(s7WithUnset,BitFieldHelpers.bitFieldToStruct(
      BitFieldHelpers.structToBitField(s7WithUnset), s7WithUnset.meta))
    A.assertEquals(s7WithUnset, BitFieldHelpers.longBitFieldToStruct(
      BitFieldHelpers.structToLongBitField(s7WithUnset), s7WithUnset.meta))

    A.assertEquals(s16, BitFieldHelpers.bitFieldToStruct(BitFieldHelpers.structToBitField(s16), s16.meta))
    A.assertEquals(s16, BitFieldHelpers.longBitFieldToStruct(BitFieldHelpers.structToLongBitField(s16), s16.meta))

    A.assertEquals(s32,BitFieldHelpers.bitFieldToStructNoSetBits(BitFieldHelpers.structToBitFieldNoSetBits(s32), s32.meta))

    A.assertEquals(s64, BitFieldHelpers.longBitFieldToStructNoSetBits(
      BitFieldHelpers.structToLongBitFieldNoSetBits(s64), s64.meta))
  }

  @Test
  def toStruct(): Unit = {
    val int = 0xFFFFAA55
    val long = 0xFFFFFFFFAAAA5555L

    A.assertEquals(int, BitFieldHelpers.structToBitField(BitFieldHelpers.bitFieldToStruct(int, s16.meta)))
    A.assertEquals(int, BitFieldHelpers.structToBitFieldNoSetBits(
      BitFieldHelpers.bitFieldToStructNoSetBits(int, s32.meta)))

    A.assertEquals(long, BitFieldHelpers.structToLongBitFieldNoSetBits(
      BitFieldHelpers.longBitFieldToStructNoSetBits(long, s64.meta)))
  }

  @Test
  def testBuilder(): Unit = {
    A.assertEquals(ParentStruct.newBuilder.s7As32Struct(s7).result(),
      ParentStruct.newBuilder.s7As32(BitFieldHelpers.structToBitField(s7)).result())
    A.assertEquals(ParentStruct.newBuilder.s16As32Struct(s16).result(),
      ParentStruct.newBuilder.s16As32(BitFieldHelpers.structToBitField(s16)).result())

    A.assertEquals(ParentStruct.newBuilder.s7As64Struct(s7).result(),
      ParentStruct.newBuilder.s7As64(BitFieldHelpers.structToLongBitField(s7)).result())

    A.assertEquals(ParentStruct.newBuilder.s7As32NoSetStruct(s7).result(),
      ParentStruct.newBuilder.s7As32NoSet(BitFieldHelpers.structToBitFieldNoSetBits(s7)).result())
    A.assertEquals(ParentStruct.newBuilder.s32As32NoSetStruct(s32).result(),
      ParentStruct.newBuilder.s32As32NoSet(BitFieldHelpers.structToBitFieldNoSetBits(s32)).result())

    A.assertEquals(ParentStruct.newBuilder.s7As64NoSetStruct(s7).result(),
      ParentStruct.newBuilder.s7As64NoSet(BitFieldHelpers.structToLongBitFieldNoSetBits(s7)).result())
    A.assertEquals(ParentStruct.newBuilder.s64As64NoSetStruct(s64).result(),
      ParentStruct.newBuilder.s64As64NoSet(BitFieldHelpers.structToLongBitFieldNoSetBits(s64)).result())
  }

  val s7 = ChildStruct7.newBuilder
    .member1(true)
    .member2(false)
    .member3(true)
    .member4(false)
    .member5(true)
    .member6(false)
    .member7(true)
    .result()

  val s7WithUnset = ChildStruct7.newBuilder
    .member1(true)
    .member2(false)
    .member3(true)
    .member4(false)
    .result()

  val s16 = ChildStruct16.newBuilder
    .member1(true)
    .member2(false)
    .member3(true)
    .member4(false)
    .member5(true)
    .member6(false)
    .member7(true)
    .member8(false)
    .member9(true)
    .member10(false)
    .member11(true)
    .member12(false)
    .member13(true)
    .member14(false)
    .member15(true)
    .member16(false)
    .result()

  val s32 = ChildStruct32.newBuilder
    .member1(true)
    .member2(false)
    .member3(true)
    .member4(false)
    .member5(true)
    .member6(false)
    .member7(true)
    .member8(false)
    .member9(true)
    .member10(false)
    .member11(true)
    .member12(false)
    .member13(true)
    .member14(false)
    .member15(true)
    .member16(false)
    .member17(true)
    .member18(false)
    .member19(true)
    .member20(false)
    .member21(true)
    .member22(false)
    .member23(true)
    .member24(false)
    .member25(true)
    .member26(false)
    .member27(true)
    .member28(false)
    .member29(true)
    .member30(false)
    .member31(true)
    .member32(false)
    .result()

  val s64 = ChildStruct64.newBuilder
    .member1(true)
    .member2(false)
    .member3(true)
    .member4(false)
    .member5(true)
    .member6(false)
    .member7(true)
    .member8(false)
    .member9(true)
    .member10(false)
    .member11(true)
    .member12(false)
    .member13(true)
    .member14(false)
    .member15(true)
    .member16(false)
    .member17(true)
    .member18(false)
    .member19(true)
    .member20(false)
    .member21(true)
    .member22(false)
    .member23(true)
    .member24(false)
    .member25(true)
    .member26(false)
    .member27(true)
    .member28(false)
    .member29(true)
    .member30(false)
    .member31(true)
    .member32(false)
    .member33(true)
    .member34(false)
    .member35(true)
    .member36(false)
    .member37(true)
    .member38(false)
    .member39(true)
    .member40(false)
    .member41(true)
    .member42(false)
    .member43(true)
    .member44(false)
    .member45(true)
    .member46(false)
    .member47(true)
    .member48(false)
    .member49(true)
    .member50(false)
    .member51(true)
    .member52(false)
    .member53(true)
    .member54(false)
    .member55(true)
    .member56(false)
    .member57(true)
    .member58(false)
    .member59(true)
    .member60(false)
    .member61(true)
    .member62(false)
    .member63(true)
    .member64(false)
    .result()
}
