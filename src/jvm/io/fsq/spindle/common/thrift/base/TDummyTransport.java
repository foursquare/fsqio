//  Copyright 2011 Foursquare Labs Inc. All Rights Reserved

package io.fsq.spindle.common.thrift.base;

import org.apache.thrift.transport.TTransport;
import org.apache.thrift.transport.TTransportException;


/**
 * TProtocol subclasses that don't use a transport can pass to the parent constructor, so we get a sensible error,
 * rather than a NullPointerException, if someone attempts to use the transport.
 */
public class TDummyTransport extends TTransport {
  @Override
  public boolean isOpen() {
    error();
    return false;
  }

  @Override
  public void open() throws TTransportException {
    error();
  }

  @Override
  public void close() {
    error();
  }

  @Override
  public int read(byte[] buf, int off, int len) throws TTransportException {
    error();
    return 0;
  }

  @Override
  public void write(byte[] buf, int off, int len) throws TTransportException {
    error();
  }

  private void error() {
    throw new UnsupportedOperationException("Must not use TDummyTransport");
  }
}
