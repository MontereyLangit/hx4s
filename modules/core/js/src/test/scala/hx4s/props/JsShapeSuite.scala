package hx4s.props

import hx4s.props.JsShape.{optional, required}
import munit.FunSuite

import scala.scalajs.js

class JsShapeSuite extends FunSuite {
  private trait Item extends js.Object {
    val name: String
  }
  private trait Probe extends js.Object {
    val label: String
    val count: Double
    val enabled: js.UndefOr[Boolean]
    val items: js.Array[Item]
  }
  private val fields = List(
    required("label", JsKind.Str), required("count", JsKind.Num), optional("enabled", JsKind.Bool),
    required("items", JsKind.Arr(JsKind.Obj(List(required("name", JsKind.Str)))))
  )
  private def parse(json: String): js.Any = js.JSON.parse(json)

  test("with all keys present it reads as a typed value; optional keys can be omitted") {
    val probe = JsShape.validate[Probe](parse("""{"label":"a","count":2,"items":[{"name":"x"}]}"""), fields).toOption.get
    assertEquals(probe.label, "a")
    assertEquals(probe.count, 2.0)
    assert(probe.enabled.isEmpty)
    assertEquals(probe.items.map(_.name).toList, List("x"))
  }

  test("missing keys, type mismatches and nested errors are returned together with paths") {
    val result = JsShape.validate[Probe](parse("""{"count":"2","enabled":1,"items":[{"name":3},{}]}"""), fields)
    assertEquals(result, Left(List(
      "props.label: missing", "props.count: expected number, got string", "props.enabled: expected boolean, got number",
      "props.items[0].name: expected string, got number", "props.items[1].name: missing"
    ).mkString("; ")))
  }

  test("null, arrays and primitives are not treated as objects") {
    List("null", "[]", "1", "\"x\"").foreach { json =>
      assert(JsShape.validate[Probe](parse(json), fields).isLeft, json)
    }
    assert(JsShape.validate[Probe](parse("""{"label":"a","count":1,"items":{}}"""), fields).isLeft)
  }

  test("Unicode strings pass through unchanged") {
    val probe = JsShape.validate[Probe](parse("""{"label":"Ünïcödé😀 \"quoted\"","count":0,"items":[]}"""), fields).toOption.get
    assertEquals(probe.label, "Ünïcödé😀 \"quoted\"")
  }

  test("Num rejects NaN and infinities, and Int also rejects fractions and values outside the Int range") {
    val numbers = List(required("n", JsKind.Num))
    val integers = List(required("n", JsKind.Int))
    def probe(value: Double): js.Any = js.Dynamic.literal(n = value)
    assert(JsShape.validate[js.Object](probe(1.5), numbers).isRight)
    List(Double.NaN, Double.PositiveInfinity, Double.NegativeInfinity).foreach { value =>
      assert(JsShape.validate[js.Object](probe(value), numbers).left.exists(_.contains("expected finite number")), value)
      assert(JsShape.validate[js.Object](probe(value), integers).isLeft, value)
    }
    List(1.5, -0.25, 2147483648d, -2147483649d).foreach { value =>
      assertEquals(JsShape.validate[js.Object](probe(value), integers).left.map(_.startsWith("props.n: expected integer in Int range")), Left(true), value)
    }
    List(0d, -3d, 2147483647d, -2147483648d).foreach { value =>
      assert(JsShape.validate[js.Object](probe(value), integers).isRight, value)
    }
    assertEquals(JsShape.validate[js.Object](parse("""{"n":"1"}"""), integers), Left("props.n: expected number, got string"))
  }

  test("read logs failures to console.error and returns None without throwing") {
    val console = js.Dynamic.global.console
    val original = console.error
    val messages = js.Array[String]()
    console.error = ((message: String) => { messages.push(message); () }): js.Function1[String, Unit]
    try {
      assertEquals(JsShape.read[Probe]("ProbeJs", parse("{}"), fields), None)
      assertEquals(messages.toList.size, 1)
      assert(messages(0).startsWith("[ProbeJs] invalid props: props.label: missing"), messages(0))
    } finally {
      console.error = original
    }
  }
}
