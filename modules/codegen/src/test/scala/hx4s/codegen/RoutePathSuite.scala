package hx4s.codegen

import munit.FunSuite

import scala.io.Source
import scala.util.Using

class RoutePathSuite extends FunSuite {
  private val table: List[(String, String)] =
    Using.resource(Source.fromResource("route-paths.tsv", getClass.getClassLoader)("UTF-8"))(_.getLines().toList)
      .filterNot(line => line.isEmpty || line.startsWith("#"))
      .map { line =>
        line.split("\t").toList match {
          case List(file, path) => file -> path
          case _ => fail(s"malformed line: $line")
        }
      }

  test("converts to the same URL patterns as HonoX createApp (tests/route-paths.test.js checks the same table with HonoX)") {
    assert(table.sizeIs > 10)
    table.foreach { case (file, path) => assertEquals(RoutePath.pattern(file), path, file) }
  }

  test("the comparison shape drops parameter names but distinguishes catch-all from ordinary parameters") {
    assertEquals(RoutePath.shape("/items/:id/edit"), RoutePath.shape("/items/:item/edit"))
    assertEquals(RoutePath.shape("/docs/:slug{.+}"), "/docs/:{.+}")
    assertNotEquals(RoutePath.shape("/docs/:slug{.+}"), RoutePath.shape("/docs/:slug"))
  }
}
