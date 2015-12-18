//  Copyright 2011 Foursquare Labs Inc. All Rights Reserved

package io.fsq.spindle.common.thrift.bson;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.Date;

import io.fsq.spindle.common.thrift.base.EnhancedTField;
import io.fsq.spindle.common.thrift.base.TDummyTransport;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import org.apache.thrift.TBaseHelper;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TField;
import org.apache.thrift.protocol.TList;
import org.apache.thrift.protocol.TMap;
import org.apache.thrift.protocol.TMessage;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TSet;
import org.apache.thrift.protocol.TStruct;
import org.apache.thrift.transport.TTransport;
import org.bson.BSONObject;
import org.bson.BasicBSONObject;
import org.bson.types.ObjectId;


/**
 * Thrift protocol to read and write to BSONObjects.
 *
 * BSON is a serialization protocol. Its primary use is as the data format for MongoDB. The MongoDB driver doesn't
 * take raw BSON. Rather, you provide it with an in-memory BSONObject representation of serialized BSON. Therefore,
 * to interoperate with Thrift we need to convert Thrift objects to/from BSONObjects.
 *
 * Rather than invent a new API for this, we piggyback on the standard Thrift serialization API, even though we're
 * not 'serializing' in the strict sense.
 *
 * Notes:
 *
 * 1. The BSON and Thrift type systems don't align precisely, so we handle the necessary conversions.
 *
 * 2. Since we use the field's name (or its "wire_name" annotation) in the BSON, and not the field id, changing field
 *    names will break wire compatibility, which would not be the case with other Thrift protocols.
 *
 * 3. Unfortunately the Thrift TProtocol interface encapsulates both read and write functionality, even though they
 *    are completely separate. We implement the reading and writing parts of this protocol separately, and delegate
 *    to those implementations in this class. So instance of TBSONObjectProtocol can only be used either for
 *    reading or for writing, but not for both.
 *
 * 4. If any ClassCastExceptions occur this indicates either invalid BSON or a serious bug in the Thrift libraries or
 *    the generated Thrift code. In these cases we throw a TException with details of the unexpected context mismatch.
 *
 * 5. If you actually do want to serialize/deserialize raw BSON, see the TBSONProtocol subclass.
 *
 * See http://bsonspec.org/ and http://www.mongodb.org/display/DOCS/BSON for details on BSON.
 */
public class TBSONObjectProtocol extends TProtocol {

  // -----------------------------------------
  // Factories for writer and reader protocols
  // -----------------------------------------

  /**
   * Useful base class for the writer and reader factories.
   *
   * Note that this is not a subclass of TProtocolFactory, since we don't take a TTransport argument.
   */
  abstract static class Factory {
    public TBSONObjectProtocol getProtocol() {
      return doGetProtocol(null);  // We don't use a transport.
    }

    // The read and write factories implement this to set the protocol object up appropriately.
    protected abstract TBSONObjectProtocol doGetProtocol(TTransport trans);

    // Factories for TBSONObjectProtocol subclasses (E.g., TBSONProtocol) can provide an instance here.
    protected TBSONObjectProtocol createBlankProtocol(TTransport trans) {
      return new TBSONObjectProtocol();  // We ignore the transport, as we always write to a dummy.
    }
  }

  /**
   * Factory for protocol objects used for writing to BSONObjects.
   *
   * B is the type of the BSONObject instances we create for the top-level document and all its subdocuments.
   */
  public static class WriterFactory<B extends BSONObject> extends Factory {
    private BSONObjectFactory<B> bsonObjectFactory;

    public WriterFactory(BSONObjectFactory<B> bsonObjectFactory) {
      this.bsonObjectFactory = bsonObjectFactory;
    }

    protected TBSONObjectProtocol doGetProtocol(TTransport trans) {
      // Set up the protocol for writing.
      TBSONObjectProtocol ret = createBlankProtocol(trans);
      ret.writeState = new BSONWriteState<B>(bsonObjectFactory);
      return ret;
    }
  }

  /**
   * Factory for protocol objects used for writing to MongoDB DBObjects.
   *
   * Use this factory if you plan to save the resulting objects to MongoDB.
   */
  public static class WriterFactoryForDBObject extends WriterFactory<DBObject> {
    public WriterFactoryForDBObject() {
      super(new BSONObjectFactory<DBObject>() { public DBObject create() { return new BasicDBObject(); } });
    }
  }

  /**
   * Factory for protocol objects used for writing to vanilla BSONObjects.
   *
   * Use this factory if you don't want any dependency on MongoDB classes.
   */
  public static class WriterFactoryForVanillaBSONObject extends WriterFactory<BSONObject> {
    public WriterFactoryForVanillaBSONObject() {
      super(new BSONObjectFactory<BSONObject>() { public BSONObject create() { return new BasicBSONObject(); } });
    }
  }

  /**
   * Factory for protocol objects used for reading from BSONObjects.
   */
  public static class ReaderFactory extends Factory {

    protected TBSONObjectProtocol doGetProtocol(TTransport trans) {
      // Set up the protocol for reading.
      TBSONObjectProtocol ret = createBlankProtocol(trans);
      ret.readState = new BSONReadState();
      return ret;
    }
  }


  // Are only read, never written, so can be used concurrently.
  private static final TMessage ANONYMOUS_MESSAGE = new TMessage();
  private static final TStruct ANONYMOUS_STRUCT = new TStruct();

  // Exactly one of these is set at any one time, depending on which factory was used to create this instance.
  private BSONWriteState writeState = null;
  private BSONReadState readState = null;

  // If writing a field, the value, if any, of the "enhanced_type" annotation on that field. Unused otherwise.
  private String enhancedType = null;

  protected TBSONObjectProtocol() {
    super(new TDummyTransport());
  }


  // ------------------------------------------------------------
  // Non-standard methods to interact with this specific protocol
  // ------------------------------------------------------------

  /**
   * Set the source object to read from.
   *
   * @param srcObject Read from this object.
   * @throws TException if this object was not created for reading, or if we're in the middle of reading.
   */
  public void setSource(BSONObject srcObject) throws TException {
    if (readState == null) {
      throw new TException("Can't set source on object created for writing");
    }
    readState.setSource(srcObject);
  }

  /**
   * Get the object we've written to.
   * @return The result of writing to a BSONObject.
   * @throws TException if this object was not created for writing, or if we're in the middle of writing.
   */
  public BSONObject getOutput() throws TException {
    if (writeState == null) {
      throw new TException("Can't get output from object created for reading");
    }
    return writeState.getOutput();
  }

  protected boolean inFlight() {
    if (writeState != null) {
      return writeState.inFlight();
    } else if (readState != null) {
      return readState.inFlight();
    } else {
      throw new RuntimeException("Logic error: TBSONObjectProtocol set up neither for reading nor for writing.");
    }
  }


  // -----------------------------------
  // Implementation of TProtocol methods
  // -----------------------------------

  // These all delegate to the writeState/readState.

  @Override
  public void reset() {
    if (writeState != null) {
      writeState.reset();
    }
    if (readState != null) {
      readState.reset();
    }
    enhancedType = null;
  }


  // Write methods
  // -------------

  @Override
  public void writeMessageBegin(TMessage tMessage) throws TException {
  }

  @Override
  public void writeMessageEnd() throws TException {
  }

  @Override
  public void writeStructBegin(TStruct tStruct) throws TException {
    if (writeState == null) {
      throw new TException("Can't write on a read protocol.");
    }
    writeState.pushDocumentWriteContext();
  }

  @Override
  public void writeStructEnd() throws TException {
    writeState.popWriteContext();
  }

  @Override
  public void writeFieldBegin(TField tField) throws TException {
    writeState.putValue(tField.name);
    if (tField instanceof EnhancedTField) {
      enhancedType = ((EnhancedTField)tField).enhancedTypes.get("bson");
    } else {
      enhancedType = null;
    }
  }

  @Override
  public void writeFieldEnd() throws TException {
  }

  @Override
  public void writeFieldStop() throws TException {
  }

  @Override
  public void writeMapBegin(TMap tMap) throws TException {
    writeState.pushDocumentWriteContext();
  }

  @Override
  public void writeMapEnd() throws TException {
    writeState.popWriteContext();
  }

  @Override
  public void writeListBegin(TList tList) throws TException {
    writeState.pushArrayWriteContext(tList.size);
  }

  @Override
  public void writeListEnd() throws TException {
    writeState.popWriteContext();
  }

  @Override
  public void writeSetBegin(TSet tSet) throws TException {
    writeState.pushArrayWriteContext(tSet.size);    // BSON has no native set type, so we use an array.
  }

  @Override
  public void writeSetEnd() throws TException {
    writeState.popWriteContext();
  }

  @Override
  public void writeBool(boolean b) throws TException {
    writeState.putValue(b);
  }

  @Override
  public void writeByte(byte b) throws TException {
    writeState.putValue((int)b);  // BSON has no native byte type, so we use an int32.
  }

  @Override
  public void writeI16(short i) throws TException {
    writeState.putValue((int)i);  // BSON has no native short type, so we use an int32.
  }

  @Override
  public void writeI32(int i) throws TException {
    writeState.putValue(i);
  }

  @Override
  public void writeI64(long l) throws TException {
    if ("DateTime".equals(enhancedType)) {
      writeState.putValue(new Date(l));
    } else {
      writeState.putValue(l);
    }
  }

  @Override
  public void writeDouble(double v) throws TException {
    writeState.putValue(v);
  }

  @Override
  public void writeString(String s) throws TException {
    writeState.putValue(s);
  }

  @Override
  public void writeBinary(ByteBuffer byteBuffer) throws TException {
    byte[] arr = TBaseHelper.rightSize(byteBuffer).array();
    if ("ObjectId".equals(enhancedType)) {
      writeState.putValue(new ObjectId(arr));
    } else {
      writeState.putValue(arr);
    }
  }


  // Read methods
  // ------------

  @Override
  public TMessage readMessageBegin() throws TException {
    return ANONYMOUS_MESSAGE;
  }

  @Override
  public void readMessageEnd() throws TException {
  }

  @Override
  public TStruct readStructBegin() throws TException {
    if (readState == null) {
      throw new TException("Can't read on a write protocol.");
    }
    readState.readStructBegin();
    return ANONYMOUS_STRUCT;
  }

  @Override
  public void readStructEnd() throws TException {
    readState.readEnd();
  }

  @Override
  public TField readFieldBegin() throws TException {
    return readState.nextField();
  }

  @Override
  public void readFieldEnd() throws TException {
  }

  @Override
  public TMap readMapBegin() throws TException {
    return readState.readMapBegin();
  }

  @Override
  public void readMapEnd() throws TException {
    readState.readEnd();
  }

  @Override
  public TList readListBegin() throws TException {
    return readState.readListBegin();
  }

  @Override
  public void readListEnd() throws TException {
    readState.readEnd();
  }

  @Override
  public TSet readSetBegin() throws TException {
    return readState.readSetBegin();
  }

  @Override
  public void readSetEnd() throws TException {
    readState.readEnd();
  }

  @Override
  public boolean readBool() throws TException {
    try {
      return (Boolean)readState.getNextItem();
    } catch (ClassCastException e) {
      throw new TException("Expected Boolean value.");
    }
  }

  @Override
  public byte readByte() throws TException {
    try {
      return ((Integer)readState.getNextItem()).byteValue();
    } catch (ClassCastException e) {
      throw new TException("Expected Byte value.");
    }
  }

  @Override
  public short readI16() throws TException {
    try {
      return ((Integer)readState.getNextItem()).shortValue();
    } catch (ClassCastException e) {
      throw new TException("Expected Short value.");
    }
  }

  @Override
  public int readI32() throws TException {
    try {
      return (Integer)readState.getNextItem();
    } catch (ClassCastException e) {
      throw new TException("Expected Integer value.");
    }
  }

  @Override
  public long readI64() throws TException {
    Object item = readState.getNextItem();
    if (item instanceof Long) {
      return (Long)item;
    } else if (item instanceof Integer) {
      // This is unfortunately necessary because liftweb, for reasons too annoying to repeat here, writes long fields
      // as i32 BSON fields if their value is within the 32-bit range.
      return ((Integer)item).longValue();
    } else if (item instanceof Date) {
      return ((Date)item).getTime();
    } else {
      throw new TException("Cannot convert item to long: " + item);
    }
  }

  @Override
  public double readDouble() throws TException {
    try {
      return (Double)readState.getNextItem();
    } catch (ClassCastException e) {
      throw new TException("Expected Double value.");
    }
  }

  @Override
  public String readString() throws TException {
    try {
      Object item = readState.getNextItem();
      if (item instanceof String) {
        return (String)item;
      } else if (item instanceof byte[]) {
        // A string field unknown to an older version of a struct will be serialized as binary.
        try {
          return new String((byte[])item, "UTF8");
        } catch (UnsupportedEncodingException e) {
          throw new TException(e);
        }
      } else {
        throw new TException("Can't convert type to String: " + item.getClass().getName());
      }
    } catch (ClassCastException e) {
      throw new TException("Expected String value.");
    }
  }

  @Override
  public ByteBuffer readBinary() throws TException {
    byte[] bin;
    Object item = readState.getNextItem();
    if (item instanceof ObjectId) {
      bin = ((ObjectId)item).toByteArray();
    } else if (item instanceof byte[]) {
      bin = (byte[])item;
    } else if (item instanceof String) {
      // Thrift implicitly expects to be able to read strings as binary where needed. This capability is used
      // in TProtocolUtil.skip() and when reading unknown string fields.
      try {
        bin = ((String)item).getBytes("UTF8");
      } catch (UnsupportedEncodingException e) {
        throw new TException(e);
      }
    } else {
      throw new TException("Can't convert type to byte[]: " + item.getClass().getName());
    }
    return ByteBuffer.wrap(bin);
  }
}
