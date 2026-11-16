package example.client

import example.shared.{CounterProps, ItemListProps}
import hx4s.annotation.islands
import hx4s.react.IslandExport

import scala.scalajs.js.annotation.JSExportTopLevel

/** ブラウザへ配る island の export。宣言型の IslandExport[P] と @islands[P] の P は codegen が照合する */
object Main {

  @islands[example.shared.CounterProps]("counter.tsx")
  @JSExportTopLevel("CounterJs", "client")
  val counter: IslandExport[CounterProps] = IslandExport("CounterJs", CounterProps.fields)(props => Counter.Component(props))

  @islands[example.shared.ItemListProps]("item-list.tsx")
  @JSExportTopLevel("ItemListJs", "client")
  val itemList: IslandExport[ItemListProps] = IslandExport("ItemListJs", ItemListProps.fields)(props => ItemList.Component(props))
}
