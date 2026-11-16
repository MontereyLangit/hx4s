package hx4s.codegen

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}

/** 生成器の識別子と衝突しうる export 名で route / island の TS を書き出す。tests/generated-ts.test.js が Node で読み込んで確かめる */
object TsFixtures {
  val Routes: List[RouteEntry] = List(
    RouteEntry("collide.ts", List(
      RouteHandler(HttpMethod.GET, "createRoute"),
      RouteHandler(HttpMethod.POST, "POST"),
      RouteHandler(HttpMethod.PUT, "GET"),
      RouteHandler(HttpMethod.DELETE, "default"),
      RouteHandler(HttpMethod.PATCH, "class"),
      RouteHandler(HttpMethod.OPTIONS, "__hx4s_createRoute")
    )),
    RouteEntry("shared.ts", List(RouteHandler(HttpMethod.GET, "c"), RouteHandler(HttpMethod.POST, "c")))
  )

  val Islands: List[IslandEntry] = List(IslandEntry("default-name.tsx", "default", "client.js"), IslandEntry("route-name.tsx", "createRoute", "client.js"))

  def main(args: Array[String]): Unit = {
    val dir = Paths.get(args.headOption.getOrElse("target/ts-fixtures")).toAbsolutePath
    Files.createDirectories(dir)
    val config = CodegenConfig.default(dir, Nil, Nil)
    val files = Routes.map(entry => entry.filePath -> AppFileRenderer.route(entry, config)) ++
      Islands.map(entry => entry.filePath -> AppFileRenderer.island(entry, config))
    val manifest = Routes.map { entry =>
      val handlers = entry.sortedHandlers.map(h => s"""{"method":"${h.method}","exportName":"${h.exportName}"}""").mkString(",")
      s"""{"file":"${entry.filePath}","handlers":[$handlers]}"""
    } ++ Islands.map(entry => s"""{"file":"${entry.filePath}","exportName":"${entry.exportedName}"}""")
    (files :+ ("manifest.json" -> manifest.mkString("[\n", ",\n", "\n]\n"))).foreach { case (name, content) =>
      Files.write(dir.resolve(name), content.getBytes(StandardCharsets.UTF_8))
    }
  }
}
