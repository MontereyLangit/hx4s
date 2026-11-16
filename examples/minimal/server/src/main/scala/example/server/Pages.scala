package example.server

import example.shared.{CounterProps, ItemListProps, ItemProps, OwnerProps}
import hx4s.generated.IslandComponents
import japgolly.scalajs.react.vdom.html_<^.*

import scala.scalajs.js
import scala.scalajs.js.JSConverters.*

/** ページの SSR。island は生成された IslandComponents で置く */
object Pages {
  /** client のバンドルに入っていないことを確かめるための、server だけにある文字列 */
  val ServerOnlyMarker = "hx4s-minimal-server-only-marker"

  private def counter(text: String, initial: Int): VdomElement =
    IslandComponents.CounterJs(new CounterProps { val label = text; val start = initial })

  private def itemList: VdomElement = {
    val listItems = Catalog.items.map { item =>
      new ItemProps { val id = item.id; val name = item.name; val tags = item.tags.toJSArray }
    }.toJSArray
    IslandComponents.ItemListJs(new ItemListProps {
      val title = "品目の一覧"
      val owner = new OwnerProps { val name = "山田 太郎"; val since = 2024 }
      val items = listItems
      val note = "注記: </script><!-- \"引用\" & 'single'"
    })
  }

  def index(): VdomElement =
    <.main(
      <.h1("hx4s minimal"),
      <.p(^.className := "lead", "JavaScript が無くても読める SSR のページです。下のボタンは island として hydrate されます。"),
      <.div(^.className := "counters", counter("A", 0), counter("B", 10)),
      itemList,
      <.nav(<.ul(Catalog.items.toVdomArray(item => <.li(^.key := item.id, <.a(^.href := s"/items/${item.id}", s"品目 ${item.id}"))))),
      <.footer(ServerOnlyMarker)
    )

  def item(item: Item, saved: Option[String], error: Option[String] = None): VdomElement =
    <.main(
      <.h1(^.className := "item-name", item.name),
      <.p(^.className := "item-id", s"ID: ${item.id}"),
      <.p(^.className := "item-tags", s"タグ: ${if (item.tags.isEmpty) "なし" else item.tags.mkString(", ")}"),
      saved.fold(EmptyVdom)(name => <.p(^.className := "saved", s"保存しました: $name")),
      error.fold(EmptyVdom)(reason => <.p(^.className := "error", reason)),
      <.form(^.method := "post", ^.action := s"/items/${item.id}",
        <.label("新しい名前 ", <.input(^.name := "name", ^.`type` := "text")),
        <.button(^.`type` := "submit", "保存")),
      <.a(^.href := "/", "トップへ")
    )

  def notFound(id: String): VdomElement =
    <.main(<.h1("見つかりません"), <.p(s"品目 $id はありません"), <.a(^.href := "/", "トップへ"))
}
