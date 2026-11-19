package example.client

import example.shared.{CounterProps, ItemListProps}
import hx4s.annotation.islands
import hx4s.react.IslandExport

import scala.scalajs.js.annotation.JSExportTopLevel

/** Exposes the counter island factory to HonoX. */
object CounterEntry {

  /** Creates a counter component with the shared props validator. */
  @islands[example.shared.CounterProps]("counter.tsx")
  @JSExportTopLevel("CounterJs", "island-counter")
  def counter(): IslandExport[CounterProps] = IslandExport("CounterJs", CounterProps.fields)(props => Counter.Component(props))
}

/** Exposes the item-list island factory to HonoX. */
object ItemListEntry {
  /** Creates an item-list component with the shared props validator. */
  @islands[example.shared.ItemListProps]("item-list.tsx")
  @JSExportTopLevel("ItemListJs", "island-item-list")
  def itemList(): IslandExport[ItemListProps] = IslandExport("ItemListJs", ItemListProps.fields)(props => ItemList.Component(props))
}
