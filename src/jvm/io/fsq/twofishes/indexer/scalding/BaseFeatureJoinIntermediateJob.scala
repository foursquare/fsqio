// Copyright 2014 Foursquare Labs Inc. All Rights Reserved.
package io.fsq.twofishes.indexer.scalding

import com.twitter.scalding._
import com.twitter.scalding.typed.TypedSink
import io.fsq.twofishes.indexer.util.{SpindleSequenceFileSource, ThriftConverter}
import org.apache.hadoop.io.Writable

class BaseFeatureJoinIntermediateJob[
  K <: Writable with Comparable[K]: Manifest,
  L <: ThriftConverter.TType: Manifest: TupleConverter,
  R <: ThriftConverter.TType: Manifest: TupleConverter,
  O <: ThriftConverter.TType: Manifest: TupleConverter
](
  name: String,
  leftSources: Seq[String],
  rightSources: Seq[String],
  joiner: (Grouped[K, L], Grouped[K, R]) => TypedPipe[(K, O)],
  args: Args
) extends TwofishesIntermediateJob(name, args) {

  val left = getJobOutputsAsTypedPipe[K, L](leftSources).group
  val right = getJobOutputsAsTypedPipe[K, R](rightSources).group
  
  val joined = joiner(left, right)

  joined.write(TypedSink[(K, O)](SpindleSequenceFileSource[K, O](outputPath)))
}
