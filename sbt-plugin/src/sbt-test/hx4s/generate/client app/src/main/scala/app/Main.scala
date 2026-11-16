package app

import hx4s.annotation.islands
import hx4s.react.IslandExport
import scala.scalajs.js.annotation.JSExportTopLevel

object Main {
  @islands[app.shared.CounterProps]("counter.tsx")
  @JSExportTopLevel("CounterJs", "client")
  val counter: IslandExport[app.shared.CounterProps] = ???
}
