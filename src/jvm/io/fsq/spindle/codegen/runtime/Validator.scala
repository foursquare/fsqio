// Copyright 2022 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.spindle.codegen.runtime

object Validator {

  /** Ensures Thrift `shard_key` annotations are correctly formatted and specify valid fields.
    *
    * NOTE(ali): This does not validate subfields; e.g., if the shard key is an embedded document like `id.innerId`,
    * this will only validate the `id` part.
    *
    * @param shardKeyAnnotations Thrift shard key annotations string to be parsed and validated
    * @param fieldNames sequence of all Thrift field names
    * @param className Thrift class name used for error handling
    * @throws CodegenException if shard key annotation format is invalid
    * @throws CodegenException if a compound shard key annotation is detected; e.g., `id:1,u:1`
    * @throws CodegenException if provided shard key field names don't exist in class field names
    */
  def validateShardKeyAnnotations(shardKeyAnnotations: String, fieldNames: Seq[String], className: String): Unit = {
    val annotationParts: Seq[String] = shardKeyAnnotations.split(',')
    annotationParts match {
      case Seq(singleFieldShardKey) => validateShardKeyParts(singleFieldShardKey, fieldNames, className)
      case _ =>
        throw new CodegenException(
          s"Invalid shard_key specifier: $shardKeyAnnotations for class $className; " +
            s"compound shard keys are not yet supported"
        )
    }
  }

  private def validateShardKeyParts(shardKeyAnnotation: String, fieldNames: Seq[String], className: String): Unit = {
    val shardKeyParts: Seq[String] = shardKeyAnnotation.split(':')
    shardKeyParts match {
      case Seq(shardKeyFields, _) => {
        val shardKeyFieldName: String = shardKeyFields.split('.').head
        if (!fieldNames.contains(shardKeyFieldName)) {
          throw new CodegenException(
            s"Unknown field name '$shardKeyFieldName' in shard_key annotation for class $className"
          )
        }
      }
      case _ =>
        throw new CodegenException(
          s"Invalid shard_key specifier '$shardKeyAnnotation' for class $className -- format must be " +
            s"FIELD_NAME:SHARD_TYPE; e.g., `id:hashed`"
        )
    }
  }
}
