# Rogue

Rogue is a type-safe internal Scala DSL for constructing and executing find and modify commands against
MongoDB. It is fully expressive with respect to the basic options provided
by MongoDB's native query language, but in a type-safe manner, building on the record types specified in
your models. While originally written for use with the [Lift framework](https://github.com/lift/framework),
Rogue is model-agnostic and includes bundled support for thrift models via [Spindle](../spindle/docs/source/mongo.rst),
as well as examples with vanilla Scala case classes in the [test suite](../../../../../test/jvm/io/fsq/rogue/query/test/TrivialORMQueryTest.scala).

An example:

    Venue.where(_.mayor eqs 1234).and(_.tags contains "Thai").fetch(10)

The type system enforces the following constraints:

- the fields must actually belong to the record (e.g., mayor is a field on the Venue record)
- the field type must match the operand type (e.g., mayor is an IntField)
- the operator must make sense for the field type (e.g., categories is a MongoListField[String])

In addition, the type system ensures that certain builder methods are only used in certain circumstances.
For example, take this more complex query:

    Venue.where(_.closed eqs false).orderAsc(_.popularity).limit(10).modify(_.closed setTo true).updateMulti

This query purportedly finds the 10 least popular open venues and closes them. However, MongoDB
does not (currently) allow you to specify limits on modify queries, so Rogue won't let you either.
The above will generate a compiler error.

Constructions like this:

    def myMayorships = Venue.where(_.mayor eqs 1234).limit(5)
    ...
    myMayorships.fetch(10)

will also not compile, here because a limit is being specified twice. Other similar constraints
are in place to prevent you from accidentally doing things you don't want to do anyway.

## Installation

Jars are published on an as needed basis and can be found on maven central [here](https://search.maven.org/search?q=g:io.fsq%20rogue).

## Setup

### Lift

Define your record classes in Lift like you would normally (see the [test models](../../../../../test/jvm/io/fsq/rogue/lift/testlib/Models.scala) for examples).

Then anywhere you want to use rogue queries against these records, import the following:

    import com.foursquare.rogue.LiftRogue._

See [EndToEndTest.scala](../../../../../test/jvm/io/fsq/rogue/lift/test/EndToEndTest.scala) for a complete working example.

### Spindle

See [Spindle docs](../spindle/docs/source/mongo.rst).

## Async Clients

Rogue supports both the async and blocking versions of the mongo java driver clients. There is a bundled
async client adapter using [twitter-util Futures](https://github.com/twitter/util#futures):

    val clientManager = new AsyncMongoClientManager
    val clientAdapter = new AsyncMongoClientAdapter(
      new SpindleMongoCollectionFactory(clientManager),
      new DefaultQueryUtilities
    )

    val db = new QueryExecutor(
      clientAdapter,
      new QueryOptimizer,
      new SpindleRogueSerializer
    )

## More Examples

### Query Tests

[Lift](../../../../../test/jvm/io/fsq/rogue/lift/test/QueryTest.scala)
[Spindle](../../../../../test/jvm/io/fsq/spindle/rogue/test/QueryTest.scala)

The query tests contain sample Records and examples of every kind of query supported by Rogue, and indicate
what each query translates to in MongoDB's JSON query language. They're a good place to look when getting
started using Rogue.

NB: The examples in QueryTest only construct query objects; none are actually executed.
Once you have a query object, the following operations are supported (listed here because
they are not demonstrated in QueryTest):

For "find" query objects

    val query = Venue.where(_.venuename eqs "Starbucks")
    query.count()
    query.countDistinct(_.mayor)
    query.fetch()
    query.fetch(n)
    query.get()     // equivalent to query.fetch(1).headOption
    query.exists()  // equivalent to query.fetch(1).size > 0
    query.foreach{v: Venue => ... }
    query.paginate(pageSize)
    query.fetchBatch(pageSize){vs: List[Venue] => ...}
    query.bulkDelete_!!(WriteConcern.ACKNOWLEDGED)
    query.findAndDeleteOne()
    query.explain()
    query.iterate(handler)

For "modify" query objects

    val modify = query.modify(_.mayor_count inc 1)
    modify.updateMulti()
    modify.updateOne()
    modify.upsertOne()

for "findAndModify" query objects

    val modify = query.where(_.legacyid eqs 222).findAndModify(_.closed setTo true)
    modify.updateOne(returnNew = ...)
    modify.upsertOne(returnNew = ...)

### End to End Tests

[Lift](../../../../../test/jvm/io/fsq/rogue/lift/test/EndToEndTest.scala)
[Spindle](../../../../../test/jvm/io/fsq/spindle/rogue/test/EndToEndTest.scala)

### TrivialORM Query Tests

[TrivialORMQueryTest.scala](../../../../../test/jvm/io/fsq/rogue/query/test/TrivialORMQueryTest.scala)
is a proof of concept test suite using vanilla Scala case classes.

## Releases

We currently push updates here and publish artifacts on an as needed basis. Please open an issue to request an update.

## Maintainers

Rogue was initially developed by Foursquare Labs for internal use --
nearly all of the MongoDB queries in foursquare's codebase go through this library.

Contributions welcome!
