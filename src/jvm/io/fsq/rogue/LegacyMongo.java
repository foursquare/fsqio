// Copyright 2020 Foursquare Labs, Inc. All Rights Reserved.

package io.fsq.rogue;

import com.mongodb.DB;
import com.mongodb.MongoClient;

/**
 * This provides access to the deprecated Mongo DB api (as opposed
 * to the preferred MongoDatabase)
 *
 * It is in Java because we don't have a great way of suppressing
 * deprecations in Scala.
 *
 * TODO: Delete all usages and migrate!
 */
@SuppressWarnings("deprecation")
public class LegacyMongo {
    public static DB getDB(MongoClient client, String name) {
        return client.getDB(name);
    }
}
