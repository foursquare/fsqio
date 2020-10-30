/*
 * Copyright 2009 Twitter, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.fsq.twitter.ostrich.stats

import java.util.concurrent.atomic.AtomicLong

/**
  * A Counter simply keeps track of how many times an event occurred.
  * All operations are atomic and thread-safe.
  */
class Counter(value: AtomicLong) {
  def this() = this(new AtomicLong())

  /**
    * Increment the counter by one.
    */
  def incr(): Long = value.incrementAndGet

  /**
    * Increment the counter by `n`, atomically.
    */
  def incr(n: Int): Long = value.addAndGet(n)

  /**
    * Get the current value.
    */
  def apply(): Long = value.get()

  /**
    * Set a new value, wiping the old one.
    */
  def update(n: Long) = value.set(n)

  /**
    * Clear the counter back to zero.
    */
  def reset() = update(0L)

  override def toString() = "Counter(%d)".format(value.get())
}
