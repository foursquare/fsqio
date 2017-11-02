# coding=utf-8
# Copyright 2017 Foursquare Labs Inc. All Rights Reserved.

from __future__ import absolute_import, print_function

import functools
import logging

from pants.bin.exiter import Exiter
from pants.goal.run_tracker import RunTracker
from pants.option.options import Options
from pants.reporting.reporter import Reporter

from fsqio.pants.tracing.jaeger_reporter import JaegerReporter
from fsqio.pants.tracing.span_reporter import SpanReporter
from fsqio.pants.tracing.zipkin_reporter import ZipkinReporter


def generic_decorator(f, pre=None, post=None):
  "pre() modifies args to f(), post() modifies return val."
  @functools.wraps(f)
  def wrapper(*args, **kwargs):
    if pre:
      args, kwargs = pre(*args, **kwargs)
    ret = f(*args, **kwargs)
    if post:
      ret = post(ret, *args, **kwargs)
    return ret
  return wrapper


def run_tracker_prestart(self, report):
  "Installs our reporters on global RunTracker."
  assert isinstance(self, RunTracker)
  # TODO(awinter): I think pants doesn't use stock levels, plus loglevel probably set somewhere
  report.add_reporter('zipkin', ZipkinReporter(self, Reporter.Settings(logging.INFO)))
  report.add_reporter('jaeger', JaegerReporter(self, Reporter.Settings(logging.INFO)))
  return (self, report), {}


def exit_options_pre(*args, **kwargs):
  "Registers the global Options instance as a class attribute on SpanReporter."
  _, options = args
  assert isinstance(options, Options)
  SpanReporter.GLOBAL_OPTIONS_INSTANCE = options
  return args, kwargs


def global_subsystems():
  "Register our reporter subclasses as subsystems (see comments for why), register init hooks."

  # TODO(awinter): injection = brittle & bad. If we don't upstream this plugin, we should at
  #   least upstream an easier way to hook reporting.
  Exiter.apply_options = generic_decorator(Exiter.apply_options, pre=exit_options_pre)
  RunTracker.start = generic_decorator(RunTracker.start, pre=run_tracker_prestart)

  # note: I made this a subsystem so it can use Options.for_scope.
  return [ZipkinReporter, JaegerReporter]
