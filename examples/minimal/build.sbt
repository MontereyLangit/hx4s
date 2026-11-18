import org.scalajs.jsenv.nodejs.NodeJSEnv

ThisBuild / scalaVersion := "3.3.8"

lazy val commonSettings = Seq(
  scalacOptions ++= Seq("-deprecation", "-feature", "-unchecked"),
  scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.ESModule) },
  libraryDependencies += "io.github.montereylangit" %%% "hx4s-react" % hx4sVersion
)

// Island props shared by server and client. Depends on neither
lazy val shared = project
  .enablePlugins(ScalaJSPlugin)
  .settings(commonSettings)

// @route handlers and SSR. Does not depend on the client (client islands are loaded through Vite's virtual module scalajs:client.js)
lazy val server = project
  .enablePlugins(ScalaJSPlugin, Hx4sPlugin)
  .dependsOn(shared)
  .settings(commonSettings)
  .settings(
    hx4sClientProject := client,
    libraryDependencies += "org.scalameta" %%% "munit" % "1.0.0" % Test,
    // Resolve scalajs:client.js imported by the test link output, and hono/jsx inside HonoX, with hx4s-vite's loader
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

// Islands shipped to the browser. Does not depend on the server
lazy val client = project
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(shared)
  .settings(commonSettings)
