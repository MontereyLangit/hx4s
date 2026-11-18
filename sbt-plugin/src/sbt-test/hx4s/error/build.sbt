ThisBuild / scalaVersion := "3.3.8"

lazy val client = project.in(file("client app"))

lazy val server = project
  .enablePlugins(Hx4sPlugin)
  .settings(hx4sClientProject := client)

def failureMessage(result: Result[_]): String = result match {
  case Inc(incomplete) => Incomplete.allExceptions(incomplete).map(_.getMessage).mkString("\n")
  case Value(_) => sys.error("the task should fail")
}

val checkGenerateFailure = taskKey[Unit]("Checks that the hx4sGenerate failure shows the source path and line number")

checkGenerateFailure := {
  val message = failureMessage((server / hx4sGenerate).result.value)
  assert(message.contains("server/src/main/scala/app/Main.scala:7: HEAD is not registered by HonoX createApp"), message)
  assert(message.contains("client app/src/main/scala/app/Main.scala:10: IslandExport[app.shared.OtherProps] does not match @islands[app.shared.CounterProps]"), message)
  assert(!((ThisBuild / baseDirectory).value / "app").exists, "nothing is written when there are errors")
  val lines = message.split("\n").toList
  assert(lines.distinct == lines, s"duplicated lines in the message: $message")
}

val checkGenerateLog = taskKey[Unit]("Checks that each error line appears only once in the log of the hx4sGenerate failure")

checkGenerateLog := {
  val logs = ((server / target).value / "streams" ** "out").get.filter(_.getPath.contains("hx4sGenerate"))
  assert(logs.size == 1, s"expected one log of hx4sGenerate: $logs")
  val lines = IO.readLines(logs.head)
  val errors = lines.filter(_.contains("[hx4s-codegen] error: "))
  val text = lines.mkString("\n")
  assert(errors.size == 2, text)
  assert(errors.exists(_.contains("server/src/main/scala/app/Main.scala:7: HEAD is not registered by HonoX createApp")), text)
  assert(errors.exists(_.contains("client app/src/main/scala/app/Main.scala:10: IslandExport[app.shared.OtherProps] does not match")), text)
  assert(!lines.exists(_.contains("Exception in thread")), text)
}

val checkIslandComponentsFailure = taskKey[Unit]("Checks that the sourceGenerators failure shows the source path and line number")

checkIslandComponentsFailure := {
  val message = failureMessage((server / Compile / managedSources).result.value)
  assert(message.contains("client app/src/main/scala/app/Main.scala:10: IslandExport[app.shared.OtherProps]"), message)
}
