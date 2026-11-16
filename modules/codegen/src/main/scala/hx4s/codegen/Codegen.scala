package hx4s.codegen

import hx4s.codegen.CodegenError.Result

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import scala.util.Try

/** 設定に従ってソースを読み、route / island の TS ファイルと SSR 用 Scala API を生成する */
object Codegen {

  /** route と island の TS ファイルを書く。古い生成ファイルは消す。ソースが空なら出力も空にする */
  def appFiles(config: CodegenConfig): Result[WriteSummary] =
    appOutputs(config).flatMap { files =>
      OutputWriter.write(files, List(config.routeRoot, config.islandRoot), config.generatedMarker, config.projectRoot)
    }

  /** route と island の TS ファイルの内容 */
  def appOutputs(config: CodegenConfig): Result[List[GeneratedFile]] =
    validate(config).flatMap { _ =>
      // server と client の誤りをまとめて返す
      val routes = read(config, config.serverSources).flatMap(AnnotationScanner.routes(_, config))
      val islands = read(config, config.clientSources).flatMap(AnnotationScanner.islands(_, config))
      (routes, islands) match {
        case (Right(routeEntries), Right(islandEntries)) =>
          Right(routeEntries.map(entry => GeneratedFile(config.routeRoot, entry.filePath, AppFileRenderer.route(entry, config))) ++
            islandEntries.map(entry => GeneratedFile(config.islandRoot, entry.filePath, AppFileRenderer.island(entry, config))))
        case _ => Left(routes.left.getOrElse(Nil) ++ islands.left.getOrElse(Nil))
      }
    }

  /** SSR 用 Scala API。入力は client のソースだけ */
  def islandComponents(config: CodegenConfig): Result[WriteSummary] =
    for {
      _ <- validate(config)
      output <- config.scalaOutput.toRight(List(CodegenError("<config>", None, "scalaOutput is not set")))
      islands <- read(config, config.clientSources).flatMap(AnnotationScanner.islands(_, config))
      absolute = config.projectRoot.resolve(output).toAbsolutePath.normalize()
      file = GeneratedFile(absolute.getParent, absolute.getFileName.toString, AppFileRenderer.islandComponents(islands, config))
      summary <- OutputWriter.write(List(file), Nil, config.generatedMarker, config.projectRoot)
    } yield summary

  /** 設定の値を確かめる。生成ソースへ埋め込む値は識別子・パスとして使える文字に限る。誤りはまとめて返す */
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

  private val ModuleId = "[A-Za-z0-9_][A-Za-z0-9_.-]*"
  private val ModulePrefix = "[A-Za-z0-9_@./:-]+"
  private val UrlPrefix = "[A-Za-z0-9_@./:~-]*"
  private[codegen] val LineTerminators: Set[Char] = Set('\n', '\r', '\u2028', '\u2029')

  /** Scala 3 の予約語。生成する package・object 名と、生成コードが参照する完全修飾名の各部に使えない */
  private val ScalaKeywords = Set(
    "abstract", "case", "catch", "class", "def", "do", "else", "enum", "export", "extends", "false", "final", "finally", "for",
    "given", "if", "implicit", "import", "lazy", "match", "new", "null", "object", "override", "package", "private", "protected",
    "return", "sealed", "super", "then", "throw", "trait", "true", "try", "type", "val", "var", "while", "with", "yield"
  )

  private def read(config: CodegenConfig, sources: List[String]): Result[List[(String, String)]] =
    CodegenError.sequence(sources.map { source =>
      Try(Files.readString(config.projectRoot.resolve(source), StandardCharsets.UTF_8)).toEither
        .map(text => source -> text)
        .left.map(error => List(CodegenError(source, None, s"cannot read source: ${error.getMessage}")))
    })
}
