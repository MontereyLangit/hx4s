package hx4s.codegen

/** Renders route / island TS files and the Scala API for SSR */
object AppFileRenderer {
  /** Aliases for imported names, so that export names do not collide with identifiers in generated code (createRoute, POST, etc.) */
  private val CreateRoute = "__hx4s_createRoute"
  private val HandlerPrefix = "__hx4s_handler_"
  private val IslandLocal = "__hx4s_island"

  private def lines(values: List[String]): String = values.mkString("", "\n", "\n")

  /** A double-quoted string literal with the same value in both TS and Scala */
  private[codegen] def quote(text: String): String = {
    val escaped = text.flatMap {
      case '"' => "\\\""
      case '\\' => "\\\\"
      case c if c < ' ' || c == '\u007f' || c == '\u2028' || c == '\u2029' => f"\\u${c.toInt}%04x"
      case c => c.toString
    }
    s"\"$escaped\""
  }

  /** GET becomes `export default`, others `export const <METHOD>` */
  def route(entry: RouteEntry, config: CodegenConfig): String = {
    val module = config.virtualModule(config.serverModule)
    val handlers = entry.sortedHandlers
    val exportLines = handlers.map { handler =>
      val call = s"$CreateRoute((c) => $HandlerPrefix${handler.exportName}(c));"
      if (handler.method == HttpMethod.GET) s"export default $call"
      else s"export const ${handler.method} = $call"
    }
    val imports = handlers.map(_.exportName).distinct.map(name => s"$name as $HandlerPrefix$name").mkString(", ")
    lines(config.banner ++ List(
      s"""import { createRoute as $CreateRoute } from "honox/factory";""",
      s"// @ts-expect-error $module is a virtual module (see vite.config.ts).",
      s"import { $imports } from ${quote(module)};",
      ""
    ) ++ exportLines)
  }

  /** Re-exports the client export as a default export HonoX can discover */
  def island(entry: IslandEntry, config: CodegenConfig): String = {
    val module = s"${config.virtualModulePrefix}${entry.sourceModule}"
    lines(config.banner ++ List(
      s"// @ts-expect-error $module is a virtual module resolved by @scala-js/vite-plugin-scalajs (no .d.ts).",
      s"import { ${entry.exportedName} as $IslandLocal } from ${quote(module)};",
      s"export default $IslandLocal;"
    ))
  }

  /** Outputs an object with one SSR method per island that ties the client export to the HonoX identifier. Ordered by file path */
  def islandComponents(entries: List[IslandEntry], config: CodegenConfig): String = {
    val sorted = entries.sortBy(_.filePath)
    val header = lines(config.banner :+ s"package ${config.scalaPackage}")
    if (sorted.isEmpty) s"$header\nobject ${config.scalaObject} {}\n"
    else {
      val imports =
        """
          |import japgolly.scalajs.react.facade.{HasMutableDisplayName, React}
          |import scala.scalajs.js
          |import scala.scalajs.js.annotation.JSImport
          |""".stripMargin
      def props(entry: IslandEntry): String = entry.propsType.getOrElse("_root_.scala.scalajs.js.Object")
      val components = sorted.zipWithIndex.map { case (entry, index) =>
        s"""
           |@js.native
           |@JSImport(${quote(s"${config.virtualModulePrefix}${entry.sourceModule}")}, ${quote(entry.exportedName)})
           |private object IslandComponent$index extends js.Function1[${props(entry)}, React.Node] with HasMutableDisplayName {
           |  def apply(props: ${props(entry)}): React.Node = js.native
           |}
           |""".stripMargin
      }.mkString
      val methods = sorted.zipWithIndex.map { case (entry, index) =>
        val componentName = quote(s"${config.islandIdPrefix}${entry.filePath}")
        s"""  def `${entry.exportedName}`(props: ${props(entry)}): _root_.japgolly.scalajs.react.vdom.VdomElement = {
           |    _root_.${config.islandBridge}(_root_.${config.scalaPackage}.IslandComponent$index, props, $componentName)
           |  }
           |
           |""".stripMargin
      }.mkString
      s"$header$imports$components\nobject ${config.scalaObject} {\n$methods}\n"
    }
  }
}
