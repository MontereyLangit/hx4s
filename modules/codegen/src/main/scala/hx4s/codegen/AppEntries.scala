package hx4s.codegen

/** HTTP methods that can go in a route file (those HonoX createApp registers). Exports in a file follow this order */
enum HttpMethod {
  case GET, POST, PUT, DELETE, PATCH, OPTIONS
}

object HttpMethod {
  def parse(name: String): Option[HttpMethod] = values.find(_.toString == name)
}

final case class RouteHandler(method: HttpMethod, exportName: String)

/**
 * One route file
 * @param filePath `/`-separated path relative to the route output directory (e.g. `"items/[id].ts"`)
 */
final case class RouteEntry(filePath: String, handlers: List[RouteHandler]) {
  def sortedHandlers: List[RouteHandler] = handlers.sortBy(_.method.ordinal)
}

/**
 * One island file
 * @param filePath     path relative to the island output directory (e.g. `"counter.tsx"`)
 * @param exportedName name of the `@JSExportTopLevel`
 * @param sourceModule virtual module name imported from (e.g. `"client.js"`)
 * @param propsType    fully qualified name of the props type (with `_root_.`). None if there is no type argument
 */
final case class IslandEntry(filePath: String, exportedName: String, sourceModule: String, propsType: Option[String] = None)

final case class AppEntries(routes: List[RouteEntry], islands: List[IslandEntry])
