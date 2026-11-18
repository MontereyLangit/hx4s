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

  test("Island wraps in a HonoX boundary and serializes props into an attribute") {
    val props = new LabelProps { val label = "Ünïcödé \"<&>" }
    val html = ReactDOMServer.renderToStaticMarkup(Island(Label, props, "/app/islands/label.tsx"))
    assert(html.startsWith("<honox-island component-name=\"/app/islands/label.tsx\""), html)
    assert(html.contains("data-serialized-props=\"{&quot;label&quot;:&quot;Ünïcödé \\&quot;&lt;&amp;&gt;&quot;}\""), html)
    assert(html.contains("<button>Ünïcödé &quot;&lt;&amp;&gt;</button>"), html)
  }

  test("Island props follow the props type of the component") {
    assertEquals(compileErrors("Island(Label, new LabelProps { val label = \"x\" }, \"/app/islands/label.tsx\")"), "")
    assert(compileErrors("Island(Label, new js.Object, \"/app/islands/label.tsx\")").nonEmpty)
  }
}
