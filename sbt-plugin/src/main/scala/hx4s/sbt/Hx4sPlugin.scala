package hx4s.sbt

import sbt._
import sbt.Keys._
import sbt.internal.util.FeedbackProvidedException

import scala.collection.mutable.ListBuffer

/**
 * Generates HonoX routes and islands and IslandComponents for SSR from route / islands annotations. Enable it on the server project
 * hx4s-codegen (Scala 3) is resolved in a dedicated configuration and run in a forked JVM
 */
object Hx4sPlugin extends AutoPlugin {
  override def trigger = noTrigger
  override def requires = plugins.JvmPlugin

  object autoImport {
    val Hx4sCodegen = config("hx4s-codegen").hide

    /** This plugin's version. Use the same version for the hx4s-core / hx4s-react dependencies */
    val hx4sVersion: String = Hx4sBuildInfo.version

    val hx4sClientProject = settingKey[ProjectReference]("The client project with @islands. Default source of hx4sClientSources. If unset, there is no client (SSR only)")
    val hx4sServerSources = taskKey[Seq[File]]("Source files searched for @route. Defaults to this project's Compile Scala sources")
    val hx4sClientSources = taskKey[Seq[File]]("Source files searched for @islands. Defaults to hx4sClientProject's Compile Scala sources")
    val hx4sProjectRoot = settingKey[File]("Base directory for generation (Vite's root). Defaults to the build root")
    val hx4sRouteDir = settingKey[File]("Output directory for route files. Defaults to <hx4sProjectRoot>/app/routes")
    val hx4sIslandDir = settingKey[File]("Output directory for island files. Defaults to <hx4sProjectRoot>/app/islands")
    val hx4sIslandIdPrefix = settingKey[String]("Prefix of the island identifier passed to HonoX during SSR. Defaults to /app/islands/")
    val hx4sServerModule = settingKey[String]("moduleID of the server's @JSExportTopLevel. Defaults to server")
    val hx4sClientModule = settingKey[String]("moduleID of the client's @JSExportTopLevel. Defaults to client")
    val hx4sIslandComponentsPackage = settingKey[String]("Package of the generated IslandComponents. Defaults to hx4s.generated")
    val hx4sIslandComponentsObject = settingKey[String]("Name of the generated object. Defaults to IslandComponents")
    val hx4sIslandBridge = settingKey[String]("Fully qualified name of the function that marks island boundaries. Defaults to hx4s.react.Island")
    val hx4sIslandExportType = settingKey[String]("Type of client exports matched against @islands[P]. Defaults to hx4s.react.IslandExport")
    val hx4sAnnotationPackage = settingKey[String]("Package of the route / islands annotations. Defaults to hx4s.annotation")
    val hx4sVirtualModulePrefix = settingKey[String]("Prefix of the virtual module pointing to the Scala.js link output. Defaults to scalajs:")
    val hx4sBanner = settingKey[Seq[String]]("First lines of generated files. hx4s-codegen's default if empty")
    val hx4sGeneratedMarker = settingKey[Option[String]]("Prefix of the first line that identifies generated files. hx4s-codegen's default if None")
    val hx4sCodegenVersion = settingKey[String]("Version of hx4s-codegen to run. Defaults to the same version as this plugin")
    val hx4sCodegenJavaOptions = settingKey[Seq[String]]("Options for the JVM that runs hx4s-codegen")
    val hx4sCodegenClasspath = taskKey[Seq[File]]("Runtime classpath of hx4s-codegen")
    val hx4sCheckDependencies = taskKey[Unit]("Checks that the client does not depend on a project with Hx4sPlugin enabled, and that this project does not depend on the client")
    val hx4sGenerate = taskKey[Seq[File]]("Generates route / island TS files and returns the files generated (or kept because their content was identical)")
    val hx4sIslandComponents = taskKey[Seq[File]]("Generates IslandComponents for SSR from the client's @islands")
  }

  import autoImport._

  private val MainClass = "hx4s.codegen.CodegenMain"

  override def projectConfigurations: Seq[Configuration] = Seq(Hx4sCodegen)

  override def projectSettings: Seq[Setting[_]] = Seq(
    libraryDependencies += Hx4sBuildInfo.organization % "hx4s-codegen_3" % hx4sCodegenVersion.value % Hx4sCodegen,
    hx4sCodegenVersion := Hx4sBuildInfo.version,
    hx4sCodegenJavaOptions := Seq.empty,
    hx4sCodegenClasspath := Classpaths.managedJars(Hx4sCodegen, classpathTypes.value, update.value).map(_.data),
    hx4sServerSources := scalaSources((Compile / unmanagedSources).value),
    hx4sClientSources := clientSourcesTask.value,
    hx4sProjectRoot := (ThisBuild / baseDirectory).value,
    hx4sRouteDir := hx4sProjectRoot.value / "app" / "routes",
    hx4sIslandDir := hx4sProjectRoot.value / "app" / "islands",
    hx4sIslandIdPrefix := "/app/islands/",
    hx4sServerModule := "server",
    hx4sClientModule := "client",
    hx4sIslandComponentsPackage := "hx4s.generated",
    hx4sIslandComponentsObject := "IslandComponents",
    hx4sIslandBridge := "hx4s.react.Island",
    hx4sIslandExportType := "hx4s.react.IslandExport",
    hx4sAnnotationPackage := "hx4s.annotation",
    hx4sVirtualModulePrefix := "scalajs:",
    hx4sBanner := Seq.empty,
    hx4sGeneratedMarker := None,
    hx4sCheckDependencies := checkDependenciesTask.value,
    hx4sGenerate := generateTask.value,
    hx4sIslandComponents := islandComponentsTask.value,
    Compile / sourceGenerators += hx4sIslandComponents.taskValue
  )

  private def scalaSources(files: Seq[File]): Seq[File] = files.filter(_.getName.endsWith(".scala")).sortBy(_.getAbsolutePath)

  private lazy val clientSourcesTask: Def.Initialize[Task[Seq[File]]] = Def.taskDyn {
    hx4sClientProject.?.value match {
      case Some(client) => Def.task(scalaSources((client / Compile / unmanagedSources).value))
      case None => Def.task(Seq.empty[File])
    }
  }

  private lazy val clientRefTask: Def.Initialize[Task[Option[ProjectRef]]] = Def.taskDyn {
    hx4sClientProject.?.value match {
      case Some(client) => Def.task(Option((client / thisProjectRef).value))
      case None => Def.task(Option.empty[ProjectRef])
    }
  }

  private lazy val checkDependenciesTask: Def.Initialize[Task[Unit]] = Def.task {
    val server = thisProjectRef.value
    val servers = loadedBuild.value.allProjectRefs.collect { case (ref, project) if project.autoPlugins.contains(Hx4sPlugin) => ref }.toSet
    DependencyCheck.problems(server, clientRefTask.value, servers, buildDependencies.value.classpath) match {
      case Nil => ()
      case problems => throw new MessageOnlyException(problems.mkString("\n"))
    }
  }

  /** Builds the --key value arguments of hx4s-codegen from the setting keys. Paths under root become /-separated relative paths */
  private lazy val sharedArguments: Def.Initialize[Task[Seq[String]]] = Def.task {
    val root = hx4sProjectRoot.value
    def path(file: File): String = IO.relativize(root, file).fold(file.getAbsolutePath)(_.replace(java.io.File.separatorChar, '/'))
    def sources(key: String, files: Seq[File]): Seq[String] = files.flatMap(file => Seq(s"--$key", path(file)))
    Seq(
      "--project-root", root.getAbsolutePath,
      "--server-module", hx4sServerModule.value,
      "--client-module", hx4sClientModule.value,
      "--route-dir", path(hx4sRouteDir.value),
      "--island-dir", path(hx4sIslandDir.value),
      "--island-id-prefix", hx4sIslandIdPrefix.value,
      "--scala-package", hx4sIslandComponentsPackage.value,
      "--scala-object", hx4sIslandComponentsObject.value,
      "--island-bridge", hx4sIslandBridge.value,
      "--island-export-type", hx4sIslandExportType.value,
      "--annotation-package", hx4sAnnotationPackage.value,
      "--virtual-module-prefix", hx4sVirtualModulePrefix.value
    ) ++ sources("server-sources", hx4sServerSources.value) ++ sources("client-sources", hx4sClientSources.value) ++
      hx4sBanner.value.flatMap(line => Seq("--banner", line)) ++
      hx4sGeneratedMarker.value.toSeq.flatMap(marker => Seq("--generated-marker", marker))
  }

  private lazy val generateTask: Def.Initialize[Task[Seq[File]]] = Def.task {
    hx4sCheckDependencies.value
    val log = streams.value.log
    val outputList = streams.value.cacheDirectory / "hx4s-generate" / "outputs.txt"
    IO.delete(outputList)
    runCodegen(hx4sCodegenClasspath.value, Seq("app") ++ sharedArguments.value ++ Seq("--output-list", outputList.getAbsolutePath),
      hx4sProjectRoot.value, javaHome.value, hx4sCodegenJavaOptions.value, log)
    IO.readLines(outputList).filter(_.nonEmpty).map(file).sortBy(_.getAbsolutePath)
  }

  private lazy val islandComponentsTask: Def.Initialize[Task[Seq[File]]] = Def.task {
    hx4sCheckDependencies.value
    val log = streams.value.log
    val output = (Compile / sourceManaged).value / hx4sIslandComponentsPackage.value.replace('.', '/') / s"${hx4sIslandComponentsObject.value}.scala"
    val arguments = Seq("island-components") ++ sharedArguments.value ++ Seq("--scala-output", output.getAbsolutePath)
    val classpath = hx4sCodegenClasspath.value
    val javaHomeDir = javaHome.value
    val javaOptions = hx4sCodegenJavaOptions.value
    val cacheDirectory = streams.value.cacheDirectory / "hx4s-island-components"
    // The cache key is the arguments, the JVM settings and the classpath order; classpath JARs are compared by content hash
    val argumentsFile = cacheDirectory / "arguments.txt"
    val key = arguments ++ javaHomeDir.map(dir => s"java-home ${dir.getAbsolutePath}") ++ javaOptions.map(option => s"java-option $option") ++
      classpath.map(_.getAbsolutePath)
    IO.write(argumentsFile, key.mkString("\n"))
    val classpathFiles = classpath.flatMap(entry => if (entry.isDirectory) (entry ** AllPassFilter).get.filter(_.isFile) else Seq(entry).filter(_.isFile))
    val run = FileFunction.cached(cacheDirectory / "files", FilesInfo.hash, FilesInfo.exists) { _ =>
      runCodegen(classpath, arguments, hx4sProjectRoot.value, javaHomeDir, javaOptions, log)
      Set(output)
    }
    run((hx4sClientSources.value ++ classpathFiles :+ argumentsFile).toSet).toSeq
  }

  /** Runs hx4s-codegen in a forked JVM. On failure, logs codegen's error lines once and fails the task with an exception carrying a summary */
  private def runCodegen(classpath: Seq[File], arguments: Seq[String], root: File, javaHomeDir: Option[File], javaOptions: Seq[String],
                         log: Logger): Unit = {
    if (classpath.isEmpty) throw new MessageOnlyException("hx4s-codegen could not be resolved (check hx4sCodegenVersion and resolvers)")
    val output = new CodegenOutput(log)
    val options = ForkOptions()
      .withJavaHome(javaHomeDir)
      .withWorkingDirectory(root)
      .withRunJVMOptions(javaOptions.toVector)
      .withOutputStrategy(OutputStrategy.LoggedOutput(output))
    val command = Seq("-cp", classpath.map(_.getAbsolutePath).mkString(java.io.File.pathSeparator), MainClass) ++ arguments
    val exitCode = Fork.java(options, command)
    if (exitCode != 0) {
      val errors = output.errors
      errors.foreach(line => log.error(line))
      val count = if (errors.isEmpty) s"exit code $exitCode" else s"${errors.size} error(s)"
      throw new CodegenFailed(s"hx4s-codegen ${arguments.head} failed: $count", errors)
    }
  }

  /** Only records codegen's error lines, drops the line of the exit exception, and passes other lines to the original logger */
  private final class CodegenOutput(underlying: Logger) extends Logger {
    private val errorLines: ListBuffer[String] = ListBuffer.empty

    def errors: List[String] = errorLines.synchronized { errorLines.toList }

    override def trace(t: => Throwable): Unit = underlying.trace(t)

    override def success(message: => String): Unit = underlying.success(message)

    override def log(level: Level.Value, message: => String): Unit = {
      val text = message
      if (text.startsWith(CodegenErrorPrefix)) errorLines.synchronized { errorLines += text }
      else if (!text.startsWith(CodegenExitLine)) underlying.log(level, text)
    }
  }

  private val CodegenErrorPrefix = "[hx4s-codegen] error: "
  private val CodegenExitLine = "Exception in thread \"main\" hx4s.codegen.CodegenFailure"

  /** Codegen failure. The error lines are already logged, so sbt prints only the summary (toString) on the last line. getMessage includes the error lines */
  private final class CodegenFailed(summary: String, errors: Seq[String])
    extends RuntimeException((summary +: errors).mkString("\n"), null, false, false) with FeedbackProvidedException {
    override def toString: String = summary
  }
}
