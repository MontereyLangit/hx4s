package hx4s.codegen

import java.nio.file.Path
import java.util.Locale

/** Validates relative output path syntax before filesystem checks. */
object OutputPath {
  private val SegmentPattern = "[A-Za-z0-9_.$\\[\\]-]+"
  private val Reserved = Set("CON", "PRN", "AUX", "NUL") ++ (1 to 9).flatMap(n => List(s"COM$n", s"LPT$n"))

  /** Validates `/`-separated path segments, rejecting empty and relative segments, unsupported characters, trailing dots and reserved device names. */
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

  /** Resolves a validated relative path to a normalized absolute descendant of `root`, or returns a diagnostic for an invalid path. */
  def resolve(root: Path, relative: String): Either[String, Path] = {
    segments(relative).flatMap { parts =>
      val base = root.toAbsolutePath.normalize()
      val path = parts.foldLeft(base)(_.resolve(_)).normalize()
      if (path.startsWith(base) && path != base) Right(path) else Left(s"output path $relative escapes $base")
    }
  }
}
