package example.client

import example.shared.{ItemListProps, ItemProps}
import japgolly.scalajs.react.*
import japgolly.scalajs.react.vdom.html_<^.*

/** Renders item and owner props and tracks the selected item by ID. */
object ItemList {
  private def row(item: ItemProps, selected: Boolean, select: Callback): VdomElement =
    <.li(^.key := item.id, ^.className := (if (selected) "item selected" else "item"),
      <.button(^.`type` := "button", ^.onClick --> select, item.name),
      <.span(^.className := "tags", item.tags.mkString(", ")))

  /** List component with no item selected initially. */
  final val Component = ScalaFnComponent[ItemListProps] { props =>
    for {
      selected <- useState(Option.empty[String])
    } yield {
      val current = selected.value.flatMap(id => props.items.find(_.id == id)).fold("none")(_.name)
      <.section(^.className := "item-list",
        <.h2(props.title),
        <.p(^.className := "owner", s"${props.owner.name} (since ${props.owner.since})"),
        props.note.fold(EmptyVdom)(note => <.p(^.className := "note", note)),
        <.ul(props.items.toList.toVdomArray(item => row(item, selected.value.contains(item.id), selected.setState(Some(item.id))))),
        <.p(^.className := "selected-name", s"Selected: $current"))
    }
  }
}
