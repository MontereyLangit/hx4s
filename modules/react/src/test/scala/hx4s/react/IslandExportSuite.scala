package hx4s.react

import hx4s.props.JsShape.required
import hx4s.props.{JsField, JsKind}
import japgolly.scalajs.react.ReactDOMServer
import japgolly.scalajs.react.facade.React
import japgolly.scalajs.react.vdom.VdomElement
import japgolly.scalajs.react.vdom.html_<^.*
import munit.FunSuite

import scala.scalajs.js

class IslandExportSuite extends FunSuite {
  private trait GreetingProps extends js.Object {
    val name: String
    val times: Int
  }
  private trait OtherProps extends js.Object {
    val other: String
  }
  private val fields: List[JsField] = List(required("name", JsKind.Str), required("times", JsKind.Int))

  private val greeting: IslandExport[GreetingProps] =
    IslandExport[GreetingProps]("GreetingJs", fields)(props => <.p(s"${props.name} x${props.times}"))

  private def render(component: IslandExport[?], props: js.Any): String =
    ReactDOMServer.renderToStaticMarkup(VdomElement(React.createElement(component.asInstanceOf[React.StatelessFunctionalComponent[js.Object]], props.asInstanceOf[js.Object])))

  private def withConsoleErrors[A](body: js.Array[String] => A): A = {
    val console = js.Dynamic.global.console
    val original = console.error
    val messages = js.Array[String]()
    console.error = ((message: String) => { messages.push(message); () }): js.Function1[String, Unit]
    try { body(messages) }
    finally { console.error = original }
  }

  private def validator(component: IslandExport[?]): js.Function1[js.Any, js.UndefOr[String]] =
    component.asInstanceOf[js.Dynamic].hx4sValidate.asInstanceOf[js.Function1[js.Any, js.UndefOr[String]]]

  test("renders with valid props") {
    assertEquals(render(greeting, js.JSON.parse("""{"name":"Wörld","times":2}""")), "<p>Wörld x2</p>")
  }

  test("with invalid props it logs console.error and renders nothing") {
    withConsoleErrors { messages =>
      assertEquals(render(greeting, js.JSON.parse("""{"name":1}""")), "")
      assertEquals(messages.toList, List("[GreetingJs] invalid props: props.name: expected string, got number; props.times: missing"))
    }
  }

  test("attaches the validation function used before hydration to the export") {
    val validate = validator(greeting)
    assert(js.isUndefined(validate(js.JSON.parse("""{"name":"a","times":1}"""))))
    assertEquals(validate(js.JSON.parse("""{"name":"a","times":1.5}""")).toOption, Some("props.times: expected integer in Int range, got 1.5"))
  }

  test("unchecked attaches no validation function") {
    val raw = IslandExport.unchecked[GreetingProps]("RawJs")(props => <.span(props.name))
    assertEquals(render(raw, js.JSON.parse("""{"name":"x","times":0}""")), "<span>x</span>")
    assert(js.isUndefined(raw.asInstanceOf[js.Dynamic].hx4sValidate))
  }

  test("does not compile when the declared props type differs from the props type of the render function") {
    assertEquals(compileErrors("val x: IslandExport[GreetingProps] = IslandExport(\"X\", fields)(p => <.p(p.name))"), "")
    assert(compileErrors("val x: IslandExport[OtherProps] = IslandExport[GreetingProps](\"X\", fields)(p => <.p(p.name))").nonEmpty)
    assert(compileErrors("val x: IslandExport[OtherProps] = greeting").nonEmpty)
    assert(compileErrors("val x: IslandExport[GreetingProps] = IslandExport(\"X\", fields)((p: OtherProps) => <.p(p.other))").nonEmpty)
  }
}
