package hx4s.codegen

/** Generation failure. sbt run may use the same JVM, so instead of System.exit, this exception causes a non-zero exit */
final class CodegenFailure(message: String) extends RuntimeException(message, null, false, false)

/** Logs the result for main, and throws CodegenFailure on errors */
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
