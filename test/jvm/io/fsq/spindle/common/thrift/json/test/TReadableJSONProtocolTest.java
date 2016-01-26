//  Copyright 2012 Foursquare Labs Inc. All Rights Reserved

package io.fsq.spindle.common.thrift.json.test;

import static org.junit.Assert.*;
import static org.hamcrest.core.Is.is;
import io.fsq.spindle.common.thrift.json.TReadableJSONProtocol;
import io.fsq.spindle.common.thrift.json.test.gen.Complex;
import io.fsq.spindle.common.thrift.json.test.gen.MapTest;
import io.fsq.spindle.common.thrift.json.test.gen.RawMapTest;
import io.fsq.spindle.common.thrift.json.test.gen.Simple;
import io.fsq.spindle.common.thrift.json.test.gen.RawComplex;
import io.fsq.spindle.common.thrift.json.test.gen.RawSimple;
import io.fsq.spindle.common.thrift.json.test.gen.ListOfLists;
import io.fsq.spindle.common.thrift.json.test.gen.RawListOfLists;
import io.fsq.spindle.common.thrift.json.test.gen.ListOfListsOfLists;
import io.fsq.spindle.common.thrift.json.test.gen.RawListOfListsOfLists;
import io.fsq.spindle.common.thrift.json.test.gen.MapOfLists;
import io.fsq.spindle.common.thrift.json.test.gen.RawMapOfLists;
import java.util.Arrays;
import java.util.List;
import org.apache.thrift.TDeserializer;
import org.apache.thrift.TSerializer;
import org.apache.thrift.protocol.TProtocolFactory;
import org.junit.Test;
import scala.collection.JavaConversions;
import scala.collection.Seq;

public class TReadableJSONProtocolTest {

  private TProtocolFactory factory = new TReadableJSONProtocol.Factory();

  @Test
  public void testEscapeForwardSlashWrite() throws Exception {
    TSerializer ser = new TSerializer(factory);
    Simple simple = (new Simple.Builder(new RawSimple())).myString("</script>").result();
    assertEquals("{\"myString\":\"<\\/script>\"}", ser.toString(simple));
  }

  @Test
  public void testInvalidEscapeCharacters() throws Exception {
    TSerializer ser = new TSerializer(factory);
    Simple simple = (new Simple.Builder(new RawSimple())).myString("Terminali\u2028").result();
    assertEquals("{\"myString\":\"Terminali\\u2028\"}", ser.toString(simple));
  }

  @Test
  public void testSimpleRead() throws Exception {
    String json = "{\"myString\": \"hola\", \"myBool\": true, \"myInt\": 13}";
    TDeserializer deser = new TDeserializer(factory);

    Simple simple = new RawSimple();
    deser.deserialize(simple, json.getBytes());
    assertEquals("hola", simple.myStringOrNull());
    assertEquals(true, simple.myBool());
    assertEquals(13, simple.myInt());
  }

  @Test
  public void testExtraFieldsSimpleRead() throws Exception {
    // The json contains fields that need to be skipped
    String json = "{\"myString\": \"hola\", \"myBool\": true, \"myInt\": 13, \"myOtherInt\": 4, \"thenull\":null}";
    TDeserializer deser = new TDeserializer(factory);

    Simple simple = new RawSimple();
    deser.deserialize(simple, json.getBytes());
    assertEquals("hola", simple.myStringOrNull());
    assertEquals(true, simple.myBool());
    assertEquals(13, simple.myInt());
  }

  @Test
  public void testExtraArrayFieldSimpleRead() throws Exception {
    // The json contains arrays that need to be skipped
    String json = "{\"myString\": \"hola\", \"myBool\": true, \"myInt\": 13, \"myArr\": [1, 2, 3], \"myArr2\": [{\"a\": []}, {\"a\": []}]}";
    TDeserializer deser = new TDeserializer(factory);

    Simple simple = new RawSimple();
    deser.deserialize(simple, json.getBytes());
    assertEquals("hola", simple.myStringOrNull());
    assertEquals(true, simple.myBool());
    assertEquals(13, simple.myInt());
  }

  @Test
  public void testNestedObjectRead() throws Exception {
    String json = "{\"simple\": {\"myString\": \"hi\"}}";
    TDeserializer deser = new TDeserializer(factory);

    Complex complex = new RawComplex();
    deser.deserialize(complex, json.getBytes());
    assertEquals("hi", complex.simpleOrNull().myStringOrNull());
  }

  @Test
  public void testScalarListRead() throws Exception {
    String json = "{\"iList\": [1, 2, 3, 4]}";
    TDeserializer deser = new TDeserializer(factory);

    Complex complex = new RawComplex();
    deser.deserialize(complex, json.getBytes());
    assertEquals(4, complex.iList().size());
    assertEquals(1, complex.iList().apply(0));
    assertEquals(4, complex.iList().apply(3));
  }

  @Test
  public void testNestedListRead() throws Exception {
    String json = "{\"simpleList\": [" +
      "{\"myInt\": 1}," +
      "{\"myInt\": 2}," +
      "{\"myInt\": 3}," +
      "{\"myInt\": 4}" +
    "]}";
    TDeserializer deser = new TDeserializer(factory);

    Complex complex = new RawComplex();
    deser.deserialize(complex, json.getBytes());
    assertEquals(4, complex.simpleList().size());
  }

  @Test
  public void testEmptyDoubleNestedArray() throws Exception {
    // Make sure we're reading the right array context...
    String json = "{\"simpleList\": [" +
      "{\"myInt\": 1}," +
      "{\"myInt\": 2}," +
      "{\"myInt\": 3}," +
      "{\"myInt\": 4}" +
    "], \"simpleHolderList\": {\"simpleHolders\": [{\"myInt\": 3, \"myStrings\": []}]}}";
    TDeserializer deser = new TDeserializer(factory);

    Complex complex = new RawComplex();
    deser.deserialize(complex, json.getBytes());
    assertEquals(4, complex.simpleList().size());
  }

  @Test
  public void testDoubleNestedListRead() throws Exception {
    String json = "{\"intHolderList\": {\"intHolders\": [" +
      "{\"i\": 1}," +
      "{\"i\": 2}," +
      "{\"i\": 3}," +
      "{\"i\": 4}" +
    "]}}";
    TDeserializer deser = new TDeserializer(factory);

    Complex complex = new RawComplex();
    deser.deserialize(complex, json.getBytes());
    assertEquals(4, complex.intHolderListOrNull().intHolders().size());
    assertEquals(1, complex.intHolderListOrNull().intHolders().toList().apply(0).i());
    assertEquals(4, complex.intHolderListOrNull().intHolders().toList().apply(3).i());
  }

  @Test
    public void testMapRead() throws Exception {
      String json = "{\"stringMap\": { \"a\" : \"b\", \"c\": \"d\"}, " +
        "\"intMap\": {\"a\": 1}, " +
        "\"simpleMap\": {\"simpleKey\": { \"myInt\" : 42 }}, " +
        "\"complexMap\": {\"complexKey\":" +
            "{\"intHolderMap\": { \"aKey\" : {\"i\": 5}}}} " +
      "}";
      TDeserializer deser = new TDeserializer(factory);
      MapTest mapTest = new RawMapTest();
      deser.deserialize(mapTest, json.getBytes());
      assertEquals(2, mapTest.stringMap().size());
      assertEquals("b", mapTest.stringMap().get("a").get());
      assertEquals("d", mapTest.stringMap().get("c").get());

      assertEquals(1, mapTest.intMap().size());
      assertEquals(1, mapTest.intMap().get("a").get());

      assertEquals(1, mapTest.simpleMap().size());
      assertEquals(42, mapTest.simpleMap().get("simpleKey").get().myInt());

      assertEquals(1, mapTest.complexMap().size());
      assertEquals(5, mapTest.complexMap().get("complexKey").
          get().intHolderMap().get("aKey").get().i());
  }

  @Test
  public void testListOfLists() throws Exception {
    // Make sure we're reading the right array context...
    String json = "{\"listHolders\": [" +
      "[\"12\", \"4\"]," +
      "[\"13\", \"5\"]," +
      "[\"14\", \"6\"]" +
    "]}";
    TDeserializer deser = new TDeserializer(factory);

    ListOfLists listOfLists = new RawListOfLists();
    deser.deserialize(listOfLists, json.getBytes());
    assertEquals(3, listOfLists.listHolders().size());
    assertThat(Arrays.asList("12", "4"),
      is(JavaConversions.seqAsJavaList(listOfLists.listHolders().toList().apply(0))));
    assertThat(Arrays.asList("13", "5"),
      is(JavaConversions.seqAsJavaList(listOfLists.listHolders().toList().apply(1))));
    assertThat(Arrays.asList("14", "6"),
      is(JavaConversions.seqAsJavaList(listOfLists.listHolders().toList().apply(2))));
  }

  @Test
  public void testListOfListsOfLists() throws Exception {
    // Make sure we're reading the right array context...
    String json = "{\"listHolders\": [" +
      "[ [1, 2], [3] ]," +
      "[ [5, 6], [7] ]" +
    "]}";
    TDeserializer deser = new TDeserializer(factory);

    ListOfListsOfLists listOfLists = new RawListOfListsOfLists();
    deser.deserialize(listOfLists, json.getBytes());
    assertEquals(2, listOfLists.listHolders().size());

    @SuppressWarnings("unchecked")
    List<Seq<Integer>> innerList1 =
      (List<Seq<Integer>>)(List<?>)JavaConversions.seqAsJavaList(listOfLists.listHolders().toList().apply(0));
    @SuppressWarnings("unchecked")
    List<Seq<Integer>> innerList2 =
      (List<Seq<Integer>>)(List<?>)JavaConversions.seqAsJavaList(listOfLists.listHolders().toList().apply(1));

    assertThat(Arrays.asList(1, 2), is(JavaConversions.seqAsJavaList(innerList1.get(0))));
    assertThat(Arrays.asList(3), is(JavaConversions.seqAsJavaList(innerList1.get(1))));


    assertThat(Arrays.asList(5, 6), is(JavaConversions.seqAsJavaList(innerList2.get(0))));
    assertThat(Arrays.asList(7), is(JavaConversions.seqAsJavaList(innerList2.get(1))));
  }


  @Test
  public void testMapOfLists() throws Exception {
    // Make sure we're reading the right array context...
    String json = "{\"mapHolder\": {" +
      "\"monday\": [ \"2:00\", \"4:00\" ]," +
      "\"tuesday\": [ \"3:00\", \"5:00\" ]" +
    "}}";
    TDeserializer deser = new TDeserializer(factory);

    MapOfLists mapOfLists = new RawMapOfLists();
    deser.deserialize(mapOfLists, json.getBytes());
    assertEquals(2, mapOfLists.mapHolder().size());

    assertThat(Arrays.asList("2:00", "4:00"), is(JavaConversions.seqAsJavaList(mapOfLists.mapHolder().get("monday").get())));
    assertThat(Arrays.asList("3:00", "5:00"), is(JavaConversions.seqAsJavaList(mapOfLists.mapHolder().get("tuesday").get())));
  }
}
