package example.server

import example.shared.{CounterProps, ItemListProps, ItemProps, OwnerProps}
import hx4s.generated.IslandComponents
import japgolly.scalajs.react.vdom.html_<^.*

import scala.scalajs.js
import scala.scalajs.js.JSConverters.*

/** Renders SSR pages and creates hydration boundaries through the generated `IslandComponents`. */
object Pages {
  /** Marker used by the example to check that server-only content stays out of the client bundle. */
  val ServerOnlyMarker = "hx4s-minimal-server-only-marker"

  private def counter(text: String, initial: Int): VdomElement =
    IslandComponents.CounterJs(new CounterProps { val label = text; val start = initial })

  private def itemList: VdomElement = {
    val listItems = Catalog.items.map { item =>
      new ItemProps { val id = item.id; val name = item.name; val tags = item.tags.toJSArray }
    }.toJSArray
    IslandComponents.ItemListJs(new ItemListProps {
      val title = "Items"
      val owner = new OwnerProps { val name = "Zoë Smith"; val since = 2024 }
      val items = listItems
      val note = "Note: </script><!-- \"quoted\" & 'single'"
    })
  }

  /** Renders the home page with counter and item-list islands. */
  def index(): VdomElement =
    <.main(
      <.h1("hx4s minimal"),
      <.p(^.className := "lead", "An SSR page readable without JavaScript. The buttons below are hydrated as islands."),
      <.div(^.className := "counters", counter("A", 0), counter("B", 10)),
      itemList,
      <.nav(<.ul(Catalog.items.toVdomArray(item => <.li(^.key := item.id, <.a(^.href := s"/items/${item.id}", s"Item ${item.id}"))))),
      <.footer(ServerOnlyMarker)
    )

  /** Renders the item and its rename form, with optional submitted name and validation error. */
  def item(item: Item, saved: Option[String], error: Option[String] = None): VdomElement =
    <.main(
      <.h1(^.className := "item-name", item.name),
      <.p(^.className := "item-id", s"ID: ${item.id}"),
      <.p(^.className := "item-tags", s"Tags: ${if (item.tags.isEmpty) "none" else item.tags.mkString(", ")}"),
      saved.fold(EmptyVdom)(name => <.p(^.className := "saved", s"Saved: $name")),
      error.fold(EmptyVdom)(reason => <.p(^.className := "error", reason)),
      <.form(^.method := "post", ^.action := s"/items/${item.id}",
        <.label("New name ", <.input(^.name := "name", ^.`type` := "text")),
        <.button(^.`type` := "submit", "Save")),
      <.a(^.href := "/", "Back to home")
    )

  /** Renders a page naming the missing item ID. */
  def notFound(id: String): VdomElement =
    <.main(<.h1("No such item"), <.p(s"Item $id does not exist"), <.a(^.href := "/", "Back to home"))
}
