package app

import hx4s.annotation.route
import scala.scalajs.js.annotation.JSExportTopLevel

object Main {
  @route("index.ts", "HEAD")
  @JSExportTopLevel("indexPage", "server")
  def indexPage(c: Any): Any = c
}
