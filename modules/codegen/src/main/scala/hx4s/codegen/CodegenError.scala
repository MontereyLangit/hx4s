package hx4s.codegen

import scala.meta.{Position, Tree}

/** An input or output error in codegen. line is 1-based, and None when the position is unknown */
final case class CodegenError(source: String, line: Option[Int], message: String) {
  def render: String = line.fold(s"$source: $message")(n => s"$source:$n: $message")
}

object CodegenError {
  type Result[A] = Either[List[CodegenError], A]

  def at(source: String, tree: Tree, message: String): CodegenError = position(source, tree.pos, message)

  def position(source: String, pos: Position, message: String): CodegenError =
    CodegenError(source, Option.when(pos != Position.None)(pos.startLine + 1), message)

  /** Collects all errors, or returns the list of values if there are none */
  def sequence[A](results: List[Result[A]]): Result[List[A]] = {
    val (errors, values) = partition(results)
    if (errors.nonEmpty) Left(errors) else Right(values)
  }

  /** Splits into all errors and the list of values without errors */
  def partition[A](results: List[Result[A]]): (List[CodegenError], List[A]) =
    (results.collect { case Left(e) => e }.flatten, results.collect { case Right(value) => value })

  /** Combines errors found earlier with the result of processing the remaining input */
  def combine[A](errors: List[CodegenError], result: Result[A]): Result[A] = result match {
    case Right(value) if errors.isEmpty => Right(value)
    case Right(_) => Left(errors)
    case Left(more) => Left(errors ++ more)
  }

  def check(condition: Boolean, error: => CodegenError): Result[Unit] =
    if (condition) Right(()) else Left(List(error))
}
