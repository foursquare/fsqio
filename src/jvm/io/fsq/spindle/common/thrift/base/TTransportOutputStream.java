//  Copyright 2012 Foursquare Labs Inc. All Rights Reserved

package io.fsq.spindle.common.thrift.base;

import java.io.IOException;
import java.io.OutputStream;

import org.apache.thrift.transport.TTransport;
import org.apache.thrift.transport.TTransportException;

/**
 * An OutputStream adapter around a Thrift TTransport.
 */
public class TTransportOutputStream extends OutputStream {
  private TTransport trans;
  private byte[] buf = new byte[1];

  public TTransportOutputStream(TTransport trans) {
    this.trans = trans;
  }

  @Override
  public void write(int b) throws IOException {
    try {
      buf[0] = (byte)b;
      trans.write(buf);
    } catch (TTransportException e) {
      throw new IOException(e);
    }
  }

  @Override
  public void write(byte[] bytes) throws IOException {
    try {
      trans.write(bytes, 0, bytes.length);
    } catch (TTransportException e) {
      throw new IOException(e);
    }
  }

  @Override
  public void write(byte[] bytes, int off, int len) throws IOException {
    try {
      trans.write(bytes, off, len);
    } catch (TTransportException e) {
      throw new IOException(e);
    }
  }
}
