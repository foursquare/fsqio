// Copyright 2012 Foursquare Labs Inc. All Rights Reserved.
package io.fsq.twofishes.indexer.mongo

import com.mongodb.casbah.MongoConnection

object MongoIndexerConnection {
  def apply() = {
    Option(System.getProperty("mongodb.server")).map(_.split(":")).flatMap({
      case Array(host, port) =>
        Some(MongoConnection(host, port.toInt))
      case _ => None
    }).getOrElse(MongoConnection())
  }
}
