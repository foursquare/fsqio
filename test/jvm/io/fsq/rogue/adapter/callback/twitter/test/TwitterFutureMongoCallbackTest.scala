// Copyright 2018 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.rogue.adapter.callback.twitter.test

import com.mongodb.MongoInterruptedException
import com.twitter.util.{Await, Future, Throw, Try}
import io.fsq.common.testing.matchers.{FoursquareMatchers => FM}
import io.fsq.rogue.RogueException
import io.fsq.rogue.adapter.callback.twitter.TwitterFutureMongoCallback
import org.junit.{Assert, Test}

class TwitterFutureMongoCallbackTest {
  @Test
  def testMongoInterruptExceptionWrapping(): Unit = {
    val testException = new MongoInterruptedException(
      "Knock, knock. Who's there? Interrupting cow. Interrupting cow wh- MOO!",
      new InterruptedException
    )
    val callback = new TwitterFutureMongoCallback[Integer]
    callback.onResult(null, testException)

    try {
      val value = Await.result(callback.result)
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

    val successCallback = new TwitterFutureMongoCallback[Integer]
    val successFuture = successCallback.result
    Assert.assertThat(successFuture.poll, FM.isNone[Try[Integer]])
    successCallback.onResult(testValue, null)
    Assert.assertEquals(testValue, Await.result(successFuture))

    val errorCallback = new TwitterFutureMongoCallback[Integer]
    val errorFuture = errorCallback.result
    Assert.assertThat(errorFuture.poll, FM.isNone[Try[Integer]])
    errorCallback.onResult(null, testException)
    Assert.assertEquals(Throw(testException), Await.result(errorFuture.liftToTry))

    // exceptions take precedence
    val bothCallback = new TwitterFutureMongoCallback[Integer]
    val bothFuture = bothCallback.result
    Assert.assertThat(bothFuture.poll, FM.isNone[Try[Integer]])
    bothCallback.onResult(testValue, testException)
    Assert.assertEquals(Throw(testException), Await.result(bothFuture.liftToTry))
  }
}
