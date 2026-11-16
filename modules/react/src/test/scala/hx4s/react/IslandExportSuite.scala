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

  test("正しい props なら描画する") {
    assertEquals(render(greeting, js.JSON.parse("""{"name":"世界","times":2}""")), "<p>世界 x2</p>")
  }

  test("不正な props では console.error を出して何も描画しない") {
    withConsoleErrors { messages =>
      assertEquals(render(greeting, js.JSON.parse("""{"name":1}""")), "")
      assertEquals(messages.toList, List("[GreetingJs] invalid props: props.name: expected string, got number; props.times: missing"))
    }
  }

  test("hydrate の前に使う検証関数を export に付ける") {
    val validate = validator(greeting)
    assert(js.isUndefined(validate(js.JSON.parse("""{"name":"a","times":1}"""))))
    assertEquals(validate(js.JSON.parse("""{"name":"a","times":1.5}""")).toOption, Some("props.times: expected integer in Int range, got 1.5"))
  }

  test("unchecked は検証関数を付けない") {
    val raw = IslandExport.unchecked[GreetingProps]("RawJs")(props => <.span(props.name))
    assertEquals(render(raw, js.JSON.parse("""{"name":"x","times":0}""")), "<span>x</span>")
    assert(js.isUndefined(raw.asInstanceOf[js.Dynamic].hx4sValidate))
  }

  test("宣言した props の型と描画関数の props の型が違えばコンパイルできない") {
    assertEquals(compileErrors("val x: IslandExport[GreetingProps] = IslandExport(\"X\", fields)(p => <.p(p.name))"), "")
    assert(compileErrors("val x: IslandExport[OtherProps] = IslandExport[GreetingProps](\"X\", fields)(p => <.p(p.name))").nonEmpty)
    assert(compileErrors("val x: IslandExport[OtherProps] = greeting").nonEmpty)
    assert(compileErrors("val x: IslandExport[GreetingProps] = IslandExport(\"X\", fields)((p: OtherProps) => <.p(p.other))").nonEmpty)
  }
}
