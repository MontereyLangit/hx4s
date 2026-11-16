package example.server

import japgolly.scalajs.react.facade.React
import japgolly.scalajs.react.vdom.VdomNode
import japgolly.scalajs.react.vdom.html_<^.*

/** HTML 全体。client の script は island の有無にかかわらず読み込む */
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
