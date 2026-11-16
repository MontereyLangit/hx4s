ThisBuild / scalaVersion := "3.3.8"

lazy val client = project.in(file("client app"))

lazy val server = project
  .enablePlugins(Hx4sPlugin)
  .settings(hx4sClientProject := client)

def failureMessage(result: Result[_]): String = result match {
  case Inc(incomplete) => Incomplete.allExceptions(incomplete).map(_.getMessage).mkString("\n")
  case Value(_) => sys.error("the task should fail")
}

val checkGenerateFailure = taskKey[Unit]("hx4sGenerate の失敗にソースのパスと行番号が出ることを確かめる")

checkGenerateFailure := {
  val message = failureMessage((server / hx4sGenerate).result.value)
  assert(message.contains("server/src/main/scala/app/Main.scala:7: HEAD is not registered by HonoX createApp"), message)
  assert(message.contains("client app/src/main/scala/app/Main.scala:10: IslandExport[app.shared.OtherProps] does not match @islands[app.shared.CounterProps]"), message)
  assert(!((ThisBuild / baseDirectory).value / "app").exists, "nothing is written when there are errors")
}

val checkIslandComponentsFailure = taskKey[Unit]("sourceGenerators の失敗にソースのパスと行番号が出ることを確かめる")

checkIslandComponentsFailure := {
  val message = failureMessage((server / Compile / managedSources).result.value)
  assert(message.contains("client app/src/main/scala/app/Main.scala:10: IslandExport[app.shared.OtherProps]"), message)
}
