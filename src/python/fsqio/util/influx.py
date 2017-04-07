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

def format_tags_or_fields(data):
  result = ','.join(
        '{key}={value}'.format(
          key=_backslash_escape(key, [',', ' ', '=']),
          value=_backslash_escape(value, [',', ' ', '=']),
        )
        for key, value in sorted(data.items())
      )
  return result

def build_influx_line(measurement, tags, fields, dt):
  """ An influx line consists of [key] [fields] [timestamp] separated by spaces.
  The key is the measurement name and any optional tags separated by commas.
  Fields are key-value metrics associated with the measurement.
  note, numbers not followed by 'i' will be stored as floats.
  The Timestamp value is an integer representing nanoseconds since the epoch
  eg:
  measurement[,tag_key1=tag_value1...] field_key=field_value[,field_key2=field_value2] [timestamp]
  """
  line_template = '{measurement},{tags} {fields} {timestamp}'
  return line_template.format(
    measurement=_backslash_escape(measurement, [',', ' ']),
    tags=format_tags_or_fields(tags),
    fields=format_tags_or_fields(fields),
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
