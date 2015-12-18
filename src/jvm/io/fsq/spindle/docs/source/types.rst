Custom types
============

In addition to all of the standard Thrift types, Spindle codegen makes available a few extras.

Enhanced types
--------------

Enhanced types are types tagged with an annotation so as to produce either a custom protocol format or a custom Scala
type. Enhanced types are defined with the ``enhanced_types`` annotation on a Thrift typedef, along with custom logic in
the code generator to deal with the enhanced type. For example::

    // A BSON ObjectId, stored as a binary blob.
    typedef binary (enhanced_types="bson:ObjectId") ObjectId

    // A UTC datetime, stored as millis since the epoch.
    typedef i64 (enhanced_types="bson:DateTime") DateTime

    // A BSON Object, stored as a binary blob 
    // This is especially useful if you have serialized data to mongo that cannot be represented in thrift
    typedef binary (enhanced_types="bson:BSONObject") BSONObject



Without the enhanced types mechanism, an ``ObjectId`` would be serialized as binary over the wire and represented as a
``ByteBuffer`` or an ``Array[Byte]`` in Scala code. With the enhanced types mechanism, this will be serialized as binary
in the binary protocols (``TBinaryProtocol``, ``TCompactProtocol``) but receive special handling in
``TBSONObjectProtocol`` (using the native ``ObjectId`` type) and in ``TReadableJSONProtocol`` (using a custom
``ObjectId`` encoding). In the Scala code, it will be represented as an instance of ``ObjectId``.


.. _bitfields:

Bitfields
---------

In order to use space more efficiently, sometimes you want to store several boolean values into a single integer (``i32``) or long
(``i64``) value. Spindle calls these fields "bitfields". Bitfields come in two variants, with and without "set" bits. (With "set"
bits, a single boolean value will take two bits, one to determine whether or not the boolean is set, and another for the
boolean value itself.)

Spindle has some features to make working with bitfields more convenient. You can associate a ``i32`` or ``i64`` field
with a "bitfield struct". A bitfield struct is a Thrift struct with only boolean fields. If a field ``foo`` is marked
with a ``bitfield_struct`` or ``bitfield_struct_no_setbits`` annotation, then an additional ``fooStruct`` method will be
generated which returns a populated boolean struct.

Bitfield annotations are applied directly to a class field (not through a ``typedef``), as follows::

    struct Checkin {
      1: optional i32 flags (bitfield_struct="CheckinFlags")
    }

    struct CheckinFlags {
      1: optional bool sendToTwitter
      2: optional bool sendToFacebook
      3: optional bool geoCheat
    }

In this example, the ``Checkin`` struct will have all the normal methods for dealing with ``flags`` as an ``i32``, as
well as a ``flagsStruct`` method that returns an instance of ``CheckinFlags``.

Type-safe IDs
-------------

You often use BSON ``ObjectIds`` as primary keys in Mongo collections. This means common collectons like ``Checkin`` and
``Venue`` would use the same type as their primary key. In order to avoid errors (such as passing a ``List[ObjectId]``
of checkin IDs to a method expecting a ``List[ObjectId]`` of venue IDs), Spindle includes a mechanism for tagging common
types so they have distinct type-safe version.

This behavior is triggered by using the ``new_type="true"`` annotation on a typedef. In Scala code, this will turn the
type alias into a tagged type, which means it's a new subtype of the aliased type. For example, ``CheckinId`` is a
tagged ``ObjectId`` (unique subtype of ``ObjectId``). Because it's a subtype, you can use ``CheckinId`` anywhere you
would expect an ``ObjectId``. In order to use an ``ObjectId`` where a ``CheckinId`` is required, you will need to cast
it. A convenience method (with the same name as the type) will be generated to perform this cast. For example:
``CheckinId(new ObjectId())``.

Sample usage, in Thrift (``ids.thrift``)::

    package com.foursquare.types.gen

    typedef ObjectId CheckinId (new_type="true")
    typedef ObjectId VenueId (new_type="true")

    struct Checkin {
      1: optional CheckinId id (wire_name="_id")
    }

    struct Venue {
      1: optional VenueId id (wire_name="_id")
    }

And in Scala::

    import com.foursquare.types.gen.IdsTypedefs.{CheckinId, VenueId}

    // cast a regular ObjectId to CheckinId
    val checkinId: CheckinId = CheckinId(new ObjectId()) 
    
    // cast a regular ObjectId to VenueId
    val venueId: VenueId = VenueId(new ObjectId()) 

    // compile error, this won't work because VenueId and CheckinId are different subtypes of ObjectId
    val checkinId2: CheckinId = venueId

    // works, because CheckinId is a subtype of ObjectId and can be automatically upcast
    val unsafeCheckinId: ObjectId = checkinId

Note that the tagged types live in an object with the same name as the thrift file with ``Typedefs`` appended to the end.
