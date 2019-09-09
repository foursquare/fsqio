// Copyright 2013 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.exceptionator.model.io

import net.liftweb.json.{JArray, JValue, JsonAST}

object Outgoing {
  def pretty(outgoings: Seq[Outgoing]): String = {
    JsonAST.prettyRender(JArray(outgoings.toList.map(_.doc)))
  }

  def compact(outgoings: Seq[Outgoing]): String = {
    JsonAST.compactRender(JArray(outgoings.toList.map(_.doc)))
  }
}

trait Outgoing {
  def doc: JValue
  def pretty: String = JsonAST.prettyRender(doc)
  def compact: String = JsonAST.compactRender(doc)
}
