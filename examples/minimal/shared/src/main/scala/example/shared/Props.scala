package example.shared

import hx4s.props.{JsField, JsKind}
import hx4s.props.JsShape.{optional, required}

import scala.scalajs.js

/** Counter props */
trait CounterProps extends js.Object {
  val label: String
  val start: Int
}

object CounterProps {
  val fields: List[JsField] = List(required("label", JsKind.Str), required("start", JsKind.Int))
}

/** One item in the list */
trait ItemProps extends js.Object {
  val id: String
  val name: String
  val tags: js.Array[String]
}

/** Owner of the list (an example of nested props) */
trait OwnerProps extends js.Object {
  val name: String
  val since: Int
}

/** Props of a list with arrays, nesting and optional keys */
trait ItemListProps extends js.Object {
  val title: String
  val owner: OwnerProps
  val items: js.Array[ItemProps]
  val note: js.UndefOr[String]
}

object ItemListProps {
  private val item = JsKind.Obj(List(required("id", JsKind.Str), required("name", JsKind.Str), required("tags", JsKind.Arr(JsKind.Str))))
  private val owner = JsKind.Obj(List(required("name", JsKind.Str), required("since", JsKind.Int)))
  val fields: List[JsField] = List(
    required("title", JsKind.Str), required("owner", owner), required("items", JsKind.Arr(item)), optional("note", JsKind.Str)
  )
}
