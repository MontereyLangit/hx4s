package hx4s.react

import hx4s.props.JsShape.required
import hx4s.props.JsKind
import japgolly.scalajs.react.*
import japgolly.scalajs.react.facade.React
import japgolly.scalajs.react.vdom.html_<^.*
import scala.scalajs.js
import scala.scalajs.js.annotation.JSExportTopLevel

/** テスト用 island の props */
trait CounterTestProps extends js.Object {
  val label: String
  val start: Int
}

/** Node のテスト（tests/islands-hydration.test.js）が実際の Scala のブリッジと client island で SSR・hydration するための入口 */
object IslandTestExports {
  @JSExportTopLevel("renderIslandTest", "island-test")
  def renderIslandTest(component: React.ComponentType[js.Object], props: js.Object, componentName: String): React.Element = {
    Island(component, props, componentName).rawElement
  }

  private val Counter = ScalaFnComponent[CounterTestProps] { props =>
    for {
      count <- useState(props.start)
    } yield <.button(^.onClick --> count.modState(_ + 1), s"${props.label}:${count.value}")
  }

  @JSExportTopLevel("CounterTest", "island-test")
  val counter: IslandExport[CounterTestProps] =
    IslandExport("CounterTest", List(required("label", JsKind.Str), required("start", JsKind.Int)))(props => Counter(props))
}
