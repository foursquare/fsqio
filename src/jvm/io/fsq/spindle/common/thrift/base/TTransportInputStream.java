//  Copyright 2011 Foursquare Labs Inc. All Rights Reserved

package io.fsq.spindle.common.thrift.base;

import java.io.IOException;
import java.io.InputStream;

import org.apache.thrift.transport.TTransport;
import org.apache.thrift.transport.TTransportException;

/**
 * An InputStream adapter around a Thrift TTransport.
 */
public class TTransportInputStream extends InputStream {
  private TTransport trans;
  private byte[] buf = new byte[1];

  public TTransportInputStream(TTransport trans) {
    this.trans = trans;
  }

  @Override
  public int read() throws IOException {
    try {
      trans.readAll(buf, 0, 1);
      return buf[0];
    } catch (TTransportException e) {
      throw new IOException(e);
    }
  }

  @Override
  public int read(byte[] bytes, int off, int len) throws IOException {
    try {
      return trans.read(bytes, off, len);
    } catch (TTransportException e) {
      throw new IOException(e);
    }
  }
}
