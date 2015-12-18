// Copyright 2013 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.spindle.codegen.runtime

import io.fsq.spindle.__shaded_for_spindle_bootstrap__.descriptors.{Service, ServiceProxy}

class ScalaService(override val underlying: Service, resolver: TypeReferenceResolver) extends ServiceProxy with HasAnnotations {
  val parentServiceName: Option[String] = {
    extendzOption.flatMap(extendz => resolver.resolveTypeAlias(extendz) match {
      case Right(ServiceRef(name)) => Some(name)
      case _ => throw new CodegenException("In service %s, parent service %s is not defined."
        .format(nameOption.getOrElse(throw new IllegalStateException("service missing name")), extendz))
    })
  }

  override val functions: Seq[ScalaFunction] = underlying.functions.map(new ScalaFunction(_, resolver))
}
