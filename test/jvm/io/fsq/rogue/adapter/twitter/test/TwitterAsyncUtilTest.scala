// Copyright 2018 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.rogue.adapter.twitter.test

import com.mongodb.MongoInterruptedException
import com.twitter.util.{Await, Future, Throw, Try}
import io.fsq.common.testing.matchers.{FoursquareMatchers => FM}
import io.fsq.rogue.RogueException
import io.fsq.rogue.adapter.twitter.TwitterBaseSubscriber
import org.hamcrest.MatcherAssert
import org.junit.{Assert, Test}

class TwitterAsyncUtilTest {
  // Handles a single element
  class TwitterSingleElemSubscriber[T] extends TwitterBaseSubscriber[T, T] {
    override def onNext(t: T): Unit = {
      promise.setValue(t)
    }
  }

  @Test
  def testMongoInterruptExceptionWrapping(): Unit = {
    val testException = new MongoInterruptedException(
      "Knock, knock. Who's there? Interrupting cow. Interrupting cow wh- MOO!",
      new InterruptedException
    )
    val callback = new TwitterSingleElemSubscriber[Integer]
    callback.onError(testException)

    try {
      val value = Await.result(callback.promise)
      Assert.fail(s"Expected failed Future, but was successful with value '$value'")
    } catch {
      case re: RogueException => {
        // Test message length -- this should match the magic value used in
        // getInterruptedMessage. Any change the message length should also update the
        // capacity of the StringBuilder there!
        Assert.assertEquals(Thread.currentThread.getName.length + 172, re.getMessage.length)
        Assert.assertSame(testException, re.getCause)
      }

      case other: Throwable => {
        Assert.fail(s"Expected RogueException but was ${other.getClass.getName}")
      }
    }
  }

  @Test
  def testResultEncoding(): Unit = {
    val testValue = 24601
    val testException = new Exception("This is fine.")

    val successCallback = new TwitterSingleElemSubscriber[Integer]
    val successFuture = successCallback.promise
    MatcherAssert.assertThat(successFuture.poll, FM.isNone[Try[Integer]])
    successCallback.onNext(testValue)
    Assert.assertEquals(testValue, Await.result(successFuture))

    val errorCallback = new TwitterSingleElemSubscriber[Integer]
    val errorFuture = errorCallback.promise
    MatcherAssert.assertThat(errorFuture.poll, FM.isNone[Try[Integer]])
    errorCallback.onError(testException)
    Assert.assertEquals(Throw(testException), Await.result(errorFuture.liftToTry))
  }
}
