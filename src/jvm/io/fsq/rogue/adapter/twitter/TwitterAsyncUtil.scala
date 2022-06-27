// Copyright 2020 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.rogue.adapter.twitter

import com.mongodb.MongoInterruptedException
import com.mongodb.reactivestreams.client.{Success => MongoSuccess}
import com.twitter.util.{Future, Promise}
import io.fsq.rogue.RogueException
import java.lang.{StringBuilder => JavaStringBuilder}
import org.reactivestreams.{Publisher, Subscriber, Subscription}

/**
  * This is a Subscriber that populates a Twitter Future (Promise).
  * It is up to the implementing class to do this in onNext.
  *
  * @tparam T The type of element emitted by the Publisher
  * @tparam R The result type provided by the promise
  */
abstract class TwitterBaseSubscriber[T, R] extends Subscriber[T] {

  protected[twitter] val promise = new Promise[R]
  private var subscriptionOpt: Option[Subscription] = None

  // Only call this from onNext/onError/onComplete.
  // subscriptionOpt will be set in subscribe
  protected def subscription: Subscription = subscriptionOpt.get

  override def onSubscribe(s: Subscription): Unit = {
    if (subscriptionOpt.isDefined) {
      // This really shouldn't happen. Ignore the new incoming subscription and cancel it.
      s.cancel()
    } else {
      this.subscriptionOpt = Some(s)
      s.request(1)
    }
  }

  // onComplete should always make that the promise is defined at the end.
  // If you override, it is wise to call super.onComplete() afterwards
  override def onComplete(): Unit = {
    if (!promise.isDefined) {
      val err = new RogueException("Stream completed unexpectedly", new NoSuchElementException)
      promise.setException(err)
    }
  }

  override def onError(throwable: Throwable): Unit = throwable match {
    case mie: MongoInterruptedException => {
      val message = TwitterAsyncUtil.getInterruptedMessage(Thread.currentThread.getName)
      promise.setException(new RogueException(message, mie))
    }

    case other => promise.setException(other)
  }
}

object TwitterAsyncUtil {

  def singleResult[T](pub: Publisher[T]): Future[T] = {
    val sub = new TwitterBaseSubscriber[T, T] {
      override def onNext(t: T): Unit = {
        promise.setValue(t)
      }
    }
    pub.subscribe(sub)
    sub.promise
  }

  def optResult[T](pub: Publisher[T]): Future[Option[T]] = {
    val sub = new TwitterBaseSubscriber[T, Option[T]] {
      override def onNext(t: T): Unit = {
        promise.setValue(Some(t))
      }

      override def onComplete(): Unit = {
        if (!promise.isDefined) {
          promise.setValue(None)
        }
      }
    }
    pub.subscribe(sub)
    sub.promise
  }

  def unitResult[T](pub: Publisher[MongoSuccess]): Future[Unit] = {
    val sub = new TwitterBaseSubscriber[MongoSuccess, Unit] {
      override def onNext(t: MongoSuccess): Unit = {
        promise.setValue(())
      }

      override def onComplete(): Unit = {
        // Assuming that onComplete is a way to signal success also
        if (!promise.isDefined) {
          promise.setValue(())
        }
      }
    }
    pub.subscribe(sub)
    sub.promise
  }

  def forEachResult[T](pub: Publisher[T], consume: T => Unit): Future[Unit] = {
    val sub = new TwitterBaseSubscriber[T, Unit] {
      override def onNext(t: T): Unit = {
        consume(t)
        subscription.request(1)
      }

      override def onComplete(): Unit = {
        promise.setValue(())
      }
    }
    pub.subscribe(sub)
    sub.promise
  }

  def seqResult[T](op: Publisher[T]): Future[Seq[T]] = {
    val b = Vector.newBuilder[T]
    forEachResult[T](op, elem => b += elem).map(_ => b.result())
  }

  /** NOTE(jacob): There are a couple bits of magic happening here to make allocating
    *   this string as unnecessarily micro-optimized as possible:
    *
    *     - magic number 172: computed in the repl as the length of the static component
    *       of this string. The goal here is to allocate the backing array of the string
    *       builder to exactly the size it needs to render the entire string without
    *       resizing. There is a test case in TwitterAsyncUtilTest enforcing
    *       the expected string length which will need to be updated if the message here
    *       changes.
    *
    *     - the '+' in the third append call: this takes advantage of the jvm optimizing
    *       away the '+' operation for static strings, allowing us to keep line length in
    *       check here without forcing a fourth .append call
    */
  private[twitter] def getInterruptedMessage(threadName: String): String = {
    new JavaStringBuilder(threadName.length + 172)
      .append("Mongo client interrupted on thread '")
      .append(threadName)
      .append(
        "' during an async operation (see cause). This probably means Future " +
          "execution is leaking onto threads it shouldn't (eg. a timer thread)."
      )
      .toString
  }
}
