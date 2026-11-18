package hx4s.codegen

import munit.FunSuite
import scala.meta.*

class AppFileRendererSuite extends FunSuite {
  private val config = TestConfig()
  private val bodyAfterBanner: String => String = _.linesIterator.drop(config.banner.size).mkString("\n") + "\n"

  test("route files make GET export default and others named exports, importing once each under generator-specific aliases") {
    val entry = RouteEntry("api/items.ts", List(
      RouteHandler(HttpMethod.PUT, "putHandler"), RouteHandler(HttpMethod.POST, "itemsHandler"), RouteHandler(HttpMethod.GET, "itemsHandler")
    ))
    val expected =
      """import { createRoute as __hx4s_createRoute } from "honox/factory";
        |// @ts-expect-error scalajs:server.js is a virtual module (see vite.config.ts).
        |import { itemsHandler as __hx4s_handler_itemsHandler, putHandler as __hx4s_handler_putHandler } from "scalajs:server.js";
        |
        |export default __hx4s_createRoute((c) => __hx4s_handler_itemsHandler(c));
        |export const POST = __hx4s_createRoute((c) => __hx4s_handler_itemsHandler(c));
        |export const PUT = __hx4s_createRoute((c) => __hx4s_handler_putHandler(c));
        |""".stripMargin.replace("\r\n", "\n")
    assertEquals(bodyAfterBanner(AppFileRenderer.route(entry, config)), expected)
  }

  test("island files re-export the virtual module export as the default export") {
    val rendered = AppFileRenderer.island(IslandEntry("item-view.tsx", "ItemViewJs", "client.js"), config)
    val expected =
      """// @ts-expect-error scalajs:client.js is a virtual module resolved by @scala-js/vite-plugin-scalajs (no .d.ts).
        |import { ItemViewJs as __hx4s_island } from "scalajs:client.js";
        |export default __hx4s_island;
        |""".stripMargin.replace("\r\n", "\n")
    assertEquals(bodyAfterBanner(rendered), expected)
  }

  test("every generated file starts with the configured banner and ends with a newline") {
    List(
      AppFileRenderer.route(RouteEntry("x.ts", List(RouteHandler(HttpMethod.GET, "x"))), config),
      AppFileRenderer.island(IslandEntry("x.tsx", "X", "client.js"), config),
      AppFileRenderer.islandComponents(Nil, config)
    ).foreach { out =>
      assert(out.startsWith(config.banner.mkString("", "\n", "\n")), out)
      assert(out.endsWith("\n"), out)
    }
  }

  private val entries = List(
    IslandEntry("widgets/counter.tsx", "CounterJs", "client.js", Some("_root_.pkg.CounterProps")),
    IslandEntry("panel.tsx", "match", "client.js")
  )

  test("the SSR API shares the export and the HonoX identifier, ordered by file path") {
    val source = AppFileRenderer.islandComponents(entries, config)
    assertEquals(AppFileRenderer.islandComponents(entries.reverse, config), source)
    val tree = dialects.Scala3(source).parse[Source].get
    assertEquals(tree.collect { case pkg: Pkg => pkg.ref.syntax }, List("example.generated"))
    val api = tree.collect { case obj: Defn.Object if obj.name.value == "Islands" => obj }.head
    val methods = api.templ.body.stats.collect { case method: Defn.Def => method }
    assertEquals(methods.map(_.name.value), List("match", "CounterJs"))
    assertEquals(methods.map(_.body.collect { case Lit.String(value) => value }),
      List(List("/app/islands/panel.tsx"), List("/app/islands/widgets/counter.tsx")))
    assertEquals(methods.map(_.paramClauses.head.values.head.decltpe.get.syntax),
      List("_root_.scala.scalajs.js.Object", "_root_.pkg.CounterProps"))
    assert(methods.forall(_.body.syntax.contains("_root_.example.bridge.Island(")))
    assert(source.contains("@JSImport(\"scalajs:client.js\", \"CounterJs\")"))
    assert(source.contains("extends js.Function1[_root_.pkg.CounterProps, React.Node]"))
    assert(!source.contains("@JSImport(\"/app/islands"))
  }

  test("with no islands, only an empty object is output") {
    val source = AppFileRenderer.islandComponents(Nil, config)
    val tree = dialects.Scala3(source).parse[Source].get
    assertEquals(tree.collect { case obj: Defn.Object => obj.name.value }, List("Islands"))
    assert(!source.contains("@JSImport"))
  }
}
