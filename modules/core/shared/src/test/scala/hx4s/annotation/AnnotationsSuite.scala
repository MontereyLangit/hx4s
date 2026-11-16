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

  test("@route と @islands は型引数の有無・名前付き引数・複数付与のどれでもコンパイルできる") {
    assertEquals(Declarations.hello() + Declarations.write() + Declarations.plain + Declarations.typed, 10)
  }

  test("引数は値として読める") {
    val annotation = new route("a/[id].ts", "POST")
    assertEquals((annotation.filePath, annotation.method), ("a/[id].ts", "POST"))
    assertEquals(new route("b.ts").method, "GET")
    assertEquals(new islands[SampleProps]("counter.tsx").filePath, "counter.tsx")
    assertEquals(new islands("raw.tsx").filePath, "raw.tsx")
  }

  test("filePath を省くとコンパイルできない") {
    assert(compileErrors("new route()").nonEmpty)
    assert(compileErrors("new islands[SampleProps]()").nonEmpty)
  }
}
