# coding=utf-8
# Copyright 2017 Foursquare Labs Inc. All Rights Reserved.

from __future__ import absolute_import, division, print_function

import abc
import functools
import sys

from pants.reporting.reporter import Reporter
from pants.subsystem.subsystem import Subsystem


def if_enabled(f):
  "Decorator to gate on self.opts.enabled."
  @functools.wraps(f)
  def f2(self, *args, **kwargs):
    if self.opts.enabled:
      return f(self, *args, **kwargs)
  return f2


class SpanReporter(Reporter, Subsystem):
  "Common logic for external span reporting (zipkin / jaeger)."
  # note: Reporter comes before Subsystem because we want Reporter's __init__ and
  #   Subsystem's classmethods.

  # note: this gets written by ./register.py at startup
  GLOBAL_OPTIONS_INSTANCE = None

  @classmethod
  def register_options(cls, register):
    super(SpanReporter, cls).register_options(register)
    register(
      '--enabled',
      type=bool,
      default=False,
      help="If False we won't try to upload spans.",
    )

  def __init__(self, *args, **kwargs):
    super(SpanReporter, self).__init__(*args, **kwargs)
    self.workunit_to_span = {}
    # note: self.get_options() is broken by weird MRO.
    self.opts = self.GLOBAL_OPTIONS_INSTANCE.for_scope(self.options_scope)

  def close(self):
    assert not self.workunit_to_span, "{} stack not clean at exit".format(self.__class__.__name__)

  @if_enabled
  def start_workunit(self, workunit):
    parent_span = self.workunit_to_span[workunit.parent] if workunit.parent else None
    self.workunit_to_span[workunit] = self.mkspan(
      workunit.name,
      parent=parent_span,
      tags={} if workunit.parent else {
        'argv': sys.argv,
        'goal': sys.argv[1] if len(sys.argv) > 1 else 'unknown',
      },
    )

  @if_enabled
  def end_workunit(self, workunit):
    span = self.workunit_to_span.pop(workunit)
    assert self.span_name(span) == workunit.name, (self.span_name(span), workunit.name)
    self.stop_span(span)

  @abc.abstractmethod
  def mkspan(self, name, parent=None, tags={}):
    "Subclasses return a started span instance for their driver."
    raise NotImplementedError

  @staticmethod
  @abc.abstractmethod
  def span_name(span):
    "Subclasses return span name."
    raise NotImplementedError

  @staticmethod
  @abc.abstractmethod
  def stop_span(span):
    "Subclasses close i.e. end a span."
    raise NotImplementedError
