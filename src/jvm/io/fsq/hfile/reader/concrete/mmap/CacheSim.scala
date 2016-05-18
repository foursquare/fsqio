// Copyright 2016 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.hfile.reader.concrete.mmap

import com.twitter.ostrich.stats.Stats
import com.twitter.util.FuturePool
import java.util.concurrent.Executors
import scala.collection.mutable

/**
  *  Implements a simulation of lru cache behavior based on Mattson's classic paper[1]
  *  It gives us an idea of what the cache hit rate would be for any cache size.
  *  It's roughly O(number of blocks) per lookup and so is not suited for high qps collections.
  *  If required [2] lets you simulate m different cache sizes in O(m) time per lookup.
  * 1. "Evaluation techniques for storage hierarchies" http://www-inst.eecs.berkeley.edu/~cs266/sp10/readings/mattson70.pdf
  * 2. "Implementing Stack Simulation for Highly-Associative Memories" http://pages.cs.wisc.edu/~markhill/papers/sigmetrics91_fully_assoc.pdf
  */
abstract class CacheSim[Key](numKeys: Long, pool: FuturePool = CacheSim.pool) {

  val histogram: mutable.Map[Long, Long] = mutable.Map.empty //use an array here?

  def access(k: Key):Unit = pool { Stats.time("cachesim.internal") { accessInternal(k) } }

  def accessInternal(k: Key): Unit

  var accessCount: Double = 0

  def cumlativeHist: collection.immutable.ListMap[String,Double] = {
    var total = 0L
    var percentile: Double = 1.0
    val m = List.newBuilder[(String, Double)]
    histogram.toVector.sortBy(_._1).foreach (kv => {
      total = total + kv._2
      // m += (kv._1.toString -> total.toDouble) // uncomment for more detail
      while (kv._1.toDouble/numKeys  > percentile/100) {
        percentile += 1
        m +=(s"p${percentile-1}" -> total.toDouble/accessCount)
      }
    })
    collection.immutable.ListMap(m.result:_*)
  }
}


class JavaLLCacheSim[Key](numKeys: Long) extends CacheSim[Key](numKeys) {
  val lastAccess: java.util.LinkedList[Key] = new java.util.LinkedList[Key]()
  def accessInternal(k: Key): Unit = synchronized {
    accessCount += 1
    val distance = lastAccess.indexOf(k)
    if (distance>0) {
      histogram.update(distance, histogram.getOrElseUpdate(distance, 0) + 1)
      lastAccess.remove(distance)
    } else {
      histogram.update(-1, histogram.getOrElseUpdate(-1, 0) + 1)
    }
    lastAccess.addFirst(k)
  }
}





class CacheSimLL[Key](numKeys: Long) extends CacheSim[Key](numKeys) {

// A linked list with a combined indexOf/remove operation that scans the list once.
  class LL[V] {

    case class Entry[V](obj: V, var next: Option[Entry[V]])
    var headO: Option[Entry[V]] = None

    def addFirst(o: V)  { headO  = Some(Entry(o, headO)) }

    def delete(o: V): Long = {
      headO match {
        case None => -1 // empty list, nothing to do
        case Some(head)  if (head.obj == o)  => {  // deleting the head of the list
          headO = head.next
          1
        }
        case Some(head) => {
          var hPrev: Entry[V] = head
          var h: Option[Entry[V]] = head.next
          var index = 2 // we start at the 2nd element in the list
          while (h != None && !h.exists(_.obj == o)) {
            hPrev = h.get
            h = hPrev.next
            index += 1
          }
          h match {
            case Some(h) => { // delete by skipping over h
              hPrev.next = h.next
              index
            }
            case None => -1 // not found
          }
        }
      }
    }
  }

  val lastAccess: LL[Key] = new LL[Key]
  def accessInternal(k: Key): Unit = synchronized {
    accessCount += 1
    val distance = lastAccess.delete(k)
    if (distance>0) {
      histogram.update(distance, histogram.getOrElseUpdate(distance, 0) + 1)
    } else {
      // histogram.update(-1, histogram.getOrElseUpdate(-1, 0) + 1)
    }
    lastAccess.addFirst(k)
  }
}


object CacheSim {

  val pool = FuturePool(Executors.newFixedThreadPool(2))

  def makeSim[V](numKeys: Long): CacheSim[V] = new CacheSimLL[V](numKeys)

  def test(items: Int, iterations: Long, oC: Option[CacheSim[Long]] = None) = {

    val c = oC.getOrElse(new CacheSimLL[Long](items))

    for (i <- 0L to iterations) { c.access(scala.util.Random.nextInt(items)) }
    c
    // val file = new java.io.PrintWriter("testcache.hist")
    // c.cumlativeHist.foreach (f => file.println (s"${f._1} ${f._2}"))
    // file.close
  }

  def time[T](f: => T): (T, Long) = {
    val start = System.nanoTime
    val rv = f
    val end = System.nanoTime
    (rv, end - start)
  }
}
