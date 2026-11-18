package hx4s.codegen

import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.{Files, LinkOption, Path, Paths}
import scala.jdk.CollectionConverters.*
import scala.util.Using

/** Codegen settings for tests. Uses values different from the defaults to check that settings take effect */
object TestConfig {
  def apply(root: Path = Paths.get("unused")): CodegenConfig = CodegenConfig(
    projectRoot = root,
    serverSources = List("server/App.scala"),
    clientSources = List("client/App.scala"),
    serverModule = "server",
    clientModule = "client",
    routeDir = "app/routes",
    islandDir = "app/islands",
    islandIdPrefix = "/app/islands/",
    scalaOutput = None,
    scalaPackage = "example.generated",
    scalaObject = "Islands",
    islandBridge = "example.bridge.Island",
    islandExportType = "example.bridge.IslandExport",
    annotationPackage = "example.annotations",
    virtualModulePrefix = "scalajs:",
    banner = List("// generated-by-test", "// do not edit"),
    generatedMarker = "// generated-by-test"
  )

  def withTempDir[A](test: Path => A): A = {
    val root = Files.createTempDirectory("hx4s-codegen-test-")
    try { test(root) }
    finally { delete(root) }
  }

  /** Deletes links and junctions themselves without following them */
  private def delete(path: Path): Unit = {
    val attributes = Files.readAttributes(path, classOf[BasicFileAttributes], LinkOption.NOFOLLOW_LINKS)
    if (attributes.isDirectory && !attributes.isOther) Using.resource(Files.list(path))(_.iterator().asScala.toList).foreach(delete)
    path.toFile.setWritable(true)
    Files.delete(path)
  }
}
