package hx4s.codegen

/**
 * Signals CLI failure with a summary message and no stack trace.
 *
 * @param message Summary of the generation or argument error.
 */
final class CodegenFailure(message: String) extends RuntimeException(message, null, false, false)

/** Reports generation results for the CLI. */
object CodegenReport {
  /** Log prefix shared by CLI summaries and diagnostics. */
  val Prefix = "[hx4s-codegen]"

  /**
   * Logs updated and deleted paths and a summary to stdout, or diagnostics to stderr.
   *
   * @param result Result of the generation operation.
   * @param label Label for the summary or failure message.
   * @return The successful write summary.
   * @throws CodegenFailure If `result` contains diagnostics.
   */
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
