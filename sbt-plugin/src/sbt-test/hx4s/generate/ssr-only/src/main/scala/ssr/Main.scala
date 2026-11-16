package ssr

import hx4s.annotation.route
import scala.scalajs.js.annotation.JSExportTopLevel

object Main {
  @route("index.ts")
  @JSExportTopLevel("indexPage", "server")
  def indexPage(c: Any): Any = c
}
