# coding=utf-8
# Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

from __future__ import absolute_import

from datetime import datetime
import logging

import requests


logger = logging.getLogger(__name__)


def _backslash_escape(value, to_escape):
  for char in to_escape:
    value = value.replace(char, '\\{}'.format(char))
  return value


def datetime_to_nanos_since_epoch(dt):
  seconds_since_epoch = int((dt - datetime(1970, 1, 1)).total_seconds())
  return seconds_since_epoch * 10 ** 9


def truncate_date_to_midnight(date):
  return datetime.combine(date, datetime.min.time())


def build_influx_line(measurement, tags, value, dt):
  line_template = '{measurement},{tags} value={value}i {timestamp}'
  return line_template.format(
    measurement=_backslash_escape(measurement, [',', ' ']),
    tags=','.join(
      '{tag_key}={tag_value}'.format(
        tag_key=_backslash_escape(tag_key, [',', ' ', '=']),
        tag_value=_backslash_escape(tag_value, [',', ' ', '=']),
      )
      for tag_key, tag_value in sorted(tags.items())
    ),
    value=str(value),
    timestamp=datetime_to_nanos_since_epoch(dt),
  )


def write_lines_to_influx(influxdb_url, db, lines):
  logger.info("Send points to Influx")
  influx_data = '\n'.join(lines)
  response = requests.post(
    '{}/write'.format(influxdb_url),
    params={'db': db},
    data=influx_data,
  )
  response.raise_for_status()
