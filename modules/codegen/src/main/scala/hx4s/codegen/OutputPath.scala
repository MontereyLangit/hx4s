package hx4s.codegen

import java.nio.file.Path
import java.util.Locale

/** Rules for relative paths of generated files, so that nothing is written outside the output root or to reserved names on any OS */
object OutputPath {
  private val SegmentPattern = "[A-Za-z0-9_.$\\[\\]-]+"
  private val Reserved = Set("CON", "PRN", "AUX", "NUL") ++ (1 to 9).flatMap(n => List(s"COM$n", s"LPT$n"))

  /** Checks a `/`-separated relative path segment by segment. Returns the reason on error */
  def segments(relative: String): Either[String, List[String]] = {
    val parts = relative.split("/", -1).toList
    parts.collectFirst(Function.unlift(problem)) match {
      case Some(reason) => Left(s"unsafe output path $relative: $reason")
      case None => Right(parts)
    }
  }

  private def problem(segment: String): Option[String] = {
    if (segment.isEmpty) Some("empty segment")
    else if (segment == "." || segment == "..") Some(s"relative segment $segment")
    else if (!segment.matches(SegmentPattern)) Some(s"unsupported characters in $segment")
    else if (segment.endsWith(".")) Some(s"trailing dot in $segment")
    else if (Reserved(segment.takeWhile(_ != '.').toUpperCase(Locale.ROOT))) Some(s"reserved device name $segment")
    else None
  }

  /** Resolves under root. An error if the normalized path leaves root */
  def resolve(root: Path, relative: String): Either[String, Path] = {
    segments(relative).flatMap { parts =>
      val base = root.toAbsolutePath.normalize()
      val path = parts.foldLeft(base)(_.resolve(_)).normalize()
      if (path.startsWith(base) && path != base) Right(path) else Left(s"output path $relative escapes $base")
    }
  }
}
