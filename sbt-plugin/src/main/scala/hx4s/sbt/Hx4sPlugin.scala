package hx4s.sbt

import sbt._
import sbt.Keys._
import sbt.internal.util.FeedbackProvidedException

import scala.collection.mutable.ListBuffer

/**
 * route / islands アノテーションから HonoX の route・island と SSR 用の IslandComponents を生成する。server プロジェクトで有効にする
 * hx4s-codegen（Scala 3）は専用 configuration で解決し、fork した JVM で実行する
 */
object Hx4sPlugin extends AutoPlugin {
  override def trigger = noTrigger
  override def requires = plugins.JvmPlugin

  object autoImport {
    val Hx4sCodegen = config("hx4s-codegen").hide

    /** このプラグインの版。hx4s-core / hx4s-react の依存に同じ版を使う */
    val hx4sVersion: String = Hx4sBuildInfo.version

    val hx4sClientProject = settingKey[ProjectReference]("@islands を持つ client プロジェクト。hx4sClientSources の既定の取り出し元。未設定なら client なし（SSR だけ）")
    val hx4sServerSources = taskKey[Seq[File]]("@route を探すソースファイル。既定はこのプロジェクトの Compile の Scala ソース")
    val hx4sClientSources = taskKey[Seq[File]]("@islands を探すソースファイル。既定は hx4sClientProject の Compile の Scala ソース")
    val hx4sProjectRoot = settingKey[File]("生成の基準ディレクトリ（Vite の root）。既定はビルドのルート")
    val hx4sRouteDir = settingKey[File]("route ファイルの出力先。既定は <hx4sProjectRoot>/app/routes")
    val hx4sIslandDir = settingKey[File]("island ファイルの出力先。既定は <hx4sProjectRoot>/app/islands")
    val hx4sIslandIdPrefix = settingKey[String]("SSR で HonoX に渡す island 識別子の接頭辞。既定は /app/islands/")
    val hx4sServerModule = settingKey[String]("server の @JSExportTopLevel の moduleID。既定は server")
    val hx4sClientModule = settingKey[String]("client の @JSExportTopLevel の moduleID。既定は client")
    val hx4sIslandComponentsPackage = settingKey[String]("生成する IslandComponents のパッケージ。既定は hx4s.generated")
    val hx4sIslandComponentsObject = settingKey[String]("生成する object の名前。既定は IslandComponents")
    val hx4sIslandBridge = settingKey[String]("island の境界を付ける関数の完全修飾名。既定は hx4s.react.Island")
    val hx4sIslandExportType = settingKey[String]("@islands[P] と照合する client の export の型。既定は hx4s.react.IslandExport")
    val hx4sAnnotationPackage = settingKey[String]("route / islands アノテーションのパッケージ。既定は hx4s.annotation")
    val hx4sVirtualModulePrefix = settingKey[String]("Scala.js のリンク成果物を指す仮想モジュールの接頭辞。既定は scalajs:")
    val hx4sBanner = settingKey[Seq[String]]("生成ファイルの先頭行。空なら hx4s-codegen の既定")
    val hx4sGeneratedMarker = settingKey[Option[String]]("生成ファイルを見分ける先頭行の接頭辞。None なら hx4s-codegen の既定")
    val hx4sCodegenVersion = settingKey[String]("実行する hx4s-codegen の版。既定はこのプラグインと同じ版")
    val hx4sCodegenJavaOptions = settingKey[Seq[String]]("hx4s-codegen を実行する JVM のオプション")
    val hx4sCodegenClasspath = taskKey[Seq[File]]("hx4s-codegen の実行時クラスパス")
    val hx4sCheckDependencies = taskKey[Unit]("client が Hx4sPlugin を有効にしたプロジェクトに依存していないこと、このプロジェクトが client に依存していないことを確かめる")
    val hx4sGenerate = taskKey[Seq[File]]("route / island の TS ファイルを生成し、生成した（内容が同じで据え置いた）ファイルの一覧を返す")
    val hx4sIslandComponents = taskKey[Seq[File]]("client の @islands から SSR 用の IslandComponents を生成する")
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

  /** 設定キーから hx4s-codegen の --key value の引数を作る。root の配下のパスは / 区切りの相対パスにする */
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
    IO.readLines(outputList).filter(_.nonEmpty).map(file(_)).sortBy(_.getAbsolutePath)
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
    // 引数・JVM の設定・クラスパスの並びをキャッシュのキーにし、クラスパスの JAR は内容のハッシュで比べる
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

  /** hx4s-codegen を fork した JVM で実行する。失敗したら codegen の誤りの行を 1 回だけログに出し、要約を持つ例外でタスクを失敗させる */
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

  /** codegen の誤りの行は覚えるだけにし、終了の例外の行は捨て、ほかの行は元のロガーへ渡す */
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

  /** codegen の失敗。誤りの行はログに出してあるので、sbt は最後の行に要約（toString）だけを出す。getMessage は誤りの行を含む */
  private final class CodegenFailed(summary: String, errors: Seq[String])
    extends RuntimeException((summary +: errors).mkString("\n"), null, false, false) with FeedbackProvidedException {
    override def toString: String = summary
  }
}
