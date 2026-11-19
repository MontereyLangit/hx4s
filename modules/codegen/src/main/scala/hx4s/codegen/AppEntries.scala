package hx4s.codegen

/** HTTP methods that HonoX registers as route exports, in the order used for generated handlers. */
enum HttpMethod {
  case GET, POST, PUT, DELETE, PATCH, OPTIONS
}

/** Looks up supported route methods. */
object HttpMethod {
  /** Returns the supported method with the exact uppercase `name`, or `None`. */
  def parse(name: String): Option[HttpMethod] = values.find(_.toString == name)
}

/**
 * Associates an HTTP method with a Scala.js top-level handler export.
 *
 * @param method HTTP method assigned to the handler.
 * @param exportName JavaScript name of the Scala.js top-level export.
 */
final case class RouteHandler(method: HttpMethod, exportName: String)

/**
 * Describes the handlers generated in one route file.
 *
 * @param filePath `/`-separated path relative to the route output directory.
 * @param handlers Handlers assigned to this file.
 */
final case class RouteEntry(filePath: String, handlers: List[RouteHandler]) {
  /** Returns handlers in `HttpMethod` declaration order. */
  def sortedHandlers: List[RouteHandler] = handlers.sortBy(_.method.ordinal)
}

/**
 * Describes the export used to generate one island file and its SSR method.
 *
 * @param filePath `/`-separated path relative to the island output directory.
 * @param exportedName JavaScript name of the Scala.js top-level export.
 * @param sourceModule Scala.js module filename including `.js`, before the virtual module prefix.
 * @param propsType Fully qualified props type prefixed with `_root_.`, or `None` for `scala.scalajs.js.Object`.
 * @param factory Whether the export must be invoked to obtain the component.
 */
final case class IslandEntry(filePath: String, exportedName: String, sourceModule: String, propsType: Option[String] = None, factory: Boolean = false)

/**
 * Holds the discovered output entries.
 *
 * @param routes Route file entries.
 * @param islands Island file entries.
 */
final case class AppEntries(routes: List[RouteEntry], islands: List[IslandEntry])
