package app

import hx4s.annotation.route
import scala.scalajs.js.annotation.JSExportTopLevel

object Main {
  @route("index.ts")
  @JSExportTopLevel("indexPage", "server")
  def indexPage(c: Any): Any = c

  @route("items/[id].ts")
  @JSExportTopLevel("itemPage", "server")
  def itemPage(c: Any): Any = c

  @route("items/[id].ts", "POST")
  @JSExportTopLevel("itemUpdate", "server")
  def itemUpdate(c: Any): Any = c
}
