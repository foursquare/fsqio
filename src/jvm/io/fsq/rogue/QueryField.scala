// Copyright 2011 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.rogue

import com.mongodb.DBObject
import io.fsq.field.{Field, OptionalField, RequiredField}
import java.util.Date
import java.util.regex.Pattern
import org.bson.types.ObjectId
import org.joda.time.DateTime
import scala.util.matching.Regex

object CondOps extends Enumeration {
  type Op = Value
  val Ne: Op = Value("$ne")
  val Lt: Op = Value("$lt")
  val Gt: Op = Value("$gt")
  val LtEq: Op = Value("$lte")
  val GtEq: Op = Value("$gte")
  val In: Op = Value("$in")
  val Nin: Op = Value("$nin")
  val Near: Op = Value("$near")
  val All: Op = Value("$all")
  val Size: Op = Value("$size")
  val Exists: Op = Value("$exists")
  val Type: Op = Value("$type")
  val Mod: Op = Value("$mod")
  val NearSphere: Op = Value("$nearSphere")
  val MaxDistance: Op = Value("$maxDistance")
}

object ModOps extends Enumeration {
  type Op = Value
  val Inc: Op = Value("$inc")
  val Mul: Op = Value("$mul")
  val Set: Op = Value("$set")
  val SetOnInsert: Op = Value("$setOnInsert")
  val Unset: Op = Value("$unset")
  val Push: Op = Value("$push")
  val AddToSet: Op = Value("$addToSet")
  val Pop: Op = Value("$pop")
  val Pull: Op = Value("$pull")
  val PullAll: Op = Value("$pullAll")
  val Bit: Op = Value("$bit")
  val Rename: Op = Value("$rename")
  val Min: Op = Value("$min")
  val Max: Op = Value("$max")
  val CurrentDate: Op = Value("$currentDate")
}

object BitOps extends Enumeration {
  type Op = Value
  val And: Op = Value("and")
  val Or: Op = Value("or")
  val Xor: Op = Value("xor")
}

object MongoType extends Enumeration {
  type MongoType = Value
  val Double: MongoType = Value(1)
  val String: MongoType = Value(2)
  val Object: MongoType = Value(3)
  val Array: MongoType = Value(4)
  val Binary: MongoType = Value(5)
  val ObjectId: MongoType = Value(7)
  val Boolean: MongoType = Value(8)
  val Date: MongoType = Value(9)
  val Null: MongoType = Value(10)
  val RegEx: MongoType = Value(11)
  val JavaScript: MongoType = Value(13)
  val Symbol: MongoType = Value(15)
  val Int32: MongoType = Value(16)
  val Timestamp: MongoType = Value(17)
  val Int64: MongoType = Value(18)
  val MaxKey: MongoType = Value(127)
  val MinKey: MongoType = Value(255)
}

// ********************************************************************************
// *** Query fields
// ********************************************************************************
//
// The types of fields that can be queried, and the particular query operations that each supports
// are defined below.

/**
  * Trait representing a field and all the operations on it.
  *
  * @tparam F the underlying type of the field
  * @tparam V the type of values allowed to be compared to the field
  * @tparam DB the type V is converted into in the BSON representation of the field
  * @tparam M the type of the owner of the field
  */
abstract class AbstractQueryField[F, V, DB, M](val field: Field[F, M]) {
  def valueToDB(v: V): DB
  def valuesToDB(vs: Traversable[V]): Traversable[DB] = vs.map(valueToDB)

  def eqs(v: V): EqClause[DB, Nothing] = EqClause(field.name, valueToDB(v))
  def neqs(v: V) = new NeQueryClause(field.name, valueToDB(v))
  def in[L <% Traversable[V]](vs: L): IndexableQueryClause[java.util.List[DB], Index] =
    QueryHelpers.inListClause(field.name, valuesToDB(vs))
  def nin[L <% Traversable[V]](vs: L) = new NinQueryClause(field.name, QueryHelpers.validatedList(valuesToDB(vs)))

  def lt(v: V) = new LtQueryClause(field.name, valueToDB(v))
  def gt(v: V) = new GtQueryClause(field.name, valueToDB(v))
  def lte(v: V) = new LtEqQueryClause(field.name, valueToDB(v))
  def gte(v: V) = new GtEqQueryClause(field.name, valueToDB(v))

  def <(v: V): LtQueryClause[DB] = lt(v)
  def <=(v: V): LtEqQueryClause[DB] = lte(v)
  def >(v: V): GtQueryClause[DB] = gt(v)
  def >=(v: V): GtEqQueryClause[DB] = gte(v)

  def between(v1: V, v2: V) =
    new BetweenQueryClause(field.name, valueToDB(v1), valueToDB(v2))

  def between(range: (V, V)) =
    new BetweenQueryClause(field.name, valueToDB(range._1), valueToDB(range._2))

  def exists(b: Boolean) = new ExistsQueryClause(field.name, b)
  def hastype(t: MongoType.Value) = new TypeQueryClause(field.name, t)

  def not[F2](clause: Field[F, M] => QueryClause[F2]): QueryClause[F2] = {
    val c = clause(field)
    c.negated = true
    c
  }
}

class QueryField[V: BSONType, M](field: Field[V, M]) extends AbstractQueryField[V, V, AnyRef, M](field) {
  override def valueToDB(v: V): AnyRef = BSONType[V].asBSONObject(v)
}

class DateQueryField[M](field: Field[Date, M]) extends AbstractQueryField[Date, DateTime, Date, M](field) {
  override def valueToDB(d: DateTime): Date = d.toDate

  def eqs(d: Date): EqClause[Date, Nothing] = EqClause(field.name, d)
  def neqs(d: Date) = new NeQueryClause(field.name, d)

  def between(d1: Date, d2: Date) =
    new BetweenQueryClause(field.name, d1, d2)

  def before(d: Date) = new LtQueryClause(field.name, d)
  def after(d: Date) = new GtQueryClause(field.name, d)
  def onOrBefore(d: Date) = new LtEqQueryClause(field.name, d)
  def onOrAfter(d: Date) = new GtEqQueryClause(field.name, d)

  def before(d: DateTime) = new LtQueryClause(field.name, d.toDate)
  def after(d: DateTime) = new GtQueryClause(field.name, d.toDate)
  def onOrBefore(d: DateTime) = new LtEqQueryClause(field.name, d.toDate)
  def onOrAfter(d: DateTime) = new GtEqQueryClause(field.name, d.toDate)
}

class DateTimeQueryField[M](field: Field[DateTime, M]) extends AbstractQueryField[DateTime, DateTime, Date, M](field) {
  override def valueToDB(d: DateTime): Date = d.toDate

  def before(d: DateTime) = new LtQueryClause(field.name, d.toDate)
  def after(d: DateTime) = new GtQueryClause(field.name, d.toDate)
  def onOrBefore(d: DateTime) = new LtEqQueryClause(field.name, d.toDate)
  def onOrAfter(d: DateTime) = new GtEqQueryClause(field.name, d.toDate)
}

class EnumNameQueryField[M, E <: Enumeration#Value](field: Field[E, M])
  extends AbstractQueryField[E, E, String, M](field) {
  override def valueToDB(e: E): String = e.toString
}

class EnumIdQueryField[M, E <: Enumeration#Value](field: Field[E, M]) extends AbstractQueryField[E, E, Int, M](field) {
  override def valueToDB(e: E): Int = e.id
}

class GeoQueryField[M](field: Field[LatLong, M])
  extends AbstractQueryField[LatLong, LatLong, java.util.List[Double], M](field) {
  override def valueToDB(ll: LatLong): java.util.List[Double] =
    QueryHelpers.list(List(ll.lat, ll.long))

  def eqs(lat: Double, lng: Double): EqClause[java.util.List[Double], Nothing] =
    EqClause(field.name, QueryHelpers.list(List(lat, lng)))

  def neqs(lat: Double, lng: Double) =
    new NeQueryClause(field.name, QueryHelpers.list(List(lat, lng)))

  def near(lat: Double, lng: Double, radius: Degrees) =
    new NearQueryClause(field.name, QueryHelpers.list(List(lat, lng, QueryHelpers.radius(radius))))

  def nearSphere(lat: Double, lng: Double, radians: Radians) =
    new NearSphereQueryClause(field.name, lat, lng, radians)

  def withinCircle(lat: Double, lng: Double, radius: Degrees) =
    new WithinCircleClause(field.name, lat, lng, QueryHelpers.radius(radius))

  def withinBox(lat1: Double, lng1: Double, lat2: Double, lng2: Double) =
    new WithinBoxClause(field.name, lat1, lng1, lat2, lng2)
}

class NumericQueryField[V, M](field: Field[V, M]) extends AbstractQueryField[V, V, V, M](field) {
  def mod(by: Int, eq: Int) =
    new ModQueryClause(field.name, QueryHelpers.list(List(by, eq)))
  override def valueToDB(v: V): V = v
}

class ObjectIdQueryField[F <: ObjectId, M](override val field: Field[F, M]) extends NumericQueryField(field) {
  def before(d: DateTime) =
    new LtQueryClause(field.name, new ObjectId((d.toDate.getTime / 1000).toInt, 0))

  def after(d: DateTime) =
    new GtQueryClause(field.name, new ObjectId((d.toDate.getTime / 1000).toInt, 0))

  def between(d1: DateTime, d2: DateTime) =
    new StrictBetweenQueryClause(
      field.name,
      new ObjectId((d1.toDate.getTime / 1000).toInt, 0),
      new ObjectId((d2.toDate.getTime / 1000).toInt, 0)
    )

  def between(range: (DateTime, DateTime)) =
    new StrictBetweenQueryClause(
      field.name,
      new ObjectId((range._1.toDate.getTime / 1000).toInt, 0),
      new ObjectId((range._2.toDate.getTime / 1000).toInt, 0)
    )
}

class ForeignObjectIdQueryField[F <: ObjectId, M, T](
  override val field: Field[F, M],
  val getId: T => F
) extends ObjectIdQueryField[F, M](field) {
  // The implicit parameter is solely to get around the fact that because of
  // erasure, this method and the method in AbstractQueryField look the same.
  def eqs(obj: T)(implicit ev: T =:= T): EqClause[F, Nothing] =
    EqClause(field.name, getId(obj))

  // The implicit parameter is solely to get around the fact that because of
  // erasure, this method and the method in AbstractQueryField look the same.
  def neqs(obj: T)(implicit ev: T =:= T) =
    new NeQueryClause(field.name, getId(obj))

  // The implicit parameter is solely to get around the fact that because of
  // erasure, this method and the method in AbstractQueryField look the same.
  def in(objs: Traversable[T])(implicit ev: T =:= T): IndexableQueryClause[java.util.List[F], Index] =
    QueryHelpers.inListClause(field.name, objs.map(getId))

  // The implicit parameter is solely to get around the fact that because of
  // erasure, this method and the method in AbstractQueryField look the same.
  def nin(objs: Traversable[T])(implicit ev: T =:= T) =
    new NinQueryClause(field.name, QueryHelpers.validatedList(objs.map(getId)))
}

trait StringRegexOps[V, M] {
  self: AbstractQueryField[V, _ <: String, _ <: String, M] =>

  def startsWith(s: String): RegexQueryClause[PartialIndexScan] =
    new RegexQueryClause[PartialIndexScan](field.name, PartialIndexScan, Pattern.compile("^" + Pattern.quote(s)))

  def matches(p: Pattern): RegexQueryClause[DocumentScan] =
    new RegexQueryClause[DocumentScan](field.name, DocumentScan, p)

  def matches(r: Regex): RegexQueryClause[DocumentScan] =
    matches(r.pattern)

  def regexWarningNotIndexed(p: Pattern): RegexQueryClause[DocumentScan] =
    matches(p)
}

class StringQueryField[F <: String, M](override val field: Field[F, M])
  extends AbstractQueryField[F, F, F, M](field)
  with StringRegexOps[F, M] {

  override def valueToDB(v: F): F = v
}

class BsonRecordQueryField[M, B](field: Field[B, M], asDBObject: B => DBObject, defaultValue: B)
  extends AbstractQueryField[B, B, DBObject, M](field) {
  override def valueToDB(b: B): DBObject = asDBObject(b)

  def subfield[V](subfield: B => Field[V, B]): SelectableDummyField[V, M] = {
    new SelectableDummyField[V, M](field.name + "." + subfield(defaultValue).name, field.owner)
  }

  def unsafeField[V](name: String): DummyField[V, M] = {
    new DummyField[V, M](field.name + "." + name, field.owner)
  }

  def subselect[V](f: B => Field[V, B]): SelectableDummyField[V, M] = subfield(f)
}

abstract class AbstractListQueryField[F, V, DB, M, CC[X] <: Iterable[X]](field: Field[CC[F], M])
  extends AbstractQueryField[CC[F], V, DB, M](field) {

  def all(vs: Traversable[V]): IndexableQueryClause[java.util.List[DB], Index] =
    QueryHelpers.allListClause(field.name, valuesToDB(vs))

  def eqs(vs: Traversable[V]): EqClause[java.util.List[DB], Nothing] =
    EqClause(field.name, QueryHelpers.validatedList(valuesToDB(vs)))

  def neqs(vs: Traversable[V]) =
    new NeQueryClause(field.name, QueryHelpers.validatedList(valuesToDB(vs)))

  def size(s: Int) =
    new SizeQueryClause(field.name, s)

  def contains(v: V): EqClause[DB, Nothing] =
    EqClause(field.name, valueToDB(v))

  def notcontains(v: V) =
    new NeQueryClause(field.name, valueToDB(v))

  def at(i: Int): DummyField[V, M] =
    new DummyField[V, M](field.name + "." + i.toString, field.owner)

  def idx(i: Int): DummyField[V, M] = at(i)
}

class ListQueryField[V: BSONType, M](field: Field[List[V], M])
  extends AbstractListQueryField[V, V, AnyRef, M, List](field) {
  override def valueToDB(v: V): AnyRef = BSONType[V].asBSONObject(v)
}

class StringsListQueryField[M](override val field: Field[List[String], M])
  extends AbstractListQueryField[String, String, String, M, List](field)
  with StringRegexOps[List[String], M] {

  override def valueToDB(v: String): String = v
}

class SeqQueryField[V: BSONType, M](field: Field[Seq[V], M])
  extends AbstractListQueryField[V, V, AnyRef, M, Seq](field) {
  override def valueToDB(v: V): AnyRef = BSONType[V].asBSONObject(v)
}

class SetQueryField[V: BSONType, M](field: Field[Set[V], M])
  extends AbstractListQueryField[V, V, AnyRef, M, Set](field) {
  override def valueToDB(v: V): AnyRef = BSONType[V].asBSONObject(v)
}

class BsonRecordListQueryField[M, B](field: Field[List[B], M], rec: B, asDBObject: B => DBObject)
  extends AbstractListQueryField[B, B, DBObject, M, List](field) {
  override def valueToDB(b: B): DBObject = asDBObject(b)

  def subfield[V, V1](f: B => Field[V, B])(implicit ev: Rogue.Flattened[V, V1]): SelectableDummyField[List[V1], M] = {
    new SelectableDummyField[List[V1], M](field.name + "." + f(rec).name, field.owner)
  }

  def subselect[V, V1](f: B => Field[V, B])(implicit ev: Rogue.Flattened[V, V1]): SelectField[Option[List[V1]], M] = {
    Rogue.roptionalFieldToSelectField(subfield(f))
  }

  def unsafeField[V](name: String): DummyField[V, M] = {
    new DummyField[V, M](field.name + "." + name, field.owner)
  }

  def elemMatch[V](clauseFuncs: (B => QueryClause[_])*): ElemMatchWithPredicateClause[Nothing] = {
    new ElemMatchWithPredicateClause(
      field.name,
      clauseFuncs.map(cf => cf(rec))
    )
  }
}

class MapQueryField[V, M](val field: Field[Map[String, V], M]) {
  def at(key: String): SelectableDummyField[V, M] = {
    new SelectableDummyField(field.name + "." + key, field.owner)
  }
}

class EnumerationListQueryField[V <: Enumeration#Value, M](field: Field[List[V], M])
  extends AbstractListQueryField[V, V, String, M, List](field) {
  override def valueToDB(v: V): String = v.toString
}

class EnumerationSetQueryField[V <: Enumeration#Value, M](field: Field[Set[V], M])
  extends AbstractListQueryField[V, V, String, M, Set](field) {
  override def valueToDB(v: V): String = v.toString
}

// ********************************************************************************
// *** Modify fields
// ********************************************************************************

class SafeModifyField[V, M](val field: Field[V, M]) {
  def unset = new ModifyClause(ModOps.Unset, field.name -> 1)
  def rename(newName: String) = new ModifyClause(ModOps.Rename, field.name -> newName)
}

abstract class AbstractModifyField[V, DB, M](val field: Field[V, M]) {
  def valueToDB(v: V): DB
  def setTo(v: V): ModifyClause = new ModifyClause(ModOps.Set, field.name -> valueToDB(v))
  def setTo(vOpt: Option[V]): ModifyClause = vOpt match {
    case Some(v) => setTo(v)
    case none => new SafeModifyField(field).unset
  }

  def setOnInsertTo(v: V): ModifyClause = new ModifyClause(ModOps.SetOnInsert, field.name -> valueToDB(v))

  def min(v: V) = new ModifyClause(ModOps.Min, field.name -> valueToDB(v))
  def max(v: V) = new ModifyClause(ModOps.Max, field.name -> valueToDB(v))
}

class ModifyField[V: BSONType, M](field: Field[V, M]) extends AbstractModifyField[V, AnyRef, M](field) {
  override def valueToDB(v: V): AnyRef = BSONType[V].asBSONObject(v)
}

class DateModifyField[M](field: Field[Date, M]) extends AbstractModifyField[Date, Date, M](field) {
  override def valueToDB(d: Date): Date = d

  def setTo(d: DateTime) = new ModifyClause(ModOps.Set, field.name -> d.toDate)
  def setOnInsertTo(d: DateTime): ModifyClause = new ModifyClause(ModOps.SetOnInsert, field.name -> d.toDate)
  def min(d: DateTime) = new ModifyClause(ModOps.Min, field.name -> d.toDate)
  def max(d: DateTime) = new ModifyClause(ModOps.Max, field.name -> d.toDate)

  def currentDate = new ModifyClause(ModOps.CurrentDate, field.name -> true)
}

class DateTimeModifyField[M](field: Field[DateTime, M]) extends AbstractModifyField[DateTime, Date, M](field) {
  override def valueToDB(d: DateTime): Date = d.toDate

  def currentDate = new ModifyClause(ModOps.CurrentDate, field.name -> true)
}

class EnumerationModifyField[M, E <: Enumeration#Value](field: Field[E, M])
  extends AbstractModifyField[E, String, M](field) {
  override def valueToDB(e: E): String = e.toString
}

class GeoModifyField[M](field: Field[LatLong, M])
  extends AbstractModifyField[LatLong, java.util.List[Double], M](field) {
  override def valueToDB(ll: LatLong): java.util.List[Double] =
    QueryHelpers.list(List(ll.lat, ll.long))

  def setTo(lat: Double, long: Double) =
    new ModifyClause(ModOps.Set, field.name -> QueryHelpers.list(List(lat, long)))
}

class NumericModifyField[V, M](override val field: Field[V, M]) extends AbstractModifyField[V, V, M](field) {
  override def valueToDB(v: V): V = v

  def inc(v: Int) = new ModifyClause(ModOps.Inc, field.name -> v)
  def inc(v: Long) = new ModifyClause(ModOps.Inc, field.name -> v)
  def inc(v: Double) = new ModifyClause(ModOps.Inc, field.name -> v)
  def mul(v: Int) = new ModifyClause(ModOps.Mul, field.name -> v)
  def mul(v: Long) = new ModifyClause(ModOps.Mul, field.name -> v)
  def mul(v: Double) = new ModifyClause(ModOps.Mul, field.name -> v)

  def bitAnd(v: Int) = new ModifyBitClause(field.name, v, BitOps.And)
  def bitOr(v: Int) = new ModifyBitClause(field.name, v, BitOps.Or)
  def bitXor(v: Int) = new ModifyBitClause(field.name, v, BitOps.Xor)

  def bitAnd(v: Long) = new ModifyLongBitClause(field.name, v, BitOps.And)
  def bitOr(v: Long) = new ModifyLongBitClause(field.name, v, BitOps.Or)
  def bitXor(v: Long) = new ModifyLongBitClause(field.name, v, BitOps.Xor)
}

class BsonRecordModifyField[M, B](field: Field[B, M], asDBObject: B => DBObject)
  extends AbstractModifyField[B, DBObject, M](field) {
  override def valueToDB(b: B): DBObject = asDBObject(b)
}

class MapModifyField[V, M](field: Field[Map[String, V], M])
  extends AbstractModifyField[Map[String, V], java.util.Map[String, V], M](field) {
  override def valueToDB(m: Map[String, V]): java.util.Map[String, V] = QueryHelpers.makeJavaMap(m)
}

abstract class AbstractListModifyField[V, DB, M, CC[X] <: Iterable[X]](val field: Field[CC[V], M]) {
  def valueToDB(v: V): DB

  def valuesToDB(vs: Traversable[V]): Traversable[DB] = vs.map(valueToDB _)

  def setTo(vs: Traversable[V]) =
    new ModifyClause(ModOps.Set, field.name -> QueryHelpers.list(valuesToDB(vs)))

  def setOnInsertTo(vs: Traversable[V]): ModifyClause =
    new ModifyClause(ModOps.SetOnInsert, field.name -> QueryHelpers.list(valuesToDB(vs)))

  def push(v: V) =
    new ModifyClause(ModOps.Push, field.name -> valueToDB(v))

  def push(vs: Traversable[V]) =
    new ModifyPushEachClause(field.name, valuesToDB(vs))

  def push(vs: Traversable[V], slice: Int) =
    new ModifyPushEachSliceClause(field.name, slice, valuesToDB(vs))

  def addToSet(v: V) =
    new ModifyClause(ModOps.AddToSet, field.name -> valueToDB(v))

  def addToSet(vs: Traversable[V]) =
    new ModifyAddEachClause(field.name, valuesToDB(vs))

  def popFirst =
    new ModifyClause(ModOps.Pop, field.name -> -1)

  def popLast =
    new ModifyClause(ModOps.Pop, field.name -> 1)

  def pull(v: V) =
    new ModifyClause(ModOps.Pull, field.name -> valueToDB(v))

  def pullAll(vs: Traversable[V]) =
    new ModifyClause(ModOps.PullAll, field.name -> QueryHelpers.list(valuesToDB(vs)))

  def pullWhere(clauseFuncs: (Field[V, M] => QueryClause[_])*) =
    new ModifyPullWithPredicateClause(
      field.name,
      clauseFuncs.map(cf => cf(new DummyField[V, M](field.name, field.owner)))
    )

  def $ : Field[V, M] = {
    new SelectableDummyField[V, M](field.name + ".$", field.owner)
  }
}

class SeqModifyField[V: BSONType, M](field: Field[Seq[V], M])
  extends AbstractListModifyField[V, AnyRef, M, Seq](field) {
  override def valueToDB(v: V): AnyRef = BSONType[V].asBSONObject(v)
}

class ListModifyField[V: BSONType, M](field: Field[List[V], M])
  extends AbstractListModifyField[V, AnyRef, M, List](field) {
  override def valueToDB(v: V): AnyRef = BSONType[V].asBSONObject(v)
}

class SetModifyField[V: BSONType, M](field: Field[Set[V], M])
  extends AbstractListModifyField[V, AnyRef, M, Set](field) {
  override def valueToDB(v: V): AnyRef = BSONType[V].asBSONObject(v)
}

class EnumerationListModifyField[V <: Enumeration#Value, M](field: Field[List[V], M])
  extends AbstractListModifyField[V, String, M, List](field) {
  override def valueToDB(v: V): String = v.toString
}

class EnumerationSetModifyField[V <: Enumeration#Value, M](field: Field[Set[V], M])
  extends AbstractListModifyField[V, String, M, Set](field) {
  override def valueToDB(v: V): String = v.toString
}

class BsonRecordListModifyField[M, B](field: Field[List[B], M], rec: B, asDBObject: B => DBObject)(
  implicit mf: Manifest[B]
) extends AbstractListModifyField[B, DBObject, M, List](field) {
  override def valueToDB(b: B): DBObject = asDBObject(b)

  // override def $: BsonRecordField[M, B] = {
  //   new BsonRecordField[M, B](field.owner, rec.meta)(mf) {
  //     override def name = field.name + ".$"
  //   }
  // }

  def pullObjectWhere(clauseFuncs: (B => QueryClause[_])*): ModifyPullObjWithPredicateClause[Nothing] = {
    new ModifyPullObjWithPredicateClause(
      field.name,
      clauseFuncs.map(cf => cf(rec))
    )
  }
}

// ********************************************************************************
// *** Select fields
// ********************************************************************************

/**
  * Fields that can be turned into SelectFields can be used in a .select call.
  *
  * This class is sealed because only RequiredFields and OptionalFields should
  * be selectable. Be careful when adding subclasses of this class.
  */
sealed abstract class SelectField[V, M](val field: Field[_, M], val slc: Option[(Int, Option[Int])] = None) {
  // Input will be a Box of the value, and output will either be a Box of the value or the value itself
  def valueOrDefault(v: Option[_]): Any
  def slice(s: Int): SelectField[V, M]
  def slice(s: Int, e: Int): SelectField[V, M]
  def $$ : SelectField[V, M]
}

final class MandatorySelectField[V, M](
  override val field: RequiredField[V, M],
  override val slc: Option[(Int, Option[Int])] = None
) extends SelectField[V, M](field, slc) {
  override def valueOrDefault(v: Option[_]): Any = v.getOrElse(field.defaultValue)
  override def slice(s: Int): MandatorySelectField[V, M] = {
    new MandatorySelectField(field, Some((s, None)))
  }
  override def slice(s: Int, e: Int): MandatorySelectField[V, M] = {
    new MandatorySelectField(field, Some((s, Some(e))))
  }
  def $$ : MandatorySelectField[V, M] = {
    val fld = new RequiredDummyField[V, M](field.name + ".$", field.owner, field.defaultValue)
    new MandatorySelectField(fld, slc)
  }
}

final class OptionalSelectField[V, M](
  override val field: OptionalField[V, M],
  override val slc: Option[(Int, Option[Int])] = None
) extends SelectField[Option[V], M](field, slc) {
  override def valueOrDefault(v: Option[_]): Any = v
  override def slice(s: Int): OptionalSelectField[V, M] = {
    new OptionalSelectField(field, Some((s, None)))
  }
  override def slice(s: Int, e: Int): OptionalSelectField[V, M] = {
    new OptionalSelectField(field, Some((s, Some(e))))
  }
  def $$ : OptionalSelectField[V, M] = {
    val fld = new SelectableDummyField[V, M](field.name + ".$", field.owner)
    new OptionalSelectField(fld, slc)
  }
}

// ********************************************************************************
// *** Dummy field
// ********************************************************************************

class DummyField[V, R](override val name: String, override val owner: R) extends Field[V, R]

class SelectableDummyField[V, R](override val name: String, override val owner: R) extends OptionalField[V, R]

class RequiredDummyField[V, R](override val name: String, override val owner: R, override val defaultValue: V)
  extends RequiredField[V, R]
