// Copyright 2013 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.exceptionator.model.io

import net.liftweb.json.{JArray, JValue, render}

object Outgoing {
  def pretty(outgoings: List[Outgoing]) = {
    net.liftweb.json.pretty(render(JArray(outgoings.map(_.doc))))
  }

  def compact(outgoings: List[Outgoing]) = {
    net.liftweb.json.compact(render(JArray(outgoings.map(_.doc))))
  }
}


trait Outgoing {
  def doc: JValue
  def pretty = net.liftweb.json.pretty(render(doc))
  def compact = net.liftweb.json.compact(render(doc))
}
