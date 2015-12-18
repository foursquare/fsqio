Records
=======

Creating a record
-----------------

There are three ways to create a Record.

1. ``apply`` method
~~~~~~~~~~~~~~~~~~~

The easiest way to create a Record is to use the companion object's ``apply`` method. This method only works if you know the
value of all of the Record's fields, even optional fields. This is useful for structs that have a small, usually fixed
number of fields. (Note that if you add another field to your struct, any existing calls to apply will fail due to
insufficient arguments.)

Example (Thrift file)::

    struct LatLong {
      1: optional double lat
      2: optional double long
    }

Example (Scala file)::

    val ll = LatLong(40.65, -73.78)

2. Builder
~~~~~~~~~~

If you need to omit optional fields, or if you want to pass around a partially-constructed Record before you know all
its fields, you will want to use a Builder. Builders are created using the ``newBuilder`` method on a Record's companion
object. Fields on the builder are set using methods with the same name as the field. Field setter methods can be passed
either a value or an ``Option`` value. To finish constructing a Builder, call ``result`` or ``resultMutable`` (returns a
Record that conforms to the ``Mutable`` trait). Builders enforce that all required fields are set before result can be
called.

Example::

    val v = (Venue
        .newBuilder
        .id(new ObjectId())
        .venuename(Some("Foursquare HQ"))
        .likeCount(None)
        .result())

3. Raw
~~~~~~

If for whatever reason a Builder is not flexible enough to do what you want, you can always instantiate a Raw class. You
can use the ``createRawRecord`` method on the Record's companion object or you can call ``new`` directly on the Record's
Raw class. This is unsafe, as it will not verify that required fields are set and can seriously corrupt data. Make sure
you know what you're doing before you use Raw constructors.

Reading/writing records
-----------------------

All records have read and write methods that take one argument: a ``TProtocol``. We use a number of protocols, depending on
the serialization format you want to read from or write to. See the section on :ref:`serialization-formats`. Each protocol has
a particular way in which it's constructed. Refer to the documentation for each protocol on how to use them. If you're
reading in a record, it's acceptable to use the ``createRawRecord`` method on the MetaRecord companion object to instantiate
a record that you can call read on.

Record interface methods
------------------------

Every record has a trait of the same name that defines a mostly immutable interface to that record (the exception being
some mutable methods for priming foreign keys). Methods that modify the record are generally only available as part of
the Mutable trait.


Field access
~~~~~~~~~~~~

The record's trait has a bunch of methods for field access. Not all of them are always available. Given a field named
``foo``, the following field access methods may be available:

* ``foo`` - Returns the value of the field. Only available if a default is available (in which case it aliases ``fooOrDefault``), or if the field is required (in which case it aliases ``fooOrThrow``).
* ``fooOption` - Returns an ``Option`` with the value of the field, or ``None`` if the field is not set. Always available.
* ``fooOrDefault`` - Returns the value of the field, or the default value if the field is not set. Only available if a default is available (either there's an explicit default in the ``.thrift`` file, or the field is a primitive or a collection, in which case the default is ``0``/``0.0``/``false``/``empty``/etc).
* ``fooOrNull`` - Returns the value of the field, or ``null`` if the field is not set. For primitives, typed to return the boxed type (e.g., ``java.lang.Integer`` instead of scala.Int) so that null is valid. In general, avoid using fooOrNull. Prefer fooOption in general and fooOrThrow if you want to fail fast (such as in tests, etc). Only use ``fooOrNull`` if you're writing performance-sensitive code that can't afford to allocate an ``Option``.
* ``fooOrThrow`` - Returns the value of the field, or throws an exception if the field is not set.
* ``fooIsSet`` - Returns ``true`` if the field is set, ``false`` otherwise.

Special field access
~~~~~~~~~~~~~~~~~~~~

Some field types have special access methods:

* ``fooByteArray`` - if the field is of the ``binary`` thrift type, this method will exist and return an ``Array[Byte]`` (instead of the more efficient ``java.nio.ByteBuffer``)
* ``fooStruct`` - if the field has a ``bitfield_struct`` or ``bitfield_struct_no_setbits`` annotation, this method will exist and return a populated bitfield struct. (See: :ref:`bitfields`)
* ``fooFk`` - if the field is a ``foreign_key`` field, this method will exist and return the foreign object. (See: :ref:`priming`)

Other methods
-------------

Other methods on the record trait:

* ``toString`` - produces a JSON-like string representation of the record. It is not strict JSON because it supports non-string map keys.
* ``hashCode`` - uses ``scala.util.MurmurHash`` to produce a hash code from all the already-set fields on the record
* ``equals`` - compares two records to make sure that all their fields have the same set state, and if they're both set that they have the same value
* ``compare`` - compares two records by their set state for each field and their value for each field
* ``copy`` - similar to case class copy, creates a shallow copy of a record; has method arguments with default values so you can override the value of a single field or many fields when copying
* ``deepCopy`` - creates a deep copy of a record; doesn't take arguments
* ``mutableCopy`` - creates a shallow copy of a record, with the ``Mutable`` trait as its return type
* ``mutable`` - if the underlying implementation is mutable, return this typed as a ``Mutable`` trait, otherwise make a ``mutableCopy``
* ``toBuilder`` - creates a new builder that has been initialized to have the same state as this record

Mutable trait
-------------

TODO: the Mutable trait interface is likely to change before being finalized

Raw class
---------

TODO


.. _priming:

Priming
-------

The only way to prime records is through the prime method on DatabaseService, which takes a sequence of records, the
field to be primed on those records, and the model to be primed on that field. It optionally takes a sequence of already
known foreign records and a Mongo ``ReadPreference``. For example::

    val checkins: List[Checkin] = ...
    services.db.prime(checkins, Checkin.venueId, Venue)

To access the primed foreign object on field ``foo``, use the ``fooFk`` method on the record, which takes the model of the
foreign object as an argument and returns an ``Option`` of the foreign object::

    val venues: List[Venue] = checkins.flatMap(_.venueIdFk(Venue))

(This is somewhat clunky, mostly because of having to pass around the model of the foreign object (in this case,
``Venue``) everywhere. This is necessary in order to decouple foreign key fields from the models they point to
and so avoid dependency hairballs.)

Proxies
-------

Spindle can generate proxy classes that can be used to decorate generated models with additional behavior. For example,
suppose you have this thrift definition::

    struct Rectangle {
      1: double length
      2: double width
    }

Thrift will generate a class ``Rectangle``. But suppose you want to add a convenience method ``area`` to ``Rectangle``.
First, instruct Spindle to generate a proxy::

    struct Rectangle {
      1: double length
      2: double width
    } (
      generate_proxy="1"
    )

Thrift will now generate a class ``RectangleProxy`` that by forwards all of its methods to an underlying ``Rectangle``
instance. You can now do::

    class RichRectangle(override val underlying: Rectangle) extends RectangleProxy(underlying) {
      def area = underlying.length * underlying.width
    }

    val rect: Rectangle = ... // fetch from database
    val myRect = new RichRectangle(rect)
    myRect.area

Reflection
----------

TODO

Field descriptors
-----------------

TODO
