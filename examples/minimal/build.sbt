import org.scalajs.jsenv.nodejs.NodeJSEnv

ThisBuild / scalaVersion := "3.3.8"

lazy val commonSettings = Seq(
  scalacOptions ++= Seq("-deprecation", "-feature", "-unchecked"),
  scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.ESModule) },
  libraryDependencies += "io.github.montereylangit" %%% "hx4s-react" % hx4sVersion
)

// server と client が共有する island の props。server・client のどちらにも依存しない
lazy val shared = project
  .enablePlugins(ScalaJSPlugin)
  .settings(commonSettings)

// @route のハンドラと SSR。client には依存しない（client の island は Vite の仮想モジュール scalajs:client.js で読む）
lazy val server = project
  .enablePlugins(ScalaJSPlugin, Hx4sPlugin)
  .dependsOn(shared)
  .settings(commonSettings)
  .settings(
    hx4sClientProject := client,
    libraryDependencies += "org.scalameta" %%% "munit" % "1.0.0" % Test,
    // テストのリンク成果物が import する scalajs:client.js と、HonoX 内の hono/jsx を hx4s-vite の loader で解決する
    Test / jsEnv := {
      val clientDir = (client / Compile / fastLinkJS / scalaJSLinkerOutputDirectory).value
      (client / Compile / fastLinkJS).value
      new NodeJSEnv(
        NodeJSEnv.Config()
          .withArgs(List("--import", "hx4s-vite/node-register"))
          .withEnv(Map("HX4S_CLIENT_JS_DIR" -> clientDir.getAbsolutePath))
      )
    }
  )

// ブラウザへ配る island。server には依存しない
lazy val client = project
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(shared)
  .settings(commonSettings)
