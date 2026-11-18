package hx4s.annotation

import munit.FunSuite

class AnnotationsSuite extends FunSuite {
  private trait SampleProps

  private object Declarations {
    @route("hello.ts") def hello(): Int = 1
    @route("hello.ts", "POST") @route(filePath = "other.ts", method = "PUT") def write(): Int = 2
    @islands("plain.tsx") val plain: Int = 3
    @islands[hx4s.annotation.AnnotationsSuite]("typed.tsx") val typed: Int = 4
  }

  test("@route and @islands compile with or without type arguments, with named arguments and when repeated") {
    assertEquals(Declarations.hello() + Declarations.write() + Declarations.plain + Declarations.typed, 10)
  }

  test("arguments can be read as values") {
    val annotation = new route("a/[id].ts", "POST")
    assertEquals((annotation.filePath, annotation.method), ("a/[id].ts", "POST"))
    assertEquals(new route("b.ts").method, "GET")
    assertEquals(new islands[SampleProps]("counter.tsx").filePath, "counter.tsx")
    assertEquals(new islands("raw.tsx").filePath, "raw.tsx")
  }

  test("omitting filePath does not compile") {
    assert(compileErrors("new route()").nonEmpty)
    assert(compileErrors("new islands[SampleProps]()").nonEmpty)
  }
}
