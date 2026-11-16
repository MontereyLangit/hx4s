sys.props.get("plugin.version") match {
  case Some(version) => addSbtPlugin("io.github.montereylangit" % "sbt-hx4s" % version)
  case None => sys.error("the system property plugin.version is not set (run through sbt scripted)")
}
