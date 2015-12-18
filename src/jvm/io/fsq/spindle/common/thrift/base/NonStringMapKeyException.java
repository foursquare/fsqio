package io.fsq.spindle.common.thrift.base;


import org.apache.thrift.TException;

public class NonStringMapKeyException extends TException {
  public NonStringMapKeyException(Object key) {
    super("Protocol requires string-typed map key, but got a " + key.getClass().getCanonicalName());
  }
}
