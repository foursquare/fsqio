Thrift
======

Thrift is a framework that allows you to define serializable datatypes in a language-independent manner. These datatypes
are described in ``.thrift`` files which conform to the Thrift Interface Description Language (IDL). The standard Thrift
compiler will read Thrift IDLs and generate code for many different languages: Java, Python, JavaScript, C++, etc. We've
built a custom Thrift compiler (documented here) which generates Scala code.

Data model
----------

Thrift's data model has the following base types:

* ``bool`` - true/false boolean value (Java's boolean)
* ``byte`` - 8-bit signed integer (Java's byte)
* ``i16`` - 16-bit signed integer (Java's short)
* ``i32`` - 32-bit signed integer (Java's int)
* ``i64`` - 64-bit signed integer (Java's long)
* ``double`` - 64 bit IEEE 754 double precision floating point number (Java's double)
* ``string`` - UTF-8 encoded text string (Java's string)
* ``binary`` - sequence of unencoded bytes (Java's Array[Byte] or ByteBuffer)

Thrift's data model includes an ``enum`` type (equivalent to Java's ``enum``). On the wire, enums are represented as
32-bit integers.


Thrift's data model also has the following parametrized container types. The parameters ``<a>`` and ``<b>`` can be primitives, ``enum``\s, or ``struct``\s.

* ``list<a>`` - ordered list of elements (Java's ``List<A>``)
* ``set<a>`` - unordered list of unique elements (Java's ``Set<A>``)
* ``map<a, b>`` - a map of unique keys to values (Java's ``Map<A, B>``). Warning: non-``string`` map keys will fail at runtime if you try to use ``TBSONObjectProtocol``.

Thrift includes three "class" types:

* ``struct`` - numbered fields, with a type and an optional default value
* ``union`` - tagged union types
* ``exception`` - like ``struct``\s, but can be used in the "throws" clause of a service

Finally, Thrift allows you to define constants and typedefs. A constant is a value of any Thrift type. A typedef is an alias of any Thrift type.

Interface definition language (IDL)
-----------------------------------

Thrift data structures are described in ``.thrift`` files which conform to the Thrift IDL grammar. You can see the `Thrift
Tutorial`_ for an example, or reference the `formal grammar`_.

.. _Thrift Tutorial: http://wiki.apache.org/thrift/Tutorial
.. _formal grammar: http://thrift.apache.org/docs/idl


.. _serialization-formats:

Serialization formats
---------------------

There are four serialization formats we use:

* ``TBinaryProtocol`` - The original binary encoding included with Thrift. It is not well-documented, but is fairly simple. Field names are ommitted (only integer field identifiers are used), types are encoded as byte identifiers, sizes are prepended as integers.
* ``TCompactProtocol`` - A more compact binary encoding. Also not well-documented, but somewhat described `here`_
* ``TReadableJSONProtocol`` - Reads and writes human-readable JSON. Unlike ``TJSONProtocol``, ituses actual field names for keys, rather than field identifiers. If the field has a ``wire_name`` annotation, will use that instead of the name. Includes special handling for ``ObjectId``.
* ``TBSONObjectProtocol`` - Reads and writes BSON ``DBObject``\s. It uses field names for keys. If the field has a ``wire_name`` annotation, will use that instead of the name. Includes special handling for ``ObjectId`` and ``DateTime``.

.. _here: http://wiki.apache.org/thrift/New_compact_binary_protocol
