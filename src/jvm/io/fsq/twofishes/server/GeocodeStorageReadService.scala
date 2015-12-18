// Copyright 2012 Foursquare Labs Inc. All Rights Reserved.
package io.fsq.twofishes.server

import com.vividsolutions.jts.geom.Geometry
import io.fsq.twofishes.gen.{CellGeometry, GeocodeServingFeature}
import io.fsq.twofishes.util.StoredFeatureId

trait GeocodeStorageReadService {
  def getIdsByName(name: String): Seq[StoredFeatureId]
  def getIdsByNamePrefix(name: String): Seq[StoredFeatureId]
  def getByName(name: String): Seq[GeocodeServingFeature]
  def getByFeatureIds(ids: Seq[StoredFeatureId]): Map[StoredFeatureId, GeocodeServingFeature]

  def getBySlugOrFeatureIds(ids: Seq[String]): Map[String, GeocodeServingFeature]

  def getMinS2Level: Int
  def getMaxS2Level: Int
  def getLevelMod: Int
  def getByS2CellId(id: Long): Seq[CellGeometry]
  def getPolygonByFeatureId(id: StoredFeatureId): Option[Geometry]
  def getPolygonByFeatureIds(ids: Seq[StoredFeatureId]): Map[StoredFeatureId, Geometry]
  def getS2CoveringByFeatureId(id: StoredFeatureId): Option[Seq[Long]]
  def getS2CoveringByFeatureIds(ids: Seq[StoredFeatureId]): Map[StoredFeatureId, Seq[Long]]
  def getS2InteriorByFeatureId(id: StoredFeatureId): Option[Seq[Long]]
  def getS2InteriorByFeatureIds(ids: Seq[StoredFeatureId]): Map[StoredFeatureId, Seq[Long]]

  def refresh(): Unit
}
