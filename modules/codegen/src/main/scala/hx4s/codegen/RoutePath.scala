package hx4s.codegen

/** route ファイルのパスを HonoX の createApp が登録する URL パターンへ変換する。規則は docs/hx4s/routing-design.md 2.2 */
object RoutePath {

  /** route 出力ディレクトリからの相対パス（例: `items/[id].ts`）の URL パターン（例: `/items/:id`） */
  def pattern(file: String): String = {
    val segments = file.split("/").toList
    val directory = segments.init.mkString("/")
    merge(filePathToPath(if (directory.isEmpty) "" else s"/$directory"), filePathToPath(segments.last))
  }

  /** パラメータ名を除いた比較用の形。HonoX は同じ形のパターンを名前に関係なく同じ URL に割り当てる */
  def shape(pattern: String): String = pattern.replaceAll(":[^/{]+", ":")

  /** honox の server/utils/file.js の filePathToPath と同じ置換を同じ順に行う */
  private def filePathToPath(path: String): String = {
    val replaced = path
      .replaceAll("\\.tsx?$", "")
      .replaceAll("\\.mdx?$", "")
      .replaceFirst("^/?index$", "/")
      .replaceFirst("/index$", "")
      .replaceFirst("\\[\\.{3}(.+?)\\]", ":$1{.+}")
      .replaceAll("\\((.+?)\\)", "")
      .replaceAll("\\[(.+?)\\]", ":$1")
      .replaceAll("//+", "/")
    if (replaced.startsWith("/")) replaced else s"/$replaced"
  }

  /** ディレクトリごとの sub app を app.route(dir, sub) で載せたときのパス（hono の mergePath） */
  private def merge(base: String, sub: String): String =
    if (sub == "/") base
    else if (base.endsWith("/")) s"$base${sub.stripPrefix("/")}"
    else s"$base/${sub.stripPrefix("/")}"
}
