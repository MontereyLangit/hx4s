# hx4s-codegen (wiring generation for `@route` / `@islands`)

`hx4s-codegen` reads Scala sources as syntax with scalameta and generates HonoX route / island files and a Scala API for SSR (`IslandComponents`).
See [ADR 0004](adr/0004-scalameta-codegen.md) for the choice of approach, and [build-integration.md](build-integration.md) 2.2 and 3.2 for calling it from sbt.

| File (`modules/codegen/src/main/scala/hx4s/codegen/`) | Content |
| --- | --- |
| `AnnotationScanner` | Analyzing annotations and `@JSExportTopLevel`, checking paths, methods, type arguments and the declared types of exports |
| `AppEntries` | Intermediate representation (`RouteEntry`, `IslandEntry`, `HttpMethod`) |
| `AppFileRenderer` | Templates for route TS, island TSX and `IslandComponents.scala` |
| `RoutePath` | Converts route file paths into the URL patterns HonoX registers (used for conflict checks) |
| `OutputPath` / `OutputWriter` | Checking output paths and writing |
| `CodegenConfig` / `Codegen` / `CodegenMain` | Settings, the generation entry point, a main that takes settings as arguments |

Annotations are recognized by name (the configured package name + `route` / `islands`), not by type, so `hx4s-codegen` doesn't depend on `hx4s-core`.

## Declaration rules

- Reads annotations on `def` / `val` in top-level objects (including inside packages; nested objects are not targets) of the configured source files. The object's name doesn't matter.
- A member with `@route` / `@islands` needs exactly one `@JSExportTopLevel` with a literal name and moduleID. `@route` must use the server moduleID and `@islands` the client moduleID.
- `@route("x.ts")` is GET, and `@route("x.ts", "POST")` is a named export. A member can have several `@route`s, and methods for the same path are combined into one file.
  Methods are GET, POST, PUT, DELETE, PATCH and OPTIONS. HonoX's `createApp` registers only these six, so `HEAD` is an error (the GET handler also answers HEAD).
- The P of `@islands[P]("x.tsx")` is the fully qualified name of the props type. If omitted, `js.Object`. A member can have at most one `@islands`.
- If the export's declared type is `IslandExport[T]` (setting `islandExportType`), `T` must have the same type name as `P` ([ADR 0007](adr/0007-props-validation-and-typed-export.md)).
- All arguments are string literals. Named arguments must be in declaration order.

Detected and rejected:

- Output path errors: empty segments, `.` / `..`, absolute paths, drive letters, backslashes (the only allowed characters are `A-Za-z0-9_.$[]-`), trailing dots and spaces, Windows reserved names (`CON`, etc., also with extensions)
- Routes HonoX doesn't pick up: a basename starting with `_` / `-` / `$`, `*.test.ts` / `*.spec.ts`, directories starting with `-`, names starting with a dot, and `node_modules` (a leading `.well-known/` is allowed)
- Islands HonoX doesn't pick up: a basename that isn't `[A-Za-z0-9-]+\.tsx`, paths containing directories starting with a dot or `node_modules`
- Duplicates: the same export name on several members, several `@islands` on one member, the same route method twice, route / island paths differing only in case
- URL conflicts: separate route files that become the same URL pattern in HonoX and have the same method ("URL conflicts" below)

### URL conflicts

Route file paths are turned into URL patterns with the same rules as HonoX's `createApp` (`RoutePath`), and it is an error if the shapes without parameter names are the same and the methods are the same.
The rules apply `filePathToPath` from `server/utils/file.js` of honox 0.1.62 separately to the directory part and the file name, and join them with hono's `mergePath` (because `createApp` mounts per-directory sub apps with `app.route`).

| Example | URL pattern |
| --- | --- |
| `foo.ts` / `foo/index.ts` / `foo/index/index.ts` | `/foo` (all three are the same) |
| `items/[id].ts` / `items/[slug].ts` | `/items/:id` / `/items/:slug` (conflict, since only the parameter names differ) |
| `docs/[...slug].ts` | `/docs/:slug{.+}` (a different pattern from `docs/[id].ts`) |
| `page.md.ts` | `/page` (`.md` is also removed after `.ts`, so it conflicts with `page.ts`) |

`(group)` groups are part of the conversion rules, but because of the allowed characters of output paths (no `()`), they never appear in generated routes. Optional parameters like `[[name]]` don't exist in HonoX.
A static segment and a parameter (`items/new.ts` and `items/[id].ts`) coexist thanks to HonoX's ordering (names starting with `[` go last), so they don't count as a conflict.
The conversion table is in `modules/codegen/src/test/resources/route-paths.tsv`, and is checked both by `RoutePathSuite` and by `tests/route-paths.test.js`, which runs the same table through HonoX's `createApp`.
Conflicts with hand-written files in `app/routes` are not checked (no warning either; not implemented; [routing-design.md](routing-design.md) 2.2).

Server and client errors are returned together (so that fixing one side doesn't just reveal the next). Declarations with errors in per-declaration checks (syntax, annotation arguments, `@JSExportTopLevel`, methods, file names,
matching `IslandExport[T]`) are excluded, and the remaining declarations are checked for duplicate export names, route paths differing only in case, the same URL, duplicate methods, and duplicate island paths.
Since erroneous declarations are excluded, duplicates and conflicts caused by them are not reported (they may appear only after fixing). Unreadable sources are likewise excluded and processing continues.
Server / client sources may be empty (routes only, islands only, SSR only). If empty, the analysis result is empty, stale generated files are deleted, and `IslandComponents` is an empty object.

## Output rules

- Generated TS gives imported names generator-specific aliases (`createRoute as __hx4s_createRoute`, `<export name> as __hx4s_handler_<export name>`, `as __hx4s_island` for islands).
  Even if the export name is `createRoute`, `POST`, `GET` or a JS reserved word (`default`, `class`, etc.), it doesn't collide with identifiers in the generated code. Method names in `IslandComponents` are wrapped in backquotes, so Scala reserved words are fine too.
- Output order doesn't depend on the input order. Route files are ordered by path, methods within a file in the fixed order above (GET first), and islands and `IslandComponents` methods by file path.
- Identical content isn't rewritten, and the modification time doesn't change. Different line endings count as identical content.
- Existing files without the generated marker (a first line starting with `generatedMarker`) are never overwritten or deleted.
- Generated files (`.ts` / `.tsx` / `.scala`) in the route / island output directories that aren't output this time are deleted.
- Paths that differ only in case are rejected, both among outputs and against existing files and directories. Stale generated files that differ only in case are deleted before writing under the new name.
- Conflicts between files and directories are rejected at the planning stage.
- All errors are collected before writing. If there is even one, nothing is written.
- If there is a symbolic link or a Windows junction among the ancestors of the output root (below `projectRoot`), the output root, intermediate directories, output targets, or the output directories searched for stale generated files, it isn't followed and is an error.
  Detection uses `isSymbolicLink || isOther` of `BasicFileAttributes` (`NOFOLLOW_LINKS`). Java returns junctions as `isOther`.
  Boundaries: `projectRoot` itself and above are not checked. For output roots outside `projectRoot` (e.g. when `--scala-output` is placed outside), only the root and below are checked.
  In setups where sbt's `target` is a link, the ancestors of `src_managed` are links, so generating `IslandComponents` becomes an error.
- Each file is written completely to a temporary file `.<name>.hx4s-tmp` in the same directory, then replaced with `Files.move` (`ATOMIC_MOVE`, or `REPLACE_EXISTING` if not possible).
  Even if writing fails midway, existing generated files keep their original content (including the generated marker). Temporary file names start with a dot and don't end with a generated extension,
  so neither HonoX's globs nor the detection of stale generated files picks them up. Temporary files left by a previous failure are deleted from the output directories and the parent directories of output targets on the next run.
- Writing happens in the order "delete leftover temporary files and stale generated files differing only in case → write outputs whose content changed → delete the remaining stale generated files".
  Even if it fails midway, rerunning converges (generation is deterministic, identical content isn't written, and remaining stale files and temporary files are deleted next time).

Not guaranteed: atomicity across multiple files (a failure midway can leave only some files at the new generation), mutual exclusion of concurrent runs, links being swapped between checking and writing ([build-integration.md](build-integration.md) 3.3).

## Error format

Analysis and writing don't throw; they return `Either[List[CodegenError], A]`. `CodegenMain` prints errors to standard error in the following form, throws `CodegenFailure` and exits non-zero
(it doesn't call `System.exit`, so that being called from `runMain` in the same JVM doesn't take sbt down with it).

```
[hx4s-codegen] error: server/src/main/scala/app/Main.scala:7: HEAD is not registered by HonoX createApp (GET handlers also answer HEAD)
```

sbt-hx4s includes these lines in the task's failure message.

## Settings

`CodegenConfig.default(projectRoot, serverSources, clientSources)` sets the rest to the defaults below. `CodegenMain` also uses the defaults for omitted keys.

| Item | `CodegenMain` key | sbt-hx4s key | Default |
| --- | --- | --- | --- |
| Project root | `--project-root` | `hx4sProjectRoot` | The current directory (in sbt, the build root) |
| Server sources to scan | `--server-sources` (repeat once per file; not comma-separated) | `hx4sServerSources` | Empty (in sbt, this project's Scala sources) |
| Client sources to scan | `--client-sources` (same as above) | `hx4sClientSources` / `hx4sClientProject` | Empty (in sbt, the Scala sources of `hx4sClientProject`; empty if not set) |
| Server / client moduleID | `--server-module` / `--client-module` | `hx4sServerModule` / `hx4sClientModule` | `server` / `client` |
| Route output directory | `--route-dir` | `hx4sRouteDir` | `app/routes` |
| Island output directory | `--island-dir` | `hx4sIslandDir` | `app/islands` |
| Prefix of island identifiers for SSR | `--island-id-prefix` | `hx4sIslandIdPrefix` | `/app/islands/` |
| Output of the Scala API for SSR | `--scala-output` (required for `island-components`) | (fixed under `src_managed`) | None |
| Its package name | `--scala-package` | `hx4sIslandComponentsPackage` | `hx4s.generated` |
| Its object name | `--scala-object` | `hx4sIslandComponentsObject` | `IslandComponents` |
| Fully qualified name of the island bridge | `--island-bridge` | `hx4sIslandBridge` | `hx4s.react.Island` |
| Export type to compare against | `--island-export-type` | `hx4sIslandExportType` | `hx4s.react.IslandExport` |
| Annotation package | `--annotation-package` | `hx4sAnnotationPackage` | `hx4s.annotation` |
| Virtual module prefix | `--virtual-module-prefix` | `hx4sVirtualModulePrefix` | `scalajs:` |
| Generated banner | `--banner` (repeat once per line; start with `//`) | `hx4sBanner` | `// This file is generated by hx4s-codegen. Do not edit this file manually.` |
| Generated marker | `--generated-marker` (the first line of the banner starts with it) | `hx4sGeneratedMarker` | `// This file is generated by hx4s-codegen` |
| List of generated files | `--output-list` (writes the absolute paths of written and kept files, one per line) | (used internally by `hx4sGenerate`) | Not written |

Values embedded in generated sources are checked before writing. Values containing newlines, quotes, backslashes, `*/` and so on are errors.

| Value | Allowed form |
| --- | --- |
| moduleID (server / client) | `[A-Za-z0-9_][A-Za-z0-9_.-]*` |
| Virtual module prefix | `[A-Za-z0-9_@./:-]+` |
| Island identifier prefix | `[A-Za-z0-9_@./:~-]*` |
| Package name, object name, bridge, export type, annotation package | Scala identifiers (`[A-Za-z_][A-Za-z0-9_]*`) joined with `.`. An error if any part is a Scala 3 reserved word |
| Banner, generated marker | One line (no `\n`, `\r`, U+2028 or U+2029). The banner starts with `//` |

In addition, values put into string literals (module names imported from, arguments of `@JSImport`, island identifiers) go through escaping that yields the same value in both TS and Scala.

Non-default appDir, moduleIDs and prefixes require matching the hx4s-vite and HonoX sides too, and are not verified in P0 ([build-integration.md](build-integration.md) 2.0).

Projects whose existing generated files were output with a different marker can replace those files by setting `hx4sBanner` and `hx4sGeneratedMarker` to those values
(files with a different marker are considered hand-written and are not overwritten).

The entry points are `CodegenMain app ...` (route / island TS) and `CodegenMain island-components ...` (the Scala API for SSR).

```
java -cp <runtime classpath of hx4s-codegen> hx4s.codegen.CodegenMain app --server-sources server/Main.scala --server-sources server/Api.scala --client-sources client/Main.scala
```
