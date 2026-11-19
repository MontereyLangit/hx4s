package hx4s.codegen

import hx4s.codegen.CodegenError.Result

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}

/** CLI entry point for `app` TypeScript generation and `island-components` Scala SSR generation. */
object CodegenMain {
  /** Available generation modes. */
  enum Command {
    case App, IslandComponents
  }

  /**
   * Parsed CLI command and its output settings.
   *
   * @param command Generation mode selected by the first CLI argument.
   * @param config Validated code generation settings.
   * @param outputList Optional path for the UTF-8 list of written and unchanged files.
   */
  final case class Invocation(command: Command, config: CodegenConfig, outputList: Option[Path])

  private val Repeatable = Set("server-sources", "client-sources", "banner")
  private val Keys = Set(
    "project-root", "server-sources", "client-sources", "server-module", "client-module", "route-dir", "island-dir",
    "island-id-prefix", "scala-output", "scala-package", "scala-object", "island-bridge", "island-export-type", "annotation-package",
    "virtual-module-prefix", "banner", "generated-marker", "output-list"
  )

  /**
   * Runs generation and reports diagnostics. Writes the optional output list after generation succeeds.
   *
   * @param args Command followed by `--key value` pairs.
   * @throws CodegenFailure If argument parsing returns diagnostics or generation returns errors.
   */
  def main(args: Array[String]): Unit = {
    parse(args.toList) match {
      case Left(errors) =>
        errors.foreach(error => Console.err.println(s"${CodegenReport.Prefix} error: ${error.render}"))
        throw new CodegenFailure(s"invalid arguments: ${errors.size} error(s)")
      case Right(invocation) =>
        val (result, label) = invocation.command match {
          case Command.App => (Codegen.appFiles(invocation.config), "route / island files")
          case Command.IslandComponents => (Codegen.islandComponents(invocation.config), "island components")
        }
        val summary = CodegenReport.exitOnError(result, label)
        invocation.outputList.foreach { path =>
          val files = (summary.written ++ summary.unchanged).map(_.toString).sorted
          Files.createDirectories(path.toAbsolutePath.getParent)
          Files.write(path, files.mkString("", "\n", "\n").getBytes(StandardCharsets.UTF_8))
        }
    }
  }

  /**
   * Parses the command and options and validates the resulting settings. Only source and banner options may repeat.
   *
   * @param args Command followed by `--key value` pairs.
   * @param workingDirectory Default project root when `--project-root` is omitted.
   * @return Parsed invocation using default settings for omitted options, or diagnostics for invalid arguments or settings.
   */
  def parse(args: List[String], workingDirectory: Path = Paths.get(System.getProperty("user.dir"))): Result[Invocation] = {
    def error(message: String) = CodegenError("<args>", None, message)
    val command = args.headOption match {
      case Some("app") => Right(Command.App)
      case Some("island-components") => Right(Command.IslandComponents)
      case other => Left(List(error(s"expected command app or island-components, found ${other.getOrElse("nothing")}")))
    }
    val pairs = args.drop(1).grouped(2).toList.map {
      case List(key, value) if key.startsWith("--") && Keys(key.drop(2)) => Right(key.drop(2) -> value)
      case List(key, _) => Left(List(error(s"unknown option $key")))
      case List(key) => Left(List(error(s"missing value for $key")))
      case _ => Left(List(error("invalid arguments")))
    }
    (command, CodegenError.sequence(pairs)) match {
      case (Left(a), Left(b)) => Left(a ++ b)
      case (Left(a), Right(_)) => Left(a)
      case (Right(_), Left(b)) => Left(b)
      case (Right(cmd), Right(parsed)) => toInvocation(cmd, parsed.groupMap(_._1)(_._2), workingDirectory)
    }
  }

  private def toInvocation(command: Command, values: Map[String, List[String]], workingDirectory: Path): Result[Invocation] = {
    def error(message: String) = CodegenError("<args>", None, message)
    val repeated = values.toList.sortBy(_._1).collect {
      case (key, list) if list.sizeIs > 1 && !Repeatable(key) => error(s"option --$key is given more than once")
    }
    def single(key: String): Option[String] = values.get(key).flatMap(_.headOption)
    def many(key: String): List[String] = values.getOrElse(key, Nil).filter(_.nonEmpty)
    val projectRoot = single("project-root").fold(workingDirectory)(Paths.get(_)).toAbsolutePath.normalize()
    if (repeated.nonEmpty) Left(repeated)
    else {
      val base = CodegenConfig.default(projectRoot, many("server-sources"), many("client-sources"))
      val config = base.copy(
        serverModule = single("server-module").getOrElse(base.serverModule),
        clientModule = single("client-module").getOrElse(base.clientModule),
        routeDir = single("route-dir").getOrElse(base.routeDir),
        islandDir = single("island-dir").getOrElse(base.islandDir),
        islandIdPrefix = single("island-id-prefix").getOrElse(base.islandIdPrefix),
        scalaOutput = single("scala-output").map(Paths.get(_)),
        scalaPackage = single("scala-package").getOrElse(base.scalaPackage),
        scalaObject = single("scala-object").getOrElse(base.scalaObject),
        islandBridge = single("island-bridge").getOrElse(base.islandBridge),
        islandExportType = single("island-export-type").getOrElse(base.islandExportType),
        annotationPackage = single("annotation-package").getOrElse(base.annotationPackage),
        virtualModulePrefix = single("virtual-module-prefix").getOrElse(base.virtualModulePrefix),
        banner = values.getOrElse("banner", base.banner),
        generatedMarker = single("generated-marker").getOrElse(base.generatedMarker)
      )
      Codegen.validate(config).map(_ => Invocation(command, config, single("output-list").map(Paths.get(_))))
    }
  }
}
