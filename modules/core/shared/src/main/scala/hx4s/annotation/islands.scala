package hx4s.annotation

import scala.annotation.StaticAnnotation

/** 同じ宣言の JSExportTopLevel を app/islands の filePath へ公開する。P は props の型の完全修飾名で、省くと js.Object */
final class islands[P](val filePath: String) extends StaticAnnotation
