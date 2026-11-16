package hx4s.annotation

import scala.annotation.StaticAnnotation

/** 同じ宣言の JSExportTopLevel を app/routes の filePath へ公開する。method は GET / POST / PUT / DELETE / PATCH / OPTIONS。1 つの宣言に複数付けられる */
final class route(val filePath: String, val method: String = "GET") extends StaticAnnotation
