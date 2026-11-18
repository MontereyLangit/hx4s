ThisBuild / scalaVersion := "3.3.8"

lazy val shared = project

// The client depends via mid on a server with Hx4sPlugin enabled (forbidden)
lazy val mid: Project = project.dependsOn(server)
lazy val client: Project = project.dependsOn(shared, mid)

lazy val server: Project = project
  .enablePlugins(Hx4sPlugin)
  .dependsOn(shared)
  .settings(hx4sClientProject := client)

// The server depends on the client (forbidden). Test-only dependencies are excluded
lazy val client2 = project.dependsOn(shared)
lazy val testOnly = project.dependsOn(client2 % "test")
lazy val server2 = project
  .enablePlugins(Hx4sPlugin)
  .dependsOn(shared, testOnly)
  .settings(hx4sClientProject := client2)

lazy val server3 = project
  .enablePlugins(Hx4sPlugin)
  .dependsOn(client2)
  .settings(hx4sClientProject := client2)

def failureMessage(result: Result[_]): String = result match {
  case Inc(incomplete) => Incomplete.allExceptions(incomplete).map(_.getMessage).mkString("\n")
  case Value(_) => sys.error("the task should fail")
}

val checkClientToServer = taskKey[Unit]("Checks that a transitive dependency from client to server fails and prints the path")

checkClientToServer := {
  val message = failureMessage((server / hx4sGenerate).result.value)
  assert(message.contains("the client project client must not depend on the server project server (client -> mid -> server)"), message)
  assert(failureMessage((server / Compile / managedSources).result.value).contains("client -> mid -> server"))
  assert(!((ThisBuild / baseDirectory).value / "app").exists, "nothing is generated")
}

val checkServerToClient = taskKey[Unit]("Checks that a dependency from server to client fails")

checkServerToClient := {
  val message = failureMessage((server3 / hx4sGenerate).result.value)
  assert(message.contains("the server project server3 must not depend on the client project client2 (server3 -> client2)"), message)
}
