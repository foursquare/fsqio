Working with MongoDB
====================

Annotations
-----------

In order to use Spindle structs with Mongo, they must have the required Mongo annotations.

* ``mongo_collection`` - name of the Mongo collection
* ``mongo_identifier`` - name of the Mongo instance
* ``primary_key`` - the name of the primary key field

There are also a number of optional Mongo annotations:

* ``foreign_key`` - one or more annotations with names of foreign key fields
* ``index`` - one or more annotations with indexed fields

Example::

    struct Checkin {
      1: optional ids.CheckinId id (wire_name="_id")
      2: optional ids.UserId userId (wire_name="uid")
      2: optional ids.VenueId venueId (wire_name="venueid")
    } (primary_key="id",
       foreign_key="userId",
       foreign_key="venueId",
       index="userId: asc",
       index="venueId: asc",
       mongo_collection="checkins",
       mongo_identifier="foursquare")

Rogue queries
-------------

There are two main differences between Lift Record and Spindle when it comes to creating and executing Rogue queries.
First, queries are not created by calling Rogue methods on a model. Queries must be created explicitly by wrapping a
model in a ``SpindleQuery`` (which we recommend aliasing to ``Q``). Second, queries can't be executed by an
execution method on the query, the query must be sent to a ``QueryExecutor`` object to execute it.

For example::

    import io.fsq.spindle.rogue.{SpindleQuery => Q}
    import io.fsq.spindle.rogue.SpindleRogue._

    val q = Q(Checkin).where(_.userId eqs 646).and(_.photoCount > 0)
    val checkins = db.fetch(q)

Here is a basic ``QueryExecutor`` implementation::

    import com.mongodb.MongoClient
    import io.fsq.rogue.QueryOptimizer
    import io.fsq.rogue.adapter.BlockingMongoClientAdapter
    import io.fsq.rogue.adapter.BlockingResult.Implicits._
    import io.fsq.rogue.connection.{BlockingMongoClientManager, MongoIdentifier}
    import io.fsq.rogue.query.QueryExecutor
    import io.fsq.rogue.util.DefaultQueryUtilities
    import io.fsq.spindle.rogue.adapter.SpindleMongoCollectionFactory
    import io.fsq.spindle.rogue.query.SpindleRogueSerializer

    val clientManager = new BlockingMongoClientManager
    val clientAdapter = new BlockingMongoClientAdapter(
      new SpindleMongoCollectionFactory(clientManager),
      new DefaultQueryUtilities
    )

    val db = new QueryExecutor(
      clientAdapter,
      new QueryOptimizer,
      new SpindleRogueSerializer
    )

    def initConnection(): Unit = {
      val client = new MongoClient("localhost", 27017)

      // These correspond to the `mongo_identifier` annotations on Spindle structs
      val ids = Vector(
        MongoIdentifier("foursquare")
      )

      // This example assumes a single backing mongo instance, rather than each id
      // corresponding to a separate cluster/client.
      ids.foreach(id => clientManager.defineDb(id, () => client, "myDatabase"))
    }

