# Build configuration

The hx4s build is made of three parts.

| Part | Role |
| --- | --- |
| sbt-hx4s (`Hx4sPlugin`) | Setting keys and tasks. Generates route and island TS and `IslandComponents` from `@route` / `@islands` |
| `hx4s-codegen` | The generator itself. sbt-hx4s runs it in a forked JVM (it can also be called directly) |
| `hx4s-vite` (`hx4s()`) | Connects the Scala.js projects to Vite and HonoX |

## sbt keys and tasks

Enable `Hx4sPlugin` in the server project (its `trigger` is `noTrigger`). The only things you must write are `enablePlugins(Hx4sPlugin)` and `hx4sClientProject`; everything else works with the defaults.

| Key | Type | Default | Description |
| --- | --- | --- | --- |
| `hx4sVersion` | value (`String`) | The plugin's version | Used for dependencies such as `hx4s-react` |
| `hx4sClientProject` | `ProjectReference` | Not set | The client project with `@islands`. If not set, the setup is treated as client-less (SSR only), and `IslandComponents` is empty |
| `hx4sServerSources` | `Seq[File]` (task) | `.scala` files in this project's `Compile / unmanagedSources` | Sources searched for `@route` |
| `hx4sClientSources` | `Seq[File]` (task) | `.scala` files in `Compile / unmanagedSources` of `hx4sClientProject` | Sources searched for `@islands` |
| `hx4sProjectRoot` | `File` | `ThisBuild / baseDirectory` | Base directory for generation (Vite's root). Paths in error messages are relative to it |
| `hx4sRouteDir` | `File` | `<hx4sProjectRoot>/app/routes` | Output directory for routes |
| `hx4sIslandDir` | `File` | `<hx4sProjectRoot>/app/islands` | Output directory for islands |
| `hx4sIslandIdPrefix` | `String` | `/app/islands/` | Prefix of the island identifier (`component-name`) passed to HonoX during SSR |
| `hx4sServerModule` | `String` | `server` | moduleID of the server's `@JSExportTopLevel` |
| `hx4sClientModule` | `String` | `client` | moduleID of the client |
| `hx4sVirtualModulePrefix` | `String` | `scalajs:` | Prefix of the virtual module pointing to the Scala.js link output |
| `hx4sIslandComponentsPackage` | `String` | `hx4s.generated` | Package of the generated Scala |
| `hx4sIslandComponentsObject` | `String` | `IslandComponents` | Name of the generated object |
| `hx4sIslandBridge` | `String` | `hx4s.react.Island` | Fully qualified name of the function that marks island boundaries |
| `hx4sIslandExportType` | `String` | `hx4s.react.IslandExport` | Type of the client export compared against `@islands[P]` |
| `hx4sAnnotationPackage` | `String` | `hx4s.annotation` | Package of `route` / `islands` |
| `hx4sBanner` | `Seq[String]` | Empty (codegen's default) | First lines of generated files (one line each, starting with `//`) |
| `hx4sGeneratedMarker` | `Option[String]` | `None` (codegen's default) | Prefix of the first line that identifies generated files |
| `hx4sCodegenVersion` | `String` | Same as the plugin | Version of `hx4s-codegen_3` to resolve |
| `hx4sCodegenJavaOptions` | `Seq[String]` | Empty | Options for the JVM that runs codegen |
| `hx4sCodegenClasspath` | `Seq[File]` (task) | Resolution of the dedicated configuration `hx4s-codegen` | Runtime classpath of codegen |
| `hx4sCheckDependencies` | task | — | Checks the dependency direction between client and server ([ssr-hydration.md](ssr-hydration.md)) |
| `hx4sGenerate` | `Seq[File]` (task) | — | Generates route / island TS and returns the files generated (or kept because their content was identical). Starts codegen every time |
| `hx4sIslandComponents` | `Seq[File]` (task) | — | Generates `IslandComponents.scala` in `Compile / sourceManaged`. Registered in `Compile / sourceGenerators`; does not start codegen when inputs are unchanged |

- The incremental key for `IslandComponents` is the content of the client sources, the arguments to codegen, `javaHome`, `hx4sCodegenJavaOptions` and the codegen classpath (including hashes of JAR contents).
- `hx4sGenerate` and `hx4sIslandComponents` run `hx4sCheckDependencies` first.
- `hx4s-codegen` is built with Scala 3 and can't run inside sbt 1.x (Scala 2.12), so it is resolved through a dedicated configuration and forked. On failure, each codegen error line (`[hx4s-codegen] error: <path>:<line>: <reason>`) is logged once, and the task fails with a summary (`hx4s-codegen app failed: 2 error(s)`). The exception's `getMessage` contains the summary and the error lines.
- **Non-default appDir, moduleID and prefixes are not verified.** When changing `hx4sServerModule` / `hx4sClientModule` / `hx4sVirtualModulePrefix` / `hx4sIslandIdPrefix` / `hx4sRouteDir` / `hx4sIslandDir`,
  HonoX's fixed values (`/app/routes`, `/app/islands`) and the `hx4s()` side must match, and only the defaults used in the examples have been verified.

## `hx4s()` options

```ts
import hx4s from "hx4s-vite";

const plugins = hx4s({ mode, sbt: { cwd: import.meta.dirname } });
```

| Option | Default | Description |
| --- | --- | --- |
| `mode` (required) | — | Vite's `mode`. `"client"` builds for the browser; anything else builds for SSR (server) |
| `sbt.cwd` | `process.cwd()` | Directory in which to run sbt |
| `server.projectID` | `"server"` | ID of the sbt project with `@route` |
| `client` | `{}` | Client settings. `false` means a setup without a client (API only, SSR without islands). Use it when `hx4sClientProject` is not set |
| `client.projectID` | `"client"` | ID of the sbt project with `@islands` |
| `client.module` | `"client"` | moduleID of the client link output (match `hx4sClientModule`) |
| `client.entry` | `"./app/client.ts"` | Entry of the client build (a string or an array) |
| `client.uriPrefix` | `"scalajs-client"` | Prefix of the virtual module that reads the client link output in the server build |
| `client.output.entryFileNames` | `"static/client.js"` | Output name of the client entry |
| `client.output.chunkFileNames` | `"static/assets/[name]-[hash].js"` | Output name of chunks |
| `client.output.assetFileNames` | `"static/assets/[name].[ext]"` | Output name of assets |
| `honox` | `{}` | Extra settings passed as-is to `honox()`. With a client, `islandComponents.reactApiImportSource` defaults to `"react"` (nothing is added with `client: false`) |

The return value is `PluginOption[]`.

| `mode` | Plugins returned |
| --- | --- |
| `"client"` | The client Scala.js project, and a plugin that sets output names and `emptyOutDir: false` |
| Anything else | `honox()`, the server and client Scala.js projects, the alias `scalajs:client.js` → `scalajs-client:client.js` |
| Anything else (`client: false`) | Only `honox()` and the server Scala.js project |

Combining `client: false` with `mode: "client"` makes `hx4s()` throw (there is no client build). See `examples/api-only` for an example ([api.md](api.md)).

Other exports:

| Export | Content |
| --- | --- |
| `scalaProject(cwd, projectID, uriPrefix?)` (`hx4s-vite`) | Wraps `@scala-js/vite-plugin-scalajs` and serializes `buildStart`, so that sbt isn't started for several projects at once. Use it directly in setups that don't use `hx4s()` |
| `islandPropsProblem(element)` (`hx4s-vite/client`) | Props validation before hydration. Returns a reason string, or `undefined` if none ([ssr-hydration.md](ssr-hydration.md)) |
| `hx4s-vite/node-register` | Pass it to Node's `--import`. Registers a loader that resolves `scalajs:client.js` and `hono/jsx` inside honox |
| `hx4s-vite/node-loader` | The loader itself. For registering it alongside your own loader |

`node-loader` reads the environment variable `HX4S_CLIENT_JS_DIR` (the output directory of the client's `fastLinkJS`).

## Two-mode build

The client and the server are built with separate `vite build` runs. Build the client first (with `client: false`, there is only one `vite build`).

```bash
vite build --mode client   # dist/client/static/client.js, dist/client/static/assets/*
vite build                 # dist/server/index.js (Worker)
```

- The server build links both the server and the client Scala.js projects (SSR imports the client link output). The client is built separately as a bundle for static serving
- `@scala-js/vite-plugin-scalajs` uses `fastLinkJS` only when Vite's `mode` is `development`, and `fullLinkJS` otherwise
- The output directories (`dist/client`, `dist/server`) are decided by `build.outDir` in `vite.config.ts` and the runtime adapter. hx4s doesn't fix them

## Known limitations

| Limitation | Content |
| --- | --- |
| Number of sbt starts | `npm run build` starts sbt four times (once for generation, once for the client build, and twice in the server build to link the server and the client; sequentially). Each took 15–30 seconds (Phase 3 verification, Windows 11; [hx4s/reviews/phase3-review.md](hx4s/reviews/phase3-review.md)). How to reduce this has not been investigated |
| Development server | Doesn't run on the Vite dev server (HMR). `npm run dev` builds and then starts `wrangler dev` |
| Last line of sbt output | `@scala-js/vite-plugin-scalajs` uses the last line of `sbt print`'s standard output as the output directory. It breaks if sbt prints a warning or anything else last |
| JDK 25 warning | On JDK 25, sbt prints a `sun.misc.Unsafe` warning at startup (impact on behavior not examined) |
| Verified settings | Only the default appDir, moduleID and prefixes. Other combinations are not verified |

## Handling generated files

### What to commit

| Generated file | Location | Commit |
| --- | --- | --- |
| Route TS | `app/routes/**/*.ts` | Yes |
| Island TSX | `app/islands/**/*.tsx` | Yes |
| `IslandComponents.scala` | `server/target/.../src_managed/…` | No (under `target`; generated by compilation even right after a clone) |

The TS files are committed so that the files Vite reads are in the source tree and so that changes can be followed in review. CI for `examples/minimal` checks that `app/` has no diff or untracked files after the build.

### Generated marker and safeguards

- The first line of a generated file is `// This file is generated by hx4s-codegen. Do not edit this file manually.`. Only files whose first line starts with the marker (`// This file is generated by hx4s-codegen`) are considered generated
- **Existing files without the marker are never overwritten or deleted.** Trying to generate to the same path as a hand-written file stops with an error
- Generated files (`.ts` / `.tsx` / `.scala`) under `app/routes` and `app/islands` that are not output this time are deleted. Removing an `@route` / `@islands` removes the corresponding file
- Files with identical content are not rewritten (the modification time doesn't change either; different line endings count as identical content)
- All errors are collected before writing. If there is even one, nothing is written
- Symbolic links and Windows junctions are not followed and are treated as errors (ancestors of the output root, intermediate directories, output targets, directories searched for stale generated files)
- Each file is written to a temporary file in the same directory (`.<name>.hx4s-tmp`) and then replaced. If something fails midway, existing files remain as they were, and rerunning converges
- Paths that differ only in case are rejected, both among outputs and against existing files

Not guaranteed: atomicity across multiple files (a failure midway can leave only some files at the new generation), mutual exclusion of concurrent runs.

When moving from a project that generated files with a different marker, set `hx4sBanner` and `hx4sGeneratedMarker` to those values so that the existing files can be replaced ([migration.md](migration.md)).

## `CodegenMain` arguments

Codegen can also be called without sbt. Run `hx4s.codegen.CodegenMain` with the `hx4s-codegen_3` classpath.

```
java -cp <classpath> hx4s.codegen.CodegenMain app \
  --server-sources server/Main.scala --server-sources server/Api.scala --client-sources client/Main.scala
```

The first argument is the command.

| Command | Generates |
| --- | --- |
| `app` | Route TS in `app/routes` and island TSX in `app/islands` |
| `island-components` | `IslandComponents.scala` for SSR (`--scala-output` is required) |

Follow it with `--key value` pairs. Omitted keys take their defaults. On failure, errors are printed to standard error as `[hx4s-codegen] error: …` and the process exits with a non-zero code.

| Argument | Corresponding sbt key | Default |
| --- | --- | --- |
| `--project-root` | `hx4sProjectRoot` | The current directory |
| `--server-sources` | `hx4sServerSources` | Empty (repeat once per file; not comma-separated) |
| `--client-sources` | `hx4sClientSources` | Empty (same as above) |
| `--server-module` / `--client-module` | `hx4sServerModule` / `hx4sClientModule` | `server` / `client` |
| `--route-dir` / `--island-dir` | `hx4sRouteDir` / `hx4sIslandDir` | `app/routes` / `app/islands` |
| `--island-id-prefix` | `hx4sIslandIdPrefix` | `/app/islands/` |
| `--scala-output` | (fixed to `src_managed`) | None |
| `--scala-package` / `--scala-object` | `hx4sIslandComponentsPackage` / `hx4sIslandComponentsObject` | `hx4s.generated` / `IslandComponents` |
| `--island-bridge` / `--island-export-type` | `hx4sIslandBridge` / `hx4sIslandExportType` | `hx4s.react.Island` / `hx4s.react.IslandExport` |
| `--annotation-package` | `hx4sAnnotationPackage` | `hx4s.annotation` |
| `--virtual-module-prefix` | `hx4sVirtualModulePrefix` | `scalajs:` |
| `--banner` | `hx4sBanner` | The default banner (repeat once per line; start with `//`) |
| `--generated-marker` | `hx4sGeneratedMarker` | `// This file is generated by hx4s-codegen` |
| `--output-list` | (used internally by `hx4sGenerate`) | None. Writes the absolute paths of written and kept files |

Passing the same key twice is an error (except `--server-sources`, `--client-sources` and `--banner`). Values embedded in generated sources are checked before writing,
and values that can't be used as identifiers or that contain newlines or quotes are rejected ([hx4s/codegen.md](hx4s/codegen.md)).
