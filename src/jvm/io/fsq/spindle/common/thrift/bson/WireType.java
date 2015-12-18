package io.fsq.spindle.common.thrift.bson;


import org.apache.thrift.TException;
import org.apache.thrift.protocol.TType;
import org.bson.BSONObject;
import org.bson.types.ObjectId;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WireType {
  /*
   * Returns the ttype of the specified BSON value.
   */
  public static byte valueTType(Object value) throws TException {
    Class<?> classOfValue = value.getClass();
    Byte ret = finalClassToTTypeMap.get(classOfValue);
    if (ret == null) {
      for (int i = 0; i < nonFinalClassToTType.length; ++i) {
        if (nonFinalClassToTType[i].clazz.isAssignableFrom(classOfValue)) {
          ret = nonFinalClassToTType[i].ttype;
          break;
        }
      }
    }
    if (ret == null) {
      throw new TException("Unknown TType for value of class " + classOfValue.getName());
    }
    return ret;
  }

  // Map from final classes to corresponding TType. We can look these up quickly, without using instanceof,
  // because we know that if an object is assignable to this class then it must be of this class.
  private static Map<Class<?>, Byte> finalClassToTTypeMap = new HashMap<Class<?>, Byte>();
  static {
    finalClassToTTypeMap.put(Boolean.class, TType.BOOL);
    finalClassToTTypeMap.put(Integer.class, TType.I32);
    finalClassToTTypeMap.put(Long.class, TType.I64);
    finalClassToTTypeMap.put(Double.class, TType.DOUBLE);
    finalClassToTTypeMap.put(String.class, TType.STRING);
    finalClassToTTypeMap.put(byte[].class, TType.STRING);
  }

  // List of non-final classes a value might be of.
  private static class NonFinalClassToTTypeEntry {
    public NonFinalClassToTTypeEntry(Class<?> c, byte t) { clazz = c; ttype = t; }
    public final Class<?> clazz;
    public final byte ttype;
  }

  private static NonFinalClassToTTypeEntry[] nonFinalClassToTType = {
      // Note that order matters, since, e.g., BasicBSONList is a subtype of both List and BSONObject,
      // and we want to treat it as a List here.
      new NonFinalClassToTTypeEntry(Date.class, TType.I64),
      new NonFinalClassToTTypeEntry(List.class, TType.LIST),
      new NonFinalClassToTTypeEntry(ObjectId.class, TType.STRING),
      new NonFinalClassToTTypeEntry(BSONObject.class, TType.STRUCT)
  };
}
