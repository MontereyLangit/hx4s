ThisBuild / scalaVersion := "3.3.8"

// A server with only @route handlers. No client (islands) and no React
lazy val server = project
  .enablePlugins(ScalaJSPlugin, Hx4sPlugin)
  .settings(
    scalacOptions ++= Seq("-deprecation", "-feature", "-unchecked"),
    scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.ESModule) },
    libraryDependencies ++= Seq(
      "io.github.montereylangit" %%% "hx4s-react" % hx4sVersion,
      "org.scalameta"            %%% "munit"      % "1.0.0" % Test
    )
  )
