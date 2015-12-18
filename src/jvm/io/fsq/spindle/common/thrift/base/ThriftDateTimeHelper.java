// Copyright 2013 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.spindle.common.thrift.base;

import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.TException;
import org.joda.time.DateTime;

public class ThriftDateTimeHelper {
  public static void write(TProtocol oprot, org.joda.time.DateTime value) throws TException {
    if (oprot instanceof SerializeDatesAsSeconds) {
      oprot.writeI64(value.getMillis() / 1000);
    } else {
      oprot.writeI64(value.getMillis());
    }
  }

  public static DateTime read(TProtocol iprot, long value) throws TException {
    if (iprot instanceof SerializeDatesAsSeconds) {
      return new org.joda.time.DateTime(value * 1000);
    } else {
      return new org.joda.time.DateTime(value);
    }
  }
}
