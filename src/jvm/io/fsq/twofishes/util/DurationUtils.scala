package io.fsq.twofishes.util

import com.twitter.ostrich.stats.Stats
import com.twitter.util.{Duration, Stopwatch}
import org.slf4s.Logging

object DurationUtils {
  def inMilliseconds[T](f: => T): (T, Duration) = {
    val elapsed = Stopwatch.start()
    val ret = f
    (ret, elapsed())
  }

  def inNanoseconds[T](f: => T): (T, Duration) = {
    val elapsed = Stopwatch.start()
    val ret = f
    (ret, elapsed())
  }
}

trait DurationUtils extends Logging {
  def logDuration[T](ostrichKey: String, extraInfo: String = "")(f: => T): T = {
    val (rv, duration) = DurationUtils.inNanoseconds(f)
    if (duration.inMilliseconds > 200) {
      log.debug(ostrichKey + ": " + extraInfo + " in %s µs / %s ms".format(duration.inMicroseconds, duration.inMilliseconds))
    }
    Stats.addMetric(ostrichKey + "_msec", duration.inMilliseconds.toInt)
    rv
  }

  def logPhase[T](what: String)(f: => T): T = {
    log.info("starting: " + what)
    val (rv, duration) = DurationUtils.inNanoseconds(f)
    log.info("finished: %s in %s secs / %s mins".format(
    	what, duration.inSeconds, duration.inMinutes
    ))
    rv
  }
 }
