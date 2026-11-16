package app

import hx4s.annotation.islands
import scala.scalajs.js.annotation.JSExportTopLevel

// ファイル名にカンマと空白を含むソースも 1 つのパスとして codegen に渡ることを確かめる
object Other {
  @islands("other.tsx")
  @JSExportTopLevel("OtherJs", "client")
  val other: Any = ???
}
