//  Copyright 2011 Foursquare Labs Inc. All Rights Reserved

package io.fsq.spindle.common.thrift.bson;

import io.fsq.spindle.common.thrift.base.NonStringMapKeyException;
import com.mongodb.BasicDBList;

import java.util.EmptyStackException;
import java.util.List;
import java.util.Stack;

import org.apache.thrift.TException;
import org.bson.BSONObject;


/**
 * Tracks the traversal of a BSON object while writing to it.
 *
 * If any ClassCastExceptions occur this indicates a serious bug in the Thrift libraries or the generated Thrift code.
 * In these cases we throw a TException with details of the unexpected context mismatch.
 *
 * @param <B> The subtype of BSONObject we work with (e.g., com.mongodb.DBObject). This allows us to decouple from
 * MongoDB-specific subtypes and operate at the level of generic BSON.
 */
class BSONWriteState<B extends BSONObject> {

  private interface WriteContext {
    void putItem(Object item) throws TException;
  }

  private static class DocumentWriteContext implements WriteContext {
    private BSONObject document;
    private String currentKey;

    DocumentWriteContext(BSONObject document) { this.document = document; }
    public void putItem(Object item) throws TException {
      try {
        if (currentKey == null) {
          // item is a document key, which we assume is a string, since BSON document keys must be strings.
          currentKey = (String)item;
        } else {
          document.put(currentKey, item);
          currentKey = null;
        }
      } catch (ClassCastException e) {
        throw new NonStringMapKeyException(item);
      }
    }
  }

  private static class ArrayWriteContext implements WriteContext {
    private List<Object> array;

    ArrayWriteContext(List<Object> array) { this.array = array; }
    public void putItem(Object item) throws TException { array.add(item); }
  }

  // We use this to create BSONObject instances.
  private BSONObjectFactory<B> bsonObjectFactory;

  // The top-level object we're writing to.
  private B rootDocument = null;

  // The stack of nested BSON write contexts.
  private Stack<WriteContext> writeContextStack = new Stack<WriteContext>();

  BSONWriteState(BSONObjectFactory<B> bsonObjectFactory) {
    this.bsonObjectFactory = bsonObjectFactory;
  }

  // Returns the result.
  B getOutput() throws TException {
    if (rootDocument == null || !writeContextStack.empty()) {
      throw new TException("Can't get the output object in the middle of writing");
    }
    return rootDocument;
  }

  void putValue(Object val) throws TException {
    currentWriteContext().putItem(val);
  }

  // Descend into a new document context.
  void pushDocumentWriteContext() throws TException {
    B document = bsonObjectFactory.create();
    if (writeContextStack.empty()) {
      rootDocument = document;  // This is the top-level document.
    } else {
      // A non-root document must be the current value in the current context.
      writeContextStack.peek().putItem(document);
    }
    writeContextStack.push(new DocumentWriteContext(document));
  }

  // Descend into a new array context.
  void pushArrayWriteContext(int size) throws TException {
    List<Object> list = new BasicDBList();
    currentWriteContext().putItem(list);
    writeContextStack.push(new ArrayWriteContext(list));
  }

  // Ascend out of the current context.
  WriteContext popWriteContext() throws TException {
    try {
      return writeContextStack.pop();
    } catch (EmptyStackException e) {
      throw new TException("Can't pop off an empty write context stack");
    }
  }

  void reset() {
    rootDocument = null;
    writeContextStack.clear();
  }

  boolean inFlight() {
    return !writeContextStack.isEmpty();
  }

  private WriteContext currentWriteContext() throws TException {
    try {
      return writeContextStack.peek();
    } catch (EmptyStackException e) {
      throw new TException("Can't write outside a document or array context");
    }
  }
}
