package hx4s.codegen

import hx4s.codegen.CodegenError.{Result, at, check}
import scala.meta.*
import java.util.Locale

/** Scans Scala 3 syntax for route and island annotations on `def` and `val` members of top-level objects. */
object AnnotationScanner {

  /** One output read from one annotation. The position is used to report duplicates. */
  private enum Found {
    case Route(file: String, method: HttpMethod, exportName: String, tree: Tree)
    case Island(entry: IslandEntry, tree: Tree)
  }

  private final case class Scanned(source: String, found: List[Found], exports: List[(String, Tree)])

  private val ExportAnnotations = Set("JSExportTopLevel", "scala.scalajs.js.annotation.JSExportTopLevel")
  private val IdentifierPattern = "[A-Za-z_$][A-Za-z0-9_$]*"
  private val IslandBasename = "[A-Za-z0-9-]+\\.tsx"

  /**
   * Groups server route exports by output file and checks for duplicate methods, case conflicts and equivalent URL patterns.
   *
   * @param sources Pairs of diagnostic source path and Scala source text.
   * @param config Expected annotation package and server export module ID.
   * @return Routes ordered by file path, with handlers ordered by HTTP method, or the collected diagnostics.
   */
  def routes(sources: List[(String, String)], config: CodegenConfig): Result[List[RouteEntry]] = {
    val (errors, scanned) = scanAll(sources, client = false, config)
    val found = scanned.flatMap(s => s.found.collect { case r: Found.Route => s.source -> r })
    val entries = found.map(_._2).groupBy(_.file).toList.sortBy(_._1).map { case (file, handlers) =>
      RouteEntry(file, handlers.map(h => RouteHandler(h.method, h.exportName)).sortBy(_.method.ordinal))
    }
    CodegenError.combine(errors ++ routeConflicts(found), Right(entries))
  }

  /**
   * Scans client island exports and checks export names and case-insensitive output paths for duplicates.
   *
   * @param sources Pairs of diagnostic source path and Scala source text.
   * @param config Annotation, export type and server module settings used by the scanner.
   * @return Islands ordered by file path, or the collected diagnostics.
   */
  def islands(sources: List[(String, String)], config: CodegenConfig): Result[List[IslandEntry]] = {
    val (errors, scanned) = scanAll(sources, client = true, config)
    val found = scanned.flatMap(s => s.found.collect { case i: Found.Island => s.source -> i })
    val conflicts = duplicates(found.map { case (source, i) => (source, i.tree, i.entry.filePath.toLowerCase(Locale.ROOT)) },
      key => s"duplicate island path (case-insensitive) $key")
    CodegenError.combine(errors ++ conflicts, Right(found.map(_._2.entry).sortBy(_.filePath)))
  }

  /** Reads all sources and splits them into errors and results for members without errors. Duplicate export names are checked on the latter. */
  private def scanAll(sources: List[(String, String)], client: Boolean, config: CodegenConfig): (List[CodegenError], List[Scanned]) = {
    val results = sources.map { case (path, source) => scan(path, source, client, config) }
    val scanned = results.map(_._2)
    val exports = scanned.flatMap(s => s.exports.map { case (name, tree) => (s.source, tree, name) })
    (results.flatMap(_._1) ++ duplicates(exports, name => s"export name $name is declared more than once"), scanned)
  }

  private def scan(path: String, source: String, client: Boolean, config: CodegenConfig): (List[CodegenError], Scanned) =
    dialects.Scala3(Input.VirtualFile(path, source)).parse[Source] match {
      case error: Parsed.Error => (List(CodegenError.position(path, error.pos, error.message)), Scanned(path, Nil, Nil))
      case Parsed.Success(tree) =>
        val members = topLevelObjects(tree).flatMap(_.templ.body.stats)
        val (errors, results) = CodegenError.partition(members.map(member => scanMember(path, member, client, config)))
        (errors, Scanned(path, results.flatMap(_._1), results.flatMap(_._2)))
    }

  private def topLevelObjects(tree: Source): List[Defn.Object] = {
    def walk(stats: List[Stat]): List[Defn.Object] = stats.flatMap {
      case obj: Defn.Object => List(obj)
      case pkg: Pkg => walk(pkg.body.stats)
      case _ => Nil
    }
    walk(tree.stats)
  }

  private def scanMember(path: String, member: Stat, client: Boolean, config: CodegenConfig): Result[(List[Found], List[(String, Tree)])] = {
    val annotations = member match {
      case d: Defn.Def => d.mods.collect { case a: Mod.Annot => a }
      case v: Defn.Val => v.mods.collect { case a: Mod.Annot => a }
      case _ => Nil
    }
    def named(a: Mod.Annot, name: String): Boolean = {
      val base = baseType(a.init.tpe).syntax
      base == name || base == s"${config.annotationPackage}.$name" || base == s"_root_.${config.annotationPackage}.$name"
    }
    val markers = annotations.filter(a => named(a, "route") || named(a, "islands"))
    if (markers.isEmpty) Right((Nil, Nil))
    else if (markers.count(named(_, "islands")) > 1) Left(List(at(path, member, "a member can have at most one @islands")))
    else readExport(path, member, annotations).flatMap { case (name, module) =>
      CodegenError.sequence(markers.map { marker =>
        if (named(marker, "route")) {
          check(!client, at(path, marker, "@route belongs in the server sources"))
            .flatMap(_ => route(path, marker, name, module, config))
        } else {
          for {
            _ <- check(client, at(path, marker, "@islands belongs in the client sources"))
            _ <- check(module.matches(Codegen.ModuleId), at(path, marker, s"invalid island moduleID $module"))
            _ <- check(!module.equalsIgnoreCase(config.serverModule),
              at(path, marker, s"island moduleID $module conflicts with the server module"))
            typedExport = islandExportType(member, config)
            entry <- island(path, marker, name, module)
            _ <- exportTypeMatches(path, typedExport, entry.propsType)
            factory = member match {
              case d: Defn.Def => typedExport.nonEmpty && d.paramClauses.map(_.values) == List(Nil)
              case _ => false
            }
          } yield Found.Island(entry.copy(factory = factory), marker)
        }
      }).map(found => (found, List(name -> member)))
    }
  }

  private def islandExportType(member: Stat, config: CodegenConfig): Option[Type.Apply] = {
    val declared = member match {
      case d: Defn.Def => d.decltpe
      case v: Defn.Val => v.decltpe
      case _ => None
    }
    val exportType = config.islandExportType
    val simpleName = exportType.split('.').last
    declared.collect {
      case applied: Type.Apply if Set(simpleName, exportType, s"_root_.$exportType")(applied.tpe.syntax) => applied
    }
  }

  /** Compares a recognized export type argument with `@islands` using syntax alone. An unqualified name is compared with the final segment. */
  private def exportTypeMatches(path: String, typedExport: Option[Type.Apply], propsType: Option[String]): Result[Unit] =
    typedExport match {
      case Some(applied) =>
        val base = applied.tpe
        (applied.argClause.values, propsType) match {
          case (List(arg), None) =>
            Left(List(at(path, arg, s"${base.syntax}[${arg.syntax}] needs the same type argument on @islands (@islands[${arg.syntax}])")))
          case (List(arg), Some(props)) =>
            val same = arg match {
              case select: Type.Select => (if (select.syntax.startsWith("_root_.")) select.syntax else s"_root_.${select.syntax}") == props
              case name: Type.Name => props.endsWith(s".${name.value}")
              case _ => false
            }
            check(same, at(path, arg, s"${base.syntax}[${arg.syntax}] does not match @islands[${props.stripPrefix("_root_.")}]"))
          case _ => Right(())
        }
      case _ => Right(())
    }

  private def readExport(path: String, member: Stat, annotations: List[Mod.Annot]): Result[(String, String)] = {
    annotations.filter(a => ExportAnnotations(a.init.tpe.syntax)) match {
      case List(exported) =>
        literalArgs(path, exported, List("name", "moduleID")).flatMap {
          case List(name, exportModule) =>
            check(name.matches(IdentifierPattern), at(path, exported, s"invalid export identifier $name"))
              .map(_ => (name, exportModule))
          case _ => Left(List(at(path, exported, "@JSExportTopLevel needs a literal name and moduleID")))
        }
      case _ => Left(List(at(path, member, "annotated member needs exactly one @JSExportTopLevel")))
    }
  }

  private def route(path: String, marker: Mod.Annot, name: String, module: String, config: CodegenConfig): Result[Found] = {
    for {
      _ <- check(module == config.serverModule, at(path, marker, s"expected @JSExportTopLevel moduleID ${config.serverModule}, found $module"))
      _ <- check(typeArgs(marker).isEmpty, at(path, marker, "@route takes no type argument"))
      args <- literalArgs(path, marker, List("filePath", "method"))
      _ <- check(args.nonEmpty && args.sizeIs <= 2, at(path, marker, "@route needs filePath and an optional method"))
      file <- outputPath(path, marker, args.head, ".ts")
      _ <- routeFileProblem(file).toLeft(()).left.map(reason => List(at(path, marker, reason)))
      method <- routeMethod(path, marker, args.lift(1).getOrElse("GET"))
    } yield Found.Route(file, method, name, marker)
  }

  /** Methods HonoX createApp does not register are silently ignored, so reject them. */
  private def routeMethod(path: String, marker: Mod.Annot, name: String): Result[HttpMethod] = name match {
    case "HEAD" => Left(List(at(path, marker, "HEAD is not registered by HonoX createApp (GET handlers also answer HEAD)")))
    case other => HttpMethod.parse(other).toRight(List(at(path, marker, s"unsupported HTTP method $other")))
  }

  /** Returns a reason if HonoX's default route glob (with-defaults.js) or Vite's import.meta.glob does not pick up the file name. */
  private def routeFileProblem(file: String): Option[String] = {
    val segments = file.split("/").toList
    val basename = segments.last
    if (basename.startsWith("_")) Some(s"route basename $basename is reserved by HonoX (leading _)")
    else if (basename.startsWith("-") || basename.startsWith("$")) Some(s"route basename $basename is ignored by HonoX (leading - or $$)")
    else if (basename.endsWith(".test.ts") || basename.endsWith(".spec.ts")) Some(s"route basename $basename is ignored by HonoX as a test file")
    else if (segments.init.exists(_.startsWith("-"))) Some(s"route path $file is ignored by HonoX (directory with leading -)")
    else hiddenSegment(segments, allowWellKnown = true).map(segment => s"route path $file is not discovered by import.meta.glob ($segment)")
  }

  /** import.meta.glob skips names starting with a dot and node_modules by default. HonoX picks up only a leading .well-known in routes separately. */
  private def hiddenSegment(segments: List[String], allowWellKnown: Boolean): Option[String] =
    segments.zipWithIndex.collectFirst {
      case (segment, index) if segment == "node_modules" ||
        (segment.startsWith(".") && !(allowWellKnown && index == 0 && segment == ".well-known" && segments.sizeIs > 1)) => segment
    }

  private def island(path: String, marker: Mod.Annot, name: String, module: String): Result[IslandEntry] = {
    for {
      args <- literalArgs(path, marker, List("filePath"))
      _ <- check(args.sizeIs == 1, at(path, marker, "@islands needs filePath"))
      file <- outputPath(path, marker, args.head, ".tsx")
      _ <- check(file.split("/").last.matches(IslandBasename), at(path, marker, s"island basename of $file must match $IslandBasename for HonoX discovery"))
      _ <- hiddenSegment(file.split("/").toList, allowWellKnown = false).toLeft(()).left.map(segment =>
        List(at(path, marker, s"island path $file is not discovered by import.meta.glob ($segment)")))
      propsType <- islandPropsType(path, marker)
    } yield IslandEntry(file, name, s"$module.js", propsType)
  }

  private def islandPropsType(path: String, marker: Mod.Annot): Result[Option[String]] = typeArgs(marker) match {
    case Nil => Right(None)
    case List(tpe: Type.Select) => Right(Some(if (tpe.syntax.startsWith("_root_.")) tpe.syntax else s"_root_.${tpe.syntax}"))
    case List(tpe) => Left(List(at(path, tpe,
      s"props type ${tpe.syntax} must be a fully qualified class name (for example _root_.pkg.${tpe.syntax})")))
    case _ => Left(List(at(path, marker, "@islands takes at most one type argument")))
  }

  private def baseType(tpe: Type): Type = tpe match {
    case applied: Type.Apply => applied.tpe
    case other => other
  }

  private def typeArgs(annotation: Mod.Annot): List[Type] = annotation.init.tpe match {
    case applied: Type.Apply => applied.argClause.values
    case _ => Nil
  }

  private def literalArgs(path: String, annotation: Mod.Annot, names: List[String]): Result[List[String]] =
    CodegenError.sequence(annotation.init.argClauses.flatMap(_.values).toList.zipWithIndex.map { case (arg, index) =>
      val value = arg match {
        case Term.Assign(Term.Name(name), rhs) if names.lift(index).contains(name) => Right(rhs)
        case Term.Assign(_, _) => Left(List(at(path, arg, s"annotation arguments must follow ${names.mkString(", ")}")))
        case other => Right(other)
      }
      value.flatMap {
        case Lit.String(text) => Right(text)
        case other => Left(List(at(path, other, "annotation arguments must be string literals")))
      }
    })

  private def outputPath(path: String, tree: Tree, value: String, extension: String): Result[String] = {
    val file = if (value.endsWith(extension)) value else s"$value$extension"
    // Validate the declaration before an appended suffix can hide an invalid final segment.
    OutputPath.segments(value).left.map(reason => List(at(path, tree, reason))).map(_ => file)
  }

  private def routeConflicts(found: List[(String, Found.Route)]): List[CodegenError] = {
    val byPath = found.groupBy(_._2.file.toLowerCase(Locale.ROOT)).values.toList.flatMap { group =>
      val spellings = group.map(_._2.file).distinct.sorted
      if (spellings.sizeIs > 1) group.map { case (source, r) => at(source, r.tree, s"route paths differ only by case: ${spellings.mkString(", ")}") }
      else Nil
    }
    val byUrl = found.groupBy { case (_, r) => (r.method, RoutePath.shape(RoutePath.pattern(r.file))) }.toList.sortBy(_._1.toString).flatMap {
      case ((method, _), group) =>
        val files = group.map(_._2.file).distinct.sorted
        if (files.sizeIs > 1) {
          val patterns = files.map(file => s"$file (${RoutePath.pattern(file)})").mkString(", ")
          group.map { case (source, r) => at(source, r.tree, s"route files map $method to the same URL: $patterns") }
        } else Nil
    }
    byPath ++ byUrl ++ duplicates(found.map { case (source, r) => (source, r.tree, s"${r.method} ${r.file}") }, key => s"duplicate route method $key")
  }

  private def duplicates(items: List[(String, Tree, String)], message: String => String): List[CodegenError] =
    items.groupBy(_._3).toList.sortBy(_._1).flatMap { case (key, group) =>
      if (group.sizeIs > 1) group.map { case (source, tree, _) => at(source, tree, message(key)) } else Nil
    }
}
