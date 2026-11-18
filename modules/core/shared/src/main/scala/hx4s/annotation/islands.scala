package hx4s.annotation

import scala.annotation.StaticAnnotation

/** Publishes the JSExportTopLevel of the same declaration to filePath under app/islands. P is the fully qualified name of the props type, js.Object if omitted */
final class islands[P](val filePath: String) extends StaticAnnotation
