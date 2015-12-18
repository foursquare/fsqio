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
model in a ``SpindleQuery`` (which we recommend aliasing to ``Q``). Second, queries are not executed by called an
execution method on the query, the query must be sent to a ``SpindleDatabaseService`` object to execute it.

For example::

    import io.fsq.rogue.spindle.{SpindleQuery => Q}
    import io.fsq.rogue.spindle.SpindleRogue._

    val q = Q(Checkin).where(_.userId eqs 646).and(_.photoCount > 0)
    val checkins = db.fetch(q)

Here is a basic ``SpindleDatabaseService`` implementation::

    import io.fsq.rogue.spindle.{SpindleDBCollectionFactory, SpindleDatabaseService}
    import io.fsq.spindle.runtime.UntypedMetaRecord
    import com.mongodb.{DB, Mongo, MongoClient, MongoURI}

    object db extends SpindleDatabaseService(ConcreteDBCollectionFactory)

    object ConcreteDBCollectionFactory extends SpindleDBCollectionFactory {
      lazy val db: DB = {
        val mongoUrl = System.getenv("MONGOHQ_URL")
        if (mongoUrl == null) {
          // For local testing
          new MongoClient("localhost", 27017).getDB("mydatabase")
        } else {
          // Connect using the MongoHQ connection string
          val mongoURI = new MongoURI(mongoUrl)
          val mongo = mongoURI.connectDB
          if (mongoURI.getUsername != null && mongoURI.getUsername.nonEmpty) {
            mongo.authenticate(mongoURI.getUsername, mongoURI.getPassword)
          }
          mongo
        }
      }
      override def getPrimaryDB(meta: UntypedMetaRecord) = db
      override def indexCache = None
    }

