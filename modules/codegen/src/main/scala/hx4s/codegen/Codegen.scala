package hx4s.codegen

import hx4s.codegen.CodegenError.Result

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import scala.util.Try

/** Reads annotated Scala sources and orchestrates HonoX file generation. */
object Codegen {

  /**
   * Writes route and island files and removes stale generated files under both configured output roots.
   *
   * @param config Settings and input sources for both sides. Empty source lists still allow stale files to be removed.
   * @return File write summary or diagnostics from validation, scanning or output handling.
   */
  def appFiles(config: CodegenConfig): Result[WriteSummary] =
    appOutputs(config).flatMap { files =>
      OutputWriter.write(files, List(config.routeRoot, config.islandRoot), config.generatedMarker, config.projectRoot)
    }

  /**
   * Reads and scans both source sets and renders the TypeScript outputs in memory.
   *
   * @param config Settings and input sources for both sides.
   * @return Route outputs followed by island outputs, or diagnostics.
   */
  def appOutputs(config: CodegenConfig): Result[List[GeneratedFile]] =
    validate(config).flatMap { _ =>
      // Return server and client errors together
      val routes = scan(config, config.serverSources, AnnotationScanner.routes)
      val islands = scan(config, config.clientSources, AnnotationScanner.islands)
      (routes, islands) match {
        case (Right(routeEntries), Right(islandEntries)) =>
          Right(routeEntries.map(entry => GeneratedFile(config.routeRoot, entry.filePath, AppFileRenderer.route(entry, config))) ++
            islandEntries.map(entry => GeneratedFile(config.islandRoot, entry.filePath, AppFileRenderer.island(entry, config))))
        case _ => Left(routes.left.getOrElse(Nil) ++ islands.left.getOrElse(Nil))
      }
    }

  /**
   * Writes the Scala SSR API from client sources, including an empty object when there are no islands.
   *
   * @param config Settings and client sources. `scalaOutput` must be defined.
   * @return File write summary or diagnostics from validation, scanning or output handling.
   */
  def islandComponents(config: CodegenConfig): Result[WriteSummary] =
    for {
      _ <- validate(config)
      output <- config.scalaOutput.toRight(List(CodegenError("<config>", None, "scalaOutput is not set")))
      islands <- scan(config, config.clientSources, AnnotationScanner.islands)
      absolute = config.projectRoot.resolve(output).toAbsolutePath.normalize()
      file = GeneratedFile(absolute.getParent, absolute.getFileName.toString, AppFileRenderer.islandComponents(islands, config))
      summary <- OutputWriter.write(List(file), Nil, config.generatedMarker, config.projectRoot)
    } yield summary

  /**
   * Checks generated identifiers, module IDs, prefixes and banner configuration.
   *
   * @param config Settings to check before scanning sources.
   * @return `Right(())` on success or all detected configuration errors.
   */
  def validate(config: CodegenConfig): Result[Unit] = {
    val identifier = "[A-Za-z_][A-Za-z0-9_]*"
    def scalaName(value: String): Boolean = value.matches(identifier) && !ScalaKeywords(value)
    def qualified(value: String): Boolean = value.split("\\.", -1).forall(scalaName)
    def configError(message: String) = CodegenError("<config>", None, message)
    def valid(ok: Boolean, name: String, value: String, expected: String) =
      CodegenError.check(ok, configError(s"invalid $name ${AppFileRenderer.quote(value)}: expected $expected"))
    val names = "a dot-separated Scala identifier without keywords"
    CodegenError.sequence(List(
      OutputWriter.markerProblem(config.generatedMarker).toLeft(()).left.map(reason => List(configError(reason))),
      CodegenError.check(config.banner.headOption.exists(_.startsWith(config.generatedMarker)),
        configError("the first banner line must start with generatedMarker")),
      CodegenError.check(config.banner.forall(line => line.startsWith("//") && !line.exists(LineTerminators)),
        configError("banner lines must be single-line // comments")),
      valid(qualified(config.scalaPackage), "scalaPackage", config.scalaPackage, names),
      valid(scalaName(config.scalaObject), "scalaObject", config.scalaObject, "a Scala identifier that is not a keyword"),
      valid(qualified(config.islandBridge), "islandBridge", config.islandBridge, names),
      valid(qualified(config.islandExportType), "islandExportType", config.islandExportType, names),
      valid(qualified(config.annotationPackage), "annotationPackage", config.annotationPackage, names),
      valid(config.serverModule.matches(ModuleId), "serverModule", config.serverModule, ModuleId),
      valid(config.clientModule.matches(ModuleId), "clientModule", config.clientModule, ModuleId),
      valid(config.virtualModulePrefix.matches(ModulePrefix), "virtualModulePrefix", config.virtualModulePrefix, ModulePrefix),
      valid(config.islandIdPrefix.matches(UrlPrefix), "islandIdPrefix", config.islandIdPrefix, UrlPrefix),
      CodegenError.check(config.serverModule != config.clientModule, configError("serverModule and clientModule must differ"))
    )).map(_ => ())
  }

  private[codegen] val ModuleId = "[A-Za-z0-9_][A-Za-z0-9_.-]*"
  private val ModulePrefix = "[A-Za-z0-9_@./:-]+"
  private val UrlPrefix = "[A-Za-z0-9_@./:~-]*"
  private[codegen] val LineTerminators: Set[Char] = Set('\n', '\r', '\u2028', '\u2029')

  /** Scala 3 reserved words. Not allowed in the generated package and object names or in any part of fully qualified names the generated code references. */
  private val ScalaKeywords = Set(
    "abstract", "case", "catch", "class", "def", "do", "else", "enum", "export", "extends", "false", "final", "finally", "for",
    "given", "if", "implicit", "import", "lazy", "match", "new", "null", "object", "override", "package", "private", "protected",
    "return", "sealed", "super", "then", "throw", "trait", "true", "try", "type", "val", "var", "while", "with", "yield"
  )

  /** Passes the readable sources to the scanner and returns the result together with errors for unreadable sources. */
  private def scan[A](config: CodegenConfig, sources: List[String], scanner: (List[(String, String)], CodegenConfig) => Result[A]): Result[A] = {
    val (errors, texts) = CodegenError.partition(sources.map { source =>
      Try(Files.readString(config.projectRoot.resolve(source), StandardCharsets.UTF_8)).toEither
        .map(text => source -> text)
        .left.map(error => List(CodegenError(source, None, s"cannot read source: ${error.getMessage}")))
    })
    CodegenError.combine(errors, scanner(texts, config))
  }
}
