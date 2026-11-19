package hx4s.annotation

import scala.annotation.StaticAnnotation

/**
 * Declares a HonoX route for the `@JSExportTopLevel` on the same top-level object member.
 *
 * @param filePath Literal `/`-separated `.ts` path relative to the route output directory.
 * @param method Literal uppercase `GET`, `POST`, `PUT`, `DELETE`, `PATCH`, or `OPTIONS`. Defaults to `GET`, which also handles `HEAD`.
 */
final class route(val filePath: String, val method: String = "GET") extends StaticAnnotation
