package hx4s.codegen

import scala.meta.{Position, Tree}

/**
 * Diagnostic emitted while reading inputs, validating settings or writing outputs.
 *
 * @param source Display path or context label identifying the origin of the error.
 * @param line One-based source line, or `None` when no position is available.
 * @param message Human-readable description of the problem.
 */
final case class CodegenError(source: String, line: Option[Int], message: String) {
  /** Formats a diagnostic with its source label and optional line number. */
  def render: String = line.fold(s"$source: $message")(n => s"$source:$n: $message")
}

/** Creates and combines code generation diagnostics. */
object CodegenError {
  /** Result containing a successful value or a list of diagnostics. */
  type Result[A] = Either[List[CodegenError], A]

  /** Creates a diagnostic at the starting line of `tree` when its position is available. */
  def at(source: String, tree: Tree, message: String): CodegenError = position(source, tree.pos, message)

  /** Converts a ScalaMeta position to a one-based diagnostic line, omitting it for `Position.None`. */
  def position(source: String, pos: Position, message: String): CodegenError =
    CodegenError(source, Option.when(pos != Position.None)(pos.startLine + 1), message)

  /** Returns values in input order when all results succeed, or concatenates all diagnostics in input order. */
  def sequence[A](results: List[Result[A]]): Result[List[A]] = {
    val (errors, values) = partition(results)
    if (errors.nonEmpty) Left(errors) else Right(values)
  }

  /** Returns all diagnostics and all successful values as two lists in input order. */
  def partition[A](results: List[Result[A]]): (List[CodegenError], List[A]) =
    (results.collect { case Left(e) => e }.flatten, results.collect { case Right(value) => value })

  /** Prepends earlier diagnostics to any new diagnostics. Returns a value only when no diagnostics were found. */
  def combine[A](errors: List[CodegenError], result: Result[A]): Result[A] = result match {
    case Right(value) if errors.isEmpty => Right(value)
    case Right(_) => Left(errors)
    case Left(more) => Left(errors ++ more)
  }

  /** Returns a single diagnostic when `condition` is false and evaluates `error` only in that case. */
  def check(condition: Boolean, error: => CodegenError): Result[Unit] =
    if (condition) Right(()) else Left(List(error))
}
