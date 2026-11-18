package example.server

import example.shared.{CounterProps, ItemListProps}
import hx4s.generated.IslandComponents
import japgolly.scalajs.react.ReactDOMServer
import munit.FunSuite

import scala.scalajs.js

class TypedPropsSuite extends FunSuite {

  test("the generated IslandComponents accepts only the P of @islands[P]") {
    assertEquals(compileErrors("IslandComponents.CounterJs(new CounterProps { val label = \"a\"; val start = 1 })"), "")
    assert(compileErrors("IslandComponents.CounterJs(new js.Object)").nonEmpty)
    assert(compileErrors("IslandComponents.CounterJs(js.Dynamic.literal(label = \"a\", start = 1))").nonEmpty)
    assert(compileErrors("IslandComponents.ItemListJs(new CounterProps { val label = \"a\"; val start = 1 })").nonEmpty)
    assert(compileErrors("new CounterProps { val label = \"a\" }").nonEmpty, "omitting a required key does not compile")
  }

  test("SSR wraps client islands in HonoX boundaries and gives each instance of the same island its props") {
    val html = ReactDOMServer.renderToStaticMarkup(Pages.index())
    assertEquals("component-name=\"/app/islands/counter.tsx\"".r.findAllIn(html).size, 2)
    assertEquals("component-name=\"/app/islands/item-list.tsx\"".r.findAllIn(html).size, 1)
    assert(html.contains("<button type=\"button\" class=\"counter\">A: 0</button>"), html)
    assert(html.contains("<button type=\"button\" class=\"counter\">B: 10</button>"), html)
    assert(html.contains("Zoë Smith (since 2024)"), html)
    assert(!html.contains("</script><!--"), "props strings do not appear as HTML structure")
    assert(html.contains(Pages.ServerOnlyMarker))
  }

  test("filtering by tags returns only items that have all the tags") {
    assertEquals(Catalog.withTags(Nil).map(_.id), List("1", "2", "3"))
    assertEquals(Catalog.withTags(List("fruit", "red")).map(_.id), List("1"))
    assertEquals(Catalog.withTags(List("fruit", "symbols")), Nil)
  }

  test("name validation rejects empty and too long names") {
    assertEquals(Catalog.rename("  New name "), Right("New name"))
    assertEquals(Catalog.rename(" "), Left("Enter an item name"))
    assert(Catalog.rename("a" * 41).isLeft)
  }
}
