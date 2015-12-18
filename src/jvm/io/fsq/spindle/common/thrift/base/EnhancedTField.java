//  Copyright 2012 Foursquare Labs Inc. All Rights Reserved

package io.fsq.spindle.common.thrift.base;

import java.util.HashMap;
import java.util.Map;

import org.apache.thrift.protocol.TField;

/**
 * Complicated thrift protocols may require extra information. E.g., the BSONProtocol needs to know that it should
 * map a binary thrift field to an ObjectId, instead of to a regular BSON binary field.
 * We provide this information in this TField subclass.
 */
public class EnhancedTField extends TField {
  public final Map<String, String> enhancedTypes;

  public EnhancedTField() {
    super();
    this.enhancedTypes = new HashMap<String, String>();
  }

  public EnhancedTField(String n, byte t, short i, Map<String, String> enhancedTypes) {
    super(n, t, i);
    this.enhancedTypes = enhancedTypes;
  }
}
