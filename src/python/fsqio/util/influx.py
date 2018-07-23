# coding=utf-8
# Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

from __future__ import absolute_import, division, print_function

from datetime import datetime
import logging
import math

import pytz
import requests
import six


logger = logging.getLogger(__name__)

UTC_DATETIME_MIN = datetime.min.time().replace(tzinfo=pytz.UTC)


def _backslash_escape(value, to_escape):
  for char in to_escape:
    value = value.replace(char, b'\\{}'.format(char))
  return value


def datetime_to_nanos_since_utc_epoch(dt):
  seconds_since_epoch = int((dt - datetime(1970, 1, 1, tzinfo=pytz.UTC)).total_seconds())
  return seconds_since_epoch * 10 ** 9


def truncate_date_to_utc_midnight(date):
  return datetime.combine(date, UTC_DATETIME_MIN)


def format_tags_or_fields(data):
  result = b','.join(
    b'{key}={value}'.format(
      key=_backslash_escape(key, [',', ' ', '=']),
      value=_backslash_escape(value, [',', ' ', '=']),
    )
    for key, value in sorted(data.items())
  )
  return result


def influx_format(value):
  """Format fields in the way influx expects. Returns bytes

  String types must be double quoted or influx will interpret them as bools.
  Numbers not followed by 'i' are interpreted as floats,
  """
  if isinstance(value, six.integer_types):
    return b'{}i'.format(value)
  elif isinstance(value, six.text_type):
    return b'"{}"'.format(value.encode('utf-8'))
  elif isinstance(value, six.binary_type):
    # Check bytes are utf-8. Raise an error if they're not.
    value.decode('utf-8')
    return b'"{}"'.format(value)
  elif value is None:
    return b'0i'
  elif isinstance(value, float):
    if math.isnan(value) or math.isinf(value):
      raise ValueError
    return b'{}'.format(value)
  else:
    raise TypeError


def build_influx_line(measurement, tags, fields, dt):
  """ An influx line consists of [key] [fields] [timestamp] separated by spaces.
  The key is the measurement name and any optional tags separated by commas.
  Fields are key-value metrics associated with the measurement.
  The Timestamp value is an integer representing nanoseconds since the epoch
  eg:
  measurement[,tag_key1=tag_value1...] field_key=field_value[,field_key2=field_value2] [timestamp]
  """
  line_template = b'{measurement},{tags} {fields} {timestamp}'
  return line_template.format(
    measurement=_backslash_escape(measurement, [',', ' ']),
    tags=format_tags_or_fields(tags),
    fields=format_tags_or_fields(fields),
    timestamp=datetime_to_nanos_since_utc_epoch(dt),
  )


def write_lines_to_influx(influxdb_url, db, lines, session=None):
  session = session or requests.Session()
  logger.info("Send points to Influx")
  influx_data = '\n'.join(lines)
  response = session.post(
    url='{}/write'.format(influxdb_url),
    params={'db': db},
    data=influx_data,
  )
  if response.status_code != 200:
    print(response.content)
    response.raise_for_status()
