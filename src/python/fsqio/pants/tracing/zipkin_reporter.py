# coding=utf-8
# Copyright 2017 Foursquare Labs Inc. All Rights Reserved.

from __future__ import absolute_import, print_function

import functools

from py_zipkin.zipkin import create_attrs_for_span, zipkin_span
import requests

from fsqio.pants.tracing.span_reporter import SpanReporter


def http_transport(url, data):
  try:
    requests.post(
      url,
      data=data,
      headers={'Content-Type': 'application/x-thrift'},
      timeout=5,
    )
  except Exception as err:
    # note: we don't want to crash builds on this
    print('error uploading zipkin stats', err)

class ZipkinReporter(SpanReporter):
  "Send spans to a zipkin instance."

  options_scope = 'zipkin-reporter'

  @classmethod
  def register_options(cls, register):
    super(ZipkinReporter, cls).register_options(register)
    register(
      '--url',
      default='http://localhost:9411/api/v1/spans',
      help='Zipkin reporting URL.',
    )

  def mkspan(self, name, parent=None, tags={}):
    "Helper to create (& optionally start) zipkin spans."
    # TODO(awinter): support tags on zipkin
    span = zipkin_span(
      service_name='pants',
      span_name=name,
      transport_handler=functools.partial(http_transport, self.opts.url),
      zipkin_attrs=create_attrs_for_span() if not parent else None,
    )
    span.start()
    return span

  @staticmethod
  def span_name(span):
    "Subclasses return span name."
    return span.span_name

  @staticmethod
  def stop_span(span):
    span.stop()
