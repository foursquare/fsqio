//  Copyright 2011 Foursquare Labs Inc. All Rights Reserved

package io.fsq.spindle.common.thrift.bson;

import java.io.IOException;

import io.fsq.spindle.common.thrift.base.TTransportInputStream;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TProtocolFactory;
import org.apache.thrift.protocol.TStruct;
import org.apache.thrift.transport.TTransport;
import org.bson.BSONDecoder;
import org.bson.BSONObject;
import org.bson.BasicBSONDecoder;
import org.bson.BasicBSONEncoder;


/**
 * BSON protocol implementation for thrift. Not that useful, since the only known use of BSON is as MongoDB's
 * serialization format, and the MongoDB driver doesn't take raw BSON strings. However this class may come in handy
 * for testing etc.
 *
 * See TBSONObjectProtocol for implementation notes.
 */
public class TBSONProtocol extends TBSONObjectProtocol {
  // Our superclass uses a dummy transport, and we mediate its input/output from/to this transport.
  protected TTransport realTransport;

  /**
   * Factory for protocol objects used for serializing BSON.
   */

  public static class WriterFactory extends TBSONObjectProtocol.WriterFactoryForVanillaBSONObject
      implements TProtocolFactory {
    public TProtocol getProtocol(TTransport trans) {
      return super.doGetProtocol(trans);
    }

    protected TBSONObjectProtocol createBlankProtocol(TTransport trans) { return doCreateBlankProtocol(trans); }
  }

  /**
   * Factory for protocol objects used for deserializing BSON.
   */
  public static class ReaderFactory extends TBSONObjectProtocol.ReaderFactory implements TProtocolFactory {
    public TBSONObjectProtocol getProtocol(TTransport trans) {
      return super.doGetProtocol(trans);
    }

    protected TBSONObjectProtocol createBlankProtocol(TTransport trans) { return doCreateBlankProtocol(trans); }
  }

  // need this for hive. It uses reflection and expects a #Factory static class that implements TProtocolFactory
  public static class Factory extends ReaderFactory {}

  private static TBSONObjectProtocol doCreateBlankProtocol(TTransport trans) {
    TBSONProtocol ret = new TBSONProtocol();
    ret.realTransport = trans;
    return ret;
  }

  @Override
  public void writeStructEnd() throws TException {
    super.writeStructEnd();
    if (!inFlight()) {
      // We've finished writing the root struct, so dump out to the real transport.
      realTransport.write(new BasicBSONEncoder().encode(super.getOutput()));
    }
  }


  @Override
  public TStruct readStructBegin() throws TException {
    if (!inFlight()) {
      // This is the root struct, so first decode from the real transport.
      try {
        BSONDecoder decoder = new BasicBSONDecoder();
        BSONObject srcObject = decoder.readObject(new TTransportInputStream(realTransport));
        super.setSource(srcObject);
      } catch (IOException e) {
        throw new TException(e);
      }
    }
    return super.readStructBegin();
  }
}
