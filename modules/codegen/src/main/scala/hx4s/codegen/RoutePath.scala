package hx4s.codegen

/** Converts generated route filenames to HonoX URL patterns for conflict detection. */
object RoutePath {

  /** Converts a validated route path relative to the route output directory, such as `items/[id].ts`, into a URL pattern such as `/items/:id`. */
  def pattern(file: String): String = {
    val segments = file.split("/").toList
    val directory = segments.init.mkString("/")
    merge(filePathToPath(if (directory.isEmpty) "" else s"/$directory"), filePathToPath(segments.last))
  }

  /** Removes parameter names from a URL pattern for comparisons while preserving the remaining pattern syntax. */
  def shape(pattern: String): String = pattern.replaceAll(":[^/{]+", ":")

  /** Applies the same replacements in the same order as filePathToPath in honox's server/utils/file.js. */
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

  /** Path when a per-directory sub app is mounted with app.route(dir, sub) (hono's mergePath). */
  private def merge(base: String, sub: String): String =
    if (sub == "/") base
    else if (base.endsWith("/")) s"$base${sub.stripPrefix("/")}"
    else s"$base/${sub.stripPrefix("/")}"
}
