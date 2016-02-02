// Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.macros

import scala.language.experimental.macros  // Scala made me do it.
import scala.language.implicitConversions
import scala.reflect.macros.blackbox.Context

class StackElement(val stackTraceElement: StackTraceElement) {
  /**
   * Uses StackTraceElement's toString implementation
   */
  override def toString: String = stackTraceElement.toString
}

object StackElement {
  /**
   * Explicit macro call provides a case class with the file and line number
   * {{{
   *  val here = STACKELEMENT
   * }}}
   */
  def STACKELEMENT: StackElement = macro stackElementImpl

  /**
   * Implicit macro definiton provides a case class with the file and line number
   * useful for passing as an implicit reference to methods
   * {{{
   *   def foo(bar: Int)(implicit caller: StackElement) {
   *     println("called foo(" + bar + ") at " + caller)
   *   }
   * }}}
   */
  implicit def materializeStackElement: StackElement = macro stackElementImpl

  def stackElementImpl(c: Context): c.Expr[StackElement] = {
    def enclosingClass(initialSymbol: c.Symbol): Option[c.Symbol] = {
      var symbol: c.Symbol = initialSymbol
      while (symbol != null && symbol != c.universe.NoSymbol && !symbol.isClass) {
        symbol = symbol.owner
      }

      if (symbol != null && symbol != c.universe.NoSymbol) {
        Some(symbol)
      } else {
        None
      }
    }

    def enclosingMethod(initialSymbol: c.Symbol): Option[c.Symbol] = {
      var symbol: c.Symbol = initialSymbol
      while (symbol != null && symbol != c.universe.NoSymbol && !symbol.isMethod) {
        symbol = symbol.owner
      }

      if (symbol != null && symbol != c.universe.NoSymbol) {
        Some(symbol)
      } else {
        None
      }
    }

    def constExpr[T](value: T): c.universe.Expr[T] = {
      c.Expr[T](c.universe.Literal(c.universe.Constant(value)))
    }

    val clazz = enclosingClass(c.internal.enclosingOwner).map(_.fullName).getOrElse("UNKNOWN_CLASS")

    val method = enclosingMethod(c.internal.enclosingOwner).map(symbol => {
      symbol.fullName.drop(clazz.length + 1)
    }).getOrElse({
      // default body constructor of class
      clazz.drop(clazz.lastIndexOf('.') + 1)
    })

    val file = c.enclosingPosition.source.file.name

    val line = c.enclosingPosition.line

    c.universe.reify { new StackElement(new StackTraceElement(
      constExpr(clazz).splice,
      constExpr(method).splice,
      constExpr(file).splice,
      constExpr(line).splice
    ))}
  }

  /**
   * Converts from StackElement to StackTraceElement, since StackTraceElement is final
   */
  implicit def stackElement2StackTraceElement(stackElement: StackElement): StackTraceElement = {
    stackElement.stackTraceElement
  }

}
