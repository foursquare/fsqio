# coding=utf-8
# Copyright 2017 Foursquare Labs Inc. All Rights Reserved.

from __future__ import absolute_import, print_function

import logging

from jaeger_client import Config
import opentracing

from fsqio.pants.tracing.span_reporter import SpanReporter


class JaegerReporter(SpanReporter):
  "Send spans to a jaeger instance."

  options_scope = 'jaeger-reporter'

  @classmethod
  def register_options(cls, register):
    super(JaegerReporter, cls).register_options(register)
    register('--host', default='localhost')
    register('--port', default=5775, type=int)

  def __init__(self, *args, **kwargs):
    super(JaegerReporter, self).__init__(*args, **kwargs)
    if self.opts.enabled:
      # TODO(awinter): document if this is creating global state.
      logging.getLogger('jaeger_tracing').setLevel(logging.WARNING)
      self.tracer = Config(
        config={
          'sampler': {'type': 'const', 'param': 1},
          'local_agent': {
            'reporting_host': self.opts.host,
            'reporting_port': self.opts.port,
          },
          'logging': False, # note: this doesn't seem to do anything
        },
        service_name='pants'
      ).initialize_tracer()

  def close(self):
    super(JaegerReporter, self).close()
    if self.opts.enabled:
      self.tracer.close()

  def mkspan(self, name, parent=None, tags={}):
    span = opentracing.start_child_span(parent, operation_name=name) \
      if parent \
      else self.tracer.start_span(operation_name=name)
    for k, v in tags.items():
      span.set_tag(k, v)
    return span

  @staticmethod
  def span_name(span):
    return span.operation_name

  @staticmethod
  def stop_span(span):
    span.finish()
