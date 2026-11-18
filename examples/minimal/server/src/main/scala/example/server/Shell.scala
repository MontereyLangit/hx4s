package example.server

import japgolly.scalajs.react.facade.React
import japgolly.scalajs.react.vdom.VdomNode
import japgolly.scalajs.react.vdom.html_<^.*

/** The whole HTML. The client script is loaded whether or not there are islands */
object Shell {
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
