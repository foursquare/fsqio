// Copyright 2014 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.spindle.common.thrift.base;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.EmptyStackException;
import java.util.Stack;
import javax.xml.bind.DatatypeConverter;

import org.apache.thrift.TBaseHelper;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TField;
import org.apache.thrift.protocol.TList;
import org.apache.thrift.protocol.TMap;
import org.apache.thrift.protocol.TMessage;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TProtocolFactory;
import org.apache.thrift.protocol.TSet;
import org.apache.thrift.protocol.TStruct;
import org.apache.thrift.protocol.TType;
import org.apache.thrift.transport.TTransport;


/**
 * Thrift protocol to write a string for display purposes. All read methods throw.
 */
public class TStringProtocol extends TProtocol implements SerializeDatesAsSeconds {

  private static final TMessage ANONYMOUS_MESSAGE = new TMessage();
  private static final TStruct ANONYMOUS_STRUCT = new TStruct();
  private static final short UNKNOWN_FIELD_ID = (short)-1;
  private static final byte UNKNOWN_TTYPE = TType.VOID;
  private static final TField NO_MORE_FIELDS = new TField("", TType.STOP, (short)0);

  private static final int MAP_CONTEXT = 1;
  private static final int LIST_CONTEXT = 2;

  // If true, binary values will be serialized as "Base64(\"..b64..\")" and ObjectIds will be serialized as "..oid..".
  // If false, binary values will be serialized as "..b64.." and ObjectIds will be serialized as "ObjectId(\"..oid..\")"
  //   where b64 is the base-64 ASCII representation of the binary value
  //     and oid is the hex-encoded ASCII representation of the ObjectId.
  private boolean bareObjectIds = false;

  private Writer osw = null;

  abstract class WriteContext {
    protected int items = 0;
    public int itemsRemaining() {
      return items;
    }
    public void decrementItemsRemaining() {
      items--;
    }
    public abstract String delimiter();
    public String structDelimiter() {
      return "";
    }
    public boolean isMapKey() {
      return false;
    }
    public void toggleMapKey() {}
  }

  class StructContext extends WriteContext {
    public String delimiter() {
      return "";
    }
    @Override
    public String structDelimiter() {
      if (items == 0)
        return "";
      else
        return ", ";
    }
  }

  class MapContext extends WriteContext {
    public MapContext(int n) {
      items = n;
    }

    private boolean _isMapKey = true;
    @Override
    public boolean isMapKey() {
      return _isMapKey;
    }

    @Override
    public void toggleMapKey() {
      _isMapKey = !_isMapKey;
    }

    public String delimiter() {
      if (_isMapKey)
        return ": ";
      else if (items > 1)
        return ", ";
      else
        return " ";
    }
  }

  class ListContext extends WriteContext {
    public ListContext(int n) {
      items = n;
    }
    public String delimiter() {
      if (items > 1)
        return ", ";
      else
        return " ";
    }
  }

  private void writeDelimiter(Writer osw) throws IOException {
    if (!writeContext.isEmpty()) {
      WriteContext ctx = writeContext.peek();
      osw.write(ctx.delimiter());
      ctx.toggleMapKey();
      ctx.decrementItemsRemaining();
    }
  }

  private void writeStructDelimiter(Writer osw) throws IOException {
    if (!writeContext.isEmpty()) {
      WriteContext ctx = writeContext.peek();
      osw.write(ctx.structDelimiter());
      ctx.toggleMapKey();
      ctx.decrementItemsRemaining();
    }
  }

  private boolean isMapKey() {
    if (writeContext.isEmpty()) {
      return false;
    }
    WriteContext ctx = writeContext.peek();
    return ctx.isMapKey();
  }

  private Stack<WriteContext> writeContext = new Stack<WriteContext>();

  // If writing a field, the value, if any, of the "enhanced_type" annotation
  // on that field. Unused otherwise.
  private String enhancedType = null;

  public TStringProtocol(TTransport trans) {
    this(trans, false);
  }

  public TStringProtocol(TTransport trans, boolean bareObjectIds) {
    super(trans);
    this.bareObjectIds = bareObjectIds;
  }

  private static byte[] toByteArray(ArrayList<Byte> input) {
    int size = input.size();
    byte[] result = new byte[size];
    for (int i = 0; i < size; i++) {
      result[i] = input.get(i);
    }
    return result;
  }

  /**
   * Factory class for creating TStringProtocol objects.
   */
  public static class Factory implements TProtocolFactory {
    private final boolean bareObjectIds;

    public Factory() {
      this(false);
    }

    public Factory(boolean bareObjectIds) {
      this.bareObjectIds = bareObjectIds;
    }

    @Override
    public TProtocol getProtocol(TTransport trans) {
      return new TStringProtocol(trans, bareObjectIds);
    }
  }

  // -----------------------------------
  // Implementation of TProtocol methods
  // -----------------------------------

  @Override
  public void reset() {
    enhancedType = null;
    osw = null;
    //writeContext = new Stack<WriteContext>();
  }

  private void wrapIOException(IOException e) throws TException {
    throw new TException("IOException: " + e.getMessage());
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
    if (osw == null) {
      // Set up writing.
      osw = new OutputStreamWriter(new TTransportOutputStream(getTransport()));
    }

    try {
      osw.write("{ ");
      writeContext.push(new StructContext());
    } catch (IOException e) {
      wrapIOException(e);
    }
  }

  @Override
  public void writeStructEnd() throws TException {
    try {
      osw.write(" }");
      osw.flush();
      writeContext.pop();
      writeDelimiter(osw);
    } catch (IOException e) {
      wrapIOException(e);
    }
  }

  @Override
  public void writeFieldBegin(TField tField) throws TException {
    try {
      writeStructDelimiter(osw);
      osw.write("\"");
      osw.write(tField.name);
      osw.write("\": ");
    } catch (IOException e) {
      wrapIOException(e);
    }

    if (tField instanceof EnhancedTField) {
      enhancedType = ((EnhancedTField)tField).enhancedTypes.get("bson");
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
    try {
      osw.write("{ ");
      writeContext.push(new MapContext(tMap.size*2));
    } catch (IOException e) {
      wrapIOException(e);
    }
  }

  @Override
  public void writeMapEnd() throws TException {
    try {
      osw.write("}");
      writeContext.pop();
      writeDelimiter(osw);
    } catch (IOException e) {
      wrapIOException(e);
    }
  }

  @Override
  public void writeListBegin(TList tList) throws TException {
    try {
      osw.write("[ ");
      writeContext.push(new ListContext(tList.size));
    } catch (IOException e) {
      wrapIOException(e);
    }
  }

  @Override
  public void writeListEnd() throws TException {
    try {
      osw.write("]");
      writeContext.pop();
      writeDelimiter(osw);
    } catch (IOException e) {
      wrapIOException(e);
    }
  }

  @Override
  public void writeSetBegin(TSet tSet) throws TException {
    try {
      osw.write("{ ");
      writeContext.push(new ListContext(tSet.size));
    } catch (IOException e) {
      wrapIOException(e);
    }
  }

  @Override
  public void writeSetEnd() throws TException {
    try {
      osw.write("}");
      writeContext.pop();
      writeDelimiter(osw);
    } catch (IOException e) {
      wrapIOException(e);
    }
  }

  @Override
  public void writeBool(boolean b) throws TException {
    try {
      osw.write(Boolean.toString(b));
      writeDelimiter(osw);
    } catch (IOException e) {
      wrapIOException(e);
    }
  }

  @Override
  public void writeByte(byte b) throws TException {
    try {
      osw.write(Byte.toString(b));
      writeDelimiter(osw);
    } catch (IOException e) {
      wrapIOException(e);
    }
  }

  @Override
  public void writeI16(short i) throws TException {
    try {
      osw.write(Short.toString(i));
      writeDelimiter(osw);
    } catch (IOException e) {
      wrapIOException(e);
    }
  }

  @Override
  public void writeI32(int i) throws TException {
    try {
      osw.write(Integer.toString(i));
      writeDelimiter(osw);
    } catch (IOException e) {
      wrapIOException(e);
    }
  }

  @Override
  public void writeI64(long l) throws TException {
    try {
      osw.write(Long.toString(l));
      writeDelimiter(osw);
    } catch (IOException e) {
      wrapIOException(e);
    }
  }

  @Override
  public void writeDouble(double v) throws TException {
    try {
      osw.write(Double.toString(v));
      writeDelimiter(osw);
    } catch (IOException e) {
      wrapIOException(e);
    }
  }

  @Override
  public void writeString(String s) throws TException {
    try {
      osw.write("\"");
      osw.write(s);
      osw.write("\"");
      writeDelimiter(osw);
    } catch (IOException e) {
      wrapIOException(e);
    }
  }

  @Override
  public void writeBinary(ByteBuffer bb) throws TException {
    try {
      byte[] arr = TBaseHelper.byteBufferToByteArray(bb);
      if (isMapKey()) {
        osw.write(toObjectIdString(arr));
      }
      else {
        if ("ObjectId".equals(enhancedType)) {
          osw.write(toObjectIdString(arr));
        } else {
          osw.write(toBase64String(arr));
        }
      }
      writeDelimiter(osw);
    } catch (IOException e) {
      wrapIOException(e);
    }
  }

  private String toObjectIdString(byte[] arr) {
    String hex = DatatypeConverter.printHexBinary(arr).toLowerCase();
    if (bareObjectIds) {
      return "\"" + hex + "\"";
    } else {
      return "ObjectId(\"" + hex + "\")";
    }
  }

  private String toBase64String(byte[] arr) {
    String b64 = DatatypeConverter.printBase64Binary(arr);
    if (bareObjectIds) {
      return "Base64(\"" + b64 + "\")";
    } else {
      return "\"" + b64 + "\"";
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
    throw new UnsupportedOperationException("TStringProtocol does not support reading");
  }

  @Override
  public void readStructEnd() throws TException {
  }

  @Override
  public TField readFieldBegin() throws TException {
    throw new UnsupportedOperationException("TStringProtocol does not support reading");
  }

  @Override
  public void readFieldEnd() throws TException {
  }

  @Override
  public TMap readMapBegin() throws TException {
    throw new UnsupportedOperationException("TStringProtocol does not support reading");
  }

  @Override
  public void readMapEnd() throws TException {
  }

  @Override
  public TList readListBegin() throws TException {
    throw new UnsupportedOperationException("TStringProtocol does not support reading");
  }

  @Override
  public void readListEnd() throws TException {
  }

  @Override
  public TSet readSetBegin() throws TException {
    throw new UnsupportedOperationException("TStringProtocol does not support reading");
  }

  @Override
  public void readSetEnd() throws TException {
  }

  @Override
  public boolean readBool() throws TException {
    throw new UnsupportedOperationException("TStringProtocol does not support reading");
  }

  @Override
  public byte readByte() throws TException {
    throw new UnsupportedOperationException("TStringProtocol does not support reading");
  }

  @Override
  public short readI16() throws TException {
    throw new UnsupportedOperationException("TStringProtocol does not support reading");
  }

  @Override
  public int readI32() throws TException {
    throw new UnsupportedOperationException("TStringProtocol does not support reading");
  }

  @Override
  public long readI64() throws TException {
    throw new UnsupportedOperationException("TStringProtocol does not support reading");
  }

  @Override
  public double readDouble() throws TException {
    throw new UnsupportedOperationException("TStringProtocol does not support reading");
  }

  @Override
  public String readString() throws TException {
    throw new UnsupportedOperationException("TStringProtocol does not support reading");
  }

  @Override
  public ByteBuffer readBinary() throws TException {
    throw new UnsupportedOperationException("TStringProtocol does not support reading");
  }
}
