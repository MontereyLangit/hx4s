package hx4s.annotation

import scala.annotation.StaticAnnotation

/**
 * Declares a HonoX island for the `@JSExportTopLevel` on the same top-level object member.
 *
 * @tparam P Fully qualified props type in the annotation. When the type argument is omitted, codegen uses `scala.scalajs.js.Object` for SSR.
 * @param filePath Literal `/`-separated path relative to the island output directory, with an optional `.tsx` suffix. The generated basename must match `[A-Za-z0-9-]+\.tsx`.
 */
final class islands[P](val filePath: String) extends StaticAnnotation
