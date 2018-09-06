# coding=utf-8
# Copyright 2017 Foursquare Labs Inc. All Rights Reserved.

from __future__ import absolute_import, division, print_function, unicode_literals

from datetime import datetime
import unittest

from mock import MagicMock
import pytz
import requests

from fsqio.util.influx import (
  build_influx_line,
  datetime_to_nanos_since_utc_epoch,
  influx_format,
  truncate_date_to_utc_midnight,
  write_lines_to_influx,
)


class MockResponse(object):
  def __init__(self, status_code=200, content='content'):
    self.status_code = status_code
    self.content = content

  def raise_for_status(self):
    raise Exception


class TestInflux(unittest.TestCase):

  def setUp(self):
    self.timestamp = 1494588400
    self.timestamp_in_nanos = self.timestamp * 10 ** 9
    self.utc_dt = datetime(2017, 5, 12, 11, 26, 40, tzinfo=pytz.UTC)

  def test_datetime_to_nanos_since_utc_epoch(self):
    self.assertEqual(self.timestamp_in_nanos, datetime_to_nanos_since_utc_epoch(self.utc_dt))
    naive_dt = datetime.fromtimestamp(self.timestamp)
    with self.assertRaises(TypeError):
      datetime_to_nanos_since_utc_epoch(naive_dt)

  def test_truncate_date_to_utc_midnight(self):
    self.assertEqual(datetime(2017, 5, 12, 0, 0, tzinfo=pytz.UTC), truncate_date_to_utc_midnight(self.utc_dt))

  def test_influx_format(self):
    self.assertEqual(b'12i', influx_format(12))
    self.assertEqual('0.1', influx_format(0.1))
    with self.assertRaises(ValueError):
      influx_format(float('NaN'))
    with self.assertRaises(ValueError):
      influx_format(float('inf'))
    self.assertEqual(b'"aString"', influx_format('aString'))
    self.assertEqual(b'"aString"', influx_format(u'aString'))
    with self.assertRaises(UnicodeDecodeError):
      influx_format(b'\x80abc')
    with self.assertRaises(UnicodeDecodeError):
      influx_format(u'å'.encode('latin-1'))
    self.assertEqual(b'0i', influx_format(None))

  def test_build_influx_line(self):
    influx_line = build_influx_line(
      measurement='duration',
      tags={'some_tag': 'a_tag_value'},
      fields={
        'string': '"bar"',
        'int': '42i',
        'float': '1.2',
        'empty': '0i',
      },
      dt=self.utc_dt,
    )
    self.assertEqual(
      influx_line,
      'duration,some_tag=a_tag_value empty=0i,float=1.2,int=42i,string="bar" 1494588400000000000'
    )

  def test_write_lines_to_influx(self):
    good_session = requests.Session()
    good_session.post = MagicMock(return_value=MockResponse())
    write_lines_to_influx(
      session=good_session,
      lines=['not a real line', 'fakefakefakefake'],
      influxdb_url='influxdb_url',
      db='fakedb',
    )
    good_session.post.assert_called_once_with(
      url='influxdb_url/write',
      params={'db': u'fakedb'},
      data=u'not a real line\nfakefakefakefake',
    )
    bad_session = requests.session()
    bad_session.post = MagicMock(return_value=MockResponse(status_code=404))
    with self.assertRaises(Exception):
      write_lines_to_influx(
        session=bad_session,
        lines=['not a real line', 'fakefakefakefake'],
        influxdb_url='influxdb_url',
        db='fakedb',
      )

  def tearDown(self):
    pass
