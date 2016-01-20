// Copyright 2012 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.spindle.common.thrift.json;

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
import org.codehaus.jackson.Base64Variants;
import org.codehaus.jackson.JsonFactory;
import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.JsonToken;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.PrettyPrinter;
import org.codehaus.jackson.SerializableString;
import org.codehaus.jackson.io.CharacterEscapes;
import org.codehaus.jackson.io.SerializedString;
import org.codehaus.jackson.util.DefaultPrettyPrinter;
import org.codehaus.jackson.util.TokenBuffer;

import io.fsq.spindle.common.thrift.base.EnhancedTField;
import io.fsq.spindle.common.thrift.base.NonStringMapKeyException;
import io.fsq.spindle.common.thrift.base.SerializeDatesAsSeconds;
import io.fsq.spindle.common.thrift.base.TTransportInputStream;
import io.fsq.spindle.common.thrift.base.TTransportOutputStream;


/**
 * Thrift protocol to read and write (optionally pretty) JSON.
 * Differs from Thrift's TJSONProtocol in that TReadableJSONProtocol
 * uses the actual field names instead of the raw field tag numbers.
 */
public class TReadableJSONProtocol extends TProtocol implements SerializeDatesAsSeconds {

  private static final TMessage ANONYMOUS_MESSAGE = new TMessage();
  private static final TStruct ANONYMOUS_STRUCT = new TStruct();
  private static final short UNKNOWN_FIELD_ID = (short)-1;
  private static final byte UNKNOWN_TTYPE = TType.VOID;
  private static final TField NO_MORE_FIELDS = new TField("", TType.STOP, (short)0);

  // Needed for writing and reading.
  private final JsonFactory jsonFactory;

  // For writing.
  private JsonGenerator jg = null;
  private final PrettyPrinter prettyPrinter;

  // If true, will convert all map keys to strings when writing them.
  // If false, it will be a runtime error to emit map keys that are not strings.
  // Usually this should be false. We set it to true only when using this protocol in
  // the implementation of toString() on a struct, because there's no expectation that
  // the result of toString() be parseable.
  private boolean coerceMapKeys = false;


  // If true, binary values will be serialized as "Base64(\"..b64..\")" and ObjectIds will be serialized as "..oid..".
  // If false, binary values will be serialized as "..b64.." and ObjectIds will be serialized as "ObjectId(\"..oid..\")"
  //   where b64 is the base-64 ASCII representation of the binary value
  //     and oid is the hex-encoded ASCII representation of the ObjectId.
  private boolean bareObjectIds = false;

  // If true number values will be serialized as "foo"
  // Javascript only supports 54 bit number so sending down longs (geoIds, dateTimes) will result in loss of precision and bugs
  private boolean numbersAsStrings = false;

  // For reading.
  private JsonParser jp = null;
  private final Stack<ReadContext> readContextStack = new Stack<ReadContext>();

  // If writing a field, the value, if any, of the "enhanced_type" annotation
  // on that field. Unused otherwise.
  private String enhancedType = null;

  public TReadableJSONProtocol(TTransport trans, PrettyPrinter pp) {
    this(trans, pp, null);
  }

  public TReadableJSONProtocol(TTransport trans, PrettyPrinter pp, JsonParser jp) {
    this(trans, pp, jp, false, false, false);
  }
  public TReadableJSONProtocol(TTransport trans, PrettyPrinter pp, JsonParser jp, boolean coerceMapKeys) {
    this(trans, pp, jp, coerceMapKeys, false, false);
  }
  public TReadableJSONProtocol(TTransport trans, PrettyPrinter pp, JsonParser jp, boolean coerceMapKeys, boolean bareObjectIds) {
    this(trans, pp, jp, coerceMapKeys, bareObjectIds, false);
  }
  public TReadableJSONProtocol(TTransport trans, PrettyPrinter pp, JsonParser jp, boolean coerceMapKeys, boolean bareObjectIds, boolean numbersAsStrings) {
    super(trans);

    prettyPrinter = pp;
    jsonFactory = new JsonFactory();
    jsonFactory.configure(JsonParser.Feature.ALLOW_UNQUOTED_CONTROL_CHARS, true);
    jsonFactory.setCharacterEscapes(new JsonCharacterEscapes());
    if (jp != null) {
      this.jp = jp;
    }
    this.coerceMapKeys = coerceMapKeys;
    this.bareObjectIds = bareObjectIds;
    this.numbersAsStrings = numbersAsStrings;
  }

  public static byte getElemTypeFromToken(JsonToken token) throws TException {
    byte valueType = UNKNOWN_TTYPE;
    switch (token) {
      case VALUE_TRUE:
      case VALUE_FALSE:
        valueType = TType.BOOL;
        break;
      case VALUE_NUMBER_FLOAT:
        valueType = TType.DOUBLE;
        break;
      case VALUE_NUMBER_INT:
        // If we return TType.I32 and we skip over a long field,
        // an exception is thrown.
        valueType = TType.I64;
        break;
      case VALUE_STRING:
        valueType = TType.STRING;
        break;
      case START_ARRAY:
        valueType = TType.LIST;
        break;
      case START_OBJECT:
        valueType = TType.STRUCT;
        break;
      case VALUE_NULL:
        // this is a total hack but seems to save us from errors about
        // VALUE_NULL, even on optional or unknown fields
        valueType = TType.STRING;
        break;
    }
    if (valueType == UNKNOWN_TTYPE) {
      throw new TException("Invalid token value type: " + token);
    }

    return valueType;
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
   * Factory class for creating TReadableJSONProtocol objects.
   */
  public static class Factory implements TProtocolFactory {
    private final boolean prettyPrint;
    private final boolean bareObjectIds;
    private boolean numbersAsStrings;
    private final JsonParser parser;

    public Factory() {
      this(false, false, false, null);
    }

    public Factory(boolean prettyPrint) {
      this(prettyPrint, false, false, null);
    }

    public Factory(boolean prettyPrint, JsonParser parser) {
      this(prettyPrint, false, false, parser);
    }

    public Factory(boolean prettyPrint, boolean bareObjectIds) {
      this(prettyPrint, bareObjectIds, false, null);
    }

    public Factory(boolean prettyPrint, boolean bareObjectIds, boolean numbersAsStrings) {
      this(prettyPrint, bareObjectIds, numbersAsStrings, null);
    }

    public Factory(boolean prettyPrint, boolean bareObjectIds, JsonParser parser) {
      this(prettyPrint, bareObjectIds, false, parser);
    }

    public Factory(boolean prettyPrint, boolean bareObjectIds, boolean numbersAsStrings, JsonParser parser) {
      this.prettyPrint = prettyPrint;
      this.bareObjectIds = bareObjectIds;
      this.numbersAsStrings = numbersAsStrings;
      this.parser = parser;
    }

    private boolean coerceMapKeys() {
      // prettyPrint should imply coerceMapKeys
      return prettyPrint;
    }

    private PrettyPrinter prettyPrinter() {
      return (prettyPrint ? new DefaultPrettyPrinter() : null);
    }

    @Override
    public TProtocol getProtocol(TTransport trans) {
      return new TReadableJSONProtocol(trans, prettyPrinter(), parser, coerceMapKeys(), bareObjectIds, numbersAsStrings);
    }
  }

  private void wrapIOException(IOException e) throws TException {
    throw new TException("IOException: " + e.getMessage());
  }

  private void wrapIOException(IOException e, Object val) throws TException {
    // This is a slightly hacky way to detect the non-string map key problem, but
    // alternatives are too heavyweight.
    if (e.getMessage().endsWith("expecting field name")) {
      throw new NonStringMapKeyException(val);
    } else {
      wrapIOException(e);
    }
  }

  // -----------------------------------
  // Implementation of TProtocol methods
  // -----------------------------------

  @Override
  public void reset() {
    enhancedType = null;
    jg = null;
    jp = null;
    readContextStack.clear();
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
    if (jg == null) {
      try {
        // Set up writing.
        Writer osw = new OutputStreamWriter(new TTransportOutputStream(getTransport()));
        jg = jsonFactory.createJsonGenerator(osw);
        if (prettyPrinter != null) {
          jg.setPrettyPrinter(prettyPrinter);
        }
      } catch (IOException e) {
        wrapIOException(e);
      }
    }

    try {
      jg.writeStartObject();
    } catch (IOException e) {
      wrapIOException(e);
    }
  }

  @Override
  public void writeStructEnd() throws TException {
    try {
      jg.writeEndObject();
      jg.flush();
    } catch (IOException e) {
      wrapIOException(e);
    }
  }

  @Override
  public void writeFieldBegin(TField tField) throws TException {
    try {
      jg.writeFieldName(tField.name);
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
      jg.writeStartObject();
    } catch (IOException e) {
      wrapIOException(e);
    }
  }

  @Override
  public void writeMapEnd() throws TException {
    try {
      jg.writeEndObject();
    } catch (IOException e) {
      wrapIOException(e);
    }
  }

  @Override
  public void writeListBegin(TList tList) throws TException {
    try {
      jg.writeStartArray();
    } catch (IOException e) {
      wrapIOException(e);
    }
  }

  @Override
  public void writeListEnd() throws TException {
    try {
      jg.writeEndArray();
    } catch (IOException e) {
      wrapIOException(e);
    }
  }

  @Override
  public void writeSetBegin(TSet tSet) throws TException {
    try {
      jg.writeStartArray();
    } catch (IOException e) {
      wrapIOException(e);
    }
  }

  @Override
  public void writeSetEnd() throws TException {
    try {
      jg.writeEndArray();
    } catch (IOException e) {
      wrapIOException(e);
    }
  }

  @Override
  public void writeBool(boolean b) throws TException {
    try {
      if (isMapKey()) {
        if (coerceMapKeys) {
          jg.writeFieldName(Boolean.toString(b));
        } else {
          throw new NonStringMapKeyException(b);
        }
      } else {
        jg.writeBoolean(b);
      }
    } catch (IOException e) {
      wrapIOException(e);
    }
  }

  @Override
  public void writeByte(byte b) throws TException {
    try {
      if (isMapKey()) {
        if (coerceMapKeys) {
          jg.writeFieldName(Byte.toString(b));
        } else {
          throw new NonStringMapKeyException(b);
        }
      } else {
        jg.writeNumber((int) b);
      }
    } catch (IOException e) {
      wrapIOException(e, b);
    }
  }

  @Override
  public void writeI16(short i) throws TException {
    try {
      if (isMapKey()) {
        if (coerceMapKeys) {
          jg.writeFieldName(Short.toString(i));
        } else {
          throw new NonStringMapKeyException(i);
        }
      } else {
        if (numbersAsStrings) {
          jg.writeString(Integer.toString((int)i));
        } else {
          jg.writeNumber((int)i);
        }
      }
    } catch (IOException e) {
      wrapIOException(e, i);
    }
  }

  @Override
  public void writeI32(int i) throws TException {
    try {
      if (isMapKey()) {
        if (coerceMapKeys) {
          jg.writeFieldName(Integer.toString(i));
        } else {
          throw new NonStringMapKeyException(i);
        }
      } else {
        if (numbersAsStrings) {
          jg.writeString(Integer.toString(i));
        } else {
          jg.writeNumber(i);
        }
      }
    } catch (IOException e) {
      wrapIOException(e, i);
    }
  }

  @Override
  public void writeI64(long l) throws TException {
    try {
      if (isMapKey()) {
        if (coerceMapKeys) {
          jg.writeFieldName(Long.toString(l));
        } else {
          throw new NonStringMapKeyException(l);
        }
      } else {
        if (numbersAsStrings) {
          jg.writeString(Long.toString(l));
        } else {
          jg.writeNumber(l);
        }
      }
    } catch (IOException e) {
      wrapIOException(e, l);
    }
  }

  @Override
  public void writeDouble(double v) throws TException {
    try {
      if (isMapKey()) {
        if (coerceMapKeys) {
          jg.writeFieldName(Double.toString(v));
        } else {
          throw new NonStringMapKeyException(v);
        }
      } else {
        if (numbersAsStrings) {
          jg.writeString(Double.toString(v));
        } else {
          jg.writeNumber(v);
        }
      }
    } catch (IOException e) {
      wrapIOException(e, v);
    }
  }

  @Override
  public void writeString(String s) throws TException {
    try {
      // Jackson's JsonGenerator requires that we know the difference between
      // outputting a string value and outputting a string that's the key in a
      // thrift map, but TProtocol uses writeString for both of these.
      if (isMapKey()) {
        jg.writeFieldName(s);
      } else {
        jg.writeString(s);
      }
    } catch (IOException e) {
      wrapIOException(e);
    }
  }

  @Override
  public void writeBinary(ByteBuffer bb) throws TException {
    try {
      byte[] arr = TBaseHelper.byteBufferToByteArray(bb);
      if (isMapKey()) {
        if (coerceMapKeys) {
          // TODO: We assume that map keys of binary type are object ids, because
          // those are the only such cases we have, or should ever want to have.
          // So they will always be written as "ObjectId(...)" even if the ellipsis
          // doesn't actually represent a 12-byte object id. We may want to
          // revisit this in the future, but coerced map keys are only for toString()
          // implementation anyway, so this shouldn't matter too much.
          // Note that it's not trivial to get the enhancedType of the map keys because
          // enhancedType is set at the field level, and the type of this field is map,
          // not ObjectId.
          jg.writeFieldName(toObjectIdString(arr));
        } else {
          throw new NonStringMapKeyException(arr);
        }
      } else {
        if ("ObjectId".equals(enhancedType)) {
          jg.writeString(toObjectIdString(arr));
        } else {
          jg.writeString(toBase64String(arr));
        }
      }
    } catch (IOException e) {
      wrapIOException(e, bb);
    }
  }

  private boolean isMapKey() {
    // VOODOO MAGIC: We're about to write a map field's key iff we're in an
    // object context and the current "name" (aka key) isn't set.
    return (jg.getOutputContext().inObject() && jg.getOutputContext().getCurrentName() == null);
  }

  private String toObjectIdString(byte[] arr) {
    String hex = DatatypeConverter.printHexBinary(arr).toLowerCase();
    if (bareObjectIds) {
      return hex;
    } else {
      return "ObjectId(\"" + hex + "\")";
    }
  }

  private String toBase64String(byte[] arr) {
    String b64 = DatatypeConverter.printBase64Binary(arr);
    if (bareObjectIds) {
      return "Base64(\"" + b64 + "\")";
    } else {
      return b64;
    }
  }

  private byte[] decodeBinaryString(String str) {
    try {
      if (bareObjectIds) {
        if (str.startsWith("Base64(\"") && str.endsWith("\")")) {
          String b64String = str.substring(8, str.length() - 2);
          return DatatypeConverter.parseBase64Binary(b64String);
        } else {
          return DatatypeConverter.parseHexBinary(str);
        }
      } else {
        if (str.startsWith("ObjectId(\"") && str.endsWith("\")")) {
          String b64String = str.substring(10, str.length() - 2);
          return DatatypeConverter.parseHexBinary(b64String);
        } else {
          return DatatypeConverter.parseBase64Binary(str);
        }
      }
    } catch (Exception e) {
      // Handle all kinds of exceptions that could happen by passing junk data into a parse method.
      return null;
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
    // Set up reading.
    if (jp == null) {
      try {
        jp = jsonFactory.createJsonParser(new TTransportInputStream(getTransport()));
      } catch(IOException e) {
        wrapIOException(e);
      }
    }

    if (readContextStack.empty()) {
      readContextStack.push(new ObjectReadContext(jp));
    } else {
      readContextStack.push(new ObjectReadContext(currentReadContext().parser()));
    }
    return ANONYMOUS_STRUCT;
  }

  @Override
  public void readStructEnd() throws TException {
    popCurrentReadContext();
  }

  @Override
  public TField readFieldBegin() throws TException {
    String key = (String)currentReadContext().getNextItem();
    if (key == null) {
      return NO_MORE_FIELDS;
    } else {
      byte valueTType = currentReadContext().valueTType();
      return new TField(key, valueTType, UNKNOWN_FIELD_ID);
    }
  }

  @Override
  public void readFieldEnd() throws TException {
  }

  private boolean areAllElementsEqual(byte[] array) {
    for (int i = 1; i < array.length; i++) {
      if (array[i-1] != array[i]) return false;
    }
    return true;
  }

  /**
   * IMPORTANT: Only string keyed maps are supported.
   **/
  @Override
  public TMap readMapBegin() throws TException {
    MapReadContext mapContext = new MapReadContext(currentReadContext().parser());
    readContextStack.push(mapContext);

    byte[] allTypes = mapContext.valueTTypes();
    byte valueType = (allTypes.length > 0 && areAllElementsEqual(allTypes)) ? allTypes[0] : UNKNOWN_TTYPE;
    return new TMap(TType.STRING, valueType, mapContext.mapSize());
  }

  @Override
  public void readMapEnd() throws TException {
    popCurrentReadContext();
  }

  @Override
  public TList readListBegin() throws TException {
    ArrayReadContext readContext = new ArrayReadContext(currentReadContext().parser());
    readContextStack.push(readContext);

    byte[] allTypes = readContext.tTypes();
    byte elemType = (allTypes.length > 0 && areAllElementsEqual(allTypes)) ? allTypes[0] : UNKNOWN_TTYPE;
    return new TList(elemType, readContext.listSize());
  }

  /**
   * Similar to readListBegin but returns an array with the type of each element in the list, instead of
   * returning a TList.
   * @return type of every element
   * @throws TException
   */
  public byte[] readListBeginEnhanced() throws TException {
    // Returns a list of the types of each element in the possibly heterogenous list
    ArrayReadContext readContext = new ArrayReadContext(currentReadContext().parser());
    readContextStack.push(readContext);
    return readContext.tTypes();
  }

  @Override
  public void readListEnd() throws TException {
    popCurrentReadContext();
  }

  @Override
  public TSet readSetBegin() throws TException {
    ArrayReadContext readContext = new ArrayReadContext(currentReadContext().parser());
    readContextStack.push(readContext);

    byte[] allTypes = readContext.tTypes();
    byte elemType = (allTypes.length > 0 && areAllElementsEqual(allTypes)) ? allTypes[0] : UNKNOWN_TTYPE;
    return new TSet(elemType, readContext.listSize());
  }

  @Override
  public void readSetEnd() throws TException {
    popCurrentReadContext();
  }

  @Override
  public boolean readBool() throws TException {
    JsonToken value = (JsonToken)currentReadContext().getNextItem();
    try {
      if (value != JsonToken.VALUE_TRUE &&
          value != JsonToken.VALUE_FALSE) {
        throw new TException("Expecting boolean field value");
      }
      return currentReadContext().parser().getValueAsBoolean();
    } catch (IOException e) {
      throw new TException(e);
    }
  }

  @Override
  public byte readByte() throws TException {
    JsonToken value = (JsonToken)currentReadContext().getNextItem();
    try {
      if (value != JsonToken.VALUE_NUMBER_INT) {
        throw new TException("Expecting int field value");
      }
      return (new Integer(currentReadContext().parser().getValueAsInt())).byteValue();
    } catch (IOException e) {
      throw new TException(e);
    }
  }

  @Override
  public short readI16() throws TException {
    JsonToken value = (JsonToken)currentReadContext().getNextItem();
    try {
      if (value != JsonToken.VALUE_NUMBER_INT) {
        throw new TException("Expecting int field value");
      }
      return (new Integer(currentReadContext().parser().getValueAsInt())).shortValue();
    } catch (IOException e) {
      throw new TException(e);
    }
  }

  @Override
  public int readI32() throws TException {
    JsonToken value = (JsonToken)currentReadContext().getNextItem();
    try {
      if (value != JsonToken.VALUE_NUMBER_INT) {
        throw new TException("Expecting int field value, got: " + value);
      }
      return currentReadContext().parser().getValueAsInt();
    } catch (IOException e) {
      throw new TException(e);
    }
  }

  @Override
  public long readI64() throws TException {
    JsonToken value = (JsonToken)currentReadContext().getNextItem();
    try {
      if (value != JsonToken.VALUE_NUMBER_INT) {
        throw new TException("Expecting int field value");
      }
      return currentReadContext().parser().getValueAsLong();
    } catch (IOException e) {
      throw new TException(e);
    }
  }

  @Override
  public double readDouble() throws TException {
    JsonToken value = (JsonToken)currentReadContext().getNextItem();
    try {
      if (value == JsonToken.VALUE_STRING) {
        String strValue = currentReadContext().parser().getText();
        try {
          return new Double(strValue);
        } catch (java.lang.NumberFormatException e) {
          throw new TException(e);
        }
      }
      if (value != JsonToken.VALUE_NUMBER_FLOAT && value != JsonToken.VALUE_NUMBER_INT) {
        throw new TException("Expecting float field value");
      }
      return currentReadContext().parser().getValueAsDouble();
    } catch (IOException e) {
      throw new TException(e);
    }
  }

  @Override
  public String readString() throws TException {
    JsonToken value = (JsonToken)currentReadContext().getNextItem();

    if (value == JsonToken.FIELD_NAME && currentReadContext().valueTType() == TType.MAP) {
      // This situation is okay, since we support string keyed maps, by using
      // JSON object field names.
    } else if (value == JsonToken.VALUE_NULL) {
      return null;
    } else if (value != JsonToken.VALUE_STRING) {
      throw new TException("Expecting string field value for:" + value);
    }
    try {
      return currentReadContext().parser().getText();
    } catch (IOException e) {
      throw new TException(e);
    }
  }

  @Override
  public ByteBuffer readBinary() throws TException {
    // Read binary is often used during a skip since we didn't provide the type.
    // Just consume a token.
    JsonToken value = (JsonToken)currentReadContext().getNextItem();
    if (value != JsonToken.VALUE_STRING) {
      return null;
    }
    try {
      String strValue = currentReadContext().parser().getText();
      byte byteArray[] = decodeBinaryString(strValue);
      if (byteArray == null) {
        return null;
      } else {
        return ByteBuffer.wrap(byteArray);
      }
    } catch (IOException e) {
      throw new TException(e);
    }
  }

  private ReadContext currentReadContext() throws TException {
    try {
      return readContextStack.peek();
    } catch (EmptyStackException e) {
      throw new TException("Can't read outside an object or array context");
    }
  }

  private ReadContext popCurrentReadContext() throws TException {
    try {
      ReadContext context = readContextStack.pop();
      context.contextPopped();
      // are we back at a context? we should tell it it's restored.
      if (readContextStack.size() > 0) {
        currentReadContext().contextRestored();
      }
      return context;
    } catch (EmptyStackException e) {
      throw new TException("Can't pop off an empty read context stack");
    }
  }

  /**
   * Encapsulates the current structure being read (e.g. objects or arrays)
   */
  private abstract static class ReadContext {
    /**
     * The JsonParser that can be used to read/parse fields as they're encountered.
     */
    abstract JsonParser parser();

    /**
     * The current value we're reading.
     */
    abstract Object getNextItem() throws TException;

    /**
     * Clean up after your context.
     */
    void contextPopped() throws TException {}

    /**
     * Welcome back.
     */
    void contextRestored() throws TException {}

    /**
     * TType of the current value.
     */
    abstract byte valueTType() throws TException;
  }

  /**
   * A ReadContext for reading out of a json object.
   */
  private static class ObjectReadContext extends ReadContext {
    private final JsonParser jsonParser;
    private String currentKey = null;
    private JsonToken currentValueToken = null;

    ObjectReadContext(JsonParser jp) throws TException {
      jsonParser = jp;
      // Okay, we gotta read a START_OBJECT!
      JsonToken currentToken = jsonParser.getCurrentToken();
      if (currentToken != JsonToken.START_OBJECT) {
        try {
          currentToken = jsonParser.nextToken();
        } catch (IOException e) {
          throw new TException(e);
        }
        if (currentToken != JsonToken.START_OBJECT) {
          throw new TException("Object read expecting start object, got: " + currentToken);
        }
      }
    }

    @Override
    JsonParser parser() { return jsonParser; }

    @Override
    void contextPopped() throws TException {
      // We gotta see an END_OBJECT
      JsonToken endToken = jsonParser.getCurrentToken();
      if (endToken != JsonToken.END_OBJECT) {
        throw new TException("Object read expecting end object, got: " + endToken);
      }
    }

    @Override
    void contextRestored() throws TException {
      // Clear any current state to move on to the next field.
      currentKey = null;
    }

    @Override
    Object getNextItem() throws TException {
      try {
        if (currentKey == null) {
          // Iterating to a field key.
          JsonToken currentKeyToken = jsonParser.nextToken();
          if (currentKeyToken == JsonToken.END_OBJECT) {
            // No more fields in the current object!
            currentKey = null;
          } else {
            currentKey = jsonParser.getCurrentName();
            currentValueToken = jsonParser.nextToken();

            if (currentKeyToken != JsonToken.FIELD_NAME) {
              throw new TException("Expecting token field name, got: " + currentKeyToken);
            }
            if (currentValueToken == null) {
              throw new TException("Expecting token value, got null");
            }
          }
          return currentKey;
        } else {
          // Iterating to the field's value.
          currentKey = null;
          return currentValueToken;
        }
      } catch (IOException e) {
        throw new TException(e);
      }
    }

    @Override
    byte valueTType() throws TException {
      return getElemTypeFromToken(currentValueToken);
    }
  }

  /**
   * A ReadContext for reading out of a json map.
   *
   */
  private static class MapReadContext extends ReadContext {
    private final JsonParser jsonParser;
    private final JsonParser bufferParser;
    private int fieldCount;
    private int valueCount;
    private final byte[] valueTypes;

    MapReadContext(JsonParser jp) throws TException {
      ArrayList<Byte> valueTypesArrayList = new ArrayList<Byte>();
      jsonParser = jp;
      JsonToken currentToken = jsonParser.getCurrentToken();

      if (currentToken != JsonToken.START_OBJECT) {
        // Just as in the object context, we move to the next token if it is not a start.
        try {
          currentToken = jsonParser.nextToken();
        } catch (IOException e) {
          throw new TException(e);
        }
        if (currentToken != JsonToken.START_OBJECT) {
          throw new TException("Map read expecting start map, got: " + currentToken);
        }
      }

      // To read a Map, we read-ahead to the end of the map and
      // count the number of fields in it. We copy all encountered tokens to a tokenbuffer
      // as we go and use that tokenbuffer for actual parsing.
      try {
        TokenBuffer buffer = new TokenBuffer(null);
        buffer.copyCurrentEvent(jsonParser);

        // Append elements to buffer while counting the number of elements in the object;
        valueCount = 0;
        fieldCount = 0;
        int level = 0;
        JsonToken bufferCurrentToken = null;
        boolean expectFieldName = true;
        boolean finished = false;

        // We will alternate between reading field names and values.
        while (!finished) {
          bufferCurrentToken = jsonParser.nextToken();
          buffer.copyCurrentEvent(jsonParser);
          if (expectFieldName) {
            switch (bufferCurrentToken) {
              // Expected end condition.
              case END_OBJECT:
                if (level == 0) finished = true;
              break;
              // Expected beginning of a "fieldName": *value* pair.
              case FIELD_NAME:
                fieldCount++;
              break;
              default:
                throw new TException("Map read expected field name, got: " + bufferCurrentToken);
            }
            expectFieldName = false;
          } else {
            if (level == 0 &&
                bufferCurrentToken != JsonToken.END_ARRAY &&
                bufferCurrentToken != JsonToken.END_OBJECT) {
              valueTypesArrayList.add(getElemTypeFromToken(bufferCurrentToken));
            }
            switch (bufferCurrentToken) {
              case START_OBJECT:
                if (level == 0) valueCount++;
                level++;
                break;
              case START_ARRAY:
                if (level == 0) valueCount++;
                level++;
                break;
              case END_OBJECT:
                level--;
                break;
              case END_ARRAY:
                level--;
               break;
              default:
                if (level == 0) valueCount++;
            }
            if (level == 0) { // If we have not started a nested {list, object}, we expect a field.
              expectFieldName = true;
            }
          }
        }

        valueTypes = toByteArray(valueTypesArrayList);

        // Set up the parser that users of this class will be reading from.
        bufferParser = buffer.asParser();
        bufferParser.nextToken(); // Skip to start array (next call to nextToken will return start)
      } catch (IOException e) {
        throw new TException(e);
      }

      if (fieldCount != valueCount) {
        throw new TException("Map read got different number of fields than values: " +
            fieldCount + " != " +valueCount);
      }
    }


    @Override
    JsonParser parser() { return bufferParser; }

    int mapSize() throws TException {
      return fieldCount;
    }

    byte[] valueTTypes() {
      return valueTypes;
    }

    @Override
    Object getNextItem() throws TException {
      try {
        JsonToken nextToken = bufferParser.nextToken();
        return nextToken;
      } catch (IOException e) {
        throw new TException(e);
      }
    }

    @Override
    byte valueTType() throws TException {
      return TType.MAP;
    }
  }


  /**
   * A ReadContext for reading out of a json array.
   * This read context needs to return the length of the array up front (so a TList
   * of the appropriate length can be created) so we have to do some gymnastics
   * to make that possible.
   */
  private static class ArrayReadContext extends ReadContext {
    private final JsonParser jsonParser;
    private final JsonParser bufferParser;
    private final byte[] elemTypes;

    /* NOTES:
     * This routine is funky since we handle TWO cases that the array starts EITHER at currentToken() or nextToken()
     *  (1) If currentToken() is START_ARRAY, then the parser is not advanced and array should begin on nextToken()
     *  (2) Otherwise the parser is advanced one and the invariant in (1) should hold.
     */
    ArrayReadContext(JsonParser jp) throws TException {
      ArrayList<Byte> elemTypesArrayList = new ArrayList<Byte>();
      jsonParser = jp;
      JsonToken currentToken = jsonParser.getCurrentToken();

      if (currentToken != JsonToken.START_ARRAY) {
        // Just as in the object context, we move to the next token if it is not a start.
        try {
          currentToken = jsonParser.nextToken();
        } catch (IOException e) {
          throw new TException(e);
        }
        if (currentToken != JsonToken.START_ARRAY) {
          throw new TException("Array read expecting start array, got: " + currentToken);
        }
      }

      // To read an array we go ahead and read-ahead to the end of the array and
      // save the type of each element. We copy all encountered tokens to a tokenbuffer
      // as we go and use that tokenbuffer for actual parsing.
      try {
        TokenBuffer buffer = new TokenBuffer(null);
        buffer.copyCurrentEvent(jsonParser);

        // Append elements to buffer while counting the number of elements in the array.
        int level = 0;
        JsonToken bufferCurrentToken = null;
        boolean finished = false;
        while (!finished) {
          bufferCurrentToken = jsonParser.nextToken();
          buffer.copyCurrentEvent(jsonParser);

          if (level == 0 &&
              bufferCurrentToken != JsonToken.END_ARRAY &&
              bufferCurrentToken != JsonToken.END_OBJECT) {
            elemTypesArrayList.add(getElemTypeFromToken(bufferCurrentToken));
          }
          switch (bufferCurrentToken) {
            case START_OBJECT:
              level++;
              break;
            case START_ARRAY:
              level++;
              break;
            case END_OBJECT:
              level--;
              break;
            case END_ARRAY:
              level--;
              if (level == -1) {
                finished = true;
              }
              break;
          }
        }

        elemTypes = toByteArray(elemTypesArrayList);

        // Set up the parser that users of this class will be reading from.
        bufferParser = buffer.asParser();
        // We call nextToken() so that bufferParser is at same state as the input JsonParser (will set up currentToken)
        bufferParser.nextToken();

        /* If the first element of the list is itself a list, the next action will be readList to parse the inner list.
         * This will result in the creation of another ArrayReadContext with the parser at the current state.
         * However, by NOTES(1) to hold, nextToken() must be the start of the inner array, so we need to consume
         * a START_ARRAY token.
         */
        if (elemTypes.length > 0 && elemTypes[0] == TType.LIST) {
          bufferParser.nextToken();
        }

      } catch (IOException e) {
        throw new TException(e);
      }
    }

    @Override
    JsonParser parser() { return bufferParser; }

    int listSize() {
      return elemTypes.length;
    }

    byte[] tTypes() {
      return elemTypes;
    }

    @Override
    Object getNextItem() throws TException {
      try {
        JsonToken nextToken = bufferParser.nextToken();
        return nextToken;
      } catch (IOException e) {
        throw new TException(e);
      }
    }

    @Override
    byte valueTType() throws TException {
      throw new IllegalStateException("valueTType shouldn't be called on array read contexts");
    }
  }


  /**
   * Forward slashes in JSON are unsafe to render to browsers because </script> tags
   * in strings will terminate any script tags that have been started. (Hooray html!)
   */
  private static final class JsonCharacterEscapes extends CharacterEscapes {
    private final int[] escapes;

    public JsonCharacterEscapes() {
      escapes = CharacterEscapes.standardAsciiEscapesForJSON();
      escapes['/'] = CharacterEscapes.ESCAPE_CUSTOM;
    }

    @Override
    public int[] getEscapeCodesForAscii() {
      return escapes;
    }

    @Override
    public SerializableString getEscapeSequence(int i) {
      if (i == '/') {
        return new SerializedString("\\/");
      } else if (Character.isISOControl(i) || (i >= '\u2000' && i < '\u2100')) {
        return new SerializedString("\\u" + String.format("%04x", i));
      } else {
        return null;
      }
    }
  }
}
