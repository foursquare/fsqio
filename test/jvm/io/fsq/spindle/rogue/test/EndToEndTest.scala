// Copyright 2012 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.spindle.rogue.test

import com.mongodb.ReadPreference
import io.fsq.rogue.{
  BulkInsertOne,
  BulkRemove,
  BulkRemoveOne,
  BulkReplaceOne,
  BulkUpdateMany,
  BulkUpdateOne,
  Iter,
  IterUtil
}
import io.fsq.spindle.rogue.SpindleQuery
import io.fsq.spindle.rogue.SpindleRogue._
import io.fsq.spindle.rogue.testlib.{Models, SpindleMongoTest}
import io.fsq.spindle.rogue.testlib.gen.{
  ThriftClaimStatus,
  ThriftLike,
  ThriftTip,
  ThriftVenue,
  ThriftVenueClaim,
  ThriftVenueClaimBson,
  ThriftVenueStatus
}
import io.fsq.spindle.rogue.testlib.gen.IdsTypedefs.{LikeId, TipId, VenueClaimId, VenueId}
import java.util.regex.Pattern
import org.bson.types.ObjectId
import org.junit.{Assert, Test}
import org.specs2.matcher.JUnitMustMatchers

/**
  * Contains tests that test the interaction of Rogue with a real mongo.
  */
class EndToEndTest extends SpindleMongoTest with JUnitMustMatchers {
  lazy val db = queryExecutor
  val Q = SpindleQuery

  def baseTestVenue(): ThriftVenue = {
    val claim1 = ThriftVenueClaimBson.newBuilder.userid(1234).status(ThriftClaimStatus.pending).result()
    val claim2 = ThriftVenueClaimBson.newBuilder.userid(5678).status(ThriftClaimStatus.approved).result()

    ThriftVenue.newBuilder
      .id(VenueId(new ObjectId)) // Note that this wasn't required in the lift model
      .legacyid(123)
      .userid(456)
      .venuename("test venue")
      .mayor(789)
      .mayor_count(3)
      .closed(false)
      .popularity(List(1, 2, 3))
      .categories(List(new ObjectId()))
      .geolatlng(List(40.73, -73.98))
      .status(ThriftVenueStatus.open)
      .claims(List(claim1, claim2))
      .lastClaim(claim2)
      .result()
  }
  def baseTestVenueClaim(vid: ObjectId): ThriftVenueClaim = {
    ThriftVenueClaim.newBuilder
      .id(VenueClaimId(new ObjectId)) // Note that this wasn't required in the lift model
      .venueId(VenueId(vid))
      .userId(123)
      .status(ThriftClaimStatus.approved)
      .result()
  }

  def baseTestTip(): ThriftTip = {
    ThriftTip.newBuilder
      .id(TipId(new ObjectId)) // Note that this wasn't required in the lift model
      .legacyid(234)
      .counts(Map("foo" -> 1, "bar" -> 2))
      .result()
  }

  @Test
  def testInsertAll {
    val v1 = baseTestVenue
    val vs = List(baseTestVenue, v1, baseTestVenue, baseTestVenue)
    db.insert(v1)
    try {
      db.insertAll(vs)
    } catch {
      case e: Throwable =>
    }
    db.count(Q(ThriftVenue)).unwrap must_== 2
  }

  @Test
  def eqsTests: Unit = {
    val v = baseTestVenue()
    db.insert(v)

    val vc = baseTestVenueClaim(v.id)
    db.insert(vc)

    db.fetch(Q(ThriftVenue).where(_.id eqs v.id)).map(_.id) must_== Vector(v.id)
    db.fetch(Q(ThriftVenue).where(_.mayor eqs v.mayor)).map(_.id) must_== Vector(v.id)
    db.fetch(Q(ThriftVenue).where(_.mayor eqs v.mayor)).map(_.id) must_== Vector(v.id)
    db.fetch(Q(ThriftVenue).where(_.venuename eqs v.venuenameOrThrow)).map(_.id) must_== Vector(v.id)
    db.fetch(Q(ThriftVenue).where(_.closed eqs false)).map(_.id) must_== Vector(v.id)

    db.fetch(Q(ThriftVenue).where(_.mayor eqs 432432)).map(_.id) must_== Nil
    db.fetch(Q(ThriftVenue).where(_.closed eqs true)).map(_.id) must_== Nil

    db.fetch(Q(ThriftVenueClaim).where(_.status eqs ThriftClaimStatus.approved)).map(_.id) must_== Vector(vc.id)
    db.fetch(Q(ThriftVenueClaim).where(_.venueId eqs v.id)).map(_.id) must_== Vector(vc.id)
    //TODO(rogue-foreign-key-support)
    //db.fetch(Q(ThriftVenueClaim).where(_.venueId eqs v)).map(_.id)                         must_== Vector(vc.id)
  }

  @Test
  def testInequalityQueries: Unit = {
    val v = db.insert(baseTestVenue())
    val vc = db.insert(baseTestVenueClaim(v.id))

    // neq,lt,gt, where the lone Venue has mayor_count=3, and the only
    // VenueClaim has status approved.
    db.fetch(Q(ThriftVenue).where(_.mayor_count neqs 5)).map(_.id) must_== Vector(v.id)
    db.fetch(Q(ThriftVenue).where(_.mayor_count < 5)).map(_.id) must_== Vector(v.id)
    db.fetch(Q(ThriftVenue).where(_.mayor_count lt 5)).map(_.id) must_== Vector(v.id)
    db.fetch(Q(ThriftVenue).where(_.mayor_count <= 5)).map(_.id) must_== Vector(v.id)
    db.fetch(Q(ThriftVenue).where(_.mayor_count lte 5)).map(_.id) must_== Vector(v.id)
    db.fetch(Q(ThriftVenue).where(_.mayor_count > 5)).map(_.id) must_== Nil
    db.fetch(Q(ThriftVenue).where(_.mayor_count gt 5)).map(_.id) must_== Nil
    db.fetch(Q(ThriftVenue).where(_.mayor_count >= 5)).map(_.id) must_== Nil
    db.fetch(Q(ThriftVenue).where(_.mayor_count gte 5)).map(_.id) must_== Nil
    db.fetch(Q(ThriftVenue).where(_.mayor_count between (3, 5))).map(_.id) must_== Vector(v.id)
    db.fetch(Q(ThriftVenueClaim).where(_.status neqs ThriftClaimStatus.approved)).map(_.id) must_== Nil
    db.fetch(Q(ThriftVenueClaim).where(_.status neqs ThriftClaimStatus.pending)).map(_.id) must_== Vector(vc.id)
  }

  @Test
  def selectQueries: Unit = {
    val v = db.insert(baseTestVenue())

    val base = Q(ThriftVenue).where(_.id eqs v.id)
    db.fetch(base.select(_.legacyid)).unwrap must_== Vector(v.legacyidOption)
    db.fetch(base.select(_.legacyid, _.userid)).unwrap must_== Vector((v.legacyidOption, v.useridOption))
    db.fetch(base.select(_.legacyid, _.userid, _.mayor)).unwrap must_== Vector(
      (v.legacyidOption, v.useridOption, v.mayorOption)
    )
    db.fetch(base.select(_.legacyid, _.userid, _.mayor, _.mayor_count)).unwrap must_== Vector(
      (v.legacyidOption, v.useridOption, v.mayorOption, v.mayor_countOption)
    )
    db.fetch(base.select(_.legacyid, _.userid, _.mayor, _.mayor_count, _.closed)).unwrap must_== Vector(
      (v.legacyidOption, v.useridOption, v.mayorOption, v.mayor_countOption, v.closedOption)
    )
    db.fetch(base.select(_.legacyid, _.userid, _.mayor, _.mayor_count, _.closed, _.tags)).unwrap must_== Vector(
      (v.legacyidOption, v.useridOption, v.mayorOption, v.mayor_countOption, v.closedOption, v.tagsOption)
    )
  }

  @Test
  def selectEnum: Unit = {
    val v = db.insert(baseTestVenue())
    db.fetch(Q(ThriftVenue).where(_.id eqs v.id).select(_.status)).unwrap must_== Vector(Some(ThriftVenueStatus.open))
  }

  @Test
  def selectCaseQueries: Unit = {
    val v = db.insert(baseTestVenue())

    val base = Q(ThriftVenue).where(_.id eqs v.id)
    db.fetch(base.selectCase(_.legacyid, Models.V1)).unwrap must_== Vector(Models.V1(v.legacyidOption))
    db.fetch(base.selectCase(_.legacyid, _.userid, Models.V2)).unwrap must_== Vector(
      Models.V2(v.legacyidOption, v.useridOption)
    )
    db.fetch(base.selectCase(_.legacyid, _.userid, _.mayor, Models.V3)).unwrap must_== Vector(
      Models.V3(v.legacyidOption, v.useridOption, v.mayorOption)
    )
    db.fetch(base.selectCase(_.legacyid, _.userid, _.mayor, _.mayor_count, Models.V4)).unwrap must_== Vector(
      Models.V4(v.legacyidOption, v.useridOption, v.mayorOption, v.mayor_countOption)
    )
    db.fetch(base.selectCase(_.legacyid, _.userid, _.mayor, _.mayor_count, _.closed, Models.V5)).unwrap must_== Vector(
      Models.V5(v.legacyidOption, v.useridOption, v.mayorOption, v.mayor_countOption, v.closedOption)
    )
    db.fetch(base.selectCase(_.legacyid, _.userid, _.mayor, _.mayor_count, _.closed, _.tags, Models.V6))
      .unwrap must_== Vector(
      Models.V6(v.legacyidOption, v.useridOption, v.mayorOption, v.mayor_countOption, v.closedOption, v.tagsOption)
    )
  }

  @Test
  def selectSubfieldQueries: Unit = {
    val v = db.insert(baseTestVenue())
    val t = db.insert(baseTestTip())

    // Sub-select on map
    db.fetch(Q(ThriftTip).where(_.id eqs t.id).select(_.counts at "foo")).unwrap must_== Vector(Some(1))
    // Sub-select on embedded record
    db.fetch(Q(ThriftVenue).where(_.id eqs v.id).select(_.lastClaim.sub.select(_.status))).unwrap must_== Vector(
      Some(ThriftClaimStatus.approved)
    )
    db.fetch(Q(ThriftVenue).where(_.id eqs v.id).select(_.lastClaim.unsafeField[ThriftClaimStatus]("status")))
      .unwrap must_== Vector(
      Some(ThriftClaimStatus.approved)
    )
    // Sub-select on embedded record
    db.fetch(Q(ThriftVenue).where(_.id eqs v.id).select(_.claims.sub.select(_.status))).unwrap must_== Vector(
      Some(Vector(Some(ThriftClaimStatus.pending), Some(ThriftClaimStatus.approved)))
    )

    val subuserids: Seq[Option[Seq[Option[Long]]]] =
      db.fetch(Q(ThriftVenue).where(_.id eqs v.id).select(_.claims.sub.select(_.userid))).unwrap
    subuserids must_== Vector(Some(Vector(Some(1234), Some(5678))))

    val subclaims: Seq[Option[Seq[ThriftVenueClaimBson]]] =
      db.fetch(Q(ThriftVenue).where(_.claims.sub.field(_.userid) eqs 1234).select(_.claims.$$)).unwrap
    subclaims.size must_== 1
    subclaims.head.isEmpty must_== false
    subclaims.head.get.size must_== 1
    subclaims.head.get.head.userid must_== 1234
    subclaims.head.get.head.statusOption must_== Some(ThriftClaimStatus.pending)

    // selecting a claims.userid when there is no top-level claims list should
    // have one element in the List for the one Venue, but an Empty for that
    // Venue since there's no list of claims there.
    db.updateOne(Q(ThriftVenue).where(_.id eqs v.id).modify(_.claims unset).and(_.lastClaim unset))
    db.fetch(Q(ThriftVenue).where(_.id eqs v.id).select(_.lastClaim.sub.select(_.userid))).unwrap must_== List(None)
    db.fetch(Q(ThriftVenue).where(_.id eqs v.id).select(_.claims.sub.select(_.userid))).unwrap must_== List(None)
  }

  def testSelectEnumSubfield: Unit = {
    val v = db.insert(baseTestVenue())

    val statuses: Seq[Option[ThriftClaimStatus]] =
      db.fetch(Q(ThriftVenue).where(_.id eqs v.id).select(_.lastClaim.sub.select(_.status))).unwrap

    statuses must_== Vector(Some(ThriftClaimStatus.approved))

    // TODO(rogue-sub-on-listfield)

    val subuseridsAndStatuses: Seq[(Option[Seq[Option[Long]]], Option[Seq[Option[ThriftClaimStatus]]])] =
      db.fetch(
          Q(ThriftVenue)
            .where(_.id eqs v.id)
            .select(_.claims.sub.select(_.userid), _.claims.sub.select(_.status))
        )
        .unwrap

    subuseridsAndStatuses must_== List(
      (Some(List(1234, 5678)), Some(List(ThriftClaimStatus.pending, ThriftClaimStatus.approved)))
    )
  }

  @Test
  def testReadPreference: Unit = {
    // Note: this isn't a real test of readpreference because the test mongo setup
    // doesn't have replicas. This basically just makes sure that readpreference
    // doesn't break everything.
    val v = db.insert(baseTestVenue())

    // eqs
    db.fetch(Q(ThriftVenue).where(_.id eqs v.id)).map(_.id) must_== Vector(v.id)
    db.fetch(Q(ThriftVenue).where(_.id eqs v.id).setReadPreference(ReadPreference.secondary)).map(_.id) must_== Vector(
      v.id
    )
    db.fetch(Q(ThriftVenue).where(_.id eqs v.id).setReadPreference(ReadPreference.primary)).map(_.id) must_== Vector(
      v.id
    )
  }

  @Test
  def testFindAndModify {
    val v1 = db
      .findAndUpsertOne(
        Q(ThriftVenue).where(_.venuename eqs "v1").findAndModify(_.userid setTo 5),
        returnNew = false
      )
      .unwrap
    v1 must_== None

    val v2 = db
      .findAndUpsertOne(
        Q(ThriftVenue).where(_.venuename eqs "v2").findAndModify(_.userid setTo 5),
        returnNew = true
      )
      .unwrap
    v2.map(_.userid) must_== Some(5)

    val v3 = db
      .findAndUpsertOne(
        Q(ThriftVenue).where(_.venuename eqs "v2").findAndModify(_.userid setTo 6),
        returnNew = false
      )
      .unwrap
    v3.map(_.userid) must_== Some(5)

    val v4 = db
      .findAndUpsertOne(
        Q(ThriftVenue).where(_.venuename eqs "v2").findAndModify(_.userid setTo 7),
        returnNew = true
      )
      .unwrap
    v4.map(_.userid) must_== Some(7)
  }

  @Test
  def testRegexQuery {
    val v = db.insert(baseTestVenue())
    db.count(Q(ThriftVenue).where(_.id eqs v.id).and(_.venuename startsWith "test v")).unwrap must_== 1
    db.count(Q(ThriftVenue).where(_.id eqs v.id).and(_.venuename matches ".es. v".r)).unwrap must_== 1
    db.count(Q(ThriftVenue).where(_.id eqs v.id).and(_.venuename matches "Tes. v".r)).unwrap must_== 0
    db.count(
        Q(ThriftVenue).where(_.id eqs v.id).and(_.venuename matches Pattern.compile("Tes. v", Pattern.CASE_INSENSITIVE))
      )
      .unwrap must_== 1
    db.count(
        Q(ThriftVenue).where(_.id eqs v.id).and(_.venuename matches "test .*".r).and(_.legacyid in List(v.legacyid))
      )
      .unwrap must_== 1
    db.count(
        Q(ThriftVenue).where(_.id eqs v.id).and(_.venuename matches "test .*".r).and(_.legacyid nin List(v.legacyid))
      )
      .unwrap must_== 0
  }

  @Test
  def testIterates {
    // Insert some data
    val vs = for (i <- 1 to 10) yield {
      db.insert(baseTestVenue().toBuilder.legacyid(i).result())
    }
    val ids = vs.map(_.id)

    val empty: Vector[ThriftVenue] = Vector.empty
    val items1 = db.iterate(Q(ThriftVenue).where(_.id in ids), empty) {
      case (accum, event) => {
        if (accum.length >= 3) {
          Iter.Return(accum)
        } else {
          event match {
            case Iter.OnNext(i) if i.legacyid % 2 == 0 => Iter.Continue(i +: accum)
            case Iter.OnNext(_) => Iter.Continue(accum)
            case Iter.OnComplete => Iter.Return(accum)
            case Iter.OnError(e) => Iter.Return(accum)
          }
        }
      }
    }

    items1.map(_.legacyid) must_== Vector(6, 4, 2)

    val items2: Vector[ThriftVenue] = db
      .iterate(Q(ThriftVenue).where(_.id in ids), (empty, empty), batchSizeOpt = Some(2))(
        IterUtil.batchCommand[Vector[ThriftVenue], ThriftVenue](2, (accum, items) => {
          if (accum.length >= 3) {
            Iter.Return(accum)
          } else {
            Iter.Continue(accum ++ items.filter(_.legacyid % 3 == 1))
          }
        })
      )
      ._1

    items2.map(_.legacyid) must_== Vector(1, 4, 7)

    def findIndexOfWithLimit(id: Long, limit: Int): Int = {
      db.iterate(Q(ThriftVenue).where(_.id in ids), 1) {
        case (idx, event) => {
          if (idx >= limit) {
            Iter.Return(-1)
          } else {
            event match {
              case Iter.OnNext(i) if i.legacyid == id => Iter.Return(idx)
              case Iter.OnNext(i) => Iter.Continue(idx + 1)
              case Iter.OnComplete => Iter.Return(-2)
              case Iter.OnError(e) => Iter.Return(-3)
            }
          }
        }
      }
    }

    findIndexOfWithLimit(5, 2) must_== -1
    findIndexOfWithLimit(5, 7) must_== 5
    findIndexOfWithLimit(11, 12) must_== -2
  }

  @Test
  def testSharding {
    val l1 = db.insert(ThriftLike.newBuilder.id(LikeId(new ObjectId)).userid(1).checkin(111).result())
    val l2 = db.insert(ThriftLike.newBuilder.id(LikeId(new ObjectId)).userid(2).checkin(111).result())
    val l3 = db.insert(ThriftLike.newBuilder.id(LikeId(new ObjectId)).userid(2).checkin(333).result())
    val l4 = db.insert(ThriftLike.newBuilder.id(LikeId(new ObjectId)).userid(2).checkin(444).result())

    // Find
    db.count(Q(ThriftLike).where(_.checkin eqs 111).allShards).unwrap must_== 2
    /* TODO(jacob): It appears shard key aware queries were never implemented for spindle?
    db.count(Q(ThriftLike).where(_.checkin eqs 111).withShardKey(_.userid eqs 1)) must_== 1
    db.count(Q(ThriftLike).withShardKey(_.userid eqs 1).where(_.checkin eqs 111)) must_== 1
    db.count(Q(ThriftLike).withShardKey(_.userid eqs 1)) must_== 1
    db.count(Q(ThriftLike).withShardKey(_.userid eqs 2)) must_== 3
    db.count(Q(ThriftLike).where(_.checkin eqs 333).withShardKey(_.userid eqs 2)) must_== 1

    // Modify
    db.updateOne(Q(ThriftLike).withShardKey(_.userid eqs 2).and(_.checkin eqs 333).modify(_.checkin setTo 334))
    Q(ThriftLike).find(l3.id).openOrThrowException("automated transition from open_!").checkin must_== 334
    db.count(Q(ThriftLike).where(_.checkin eqs 334).allShards) must_== 1

    db.updateMulti(Q(ThriftLike).where(_.checkin eqs 111).allShards.modify(_.checkin setTo 112))
    db.count(Q(ThriftLike).where(_.checkin eqs 112).withShardKey(_.userid in List(1L, 2L))) must_== 2

    val l5 = findAndUpdateOne(Q(ThriftLike).where(_.checkin eqs 112).withShardKey(_.userid eqs 1).findAndModify(_.checkin setTo 113), returnNew = true)
    l5.get.id must_== l1.id
    l5.get.checkin must_== 113
   */
  }

  @Test
  def testLimitAndBatch {
    db.insertAll(List.fill(50)(baseTestVenue()))

    val q = Q(ThriftVenue).select(_.id)
    db.fetch(q.limit(10)).length must_== 10
    db.fetch(q.limit(-10)).length must_== 10
    db.fetchBatch(q, 20)(x => Vector(x.length)).unwrap must_== Vector(20, 20, 10)
    db.fetchBatch(q.limit(35), 20)(x => Vector(x.length)).unwrap must_== Vector(20, 15)
    db.fetchBatch(q.limit(-35), 20)(x => Vector(x.length)).unwrap must_== Vector(20, 15)
  }

  @Test
  def testCount {
    db.insertAll(List.fill(10)(baseTestVenue()))
    val q = Q(ThriftVenue).select(_.id)
    db.count(q).unwrap must_== 10
    db.count(q.limit(3)).unwrap must_== 3
    db.count(q.limit(15)).unwrap must_== 10
    db.count(q.skip(5)).unwrap must_== 5
    db.count(q.skip(12)).unwrap must_== 0
    db.count(q.skip(3).limit(5)).unwrap must_== 5
    db.count(q.skip(8).limit(4)).unwrap must_== 2
  }

  @Test
  def testSlice {
    db.insert(baseTestVenue().toBuilder.tags(List("1", "2", "3", "4")).result())
    /* TODO(rogue-select-option-option): This should probably not return an option[option[list]] */
    db.fetchOne(Q(ThriftVenue).select(_.tags.slice(2))).unwrap must_== Some(Some(Vector("1", "2")))
    db.fetchOne(Q(ThriftVenue).select(_.tags.slice(-2))).unwrap must_== Some(Some(Vector("3", "4")))
    db.fetchOne(Q(ThriftVenue).select(_.tags.slice(1, 2))).unwrap must_== Some(Some(Vector("2", "3")))
  }

  @Test
  def testSave {
    val v = baseTestVenue()
    db.insert(v)
    val mutable = v.mutable
    mutable.tags_=(List("a", "bbbb"))
    db.save(mutable)

    db.count(Q(ThriftVenue).where(_.tags contains "bbbb")).unwrap must_== 1
  }

  @Test
  def testDistinct {
    db.insertAll((1 to 5).map(_ => baseTestVenue().toBuilder.userid(1).result()))
    db.insertAll((1 to 5).map(_ => baseTestVenue().toBuilder.userid(2).result()))
    db.insertAll((1 to 5).map(_ => baseTestVenue().toBuilder.userid(3).result()))
    db.distinct(Q(ThriftVenue).where(_.mayor eqs 789))(_.userid).length must_== 3
    db.countDistinct(Q(ThriftVenue).where(_.mayor eqs 789))(_.userid).unwrap must_== 3

    db.insertAll((1 to 10).map(i => baseTestVenue().toBuilder.userid(222).venuename("test " + (i % 3)).result()))
    val names = db.distinct(Q(ThriftVenue).where(_.userid eqs 222))(_.venuename).unwrap
    names.sorted must_== Vector("test 0", "test 1", "test 2")
  }

  @Test
  def testListMethods: Unit = {
    db.insertAll((1 to 5).map(_ => baseTestVenue().toBuilder.userid(1).result()))
    db.insertAll((1 to 5).map(_ => baseTestVenue().toBuilder.userid(2).result()))
    db.insertAll((1 to 5).map(_ => baseTestVenue().toBuilder.userid(3).result()))

    val vs: Seq[ThriftVenue] = db.fetch(Q(ThriftVenue).where(_.mayor eqs 789)).unwrap
    vs.size must_== 15

    val ids: Seq[VenueId] = db.fetchBatch(Q(ThriftVenue).where(_.mayor eqs 789), 3)(b => b.map(_.id)).unwrap
    ids.size must_== 15
  }

  @Test
  def testBulkInsertOne: Unit = {
    val venues = (1 to 5).map(_ => baseTestVenue())
    val venueIds = venues.map(_.id)
    val clauses = venues.map(v => BulkInsertOne(ThriftVenue, v))
    db.bulk(clauses)

    db.count(Q(ThriftVenue).where(_.id in venueIds)).unwrap must_== venues.length
  }

  @Test
  def testBulkRemoveOne: Unit = {
    val venues = (1 to 5).map(i => baseTestVenue().toBuilder().userid(i).result())
    val venueIds = venues.map(_.id)
    db.insertAll(venues)

    val (venuesToRemove, venuesToKeep) = venues.partition(_.userid % 2 == 0)
    Assert.assertTrue("No venuesToRemove", venuesToRemove.length >= 1)
    Assert.assertTrue("No venuesToKeep", venuesToKeep.length >= 1)

    val clauses = venuesToRemove.map(v => BulkRemoveOne(Q(ThriftVenue).where(_.id eqs v.id)))
    db.bulk(clauses)

    db.count(Q(ThriftVenue).where(_.id in venuesToRemove.map(_.id))).unwrap must_== 0
    db.count(Q(ThriftVenue).where(_.id in venuesToKeep.map(_.id))).unwrap must_== venuesToKeep.length
  }

  @Test
  def testBulkRemove: Unit = {
    val venues = (1 to 5).map(i => baseTestVenue().toBuilder().userid(i).result())
    val venueIds = venues.map(_.id)
    db.insertAll(venues)

    val (venuesToRemove, venuesToKeep) = venues.partition(_.userid % 2 == 0)
    Assert.assertTrue("No venuesToRemove", venuesToRemove.length >= 1)
    Assert.assertTrue("No venuesToKeep", venuesToKeep.length >= 1)

    val clauses = Vector(BulkRemove(Q(ThriftVenue).where(_.id in venuesToRemove.map(_.id))))
    db.bulk(clauses)

    db.count(Q(ThriftVenue).where(_.id in venuesToRemove.map(_.id))).unwrap must_== 0
    db.count(Q(ThriftVenue).where(_.id in venuesToKeep.map(_.id))).unwrap must_== venuesToKeep.length
  }

  @Test
  def testBulkReplaceOne: Unit = {
    val venues = (1 to 5).map(i => baseTestVenue().toBuilder().userid(i).result())
    val VenueUserIdToReplace = 31415
    val venueToReplace = baseTestVenue().toBuilder.userid(VenueUserIdToReplace).result()
    val ReplacementVenueUserId = 271828
    val replacementVenue = venueToReplace.toBuilder().userid(ReplacementVenueUserId).result()
    val venueIds = venues.map(_.id)
    db.insertAll(venueToReplace +: venues)

    Assert.assertEquals(
      "Record to replace not in the database after being inserted",
      1,
      db.count(Q(ThriftVenue).where(_.id eqs venueToReplace.id)).unwrap
    )

    val clauses = Vector(
      BulkReplaceOne(Q(ThriftVenue).where(_.userid eqs VenueUserIdToReplace), replacementVenue, upsert = false)
    )
    db.bulk(clauses)

    Assert.assertEquals(
      "Record to replace still in the database",
      0,
      db.count(Q(ThriftVenue).where(_.userid eqs VenueUserIdToReplace)).unwrap
    )
    Assert.assertEquals(
      "Replacement id not found",
      1,
      db.count(Q(ThriftVenue).where(_.userid eqs ReplacementVenueUserId)).unwrap
    )

    val upsertVenue = baseTestVenue().toBuilder().result()
    val upsertClause = {
      BulkReplaceOne(Q(ThriftVenue).where(_.id eqs upsertVenue.id), upsertVenue, upsert = true)
    }
    db.bulk(Vector(upsertClause))

    Thread.sleep(3000)

    Assert.assertEquals("Upsert did not insert", 1, db.count(Q(ThriftVenue).where(_.id eqs upsertVenue.id)).unwrap)
  }

  @Test
  def testBulkUpdateOne: Unit = {
    val original1 = baseTestVenue().toBuilder().userid(1).result()
    val original2 = baseTestVenue().toBuilder().userid(1).result()
    val nonExistantId = VenueId(new ObjectId())
    db.insertAll(Vector(original1, original2))

    val clauses = {
      Vector(
        BulkUpdateOne(Q(ThriftVenue).where(_.id eqs original1.id).modify(_.userid setTo 999), upsert = false),
        BulkUpdateOne(Q(ThriftVenue).where(_.id eqs nonExistantId).modify(_.userid setTo 999), upsert = false)
      )
    }
    db.bulk(clauses)
    Assert.assertEquals(
      "Original venue still has original value",
      0,
      db.count(Q(ThriftVenue).where(_.id eqs original1.id).and(_.userid eqs 1)).unwrap
    )
    Assert.assertEquals(
      "Original venue does not have updated value",
      1,
      db.count(Q(ThriftVenue).where(_.id eqs original1.id).and(_.userid eqs 999)).unwrap
    )
    Assert.assertEquals(
      "Non-upsert update inserted a new venue",
      0,
      db.count(Q(ThriftVenue).where(_.id eqs nonExistantId)).unwrap
    )

    val upsertVenue1 = baseTestVenue().toBuilder().result()
    val upsertVenue2 = baseTestVenue().toBuilder().result()

    val upsertClauses = {
      Vector(
        BulkUpdateOne(Q(ThriftVenue).where(_.id eqs upsertVenue1.id).modify(_.userid setTo 999), upsert = true),
        BulkUpdateOne(Q(ThriftVenue).where(_.id eqs upsertVenue2.id).modify(_.userid setTo 999), upsert = true),
        BulkUpdateOne(Q(ThriftVenue).where(_.id eqs original2.id).modify(_.userid setTo 3141591), upsert = true)
      )
    }
    db.bulk(upsertClauses)
    Assert.assertEquals(
      "Upsert did not insert both venues",
      2,
      db.count(Q(ThriftVenue).where(_.id in Vector(upsertVenue1.id, upsertVenue2.id))).unwrap
    )

    Assert.assertEquals(
      "Upsert did not update the existing venue",
      1,
      db.count(Q(ThriftVenue).where(_.id eqs original2.id).and(_.userid eqs 3141591)).unwrap
    )
  }

  @Test
  def testBulkUpdateMany: Unit = {
    val venuesToUpdate = (1 to 5).map(_ => baseTestVenue().toBuilder().userid(1).result())
    val venuesToNotUpdate = (1 to 3).map(_ => baseTestVenue().toBuilder().userid(1).result())
    db.insertAll(venuesToUpdate ++ venuesToNotUpdate)

    val clause = {
      BulkUpdateMany(Q(ThriftVenue).where(_.id in venuesToUpdate.map(_.id)).modify(_.userid setTo 999), upsert = false)
    }
    db.bulk(Vector(clause))
    Assert.assertEquals(
      "Wrong number of un-updated records",
      venuesToNotUpdate.length,
      db.count(Q(ThriftVenue).where(_.userid eqs 1)).unwrap
    )
    Assert.assertEquals(
      "Wrong number of updated records",
      venuesToUpdate.length,
      db.count(Q(ThriftVenue).where(_.userid eqs 999)).unwrap
    )

    val upsertVenue = baseTestVenue().toBuilder().result()
    val upsertClause = {
      BulkUpdateMany(Q(ThriftVenue).where(_.id eqs upsertVenue.id).modify(_.userid setTo 999), upsert = true)
    }
    db.bulk(Vector(upsertClause))
    Assert.assertEquals("Upsert did not insert", 1, db.count(Q(ThriftVenue).where(_.id eqs upsertVenue.id)).unwrap)
  }
}
