package hx4s.annotation

import scala.annotation.StaticAnnotation

/** Publishes the JSExportTopLevel of the same declaration to filePath under app/routes. method is GET / POST / PUT / DELETE / PATCH / OPTIONS. A declaration can have several */
final class route(val filePath: String, val method: String = "GET") extends StaticAnnotation
