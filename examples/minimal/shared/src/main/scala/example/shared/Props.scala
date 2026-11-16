package example.shared

import hx4s.props.{JsField, JsKind}
import hx4s.props.JsShape.{optional, required}

import scala.scalajs.js

/** カウンターの props */
trait CounterProps extends js.Object {
  val label: String
  val start: Int
}

object CounterProps {
  val fields: List[JsField] = List(required("label", JsKind.Str), required("start", JsKind.Int))
}

/** 一覧の 1 件 */
trait ItemProps extends js.Object {
  val id: String
  val name: String
  val tags: js.Array[String]
}

/** 一覧の持ち主（入れ子の props の例） */
trait OwnerProps extends js.Object {
  val name: String
  val since: Int
}

/** 配列・入れ子・省略できるキーを持つ一覧の props */
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
