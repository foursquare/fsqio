// Copyright 2013 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.spindle.runtime

sealed trait IndexParseError
final case class InvalidField(fieldSpecifier: String) extends IndexParseError
final case class InvalidIndex(indexSpecifier: String) extends IndexParseError

case class IndexDescriptorEntry(fieldName: String, indexType: String)

object IndexParser {
  def parse(annotations: Annotations): Either[Seq[IndexParseError], Seq[Seq[IndexDescriptorEntry]]] = {
    val results = annotations.getAll("index").map(parseIndex)
    sequence(results).left.map(_.flatten)
  }

  def parseIndex(indexSpec: String): Either[Seq[IndexParseError], Seq[IndexDescriptorEntry]] = {
    val results = indexSpec.split(',').map(parseField)
    sequence(results)
  }

  def parseField(fieldSpec: String): Either[IndexParseError, IndexDescriptorEntry] = {
    val data = fieldSpec.split(':')
    if (data.length != 2) {
      Left(InvalidField(fieldSpec))
    } else {
      val fieldName = data(0).trim
      val indexTypeEither = data(1).trim.toLowerCase match {
        case "1" | "asc" | "ascending" => Right("1")
        case "-1" | "desc" | "descending" => Right("-1")
        case "2d" | "twod" => Right("2d")
        case "2dsphere" => Right("2dsphere")
        case "hashed" => Right("hashed")
        case "text" => Right("text")
        case indexSpecifier => Left(InvalidIndex(indexSpecifier))
      }

      for (indexType <- indexTypeEither.right) yield {
        IndexDescriptorEntry(fieldName, indexType)
      }
    }
  }

  /* Helper method to turn a sequence of results into a result of sequences.
   *
   * If the sequence has any error (Left), then the return value will be an
   * error (Left) that contains a sequence of all the errors.
   *
   * If the sequence has no errors, then the return value will be a success
   * (Right) that contains a sequence of all the successful values.
   */
  def sequence[A, B](results: Seq[Either[A, B]]): Either[Seq[A], Seq[B]] = {
    if (results.exists(_.isLeft)) {
      Left(results.flatMap(_.left.toOption))
    } else {
      Right(results.flatMap(_.right.toOption))
    }
  }
}
