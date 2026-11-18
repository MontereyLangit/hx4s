package hx4s.props

import scala.scalajs.js
import scala.scalajs.js.annotation.JSGlobal

/** Kinds of values that can be passed through JSON. Num is a finite number, Int an integer in the Int range */
enum JsKind {
  case Str, Num, Int, Bool
  case Obj(fields: List[JsField])
  case Arr(element: JsKind)
}

/** One key of an object. optional allows undefined (an omitted key) */
final case class JsField(name: String, kind: JsKind, optional: Boolean = false)

@js.native
@JSGlobal("console")
private object Console extends js.Object {
  def error(message: String): Unit = js.native
}

/** Checks that a JS value received at a boundary has the expected keys and types, and turns it into a typed value */
object JsShape {
  def required(name: String, kind: JsKind): JsField = JsField(name, kind)
  def optional(name: String, kind: JsKind): JsField = JsField(name, kind, optional = true)

  /** Checks every key, and returns the list of errors as one string if any */
  def validate[P <: js.Object](raw: js.Any, fields: List[JsField]): Either[String, P] =
    problems(raw, JsKind.Obj(fields), "props") match {
      case Nil => Right(raw.asInstanceOf[P])
      case found => Left(found.mkString("; "))
    }

  /** On validation failure, logs to console.error and returns None. Does not throw */
  def read[P <: js.Object](label: String, raw: js.Any, fields: List[JsField]): Option[P] =
    validate[P](raw, fields) match {
      case Right(value) => Some(value)
      case Left(message) =>
        Console.error(s"[$label] invalid props: $message")
        None
    }

  private def problems(value: js.Any, kind: JsKind, path: String): List[String] = {
    val actual = describe(value)
    def expect(name: String): List[String] = if (actual == name) Nil else List(s"$path: expected $name, got $actual")
    def number(check: Double => Option[String]): List[String] =
      if (actual != "number") expect("number")
      else check(value.asInstanceOf[Double]).map(problem => s"$path: $problem").toList
    kind match {
      case JsKind.Str => expect("string")
      case JsKind.Num => number(n => Option.when(n.isNaN || n.isInfinite)(s"expected finite number, got $n"))
      case JsKind.Int =>
        number(n => Option.unless(n.isWhole && n >= scala.Int.MinValue && n <= scala.Int.MaxValue)(s"expected integer in Int range, got $n"))
      case JsKind.Bool => expect("boolean")
      case JsKind.Arr(element) =>
        if (actual != "array") List(s"$path: expected array, got $actual")
        else value.asInstanceOf[js.Array[js.Any]].toList.zipWithIndex.flatMap { case (item, index) => problems(item, element, s"$path[$index]") }
      case JsKind.Obj(fields) =>
        if (actual != "object") List(s"$path: expected object, got $actual")
        else {
          val dictionary = value.asInstanceOf[js.Dictionary[js.Any]]
          fields.flatMap { field =>
            val child = s"$path.${field.name}"
            dictionary.get(field.name).filterNot(js.isUndefined) match {
              case None => if (field.optional) Nil else List(s"$child: missing")
              case Some(item) => problems(item, field.kind, child)
            }
          }
        }
    }
  }

  private def describe(value: js.Any): String = {
    if (value == null) "null"
    else if (js.Array.isArray(value)) "array"
    else js.typeOf(value)
  }
}
