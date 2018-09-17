// Copyright 2013 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.exceptionator.model.io

import net.liftweb.json.{JArray, JValue, render}

object Outgoing {
  def pretty(outgoings: Seq[Outgoing]) = {
    net.liftweb.json.pretty(render(JArray(outgoings.toList.map(_.doc))))
  }

  def compact(outgoings: Seq[Outgoing]) = {
    net.liftweb.json.compact(render(JArray(outgoings.toList.map(_.doc))))
  }
}

trait Outgoing {
  def doc: JValue
  def pretty = net.liftweb.json.pretty(render(doc))
  def compact = net.liftweb.json.compact(render(doc))
}
