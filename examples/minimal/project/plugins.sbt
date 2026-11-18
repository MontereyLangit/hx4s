// Uses hx4s published to ~/.ivy2/local with scripts/publish-local.mjs.
// The version comes from the environment variable HX4S_VERSION, or else from the version file written once both the sbt and npm publishes are complete
val hx4sPublishedVersion: String = sys.env.getOrElse("HX4S_VERSION", {
  val versionFile = file("../../target/local/hx4s-version.txt")
  if (!versionFile.exists) sys.error("hx4s is not published locally. Run `node scripts/publish-local.mjs` in the hx4s repository first.")
  IO.read(versionFile).trim
})

addSbtPlugin("io.github.montereylangit" % "sbt-hx4s"                 % hx4sPublishedVersion)
addSbtPlugin("org.scala-js"             % "sbt-scalajs"              % "1.22.0")
addSbtPlugin("org.portable-scala"       % "sbt-scalajs-crossproject" % "1.3.2")
