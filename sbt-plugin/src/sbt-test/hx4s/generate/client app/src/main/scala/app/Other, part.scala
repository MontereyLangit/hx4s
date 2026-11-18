package app

import hx4s.annotation.islands
import scala.scalajs.js.annotation.JSExportTopLevel

// Checks that a source whose file name contains a comma and a space is passed to codegen as one path
object Other {
  @islands("other.tsx")
  @JSExportTopLevel("OtherJs", "client")
  val other: Any = ???
}
