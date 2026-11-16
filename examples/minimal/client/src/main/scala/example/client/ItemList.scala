package example.client

import example.shared.{ItemListProps, ItemProps}
import japgolly.scalajs.react.*
import japgolly.scalajs.react.vdom.html_<^.*

/** 配列・入れ子の props を表示し、クリックした項目を選択中として示す一覧 */
object ItemList {
  private def row(item: ItemProps, selected: Boolean, select: Callback): VdomElement =
    <.li(^.key := item.id, ^.className := (if (selected) "item selected" else "item"),
      <.button(^.`type` := "button", ^.onClick --> select, item.name),
      <.span(^.className := "tags", item.tags.mkString(", ")))

  final val Component = ScalaFnComponent[ItemListProps] { props =>
    for {
      selected <- useState(Option.empty[String])
    } yield {
      val current = selected.value.flatMap(id => props.items.find(_.id == id)).fold("なし")(_.name)
      <.section(^.className := "item-list",
        <.h2(props.title),
        <.p(^.className := "owner", s"${props.owner.name}（${props.owner.since} 年から）"),
        props.note.fold(EmptyVdom)(note => <.p(^.className := "note", note)),
        <.ul(props.items.toList.toVdomArray(item => row(item, selected.value.contains(item.id), selected.setState(Some(item.id))))),
        <.p(^.className := "selected-name", s"選択中: $current"))
    }
  }
}
