// Copyright 2012 Foursquare Labs Inc. All Rights Reserved.
package io.fsq.twofishes.indexer.mongo

import com.mongodb.casbah.MongoClient

object MongoIndexerConnection {
  def apply(): MongoClient = {
    Option(System.getProperty("mongodb.server")).map(_.split(":")).flatMap({
      case Array(host, port) =>
        Some(MongoClient(host, port.toInt))
      case _ => None
    }).getOrElse(MongoClient())
  }
}
