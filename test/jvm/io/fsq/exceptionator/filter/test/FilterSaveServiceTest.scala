// Copyright 2013 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.exceptionator.filter.test

import com.twitter.finagle.Service
import com.twitter.util.Future
import io.fsq.exceptionator.filter.{BucketSpec, FilteredIncoming, FilteredSaveService, PreSaveFilter,
    ProcessedIncoming, Registry}
import io.fsq.exceptionator.model.io.Incoming
import org.junit.{Assert => A, Test}

class OrderedPreSaveFilter(id: String) extends PreSaveFilter {
  def register(registry: Registry): Unit = {}
  def apply(
    incoming: FilteredIncoming,
    service: Service[FilteredIncoming, ProcessedIncoming]
  ): Future[ProcessedIncoming] = {
    service(incoming.copy(tags = incoming.tags.map(_ + id)))
  }
}

class TestService extends Service[FilteredIncoming, ProcessedIncoming] {
  def apply(incoming: FilteredIncoming): Future[ProcessedIncoming] = {
    Future.value(ProcessedIncoming(
      None,
      incoming.incoming,
      incoming.tags,
      incoming.keywords,
      incoming.buckets))
  }
}

class FilteredSaveServiceTest {
  // Make sure that the service is built up in the proper order,
  // with the end of the PreSaveFilter list executing last
  // This allows the user to use filter ordering to observe
  // the transformations of earlier filters
  @Test
  def testFilterOrder(): Unit = {
    val incoming = FilteredIncoming(Incoming(
        Nil, Nil, Nil, Map.empty, Map.empty, "", "", None, None, None)).copy(tags = Set(""))

    val service = new FilteredSaveService(
      new TestService,
      List(
        new OrderedPreSaveFilter("1"),
        new OrderedPreSaveFilter("2"),
        new OrderedPreSaveFilter("3")),
      new Registry {
        def registerBucket(spec: BucketSpec): Unit = {}
      })

    A.assertEquals(Some(Set("123")), service(incoming).poll.flatMap(_.toOption.map(_.tags)))
  }
}
