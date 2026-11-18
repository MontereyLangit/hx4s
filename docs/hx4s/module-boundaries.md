# Module boundaries

This splits the current implementation in [architecture.md](architecture.md) into framework (hx4s) and bulletin-board-specific (3ch) parts. When and how things move is in [extraction-plan.md](extraction-plan.md).
File names are those of the reference commit `875a47b` (including the `io._3ch.codegen.framework` package). 3ch's Phases 1 / 2 are in progress, so file names may change.

## 1. Classification (per file)

Meaning of the categories:

- **hx4s**: moved as-is, or with 3ch-specific names and paths turned into parameters.
- **Split**: the file mixes both.
- **3ch**: stays in 3ch.
- **3ch (future candidate)**: stays in 3ch for now, as a candidate for a separate module on the roadmap.

### 1.1 Scala

| 3CH file | Category | Destination / reason to stay |
| --- | --- | --- |
| `common/.../routes/Annotations.scala` | hx4s | `hx4s-core`. `hx4s.annotation.route` / `hx4s.annotation.islands[P]`. Add a type parameter to `islands` ([ADR 0003](adr/0003-typed-island-props.md)) |
| `common/.../routes/RouteTable.scala` | 3ch | Only `publicPatterns`. Table 1.2 below |
| `codegen/.../framework/AnnotationScanner.scala`, `AppEntries.scala` | hx4s | `hx4s-codegen`. Walking a fixed `object Main`, moduleIDs and the fully qualified names of annotations (`CodegenConfig.annotationPackage`) are already settings |
| `codegen/.../framework/AppFileRenderer.scala` | hx4s | `hx4s-codegen`. Templates for routes / islands / `IslandComponents`. The output package and the fully qualified name of the bridge are values in `CodegenConfig` |
| `codegen/.../framework/{OutputPath,OutputWriter,Codegen,CodegenConfig,CodegenError,CodegenReport}.scala` | hx4s | `hx4s-codegen`. Output rules, settings, error types |
| `codegen/.../GenerateAppFiles.scala` | 3ch | 3ch's `CodegenConfig` (fixed input paths, banner) and the call to generate DO TS types. Replaced by sbt-hx4s's `hx4sGenerate` |
| `codegen/.../DurableObjectSources.scala` | 3ch (future candidate) | DO-only. A `hx4s-cloudflare` candidate |
| `codegen/.../GenerateDurableObjectTypes.scala` | 3ch (future candidate) | Same as above |
| `codegen/.../GenerateDurableObjectStubs.scala` / `GenerateDurableObjectStubsMain.scala` | 3ch (future candidate) | Same as above. Has the package `io._3ch.bbs.external.cloudflare` hard-coded |
| `codegen/src/test/.../framework/*Suite.scala`, `TestConfig.scala` | hx4s | Tests of `hx4s-codegen` |
| `codegen/src/test/.../GenerateAppFilesSuite.scala` | Split | Parts using 3ch's settings stay in 3ch. Generic parts are moved into the `framework` tests |
| `codegen/src/test/.../GenerateDurableObject*Suite.scala` | 3ch | Accompanies the DO generators |
| `common/src/test/.../routes/RouteTableSuite.scala` | 3ch | Checks `publicPatterns`. Template checks have moved to the `framework` tests |
| `bbs/.../external/honox/Island.scala` | hx4s | `hx4s-react` (`hx4s.react.Island`) |
| `bbs/.../external/hono/HonoContext.scala`, `HonoContextFacade.scala` | Split | `HonoRequestFacade` / `HonoContextFacade` / `renderPromise` go to `hx4s-react` (with Env as a type parameter). `title` / `authModal` of `RenderProps` stay in 3ch |
| `bbs/.../external/web/WebFacade.scala` | 3ch | The proposal is for hx4s to use scalajs-dom's Fetch types (`org.scalajs.dom.Request` / `Response`) (open question U-6). 3ch's hand-written facade stays in 3ch |
| `bbs/.../external/cloudflare/Bindings.scala` | 3ch (future candidate) | `WorkerEnv` and so on. A `hx4s-cloudflare` candidate |
| `bbs/.../Main.scala` | 3ch | The declarations themselves. hx4s only specifies how to write them |
| `bbs-client/.../Main.scala` and `islands/*` | 3ch | The bulletin board UI |
| `bbs/.../generated/IslandComponents.scala` | 3ch (generated) | The generator is hx4s; the output lives in the user's project |
| `bbs/.../handler/RendererShell.scala` | 3ch | `<html lang="ja">`, CSS paths, auth modal, Shift_JIS |
| `bbs/.../components/*` | 3ch | Users of `IslandComponents` |
| `bbs/src/test/.../IslandTestExports.scala` | Split | `renderIslandTest` goes to test support in `hx4s-react`. `renderBoardTopTest` stays in 3ch |

### 1.2 What is mixed in `common`

As of `875a47b`, `common/.../routes/RouteTable.scala` holds only `publicPatterns`. The generation templates (formerly `RouteKind` / `RouteEntry` / `IslandEntry` / `render*` / the banner) have
already moved to `codegen/.../framework/` (`AppEntries`, `AppFileRenderer`).

| Element | Category | Reason |
| --- | --- | --- |
| `publicPatterns` | 3ch | URL patterns for v5-compatible dat / subject.txt / SETTING.TXT. Used only by `PublicRoutes`. Move to 3ch's `common` or to the handler side of `bbs` (Phase 1 step 1-4) |
| `Annotations.scala` (`route` / `islands`) | hx4s-core | Still in `routes` of `common`. Move to `hx4s.annotation` in Phase 3 |

With the templates moved to `codegen`, the problem of template strings ending up in the Scala.js link of `bbs` is resolved (since `RouteTable` holds only `publicPatterns`).

### 1.3 TypeScript / JavaScript / settings

| 3CH file | Category | Destination / reason to stay |
| --- | --- | --- |
| `scalaProject()` in `vite.config.ts` | hx4s | `hx4s-vite`. A wrapper that makes `buildStart` `sequential: true` |
| `resolve.alias["scalajs:client.js"]` and the second Scala.js plugin (`uriPrefix: "scalajs-client"`) in `vite.config.ts` | hx4s | `hx4s-vite` |
| `honox({ islandComponents: { reactApiImportSource: "react" } })` in `vite.config.ts` | hx4s | `hx4s-vite` passes it as a default |
| `rollupOptions` of client mode in `vite.config.ts` (`input`, output names) | hx4s | Defaults of `hx4s-vite` (overridable) |
| `tailwindcss()` in `vite.config.ts` | 3ch | The choice of CSS is the user's |
| `@hono/vite-build/cloudflare-workers` and the DO `entryContentAfterHooks` in `vite.config.ts` | 3ch (future candidate) | Runtime-specific |
| `app/server.ts` | 3ch | Middleware, auth API, LegacyDispatch, DO exports. `examples/minimal` has a minimal `server.ts` |
| `app/client.ts` | 3ch (template in hx4s) | The content is generic. The same shape goes in `examples/minimal`. Turning it into a helper is open question U-7 |
| `app/routes/_renderer.tsx` / `app/renderer.d.ts` / `app/scalajs-server.d.ts` | 3ch | The renderer props and the types of Scala-side exports are 3ch-specific |
| Generated files in `app/routes/**` / `app/islands/**` | 3ch (generated) | — |
| `bbs/test-stubs/cloudflare-workers-stub.mjs` | 3ch | Cloudflare-specific |
| `bbs/test-stubs/cloudflare-workers-loader.mjs` | Split | Resolving `scalajs:client.js` and replacing `hono/jsx` → `react` inside HonoX go to the Node loader in `hx4s-vite`. `cloudflare:workers` stays in 3ch. The environment variable `CH3S_CLIENT_JS_DIR` becomes `HX4S_CLIENT_JS_DIR` |
| `bbs/test-stubs/register.mjs` | Split | Same as above |
| `Test / jsEnv` in `build.sbt` (`--import register.mjs`, running the client's fastLinkJS first) | Split | The mechanism becomes a helper in `sbt-hx4s` (P1). Replacing `cloudflare:workers` stays in 3ch |
| The `codegen` project and `Compile / mainClass` in `build.sbt` | hx4s | Replaced by `hx4sGenerate` in `sbt-hx4s` |
| `generateDurableObjectStubs` in `build.sbt` | 3ch (future candidate) | DO-specific |
| The `v6:*` scripts in `package.json` | 3ch | Rewrite them to assume `sbt hx4sGenerate` and hx4s-vite |
| `tests/islands-bridge.test.js` | hx4s | Compatibility tests for `hx4s-vite` / `hx4s-react`. The parts that read 3ch's `vite.config.ts` are replaced with the examples |
| `tests/islands-hydration.test.js` | hx4s | Same as above. Uses hx4s-react's test exports instead of `bbs-test-fastopt/island-test.js` |
| `tests/board-islands.test.js` / `tests/board-top-hydration.test.js` | 3ch | Bulletin board UI |
| `tests/client-bundle.test.js` | 3ch (principle goes to hx4s) | The strings are 3ch-specific. A check that "server code doesn't end up in the client output" is also put in the examples |
| `tests/worker-bundle.test.js` | 3ch | DO exports |

## 2. Dependency direction rules

1. One way, 3ch → hx4s. No hx4s module references 3ch (`io._3ch.*`, `ch3s`, 3ch's file paths and wording) ([ADR 0006](adr/0006-one-way-dependency-and-local-publish.md)).
2. `hx4s-react` → `hx4s-core`. `hx4s-core` depends on no other hx4s module.
3. `hx4s-codegen` has no compile dependency on `hx4s-core` (annotations are matched by name). Don't add heavy dependencies other than scalameta.
4. `sbt-hx4s` **resolves `hx4s-codegen` at run time** (not as a library dependency).
5. `hx4s-vite` makes `@scala-js/vite-plugin-scalajs` and `honox` peerDependencies and leaves the version choice to the user (proposal; U-8).
6. No module depends on Cloudflare-specific APIs (`cloudflare:workers`, DOs, Bindings, `@hono/vite-build/cloudflare-workers`) ([ADR 0005](adr/0005-core-independent-of-cloudflare.md)).
7. The dependency direction between the user's projects is as follows.
   - server → shared and client → shared are allowed.
   - **server → client is forbidden** (client implementation would mix into the server).
   - **client → server is forbidden** (server processing would leak into the client output; the core of 3ch's KB-27).
   - **shared → server and shared → client are forbidden** (server code would reach the client through shared).
   Types shared by both (island props) go in shared. These are documented as hx4s conventions.
   They are checked with the sbt dependency graph (the server is not among the client's transitive dependencies) and Vite's module graph (the client build contains no server modules).
   String checks on the bundle (3ch's `tests/client-bundle.test.js`) are a supplement that catches whatever slips past those checks, not the primary guarantee.
   - **sbt dependency graph: implemented (Phase 3 review K7)**. sbt-hx4s's `hx4sCheckDependencies` fails the build and prints the path if `hx4sClientProject` and its transitive `dependsOn` (those affecting Compile)
     reach a project with `Hx4sPlugin` enabled, or if the server reaches the client. It runs before `hx4sGenerate` and the generation of `IslandComponents`.
     shared → server is detected as the path client → shared → server if the client depends on shared. The same goes for shared → client and server → shared → client.
   - **Vite module graph: not implemented**. Paths that slip past the sbt check (client Scala.js `@JSImport`ing the server's link output, `app/client.ts` importing a server module, etc.)
     are currently caught only by string checks on the examples' bundles ([roadmap.md](roadmap.md)).

## 3. Public and internal APIs

| Module | Public API (compatibility is maintained) | Internal API (may change without notice) |
| --- | --- | --- |
| hx4s-core | The names, argument order and argument names of `hx4s.annotation.route` and `hx4s.annotation.islands[P]`. On the JS side, `hx4s.props.{JsShape, JsKind, JsField}` | — |
| hx4s-react | `hx4s.react.Island.apply`, `hx4s.react.IslandExport[P]` (`apply` / `unchecked`) and the name of the validation function attached to exports, `hx4sValidate`, the `hx4s.hono.Context[Env, R]` / `HonoRequest` / `ExecutionContext` facades, `ContextVariable[A]`, the extension methods `renderPromise` / `executionCtxOption` / `setVariable` / `getVariable` | The `HonoXIsland` facade and `IslandProps` (H1). Made `private` |
| hx4s-codegen | The settings case class (prototyped by 3ch's `CodegenConfig`; the name is fixed in Phase 3), CLI arguments, the shape of generated files (banner, file layout, import aliases, the method naming rule of `IslandComponents`) | Intermediate representations such as `AppEntries`, the template functions of `AppFileRenderer`, `RoutePath` |
| sbt-hx4s | Setting keys (`hx4s*`) and the tasks `hx4sGenerate`, `hx4sIslandComponents` and `hx4sCheckDependencies` | The name of the dedicated configuration, how isolated execution works, the handoff with codegen (`--output-list`) |
| hx4s-vite | Options of the `hx4s()` plugin function, `islandPropsProblem` in `hx4s-vite/client`, the Node loader entry points | Details of the Vite settings assembled internally (alias prefixes, etc.) |

The shape of generated files (TS import statements, banner text) counts as "public". They are committed to users' repositories, so changing them produces diffs in every file.
When changing them, bump the minor version and describe the change in the CHANGELOG.
