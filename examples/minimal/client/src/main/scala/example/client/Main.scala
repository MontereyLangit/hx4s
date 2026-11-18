package example.client

import example.shared.{CounterProps, ItemListProps}
import hx4s.annotation.islands
import hx4s.react.IslandExport

import scala.scalajs.js.annotation.JSExportTopLevel

/** Island exports shipped to the browser. Codegen matches the declared type IslandExport[P] against the P of @islands[P] */
object CounterEntry {

  @islands[example.shared.CounterProps]("counter.tsx")
  @JSExportTopLevel("CounterJs", "island-counter")
  def counter(): IslandExport[CounterProps] = IslandExport("CounterJs", CounterProps.fields)(props => Counter.Component(props))
}

object ItemListEntry {
  @islands[example.shared.ItemListProps]("item-list.tsx")
  @JSExportTopLevel("ItemListJs", "island-item-list")
  def itemList(): IslandExport[ItemListProps] = IslandExport("ItemListJs", ItemListProps.fields)(props => ItemList.Component(props))
}
