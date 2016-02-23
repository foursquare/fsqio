// Copyright 2011 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.common.scala.test

import io.fsq.common.scala.{Lists, TryO}
import io.fsq.common.scala.Identity._
import org.junit.{Assert, Test}
import org.scalacheck.{ConsoleReporter, Prop, Test => Check}
import org.specs.SpecsMatchers

class ListsTest extends SpecsMatchers with Lists.Implicits {

  private def mustPass(p: Prop) = {
    val res = Check.check(Check.Parameters.default, p)
    ConsoleReporter(1).onTestResult(this.getClass.getName, res)
    Assert.assertTrue(res.passed)
  }

  @Test
  def testHasTypes {
    val list = List[Int](1, 2, 3, 4, 5)
    val set = Set[String]("a", "b", "c")
    val map = Map[Int, String](1 -> "1", 2 -> "2", 3 -> "3")

    list.has(3) must_== true
    list.has(-1) must_== false

    set.has("a") must_== true
    set.has("d") must_== false

    map.has(1 -> "1") must_== true
    map.has(1 -> "2") must_== false
    map.has(4 -> "4") must_== false
  }

  @Test
  def testRemoveAll {
    val propRemovedElements = Prop.forAll { (l1: List[Int], l2: List[Int]) => {
      val r = Lists.removeAll(l1, l2)
      l2.forall(i => !r.has(i))

      val r2 = Lists.removeAllBy(l1, l2)(identity)
      l2.forall(i => !r2.has(i))
    }} label "removed elements property"

    val propLength = Prop.forAll { (l1: List[Int], l2: List[Int]) => {
      val l1d = l1.distinct
      val l2d = l2.distinct
      val r = Lists.removeAll(l1d, l2d)
      r.length >= l1d.length - l2d.length
    }} label "length property"

    val propRemovedElementsBy = Prop.forAll { (l1: List[Int], l2: List[Int]) => {
      val f = (i: Int) => i % 2

      val r = Lists.removeAllBy(l1, l2)(f)
      l2.forall(i => !r.has(f(i)))
    }} label "removed elements property"

    mustPass(propRemovedElements)
    mustPass(propRemovedElementsBy)
    mustPass(propLength)
  }

  @Test
  def testAggregate {
    def getKey(i: Int) = i % 5
    def agg1(prev: Option[Int], next: Int) = prev.getOrElse(0) + next
    def agg2(prev: Option[List[Int]], next: Int) = next :: prev.getOrElse(Nil)
    val prop = Prop.forAll{ (l: List[Int]) => {
      Lists.aggregate(l)(getKey)(agg1) == l.groupBy(getKey).mappedValues(_.sum)
      Lists.aggregate(l)(getKey)(agg2) == l.groupBy(getKey).mappedValues(_.reverse)
    }}

    mustPass(prop)
  }

  @Test
  def testCartesianProduct {
    val propLength1 = Prop.forAll { (l1: List[Int], l2: List[Int]) => {
      val r = Lists.product(l1, l2)
      r.length == l1.length * l2.length
    }} label "length property 1"

    val propLength2 = Prop.forAll { (l1: List[Int], l2: List[Int]) => {
      val r = Lists.product(l1, l2)
      r.forall(_.length == 2)
    }} label "length property 2"

    val propElements1 = Prop.forAll { (l1: List[Int], l2: List[Int]) => {
      val l1d = l1.distinct
      val l2d = l2.distinct
      val r = Lists.product(l1d, l2d)
      l1d.forall(e1 => r.count(p => p(0) == e1) == l2d.length)
    }} label "elements property 1"

    val propElements2 = Prop.forAll { (l1: List[Int], l2: List[Int]) => {
      val l1d = l1.distinct
      val l2d = l2.distinct
      val r = Lists.product(l1d, l2d)
      l2d.forall(e2 => r.count(p => p(1) == e2) == l1d.length)
    }} label "elements property 2"

    mustPass(propLength1)
    mustPass(propLength2)
    mustPass(propElements1)
    mustPass(propElements2)
  }

  @Test
  def testPowerSet {
    val propLength = Prop.forAll { (l1: List[Int]) => {
      val l1t = l1.take(10)
      val r = Lists.powerset(l1t)
      r.length == (math.pow(2.0, l1t.length).toInt)
    }} label "length property"

    val propElements = Prop.forAll { (l1: List[Int]) => {
      val l1d = l1.distinct.take(10)
      val r = Lists.powerset(l1d)
      val half = r.length / 2
      l1d.forall(e1 => r.count(_.has(e1)) == half)
    }} label "elements property"

    mustPass(propLength)
    mustPass(propElements)
  }

  @Test
  def testZipWith {
    val prop = Prop.forAll { (l1: List[Int], l2: List[Int]) => {
      val r1 = Lists.zipWith(l1, l2)(_ + _)
      val r2 = Lists.zipWith(r1, l1)(_ - _)
      val r3 = Lists.zipWith(l2, r2)(_ == _)
      r3.forall(t=>t)
    }}
    mustPass(prop)
  }

  @Test
  def testUnfold {
    val r = List.unfold(0){ i => if (i == 10) None else Some((i+1, i))}
    r must_== (0 to 9).toList
  }

  @Test
  def testMkJoin {
    val prop = Prop.forAll { (l1: List[Int]) => {
      val r = l1.mkJoin(0)
      r.zipWithIndex.forall{ case (e, i) => {
        if (i % 2 == 0) {
          e == l1(i/2)
        } else {
          e == 0
        }
      }}
    }}
    mustPass(prop)
  }

  @Test
  def testFlatGroupBy {
    val prop = Prop.forAll { (l1: List[Int]) => {
      val r = l1.flatGroupBy(e => if (e % 3 == 0) None else Some(e % 3))
      r.get(0) == None && (0 to 2).forall(n => r.getOrElse(n, Nil).forall(e => e % 3 == n))
    }}
    mustPass(prop)
  }

  @Test
  def testMapAccum {
    val prop = Prop.forAll { (l1: List[Int]) => {
      val (acc, r) = l1.mapAccum(0){ case (acc, e) => (acc + e, acc)}
      acc == l1.sum && r.zipWithIndex.forall { case (e, i) => {
        l1.take(i).sum == r(i)
      }}
    }}
    mustPass(prop)
  }

  @Test
  def testFlatMapAccum {
    val prop = Prop.forAll { (l1: List[Int]) => {
      val (acc, r) = l1.flatMapAccum(0){ case (acc, e) => (acc + e, List(acc, 0))}
      acc == l1.sum && r.zipWithIndex.forall { case (e, i) => {
        if (i % 2 == 0) {
          l1.take(i/2).sum == r(i)
        } else {
          r(i) == 0
        }
      }}
    }}
    mustPass(prop)
  }

  @Test
  def testRemoveDuplicatesOn {
    val propId = Prop.forAll { (l1: List[Int]) => {
      val r = l1.distinctBy(x => x)
      r == l1.distinct
    }} label "identity check"

    val propMod = Prop.forAll { (l1: List[Int]) => {
      val l1a = l1.map(math.abs _)
      val firstEven = l1a.find(_ % 2 == 0)
      val firstOdd = l1a.find(_ % 2 == 1)
      val r = l1a.distinctBy(_ % 2)
      List(firstEven, firstOdd).flatten.sorted == r.sorted
    }} label "mod check"

    mustPass(propId)
    mustPass(propMod)
  }

  @Test
  def testCountDistinctOn {
    val propId = Prop.forAll { (l1: List[Int]) => {
      val r = l1.countDistinctBy(x => x)
      r == l1.distinct.length
    }} label "identity check"

    val propMod = Prop.forAll { (l1: List[Int]) => {
      val l1a = l1.map(math.abs _)
      val r = l1a.countDistinctBy(_ % 5)
      r == l1a.distinctBy(_ % 5).length
    }} label "mod check"

    mustPass(propId)
    mustPass(propMod)
  }

  @Test
  def testMinByOption {
    val propId = Prop.forAll { (l1: List[Int]) => {
      l1.minOption.forall(_ == l1.min)
    }} label "identity"

    val propFn1 = Prop.forAll { (l1: List[Int]) => {
      val f = (x: Int) => (10 - x)
      l1.minByOption(f).forall(e => f(e) == l1.map(f).min)
    }} label "with function 1"

    val propFn2 = Prop.forAll { (l1: List[Int]) => {
      val f = (x: Int) => x.toString
      l1.minByOption(f).forall(e => f(e) == l1.map(f).min)
    }} label "with function 2"

    mustPass(propId)
    mustPass(propFn1)
    mustPass(propFn2)
  }

  @Test
  def testMaxByOption {
    val propId = Prop.forAll { (l1: List[Int]) => {
      l1.maxOption.forall(_ == l1.max)
    }} label "identity"

    val propFn1 = Prop.forAll { (l1: List[Int]) => {
      val f = (x: Int) => (10 - x)
      l1.maxByOption(f).forall(e => f(e) == l1.map(f).max)
    }} label "with function 1"

    val propFn2 = Prop.forAll { (l1: List[Int]) => {
      val f = (x: Int) => x.toString
      l1.maxByOption(f).forall(e => f(e) == l1.map(f).max)
    }} label "with function 2"

    mustPass(propId)
    mustPass(propFn1)
    mustPass(propFn2)
  }

  @Test
  def testCollectFirst {
    val xs = List(1, 2, 3, 4, 5)
    xs.collectFirstOpt(Some(_).filter(_ > 2)) must_== Some(3)
    xs.collectFirstOpt(Some(_).filter(_ > 5)) must_== None

    class ThrowAfter3 extends Iterator[Int] {
      var x = -1
      override def hasNext: Boolean = true
      override def next: Int = {
        x += 1
        if (x > 3)
          throw new RuntimeException("called next() after 3")
        x
      }
    }

    val danger = new ThrowAfter3().toTraversable
    danger.collectFirstOpt(Some(_).filter(_ > 2)) must_== Some(3)
  }

  @Test
  def testGroupWhile {
    val xs = List(1,2,3,11,12,13,21,22)
    xs.groupWhile(_ / 10 == _ / 10) must_== List(List(1,2,3),List(11,12,13), List(21,22))
    List.empty[Int].groupWhile(_ == _) must_== Nil
  }

  @Test
  def testCrowd {
    val prop = Prop.forAll { (l1: List[Int]) => {
      val f = (x: Int) => x % 2
      val r = l1.crowd(3, f)
      r.groupBy(f).forall{ case (k, v) => v.length <= 3 }
    }}
    mustPass(prop)
  }

  @Test
  def testChunkMap {
    val propFn1 = Prop.forAll { (l1: List[Int]) => {
      val f = (xs: List[Int]) => xs.sum
      l1.chunkMap(5)(f) == l1.grouped(5).toList.map(f)
    }} label "function 1"

    val propFn2 = Prop.forAll { (l1: List[Int]) => {
      val f = (xs: List[Int]) => xs.headOption.getOrElse(0)
      l1.chunkMap(5)(f) == l1.grouped(5).toList.map(f)
    }} label "function 2"

    mustPass(propFn1)
    mustPass(propFn2)
  }

  @Test
  def testChunkFlatMap {
    val propFn1 = Prop.forAll { (l1: List[Int]) => {
      val f = (xs: List[Int]) => xs.take(3)
      l1.chunkMap(5)(f) == l1.grouped(5).toList.map(f)
    }} label "function 1"

    val propFn2 = Prop.forAll { (l1: List[Int]) => {
      val f = (xs: List[Int]) => xs.headOption
      l1.chunkMap(5)(f) == l1.grouped(5).toList.map(f)
    }} label "function 2"

    mustPass(propFn1)
    mustPass(propFn2)
  }

  @Test
  def testFilterOutWith {
    val prop = Prop.forAll { (l1: List[Int]) => {
      val evens = new scala.collection.mutable.ListBuffer[Int]
      val r = l1.filterOutWith(_ % 2 == 0, evens += _)
      r.forall(_ % 2 != 0) && evens.forall(_ % 2 == 0) && r.size + evens.size == l1.size
    }}
    mustPass(prop)
  }

  @Test
  def testSample {
    val prop = Prop.forAll { (l1: List[Int], n: Int) => {
      val l1d = l1.distinct
      val l1dSet = l1d.toSet
      val n2 = math.abs(n % 100)
      val s = l1d.sample(n2)
      s.length == math.min(l1d.length, n2) && s.forall(l1dSet)
    }}

    mustPass(prop)
  }

  @Test
  def testNth {
    val prop = Prop.forAll { (l1: List[Int]) => {
      val l1s = l1.sorted
      (0 to l1.length).forall { idx =>
        l1.nth(idx) == l1.sorted.lift(idx)
      }
    }}
    mustPass(prop)
  }

  @Test
  def testSortByDesc {
    val propId = Prop.forAll { (l1: List[Int]) => {
      val f = (x: Int) => x
      l1.sortByDesc(f) == l1.sortBy(f).reverse
    }} label "identity"

    val propFn1 = Prop.forAll { (l1: List[Int]) => {
      val f = (x: Int) => x * -1
      l1.sortByDesc(f) == l1.sortBy(f).reverse
    }} label "function 1"

    val propFn2 = Prop.forAll { (l1: List[Int]) => {
      val f = (x: Int) => x.toString
      l1.sortByDesc(f) == l1.sortBy(f).reverse
    }} label "function 2"

    mustPass(propId)
    mustPass(propFn1)
    mustPass(propFn2)
  }

  @Test
  def distinctCounts {
    val prop = Prop.forAll { (l1: List[Int]) => {
      val l1m = l1.map(_ % 5)
      val r = l1m.distinctCounts
      r.forall { case (v, count) => l1m.count(_ == v) == count}
    }}
    mustPass(prop)
  }

  @Test
  def testToMapBy {
    // Empty input
    Assert.assertTrue(Seq.empty[Int].toMapBy(x => (x -> (x + 10))) equals Map.empty[Int, Int])

    val testValue = Seq(1, 2, 3).toMapBy(x => (x -> (x + 10)))
    val result = Map(1 -> 11, 2 -> 12, 3 -> 13)
    Assert.assertTrue(testValue equals result)
  }

  @Test
  def testToMapByKey {
    //Empty input
    Assert.assertTrue(Seq.empty[Int].toMapByKey(x => x) equals Map.empty[Int, Int])

    val testValue = Seq(1 -> "a", 2 -> "b").toMapByKey(_._1)
    val result = Map(1 -> (1, "a"), 2 -> (2, "b"))
    Assert.assertTrue(testValue equals result)
  }

  @Test
  def testGroupByKeyValue {
    // Empty input
    Assert.assertTrue(Seq.empty[Int].groupByKeyValue(x => (x -> x)) equals Map.empty[Int, Seq[Int]])

    val testValue = Seq(1 -> "a", 2 -> "a", 1 -> "b").groupByKeyValue(x => x)
    val result = Map(1 -> Seq("a", "b"), 2 -> Seq("a"))

    Assert.assertTrue(testValue equals result)
  }

  @Test
  def testToMapAccumValues {
    // Empty input
    Assert.assertTrue(Seq.empty[(Int, Int)].toMapAccumValues equals Map.empty[Int, Seq[Int]])

    val testValue = Seq(1 -> "a", 2 -> "a", 1 -> "b").toMapAccumValues
    val result = Map(1 -> Seq("a", "b"), 2 -> Seq("a"))

    Assert.assertTrue(testValue equals result)
  }

  @Test
  def testInvert {
    val prop = Prop.forAll { (l1: List[Int]) => {
      val m = l1.groupBy(_ % 5)
      val mii = m.invert.invert
      mapListContains(mii, m) && mapListContains(m, mii)
    }}
    mustPass(prop)
  }

  @Test
  def testFlattenValues {
    val prop = Prop.forAll { (l1: List[Int]) => {
      val m = l1.map(n => math.abs(n % 100000)).groupBy(_ % 10)
      val mopt = (0 to 9).map(k => k -> m.get(k)).toMap
      val fm = mopt.flattenValues
      mapContains(fm, m) && mapContains(m, fm)
    }}
    mustPass(prop)
  }

  @Test
  def testFlatMapValues {
    val prop = Prop.forAll { (l1: List[Int]) => {
      val m = l1.map(n => math.abs(n % 100000)).groupBy(_ % 10).mappedValues(_.head)
      val mopt = (0 to 9).map(k => k -> m.get(k)).toMap
      val fm = mopt.flatMapValues(x => x)
      mapContains(fm, m) && mapContains(m, fm)
    }}
    mustPass(prop)
  }

  private def mapListContains[A](m1: Map[A, List[A]], m2: Map[A, List[A]])(implicit ord: Ordering[A]) = {
    m1.forall{ case (k, v) => m2.get(k).exists(_.sorted == v.sorted) }
  }

  private def mapContains[A, B](m1: Map[A, B], m2: Map[A, B]) = {
    m1.forall{ case (k, v) => m2.get(k) == Some(v) }
  }

  @Test
  def testSlidingPairsLists {
    Assert.assertTrue(List(1,2,3).slidingPairs =? List((1,2), (2,3)))
    Assert.assertTrue(List(1,2,3,4).slidingPairs =? List((1,2), (2,3), (3,4)))
    Assert.assertTrue(List(1).slidingPairs =? Nil)
    Assert.assertTrue(Nil.slidingPairs =? Nil)
  }

  @Test
  def testSlidingPairsSeqs {
    Assert.assertTrue(Seq(1,2,3).slidingPairs =? Seq((1,2), (2,3)))
    Assert.assertTrue(Seq(1,2,3).slidingPairs =? Seq((1,2), (2,3)))
    Assert.assertTrue(Seq(1,2,3,4).slidingPairs =? Seq((1,2), (2,3), (3,4)))
    Assert.assertTrue(Seq(1).slidingPairs =? Seq())
    Assert.assertTrue(Seq().slidingPairs =? Seq())
  }

  @Test
  def testSlidingPairsIterables {
    Assert.assertTrue(Iterable(1,2,3).slidingPairs =? Iterable((1,2), (2,3)))
    Assert.assertTrue(Iterable(1,2,3,4).slidingPairs =? Iterable((1,2), (2,3), (3,4)))
    Assert.assertTrue(Iterable(1).slidingPairs =? Iterable())
    Assert.assertTrue(Iterable().slidingPairs =? Iterable())
  }

  @Test
  def testSlidingPairsOpts {
    Assert.assertTrue(Iterable(1,2,3).slidingOptPairs =? Iterable(
      (1,Some(2)),
      (2,Some(3)),
      (3, None)
    ))
    Assert.assertTrue(Iterable(1,2,3,4).slidingOptPairs =? Iterable(
      (1,Some(2)),
      (2,Some(3)),
      (3,Some(4)),
      (4, None)
    ))
    Assert.assertTrue(Iterable(1).slidingOptPairs =? Iterable((1,None)))
    Assert.assertTrue(Iterable().slidingOptPairs =? Iterable())
  }

  @Test
  def testTopN {
    // The order isn't guaranteed, hence the Set comparison
    Assert.assertEquals(Iterable(-1,1,2,3,4).topN(1).toSet, Set(4))
    Assert.assertEquals(Iterable(4,3,2,1,-1).topN(1).toSet, Set(4))
    Assert.assertEquals(Iterable(-1,1,4,3,2).topN(1).toSet, Set(4))
    Assert.assertEquals(Iterable(-1,1,4,2,3).topN(3).toSet, Set(4,2,3))
    Assert.assertEquals(Iterable(-1,1,4,2,3).topN(5).toSet, Set(-1,1,4,2,3))
    Assert.assertEquals(Iterable(-1,1,4,2,3).topN(100).toSet, Set(-1,1,4,2,3))
    Assert.assertEquals(Iterable("k", "y", "m", "x", "c", "z").topN(3).toSet, Set("x", "y", "z"))
  }

  @Test def toListBy {
    Assert.assertEquals(Iterable(1,2,3,4).toListBy(identity), List(1,2,3,4))
    Assert.assertEquals(Iterable(1,2,3,4).toListBy(_ % 2 =? 0), List(false, true, false, true))
    Assert.assertEquals(List(1,2,3,4).toListBy(_ % 2 =? 0), List(false, true, false, true))
    Assert.assertEquals(Vector(1,2,3,4).toListBy(_ % 2 =? 0), List(false, true, false, true))
    Assert.assertEquals(Map(1 -> Seq(10, 11), 2 -> Seq(20, 21)).toListBy(_._2).flatten.sorted, List(10, 11, 20, 21))
    Assert.assertEquals(Iterable[Int]().toListBy(_ % 2 =? 0), Nil)
  }

  @Test def flatToListBy {
    Assert.assertEquals(Iterable("hi", "hello", "25").flatToListBy(TryO.toInt(_)), List(25))
    Assert.assertEquals(Iterable("hi", "25", "hello", "25", "30").flatToListBy(TryO.toInt(_)), List(25, 25, 30))
    Assert.assertEquals(Iterable("hi", "twenty-five", "hello").flatToListBy(TryO.toInt(_)), List[Int]())
    Assert.assertEquals(Iterable("hi").flatToListBy(TryO.toInt(_)), List[Int]())
    Assert.assertEquals(Iterable("25").flatToListBy(TryO.toInt(_)), List(25))
    Assert.assertEquals(Iterable[String]().flatToListBy(TryO.toInt(_)), List[Int]())
  }

  @Test def toVectorBy {
    Assert.assertEquals(Iterable(1,2,3,4).toVectorBy(identity), Vector(1,2,3,4))
    Assert.assertEquals(Iterable(1,2,3,4).toVectorBy(_ % 2 =? 0), Vector(false, true, false, true))
    Assert.assertEquals(List(1,2,3,4).toListBy(_ % 2 =? 0), Vector(false, true, false, true))
    Assert.assertEquals(Vector(1,2,3,4).toListBy(_ % 2 =? 0), Vector(false, true, false, true))
    Assert.assertEquals(Map(1 -> Seq(10, 11), 2 -> Seq(20, 21)).toListBy(_._2).flatten.sorted, Vector(10, 11, 20, 21))
    Assert.assertEquals(Iterable[Int]().toVectorBy(_ % 2 =? 0), Vector())
    Assert.assertEquals(Some(1).toVectorBy(identity), Vector(1))
    Assert.assertEquals(None.toVectorBy(identity), Vector.empty)
  }

  @Test def flatToVectorBy {
    Assert.assertEquals(Iterable("hi", "hello", "25").flatToVectorBy(TryO.toInt(_)), Vector(25))
    Assert.assertEquals(Iterable("hi", "25", "hello", "25", "30").flatToVectorBy(TryO.toInt(_)), Vector(25, 25, 30))
    Assert.assertEquals(Iterable("hi", "twenty-five", "hello").flatToVectorBy(TryO.toInt(_)), Vector[Int]())
    Assert.assertEquals(Iterable("hi").flatToVectorBy(TryO.toInt(_)), Vector[Int]())
    Assert.assertEquals(Iterable("25").flatToVectorBy(TryO.toInt(_)), Vector(25))
    Assert.assertEquals(Iterable[String]().flatToVectorBy(TryO.toInt(_)), Vector[Int]())
  }

  @Test def flatMapFind {
    Assert.assertEquals(Iterable("hi", "hello", "25").flatMapFind(TryO.toInt(_)), Some(25))
    Assert.assertEquals(Iterable("hi", "25", "hello", "25").flatMapFind(TryO.toInt(_)), Some(25))
    Assert.assertEquals(Iterable("hi", "twenty-five", "hello").flatMapFind(TryO.toInt(_)), None)
    Assert.assertEquals(Iterable("hi").flatMapFind(TryO.toInt(_)), None)
    Assert.assertEquals(Iterable("25").flatMapFind(TryO.toInt(_)), Some(25))
    Assert.assertEquals(Iterable[String]().flatMapFind(TryO.toInt(_)), None)
  }
}

