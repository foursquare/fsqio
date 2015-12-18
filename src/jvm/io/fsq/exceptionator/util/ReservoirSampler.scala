// Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.exceptionator.util

import scala.collection.mutable.ArrayBuffer
import scala.reflect.ClassTag
import scala.util.Random


/**
  * Samples size objects of type T uniformly
  * See: http://en.wikipedia.org/wiki/Reservoir_sampling
  *
  * This is a threadsafe version of a reservoir sampler that exposes an
  * interface in it's companion object for merging two samplers together in a
  * way that preserves the probabilistic properties of a reservoir sampler.
  */
class ReservoirSampler[T: ClassTag](val size: Int) {
  private val random = new Random()
  private val reservoir = new ArrayBuffer[T](size)
  private[ReservoirSampler] var numSampled = 0

  def update(x: T): Unit = this.synchronized {
    if (numSampled < size) {
      reservoir.append(x)
    } else {
      val i = random.nextInt(numSampled)
      if (i < reservoir.length) {
        reservoir.update(i, x)
      }
    }
    numSampled += 1
  }

  def sampled: Int = numSampled
  def samples: Array[T] = this.synchronized(reservoir.toArray)
  def state: ReservoirSampler.State[T] = this.synchronized(ReservoirSampler.State(samples, sampled))

  def copy: ReservoirSampler[T] = ReservoirSampler(size, state)
}

object ReservoirSampler {
  case class State[T](samples: Seq[T], sampled: Int)

  def apply[T: ClassTag](size: Int, state: ReservoirSampler.State[T]): ReservoirSampler[T] = {
    val sampler = new ReservoirSampler[T](size)
    state.samples.foreach(sampler.update(_))
    sampler.numSampled = state.sampled
    sampler
  }

  private def mergeNotFull[T](
    maybeFull: ReservoirSampler[T],
    notFull: ReservoirSampler[T]
  ): ReservoirSampler[T] = {
    val merged = maybeFull.copy
    notFull.samples.foreach(merged.update(_))
    merged
  }

  /** Claim: This preserves the probabilistic properties of a reservoir sampler.
    * The math works out, try it! */
  def merge[T: ClassTag](r1: ReservoirSampler[T], r2: ReservoirSampler[T]): ReservoirSampler[T] = {
    val size = if (r1.size != r2.size) {
      throw new IllegalArgumentException("Can only merge reservoirs of the same size")
    } else r1.size

    val State(r1Samples, r1Sampled) = r1.state
    val State(r2Samples, r2Sampled) = r2.state

    if (r1Sampled >= size && r2Sampled >= size) {
      val (fromR1, fromR2) = {
        val r1Ratio = r1Sampled.toDouble / (r1Sampled+r2Sampled)
        val r1ExactNum = r1Ratio * size
        val r1Remainder = r1ExactNum - r1ExactNum.toInt
        val r1Extra = if (Random.nextDouble < r1Remainder) 1 else 0
        val r1Num = r1ExactNum.toInt + r1Extra
        (r1Num, size - r1Num)
      }
      val r1Take = Random.shuffle(r1Samples.iterator).take(fromR1)
      val r2Take = Random.shuffle(r2Samples.iterator).take(fromR2)
      val mergedState = State((r1Take++r2Take).toSeq, r1Sampled+r2Sampled)
      ReservoirSampler(size, mergedState)
    } else if (r1Sampled >= size) {
      mergeNotFull(r1, r2)
    } else {
      mergeNotFull(r2, r1)
    }
  }
}
