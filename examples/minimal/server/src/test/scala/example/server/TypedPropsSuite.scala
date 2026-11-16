package example.server

import example.shared.{CounterProps, ItemListProps}
import hx4s.generated.IslandComponents
import japgolly.scalajs.react.ReactDOMServer
import munit.FunSuite

import scala.scalajs.js

class TypedPropsSuite extends FunSuite {

  test("生成された IslandComponents は @islands[P] の P だけを受け付ける") {
    assertEquals(compileErrors("IslandComponents.CounterJs(new CounterProps { val label = \"a\"; val start = 1 })"), "")
    assert(compileErrors("IslandComponents.CounterJs(new js.Object)").nonEmpty)
    assert(compileErrors("IslandComponents.CounterJs(js.Dynamic.literal(label = \"a\", start = 1))").nonEmpty)
    assert(compileErrors("IslandComponents.ItemListJs(new CounterProps { val label = \"a\"; val start = 1 })").nonEmpty)
    assert(compileErrors("new CounterProps { val label = \"a\" }").nonEmpty, "必須のキーを省くとコンパイルできない")
  }

  test("SSR は client の island を HonoX の境界で包み、同じ island の各インスタンスに props を付ける") {
    val html = ReactDOMServer.renderToStaticMarkup(Pages.index())
    assertEquals("component-name=\"/app/islands/counter.tsx\"".r.findAllIn(html).size, 2)
    assertEquals("component-name=\"/app/islands/item-list.tsx\"".r.findAllIn(html).size, 1)
    assert(html.contains("<button type=\"button\" class=\"counter\">A: 0</button>"), html)
    assert(html.contains("<button type=\"button\" class=\"counter\">B: 10</button>"), html)
    assert(html.contains("山田 太郎（2024 年から）"), html)
    assert(!html.contains("</script><!--"), "props の文字列が HTML の構造として出ない")
    assert(html.contains(Pages.ServerOnlyMarker))
  }

  test("名前の検証は空と長すぎる名前を拒否する") {
    assertEquals(Catalog.rename("  新しい名前 "), Right("新しい名前"))
    assertEquals(Catalog.rename(" "), Left("名前を入力してください"))
    assert(Catalog.rename("あ" * 41).isLeft)
  }
}
