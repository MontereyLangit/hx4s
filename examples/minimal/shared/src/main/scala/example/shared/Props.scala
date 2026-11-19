package example.shared

import hx4s.props.{JsField, JsKind}
import hx4s.props.JsShape.{optional, required}

import scala.scalajs.js

/** Hydratable counter input containing a display label and initial count. */
trait CounterProps extends js.Object {
  val label: String
  val start: Int
}

/** Provides the counter's hydration validation fields. */
object CounterProps {
  /** Requires a string label and a signed 32-bit integer start value. */
  val fields: List[JsField] = List(required("label", JsKind.Str), required("start", JsKind.Int))
}

/** Hydratable item data containing an ID, display name and array of tags. */
trait ItemProps extends js.Object {
  val id: String
  val name: String
  val tags: js.Array[String]
}

/** Hydratable owner data containing a name and starting year. */
trait OwnerProps extends js.Object {
  val name: String
  val since: Int
}

/** Hydratable list input with nested owner and item data and an optional note. */
trait ItemListProps extends js.Object {
  val title: String
  val owner: OwnerProps
  val items: js.Array[ItemProps]
  val note: js.UndefOr[String]
}

/** Provides the list's hydration schema, including nested owner and item fields. */
object ItemListProps {
  private val item = JsKind.Obj(List(required("id", JsKind.Str), required("name", JsKind.Str), required("tags", JsKind.Arr(JsKind.Str))))
  private val owner = JsKind.Obj(List(required("name", JsKind.Str), required("since", JsKind.Int)))
  /** Requires title, owner and items and accepts an omitted or undefined note. */
  val fields: List[JsField] = List(
    required("title", JsKind.Str), required("owner", owner), required("items", JsKind.Arr(item)), optional("note", JsKind.Str)
  )
}
