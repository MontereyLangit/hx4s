package hx4s.sbt

import sbt._
import sbt.Keys._
import sbt.internal.util.FeedbackProvidedException

import scala.collection.mutable.ListBuffer

/** Generates HonoX TypeScript wrappers and the managed Scala SSR API. Enable it on the server project to run codegen in a forked JVM. */
object Hx4sPlugin extends AutoPlugin {
  override def trigger = noTrigger
  override def requires = plugins.JvmPlugin

  /** Exposes the settings, tasks and configuration available in sbt builds. */
  object autoImport {
    /** Hidden configuration used to resolve the code generator and its runtime dependencies. */
    val Hx4sCodegen = config("hx4s-codegen").hide

    /** Version of this plugin, used as the default code generator version. */
    val hx4sVersion: String = Hx4sBuildInfo.version

    /** Client project supplying island sources. An unset project gives an empty default client source list. */
    val hx4sClientProject = settingKey[ProjectReference]("The client project with @islands. Default source of hx4sClientSources. If unset, there is no client (SSR only)")
    /** Route inputs, defaulting to the server project's unmanaged Scala compile sources. */
    val hx4sServerSources = taskKey[Seq[File]]("Source files searched for @route. Defaults to this project's Compile Scala sources")
    /** Island inputs, defaulting to the configured client's unmanaged Scala compile sources. */
    val hx4sClientSources = taskKey[Seq[File]]("Source files searched for @islands. Defaults to hx4sClientProject's Compile Scala sources")
    /** Base directory for relative generation paths, defaulting to the build root. */
    val hx4sProjectRoot = settingKey[File]("Base directory for generation (Vite's root). Defaults to the build root")
    /** Route output directory, defaulting to `app/routes` under the generation root. */
    val hx4sRouteDir = settingKey[File]("Output directory for route files. Defaults to <hx4sProjectRoot>/app/routes")
    /** Island output directory, defaulting to `app/islands` under the generation root. */
    val hx4sIslandDir = settingKey[File]("Output directory for island files. Defaults to <hx4sProjectRoot>/app/islands")
    /** Prefix added to each island path to form the hydration identifier, defaulting to `/app/islands/`. */
    val hx4sIslandIdPrefix = settingKey[String]("Prefix of the island identifier passed to HonoX during SSR. Defaults to /app/islands/")
    /** Required Scala.js module ID of route exports, defaulting to `server`. */
    val hx4sServerModule = settingKey[String]("moduleID of the server's @JSExportTopLevel. Defaults to server")
    /** Compatibility module ID setting, defaulting to `client`. Island imports use the module IDs on their exports. */
    val hx4sClientModule = settingKey[String]("moduleID of the client's @JSExportTopLevel. Defaults to client")
    /** Package of the generated Scala SSR object, defaulting to `hx4s.generated`. */
    val hx4sIslandComponentsPackage = settingKey[String]("Package of the generated IslandComponents. Defaults to hx4s.generated")
    /** Name of the generated Scala SSR object, defaulting to `IslandComponents`. */
    val hx4sIslandComponentsObject = settingKey[String]("Name of the generated object. Defaults to IslandComponents")
    /** Fully qualified name called by generated SSR methods, defaulting to `hx4s.react.Island`. */
    val hx4sIslandBridge = settingKey[String]("Fully qualified name of the function that marks island boundaries. Defaults to hx4s.react.Island")
    /** Fully qualified name used to recognize typed exports, defaulting to `hx4s.react.IslandExport`. */
    val hx4sIslandExportType = settingKey[String]("Type of client exports matched against @islands[P]. Defaults to hx4s.react.IslandExport")
    /** Package used to recognize qualified route and island annotations, defaulting to `hx4s.annotation`. */
    val hx4sAnnotationPackage = settingKey[String]("Package of the route / islands annotations. Defaults to hx4s.annotation")
    /** Prefix prepended to Scala.js module filenames in generated imports, defaulting to `scalajs:`. */
    val hx4sVirtualModulePrefix = settingKey[String]("Prefix of the virtual module pointing to the Scala.js link output. Defaults to scalajs:")
    /** Leading `//` comment lines in generated sources. An empty sequence delegates to the code generator's default. */
    val hx4sBanner = settingKey[Seq[String]]("First lines of generated files. hx4s-codegen's default if empty")
    /** First-line prefix used to identify generated files. `None` delegates to the code generator's default. */
    val hx4sGeneratedMarker = settingKey[Option[String]]("Prefix of the first line that identifies generated files. hx4s-codegen's default if None")
    /** Version of the code generator to resolve, defaulting to `hx4sVersion`. */
    val hx4sCodegenVersion = settingKey[String]("Version of hx4s-codegen to run. Defaults to the same version as this plugin")
    /** Additional JVM options for the code generator process, defaulting to an empty sequence. */
    val hx4sCodegenJavaOptions = settingKey[Seq[String]]("Options for the JVM that runs hx4s-codegen")
    /** Resolved runtime classpath for the forked code generator. */
    val hx4sCodegenClasspath = taskKey[Seq[File]]("Runtime classpath of hx4s-codegen")
    /** Fails if the client depends on a server or this server depends on the client through compile or runtime project edges. */
    val hx4sCheckDependencies = taskKey[Unit]("Checks that the client does not depend on a project with Hx4sPlugin enabled, and that this project does not depend on the client")
    /** Checks dependencies and generates route and island wrappers. Returns the written and unchanged output paths. */
    val hx4sGenerate = taskKey[Seq[File]]("Generates route / island TS files and returns the files generated (or kept because their content was identical)")
    /** Checks dependencies and generates the Scala SSR object as a managed compile source from client sources. */
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

  /** Builds the --key value arguments of hx4s-codegen from the setting keys. Paths under root become /-separated relative paths. */
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

  /** Runs hx4s-codegen in a forked JVM. On failure, logs codegen's error lines once and fails the task with an exception carrying a summary. */
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

  /** Only records codegen's error lines, drops the line of the exit exception, and passes other lines to the original logger. */
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

  /** Codegen failure. The error lines are already logged, so sbt prints only the summary (toString) on the last line. getMessage includes the error lines. */
  private final class CodegenFailed(summary: String, errors: Seq[String])
    extends RuntimeException((summary +: errors).mkString("\n"), null, false, false) with FeedbackProvidedException {
    override def toString: String = summary
  }
}
