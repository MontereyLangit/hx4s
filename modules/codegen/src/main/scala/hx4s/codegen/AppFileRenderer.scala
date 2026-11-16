package hx4s.codegen

/** route / island の TS ファイルと、SSR 用 Scala API を描画する */
object AppFileRenderer {
  /** import した名前に付ける別名。export 名が生成コードの識別子（createRoute、POST など）と衝突しないようにする */
  private val CreateRoute = "__hx4s_createRoute"
  private val HandlerPrefix = "__hx4s_handler_"
  private val IslandLocal = "__hx4s_island"

  private def lines(values: List[String]): String = values.mkString("", "\n", "\n")

  /** TS と Scala のどちらでも同じ値になる二重引用符の文字列リテラル */
  private[codegen] def quote(text: String): String = {
    val escaped = text.flatMap {
      case '"' => "\\\""
      case '\\' => "\\\\"
      case c if c < ' ' || c == '\u007f' || c == '\u2028' || c == '\u2029' => f"\\u${c.toInt}%04x"
      case c => c.toString
    }
    s"\"$escaped\""
  }

  /** GET は `export default`、それ以外は `export const <METHOD>` にする */
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
      s"// @ts-expect-error $module は仮想モジュール（vite.config.ts参照）。",
      s"import { $imports } from ${quote(module)};",
      ""
    ) ++ exportLines)
  }

  /** client の export を HonoX が見つけられる default export として再公開する */
  def island(entry: IslandEntry, config: CodegenConfig): String = {
    val module = s"${config.virtualModulePrefix}${entry.sourceModule}"
    lines(config.banner ++ List(
      s"// @ts-expect-error $module は @scala-js/vite-plugin-scalajs が解決する仮想モジュール（.d.ts無し）。",
      s"import { ${entry.exportedName} as $IslandLocal } from ${quote(module)};",
      s"export default $IslandLocal;"
    ))
  }

  /** island ごとに、client の export と HonoX の識別子を結んだ SSR 用メソッドを持つ object を出す。並びはファイルパス順 */
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
