// Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.spindle.common.thrift.bson

import io.fsq.spindle.common.thrift.base.TTransportInputStream
import java.io.InputStream
import java.lang.UnsupportedOperationException
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.Stack
import org.apache.thrift.{TBaseHelper, TException}
import org.apache.thrift.protocol.{TField, TList, TMap, TMessage, TProtocol, TProtocolFactory, TSet, TStruct, TType}
import org.apache.thrift.transport.TTransport


object TBSONBinaryProtocol {
  // Are only read, never written, so can be used concurrently.
  val ANONYMOUS_MESSAGE  = new TMessage()
  val ANONYMOUS_STRUCT = new TStruct()
  val NO_MORE_FIELDS = new TField("", TType.STOP, 0)
  val ERROR_KEY = "$err"
  val CODE_KEY = "code"

  class ReaderFactory extends TProtocolFactory {
    def getProtocol(trans: TTransport): TProtocol = {
      val stream = new TTransportInputStream(trans)
      val protocol = new TBSONBinaryProtocol()
      protocol.setSource(stream)
      protocol
    }
  }
}

/**
 * Thrift protocol to decode binary bson
 * Not thread safe, but can be reused assuming prior full successful reads
 * use setSource(InputStream) before passing into read method
 *
 * This only implements the read methods. Write methods will throw UnsupportedOperationException
 */
class TBSONBinaryProtocol() extends TProtocol(null) {

  private var inputStream: InputStream = null
  // used as intermediate copy buffer for field names and strings
  private val buffer = new ByteStringBuilder(32)
  
  private val readStack = new Stack[ReadState]

  private var _errorMessage: String = null
  private var _errorCode: Int = 0

  def errorMessage = _errorMessage
  def errorCode = _errorCode

  def setSource(is: InputStream): TBSONBinaryProtocol = {
    _errorMessage = null
    _errorCode = 0
    readStack.clear()
    inputStream = is
    this
  }

  private def checkReadState[T <: ReadState](readState: ReadState, clazz: Class[T]): T = {
    if (readState == null) {
      throw new TException("Internal state is null. Possibly readXEnd unpaired with readXBegin.")
    }
    if (!clazz.isInstance(readState)) {
      throw new TException(s"Internal state error. Expected ${clazz} but was ${readState.getClass}")
    }
    readState.asInstanceOf[T]
  }

  private def popReadState[T <: ReadState](clazz: Class[T]): T = {
    checkReadState(readStack.pop(), clazz)
  }

  private def currentState(): ReadState = {
    val readState = readStack.peek()
    if (readState == null) {
      throw new TException("Internal state is null.")
    }
    readState
  }

  def getTType(bsonType: Byte): Byte = bsonType match {
    case BSON.EOO => TType.STOP
    case BSON.NUMBER => TType.DOUBLE
    case BSON.STRING => TType.STRING
    case BSON.OBJECT => TType.STRUCT
    case BSON.ARRAY => TType.LIST
    case BSON.BINARY => TType.STRING
    case BSON.UNDEFINED => TType.VOID
    case BSON.OID => TType.STRING
    case BSON.BOOLEAN => TType.BOOL
    case BSON.DATE => TType.I64
    case BSON.NULL => TType.VOID
    case BSON.REGEX => TType.STRING
    case BSON.REF => TType.STRING
    case BSON.CODE => TType.STRING
    case BSON.SYMBOL => TType.STRING
    case BSON.CODE_W_SCOPE => TType.STRING
    case BSON.NUMBER_INT => TType.I32
    case BSON.TIMESTAMP => TType.I64
    case BSON.NUMBER_LONG => TType.I64
  }

  /**
   * Reading methods.
   */

  def readMessageBegin(): TMessage = {
    TBSONBinaryProtocol.ANONYMOUS_MESSAGE
  }

  def readMessageEnd(): Unit = {
  }

  def readStructBegin(): TStruct = {
    if (readStack.size == 0) {
      readStack.push(new StructReadState(inputStream, buffer))
    } else {
      readStack.push(currentState().readStruct())
    }
    TBSONBinaryProtocol.ANONYMOUS_STRUCT
  }

  def readStructEnd(): Unit = {
    val readState = popReadState(classOf[StructReadState])
    readState.readEnd()
  }

  def readFieldBegin(): TField = {
    val readState = checkReadState(readStack.peek(), classOf[StructReadState])
    def findNonNullField: TField = {
      if (readState.hasAnotherField) {
        readState.readFieldType()
        if (readState.lastFieldType == BSON.NULL) {
          findNonNullField
        } else {
          new TField(readState.lastFieldName, getTType(readState.lastFieldType), -1)
        }
      } else {
        TBSONBinaryProtocol.NO_MORE_FIELDS
      }
    }
    findNonNullField
  }

  def readFieldEnd(): Unit = {
  }

  def readMapBegin(): TMap = {
    val mapReadState = currentState().readMap()
    readStack.push(mapReadState)
    new TMap(TType.STRING, getTType(mapReadState.lastFieldType), mapReadState.itemCount)
  }

  def readMapEnd(): Unit = {
    popReadState(classOf[MapReadState])
  }

  def readListBegin(): TList = {
    val listReadState = currentState().readList()
    readStack.push(listReadState)
    new TList(getTType(listReadState.lastFieldType), listReadState.itemCount)
  }

  def readListEnd(): Unit = {
    popReadState(classOf[ListReadState])
  }

  def readSetBegin(): TSet = {
    val listReadState = currentState().readList()
    readStack.push(listReadState)
    new TSet(getTType(listReadState.lastFieldType), listReadState.itemCount)
  }

  def readSetEnd(): Unit = {
    popReadState(classOf[ListReadState])
  }

  def readBool(): Boolean = {
    currentState().readBool()
  }

  def readByte(): Byte = {
    currentState().readI32().toByte
  }

  def readI16(): Short = {
    currentState().readI32().toShort
  }

  def readI32(): Int = {
    val readState = currentState()
    val intValue = currentState().readI32()
    // hack to keep track of mongo error
    if (_errorMessage != null && readState.lastFieldName == TBSONBinaryProtocol.CODE_KEY) {
      _errorCode = intValue
    }
    intValue
  }

  def readI64(): Long = {
    val readState = currentState()
    // be lenient here to handle legacy records written out in i32
    if (readState.lastFieldType == BSON.NUMBER_INT) {
      readState.readI32()
    } else {
      readState.readI64()
    }
  }

  def readDouble(): Double = {
    currentState().readDouble()
  }

  def readString(): String = {
    val readState = currentState()
    // A string field unknown to an older version of a struct will be serialized as binary.
    if (readState.lastFieldType == BSON.BINARY) {
      new String(TBaseHelper.byteBufferToByteArray(readState.readBinary()), StandardCharsets.UTF_8)
    } else {
      readState.readString()
    }
  }

  def readBinary(): ByteBuffer = {
    val readState = currentState()
    // Thrift will skip string fields it doesn't know about using readBinary
    if (readState.lastFieldType == BSON.STRING) {
      val strValue = readState.readString()
      // hack to keep track of mongo error
      if (readState.lastFieldName == TBSONBinaryProtocol.ERROR_KEY) {
        _errorMessage = strValue
      }
      ByteBuffer.wrap(strValue.getBytes(StandardCharsets.UTF_8))
    } else {
      readState.readBinary()
    }
  }

  /**
   * Writing methods.
   */

  def writeMessageBegin(message: TMessage) = throw new UnsupportedOperationException()

  def writeMessageEnd(): Unit = throw new UnsupportedOperationException()

  def writeStructBegin(struct: TStruct): Unit = throw new UnsupportedOperationException()

  def writeStructEnd(): Unit = throw new UnsupportedOperationException()

  def writeFieldBegin(field: TField): Unit = throw new UnsupportedOperationException()

  def writeFieldEnd(): Unit = throw new UnsupportedOperationException()

  def writeFieldStop(): Unit = throw new UnsupportedOperationException()

  def writeMapBegin(map: TMap): Unit = throw new UnsupportedOperationException()

  def writeMapEnd(): Unit = throw new UnsupportedOperationException()

  def writeListBegin(list: TList): Unit = throw new UnsupportedOperationException()

  def writeListEnd(): Unit = throw new UnsupportedOperationException()

  def writeSetBegin(set: TSet): Unit = throw new UnsupportedOperationException()

  def writeSetEnd(): Unit = throw new UnsupportedOperationException()

  def writeBool(b: Boolean): Unit = throw new UnsupportedOperationException()

  def writeByte(b: Byte): Unit = throw new UnsupportedOperationException()

  def writeI16(i16: Short): Unit = throw new UnsupportedOperationException()

  def writeI32(i32: Int): Unit = throw new UnsupportedOperationException()

  def writeI64(i64: Long): Unit = throw new UnsupportedOperationException()

  def writeDouble(dub: Double): Unit = throw new UnsupportedOperationException()

  def writeString(str: String): Unit = throw new UnsupportedOperationException()

  def writeBinary(buf: ByteBuffer): Unit = throw new UnsupportedOperationException()

}