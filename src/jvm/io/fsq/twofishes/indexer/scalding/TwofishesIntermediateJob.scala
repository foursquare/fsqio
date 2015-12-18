// Copyright 2014 Foursquare Labs Inc. All Rights Reserved.
package io.fsq.twofishes.indexer.scalding

import com.twitter.scalding._
import io.fsq.twofishes.indexer.util.{SpindleSequenceFileSource, ThriftConverter}
import org.apache.hadoop.io.Writable

class TwofishesIntermediateJob(
  name: String,
  args: Args
) extends TwofishesJob(name, args) {

  def getJobOutputsAsTypedPipe[K <: Writable with Comparable[K]: Manifest, T <: ThriftConverter.TType: Manifest: TupleConverter](names: Seq[String]): TypedPipe[(K, T)] = {
    var pipe: TypedPipe[(K, T)] = TypedPipe.empty
    names.foreach(name => {
      val path = concatenatePaths(outputBaseDir, name)
      pipe = pipe ++ TypedPipe.from(SpindleSequenceFileSource[K, T](path))
    })
    pipe
  }
}
