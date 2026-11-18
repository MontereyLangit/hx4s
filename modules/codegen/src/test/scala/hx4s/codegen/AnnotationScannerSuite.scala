package hx4s.codegen

import munit.FunSuite

class AnnotationScannerSuite extends FunSuite {
  private val config = TestConfig()

  private def routes(members: String, objectName: String = "Main") =
    AnnotationScanner.routes(List("Server.scala" -> s"object $objectName {\n$members\n}"), config)
  private def islands(members: String) =
    AnnotationScanner.islands(List("Client.scala" -> s"object Main {\n$members\n}"), config)
  private def errors[A](result: CodegenError.Result[A]): List[CodegenError] =
    result.left.getOrElse(fail(s"expected errors, got $result"))

  test("the export decides the handler name, and methods of the same file are combined into one with GET first") {
    val entries = routes("""
      @route(filePath = "probe.ts", method = "POST")
      @JSExportTopLevel(name = "writeProbe", moduleID = "server") def write(c: Any) = c
      @route("probe.ts") @JSExportTopLevel("readProbe", "server") def read(c: Any) = c
      @JSExportTopLevel("unrouted", "server") def helper() = 1
    """).toOption.get
    assertEquals(entries, List(RouteEntry("probe.ts", List(RouteHandler(HttpMethod.GET, "readProbe"), RouteHandler(HttpMethod.POST, "writeProbe")))))
  }

  test("a member can have several @route annotations") {
    val entries = routes("""
      @route("form.ts") @route("form.ts", "POST") @route("other.ts", "PUT")
      @JSExportTopLevel("formHandler", "server") def form(c: Any) = c
    """).toOption.get
    assertEquals(entries, List(
      RouteEntry("form.ts", List(RouteHandler(HttpMethod.GET, "formHandler"), RouteHandler(HttpMethod.POST, "formHandler"))),
      RouteEntry("other.ts", List(RouteHandler(HttpMethod.PUT, "formHandler")))
    ))
  }

  test("output does not depend on the order of declarations") {
    val a = """@route("b.ts") @JSExportTopLevel("b", "server") def b(c: Any) = c"""
    val b = """@route("a.ts", "DELETE") @JSExportTopLevel("a2", "server") def a2(c: Any) = c"""
    val c = """@route("a.ts") @JSExportTopLevel("a1", "server") def a1(c: Any) = c"""
    val expected = routes(List(a, b, c).mkString("\n"))
    List(List(c, b, a), List(b, a, c), List(c, a, b)).foreach(order => assertEquals(routes(order.mkString("\n")), expected))
    val i1 = """@islands("z.tsx") @JSExportTopLevel("Z", "client") val z = 1"""
    val i2 = """@islands("a/b.tsx") @JSExportTopLevel("B", "client") val b = 1"""
    assertEquals(islands(s"$i1\n$i2").map(_.map(_.filePath)), Right(List("a/b.tsx", "z.tsx")))
    assertEquals(islands(s"$i2\n$i1"), islands(s"$i1\n$i2"))
  }

  test("top-level objects with names other than Main and inside packages are targets, but nested objects are not read") {
    val source = """package example
      |object Handlers {
      |  @route("a.ts") @JSExportTopLevel("a", "server") def a(c: Any) = c
      |  object Nested { @route("nested.ts") @JSExportTopLevel("n", "server") def n(c: Any) = c }
      |}
      |object More { @route("b.ts") @JSExportTopLevel("b", "server") def b(c: Any) = c }
      |""".stripMargin
    val entries = AnnotationScanner.routes(List("X.scala" -> source), config).toOption.get
    assertEquals(entries.map(_.filePath), List("a.ts", "b.ts"))
  }

  test("fully qualified annotations are read too") {
    val entries = routes("""@example.annotations.route("x.ts") @scala.scalajs.js.annotation.JSExportTopLevel("x", "server") def x() = 1""")
    assertEquals(entries.map(_.map(_.filePath)), Right(List("x.ts")))
  }

  test("islands derive the export and the source module") {
    assertEquals(islands("""@islands("widgets/view.tsx") @JSExportTopLevel("ItemView", "client") val view = 1"""),
      Right(List(IslandEntry("widgets/view.tsx", "ItemView", "client.js"))))
  }

  test("the props type argument of an island accepts only fully qualified names") {
    assertEquals(islands("""@islands[pkg.props.ViewProps]("view.tsx") @JSExportTopLevel("View", "client") val view = 1""").map(_.head.propsType),
      Right(Some("_root_.pkg.props.ViewProps")))
    assertEquals(islands("""@islands[_root_.pkg.ViewProps]("view.tsx") @JSExportTopLevel("View", "client") val view = 1""").map(_.head.propsType),
      Right(Some("_root_.pkg.ViewProps")))
    val simple = errors(islands("@islands[ViewProps](\"view.tsx\") @JSExportTopLevel(\"View\", \"client\") val view = 1"))
    assertEquals(simple.size, 1)
    assert(simple.head.message.contains("fully qualified"), simple)
    assertEquals(simple.head.line, Some(2))
    List("@islands[pkg.A, pkg.B](\"v.tsx\")", "@islands[pkg.Box[pkg.A]](\"v.tsx\")").foreach { annotation =>
      assert(islands(s"$annotation @JSExportTopLevel(\"View\", \"client\") val view = 1").isLeft, annotation)
    }
    assert(routes("@route[pkg.A](\"x.ts\") @JSExportTopLevel(\"x\", \"server\") def x() = 1").isLeft)
  }

  test("errors are not thrown but returned all together with line numbers") {
    val found = errors(routes("""
      @route(path) @JSExportTopLevel("a", "server") def a() = 1
      @route("b.ts") def b() = 1
      @route("c.ts") @JSExportTopLevel("c", "client") def c() = 1
      @route("d.ts", "BREW") @JSExportTopLevel("d", "server") def d() = 1
    """))
    assertEquals(found.map(_.line), List(Some(3), Some(4), Some(5), Some(6)))
    assert(found.forall(_.source == "Server.scala"))
    assert(found.head.render.startsWith("Server.scala:3: "), found.head.render)
  }

  test("metadata must be literals and the module must be correct") {
    List(
      """@route(path) @JSExportTopLevel("x", "server") def x() = 1""",
      """@route("x.ts") def x() = 1""",
      """@route("x.ts") @JSExportTopLevel("x", "client") def x() = 1""",
      """@route("x.ts") @JSExportTopLevel(name, "server") def x() = 1""",
      """@route("x.ts") @JSExportTopLevel("1x", "server") def x() = 1""",
      """@route("x.ts", "BREW") @JSExportTopLevel("x", "server") def x() = 1""",
      """@route("x.ts", "ALL") @JSExportTopLevel("x", "server") def x() = 1""",
      """@route(method = "GET", filePath = "x.ts") @JSExportTopLevel("x", "server") def x() = 1""",
      """@islands("x.tsx") @JSExportTopLevel("x", "server") val x = 1"""
    ).foreach(source => assert(routes(source).isLeft, source))
    assert(islands("""@route("x.ts") @JSExportTopLevel("x", "client") val x = 1""").isLeft)
  }

  test("syntax errors are returned with the source position") {
    val found = errors(AnnotationScanner.routes(List("Broken.scala" -> "object Main {\n  def x( = 1\n}"), config))
    assertEquals(found.map(_.source), List("Broken.scala"))
    assert(found.head.line.isDefined)
  }

  test("dangerous paths cannot escape the generated directory") {
    List("../x.ts", "/x.ts", "C:/x.ts", "a//x.ts", "a/./x.ts", "x.tsx", "a\\x.ts", "x\".ts", "CON.ts", "nul/x.ts", "a./x.ts", "a /x.ts", "com1.d.ts")
      .foreach { path =>
        val literal = "\"" + path.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
        assert(routes(s"@route($literal) @JSExportTopLevel(\"x\", \"server\") def x() = 1").isLeft, path)
      }
  }

  test("route and island file names follow HonoX's rules") {
    List("_renderer.ts", "a/_middleware.ts").foreach { path =>
      assert(routes(s"@route(\"$path\") @JSExportTopLevel(\"x\", \"server\") def x() = 1").isLeft, path)
    }
    List("_hidden.tsx", "item.view.tsx", "[id].tsx").foreach { path =>
      assert(islands(s"@islands(\"$path\") @JSExportTopLevel(\"X\", \"client\") val x = 1").isLeft, path)
    }
  }

  test("HEAD, which HonoX createApp does not register, is rejected with a reason") {
    val found = errors(routes("""@route("x.ts", "HEAD") @JSExportTopLevel("x", "server") def x() = 1"""))
    assertEquals(found.map(_.message), List("HEAD is not registered by HonoX createApp (GET handlers also answer HEAD)"))
  }

  test("file names HonoX's route glob and import.meta.glob skip are rejected, and a leading .well-known is allowed") {
    def route(path: String) = routes(s"@route(\"$path\") @JSExportTopLevel(\"x\", \"server\") def x() = 1")
    List("-draft.ts", "$param.ts", "a/-x.ts", "page.test.ts", "a/page.spec.ts", "-private/page.ts", "a/-private/page.ts",
      ".hidden/page.ts", "a/.hidden/page.ts", ".page.ts", "node_modules/page.ts", "a/.well-known/x.ts", ".well-known.ts").foreach { path =>
      assert(route(path).isLeft, path)
    }
    assert(errors(route("-draft.ts")).head.message.contains("ignored by HonoX"))
    assertEquals(route(".well-known/security.txt.ts").map(_.map(_.filePath)), Right(List(".well-known/security.txt.ts")))
    List(".hidden/view.tsx", "node_modules/view.tsx").foreach { path =>
      assert(islands(s"@islands(\"$path\") @JSExportTopLevel(\"X\", \"client\") val x = 1").isLeft, path)
    }
  }

  test("several @islands on one member are rejected before generating") {
    val found = errors(islands("""
      @islands("first.tsx") @islands("second.tsx") @JSExportTopLevel("Widget", "client") val widget = 1
    """))
    assertEquals(found.map(_.message), List("a member can have at most one @islands"))
    assertEquals(found.map(_.line), List(Some(3)))
  }

  test("if the declared type of the export is IslandExport[T], T must have the same type name as the P of @islands[P]") {
    def island(annotation: String, declared: String) =
      islands(s"""@$annotation @JSExportTopLevel("View", "client") val view: $declared = ???""")
    List(
      "islands[pkg.ViewProps](\"v.tsx\")" -> "IslandExport[pkg.ViewProps]",
      "islands[pkg.ViewProps](\"v.tsx\")" -> "IslandExport[_root_.pkg.ViewProps]",
      "islands[_root_.pkg.ViewProps](\"v.tsx\")" -> "example.bridge.IslandExport[pkg.ViewProps]",
      "islands[pkg.ViewProps](\"v.tsx\")" -> "IslandExport[ViewProps]",
      "islands[pkg.ViewProps](\"v.tsx\")" -> "js.Any",
      "islands(\"v.tsx\")" -> "js.Any",
      "islands[pkg.ViewProps](\"v.tsx\")" -> "other.IslandExport[pkg.OtherProps]"
    ).foreach { case (annotation, declared) =>
      assert(island(annotation, declared).isRight, s"$annotation $declared")
    }
    val mismatch = errors(island("islands[pkg.ViewProps](\"v.tsx\")", "IslandExport[pkg.OtherProps]"))
    assertEquals(mismatch.map(_.message), List("IslandExport[pkg.OtherProps] does not match @islands[pkg.ViewProps]"))
    assertEquals(mismatch.map(_.line), List(Some(2)))
    assert(island("islands[pkg.ViewProps](\"v.tsx\")", "IslandExport[OtherProps]").isLeft)
    assert(island("islands[pkg.ViewProps](\"v.tsx\")", "IslandExport[pkg.Box[pkg.ViewProps]]").isLeft)
    val missing = errors(island("islands(\"v.tsx\")", "IslandExport[pkg.ViewProps]"))
    assertEquals(missing.map(_.message), List("IslandExport[pkg.ViewProps] needs the same type argument on @islands (@islands[pkg.ViewProps])"))
    assert(islands("""@islands[pkg.ViewProps]("v.tsx") @JSExportTopLevel("View", "client") def view: IslandExport[pkg.OtherProps] = ???""").isLeft)
  }

  test("each island keeps its module in both the browser entry and the SSR import") {
    val entries = islands("""
      @islands[pkg.CounterProps]("counter.tsx") @JSExportTopLevel("Counter", "island-counter") def counter(): IslandExport[CounterProps] = ???
      @islands("list.tsx") @JSExportTopLevel(name = "ListView", moduleID = "island-list") val list = ???
      @islands("old.tsx") @JSExportTopLevel("Legacy", "client") val legacy = ???
    """).toOption.get
    assertEquals(entries.map(_.sourceModule), List("island-counter.js", "island-list.js", "client.js"))
    assertEquals(entries.map(_.factory), List(true, false, false))
    assert(AppFileRenderer.island(entries.head, config).contains("export default __hx4s_island();"))
    val ssr = AppFileRenderer.islandComponents(entries, config)
    assert(ssr.contains("lazy val component0 = _root_.example.generated.IslandComponent0()"))
    entries.foreach { entry =>
      val specifier = s"${config.virtualModulePrefix}${entry.sourceModule}"
      assert(AppFileRenderer.island(entry, config).contains(s"""from "$specifier";"""))
      assert(ssr.contains(s"""@JSImport("$specifier", "${entry.exportedName}")"""))
    }
    assert(islands("""@islands("x.tsx") @JSExportTopLevel("X", "../x") val x = 1""").isLeft)
  }

  test("duplicate route methods, island paths and export names are rejected") {
    assert(routes("""
      @route("x.ts") @JSExportTopLevel("x", "server") def x() = 1
      @route("X.ts", "POST") @JSExportTopLevel("y", "server") def y() = 1
    """).isLeft)
    assert(routes("""
      @route("x.ts") @JSExportTopLevel("x", "server") def x() = 1
      @route("x.ts", "GET") @JSExportTopLevel("y", "server") def y() = 1
    """).isLeft)
    assert(islands("""
      @islands("x.tsx") @JSExportTopLevel("X", "client") val x = 1
      @islands("X.tsx") @JSExportTopLevel("Y", "island-y") val y = 1
    """).isLeft)
    val sameExport = errors(islands("""
      @islands("first.tsx") @JSExportTopLevel("Widget", "client") val first = 1
      @islands("second.tsx") @JSExportTopLevel("Widget", "island-second") val second = 1
    """))
    assertEquals(sameExport.map(_.line), List(Some(3), Some(4)))
    assert(AnnotationScanner.routes(List(
      "A.scala" -> """object A { @route("a.ts") @JSExportTopLevel("same", "server") def a() = 1 }""",
      "B.scala" -> """object B { @route("b.ts") @JSExportTopLevel("same", "server") def b() = 1 }"""
    ), config).isLeft)
  }

  test("separate route files with the same URL in HonoX are rejected for the same method (parameter names are not distinguished)") {
    def pair(first: String, second: String, method: String = "GET") = routes(
      s"""@route("$first") @JSExportTopLevel("first", "server") def first() = 1
         |@route("$second", "$method") @JSExportTopLevel("second", "server") def second() = 1""".stripMargin)
    List("foo.ts" -> "foo/index.ts", "items/[a].ts" -> "items/[b].ts", "index.ts" -> "index/index.ts", "page.ts" -> "page.md.ts",
      "docs/[...a].ts" -> "docs/[...b].ts").foreach { case (first, second) =>
      val found = errors(pair(first, second))
      assert(found.forall(_.message.startsWith("route files map GET to the same URL:")), found)
      assertEquals(found.map(_.line), List(Some(2), Some(3)))
    }
    assert(errors(pair("foo.ts", "foo/index.ts")).head.message.contains("foo.ts (/foo), foo/index.ts (/foo)"))
    assert(pair("foo.ts", "foo/index.ts", "POST").isRight, "different methods may share a URL")
    assert(pair("docs/[a].ts", "docs/[...b].ts").isRight, "a catch-all and a single-segment parameter are different patterns")
    assert(pair("items/[id].ts", "items/new.ts").isRight, "a static segment and a parameter coexist thanks to HonoX's ordering")
  }

  test("even with declaration errors, URL conflicts, duplicates and duplicate export names among the remaining declarations are reported in the same run") {
    val found = errors(AnnotationScanner.routes(List(
      "A.scala" -> """object A {
        |  @route("index.ts", "HEAD") @JSExportTopLevel("head", "server") def head() = 1
        |  @route("foo.ts") @JSExportTopLevel("foo", "server") def foo() = 1
        |  @route("x.ts") @JSExportTopLevel("same", "server") def x() = 1
        |}""".stripMargin,
      "B.scala" -> """object B {
        |  @route("foo/index.ts") @JSExportTopLevel("fooIndex", "server") def fooIndex() = 1
        |  @route("y.ts") @JSExportTopLevel("same", "server") def y() = 1
        |}""".stripMargin,
      "Broken.scala" -> "object Broken {\n  def x( = 1\n}"
    ), config))
    val rendered = found.map(_.render)
    assert(rendered.contains("A.scala:2: HEAD is not registered by HonoX createApp (GET handlers also answer HEAD)"), rendered)
    assert(rendered.exists(_.startsWith("Broken.scala:")), rendered)
    assert(rendered.exists(_.startsWith("A.scala:3: route files map GET to the same URL: foo.ts (/foo), foo/index.ts (/foo)")), rendered)
    assert(rendered.exists(_.startsWith("B.scala:2: route files map GET to the same URL")), rendered)
    assert(rendered.contains("A.scala:4: export name same is declared more than once"), rendered)
    assert(rendered.contains("B.scala:3: export name same is declared more than once"), rendered)
    assertEquals(rendered.distinct, rendered)
  }

  test("erroneous declarations are excluded from conflict checks, and errors caused by them are not reported") {
    val found = errors(routes("""
      @route("foo.ts", "HEAD") @JSExportTopLevel("foo", "server") def foo() = 1
      @route("foo/index.ts", "HEAD") @JSExportTopLevel("fooIndex", "server") def fooIndex() = 1
      @route("bar.ts") @JSExportTopLevel("bar", "server") def bar() = 1
      @route("bar.ts", "BREW") @JSExportTopLevel("bar", "server") def bar2() = 1
    """))
    assertEquals(found.map(_.line), List(Some(3), Some(4), Some(6)))
    assert(found.forall(error => !error.message.contains("same URL") && !error.message.contains("more than once")), found)
    val islandErrors = errors(islands("""
      @islands("x.tsx") @JSExportTopLevel("X", "client") val x = 1
      @islands("X.tsx") @JSExportTopLevel("Y", "server") val y = 1
      @islands("a/z.tsx") @JSExportTopLevel("Z", "client") val z = 1
      @islands("a/Z.tsx") @JSExportTopLevel("W", "client") val w = 1
    """))
    assertEquals(islandErrors.map(_.line), List(Some(4), Some(5), Some(6)))
  }
}
