package hx4s.react

import japgolly.scalajs.react.ReactDOMServer
import japgolly.scalajs.react.facade.React
import japgolly.scalajs.react.vdom.html_<^.*
import munit.FunSuite

import scala.scalajs.js

class IslandSuite extends FunSuite {
  private trait LabelProps extends js.Object {
    val label: String
  }

  private val Label: React.ComponentType[LabelProps] =
    (((props: LabelProps) => <.button(props.label).rawElement): js.Function1[LabelProps, React.Node])
      .asInstanceOf[React.StatelessFunctionalComponent[LabelProps]]

  test("Island は HonoX の境界で包み、props を属性へシリアライズする") {
    val props = new LabelProps { val label = "日本語 \"<&>" }
    val html = ReactDOMServer.renderToStaticMarkup(Island(Label, props, "/app/islands/label.tsx"))
    assert(html.startsWith("<honox-island component-name=\"/app/islands/label.tsx\""), html)
    assert(html.contains("data-serialized-props=\"{&quot;label&quot;:&quot;日本語 \\&quot;&lt;&amp;&gt;&quot;}\""), html)
    assert(html.contains("<button>日本語 &quot;&lt;&amp;&gt;</button>"), html)
  }

  test("Island の props は component の props 型に合わせる") {
    assertEquals(compileErrors("Island(Label, new LabelProps { val label = \"x\" }, \"/app/islands/label.tsx\")"), "")
    assert(compileErrors("Island(Label, new js.Object, \"/app/islands/label.tsx\")").nonEmpty)
  }
}
