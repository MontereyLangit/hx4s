package hx4s.codegen

/** Converts route file paths into the URL patterns HonoX createApp registers. Rules in docs/hx4s/routing-design.md 2.2 */
object RoutePath {

  /** URL pattern (e.g. `/items/:id`) of a path relative to the route output directory (e.g. `items/[id].ts`) */
  def pattern(file: String): String = {
    val segments = file.split("/").toList
    val directory = segments.init.mkString("/")
    merge(filePathToPath(if (directory.isEmpty) "" else s"/$directory"), filePathToPath(segments.last))
  }

  /** Shape for comparison without parameter names. HonoX maps patterns of the same shape to the same URL regardless of names */
  def shape(pattern: String): String = pattern.replaceAll(":[^/{]+", ":")

  /** Applies the same replacements in the same order as filePathToPath in honox's server/utils/file.js */
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

  /** Path when a per-directory sub app is mounted with app.route(dir, sub) (hono's mergePath) */
  private def merge(base: String, sub: String): String =
    if (sub == "/") base
    else if (base.endsWith("/")) s"$base${sub.stripPrefix("/")}"
    else s"$base/${sub.stripPrefix("/")}"
}
