import org.scalajs.jsenv.nodejs.NodeJSEnv

val scala3Version        = "3.3.8" // Scala 3 LTS
val sbtPluginScalaVersion = "2.12.21"
val munitVersion         = "1.0.0"
val scalajsReactVersion  = "4.0.0"
val scalajsDomVersion    = "2.8.1"
val scalametaVersion     = "4.17.4"

ThisBuild / organization := "io.github.montereylangit"
// The version is written only in version.txt (docs/release.md). For local publishing, scripts/publish-local.mjs passes -Dhx4s.version=<version>-local.<…>
ThisBuild / version      := {
  val base = IO.read((ThisBuild / baseDirectory).value / "version.txt").trim
  sys.props.getOrElse("hx4s.version", s"$base-SNAPSHOT")
}
ThisBuild / scalaVersion := scala3Version
ThisBuild / licenses     := List("Apache-2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0"))

lazy val commonSettings = Seq(
  scalacOptions ++= Seq("-deprecation", "-feature", "-unchecked"),
  libraryDependencies += "org.scalameta" %%% "munit" % munitVersion % Test,
  testFrameworks += new TestFramework("munit.Framework")
)

lazy val esModule = scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.ESModule) }

// --- hx4s-core: @route / @islands[P] (JVM + JS) and props validation on the JS side ---
lazy val core = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Full)
  .in(file("modules/core"))
  .settings(commonSettings)
  .settings(name := "hx4s-core")
  .jsSettings(esModule)

lazy val coreJVM = core.jvm
lazy val coreJS  = core.js

// --- hx4s-react: HonoX island bridge, typed exports, facade for Hono's Context ---
lazy val react = project
  .in(file("modules/react"))
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(coreJS)
  .settings(commonSettings)
  .settings(
    name := "hx4s-react",
    libraryDependencies ++= Seq(
      "com.github.japgolly.scalajs-react" %%% "core"        % scalajsReactVersion,
      "org.scala-js"                      %%% "scalajs-dom" % scalajsDomVersion
    ),
    esModule,
    // Point hono/jsx imported by HonoX's HonoXIsland to react (vite/node-loader.mjs)
    Test / jsEnv := new NodeJSEnv(
      NodeJSEnv.Config().withArgs(List("--import", ((ThisBuild / baseDirectory).value / "vite" / "node-register.mjs").toURI.toString))
    )
  )

// --- hx4s-codegen: reads declarations with scalameta and generates routes / islands and IslandComponents (JVM) ---
lazy val codegen = project
  .in(file("modules/codegen"))
  .settings(commonSettings)
  .settings(
    name := "hx4s-codegen",
    libraryDependencies += ("org.scalameta" %% "scalameta" % scalametaVersion).cross(CrossVersion.for3Use2_13),
    Compile / mainClass := Some("hx4s.codegen.CodegenMain")
  )

// --- sbt-hx4s: setting keys and hx4sGenerate. Codegen is resolved in a dedicated configuration and runs in a forked JVM ---
lazy val sbtHx4s = project
  .in(file("sbt-plugin"))
  .enablePlugins(SbtPlugin)
  .settings(
    name := "sbt-hx4s",
    scalaVersion := sbtPluginScalaVersion,
    crossScalaVersions := Seq(sbtPluginScalaVersion),
    // Don't carry ThisBuild's Scala 3 options (such as the -no-indent check) over to 2.12
    scalacOptions := Seq("-deprecation", "-feature", "-unchecked", "-Xlint:_,-unused"),
    Compile / sourceGenerators += Def.task {
      val output = (Compile / sourceManaged).value / "hx4s" / "sbt" / "Hx4sBuildInfo.scala"
      IO.write(output,
        s"""package hx4s.sbt
           |
           |private[sbt] object Hx4sBuildInfo {
           |  val organization: String = "${organization.value}"
           |  val version: String = "${version.value}"
           |}
           |""".stripMargin)
      Seq(output)
    }.taskValue,
    scriptedLaunchOpts ++= Seq("-Xmx1024M", s"-Dplugin.version=${version.value}"),
    scriptedBufferLog := false,
    scriptedDependencies := scriptedDependencies.dependsOn(codegen / publishLocal).value
  )

lazy val root = project
  .in(file("."))
  .aggregate(coreJVM, coreJS, react, codegen, sbtHx4s)
  .settings(
    name := "hx4s",
    publish / skip := true
  )
