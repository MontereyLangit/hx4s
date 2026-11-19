package example.server

import japgolly.scalajs.react.facade.React
import japgolly.scalajs.react.vdom.VdomNode
import japgolly.scalajs.react.vdom.html_<^.*

/** Renders the full document shell for the HonoX renderer. */
object Shell {
  /** Renders the document with the supplied body and title. Always emits a module script using `clientScriptSrc`. */
  def apply(children: React.Node, title: String, clientScriptSrc: String): VdomElement =
    <.html(^.lang := "ja",
      <.head(
        <.meta(VdomAttr("charSet") := "utf-8"),
        <.meta(^.name := "viewport", ^.contentAttr := "width=device-width, initial-scale=1"),
        <.titleTag(title),
        <.script(^.`type` := "module", ^.src := clientScriptSrc)
      ),
      <.body(VdomNode(children))
    )
}
