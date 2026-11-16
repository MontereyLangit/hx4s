package hx4s.codegen

/** 生成の失敗。sbt の run は同じ JVM で動くことがあるので System.exit せず、この例外で非ゼロ終了にする */
final class CodegenFailure(message: String) extends RuntimeException(message, null, false, false)

/** main 向けに結果をログへ出し、誤りなら CodegenFailure を投げる */
object CodegenReport {
  val Prefix = "[hx4s-codegen]"

  def exitOnError(result: CodegenError.Result[WriteSummary], label: String): WriteSummary = result match {
    case Right(summary) =>
      (summary.written.map(p => s"wrote $p") ++ summary.deleted.map(p => s"deleted $p")).foreach(line => println(s"$Prefix $line"))
      println(s"$Prefix $label: ${summary.render}")
      summary
    case Left(errors) =>
      errors.foreach(error => Console.err.println(s"$Prefix error: ${error.render}"))
      throw new CodegenFailure(s"$label: ${errors.size} error(s)")
  }
}
