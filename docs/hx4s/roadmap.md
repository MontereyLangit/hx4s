# Roadmap

As of 2026-11-16. This describes the state including the Phase 3 implementation (splitting into the hx4s repository) and the fixes for its review findings ([reviews/phase3-review.md](reviews/phase3-review.md) chapter 5),
the shortcomings found when the original bulletin board project (3ch) switched to hx4s in Phase 4 and the responses to Codex's findings ([reviews/phase4-feedback.md](reviews/phase4-feedback.md)), and the fixes for inconsistencies found while writing the user documentation (ibid. chapter 4).
"Implemented" means it is in the hx4s repository and its tests pass.

## 1. P0 (what the first split delivers)

| Feature | Module | Status |
| --- | --- | --- |
| `@route(filePath, method)` | hx4s-core | Implemented (`hx4s.annotation.route`, JVM / JS) |
| `@islands[P](filePath)` (props given as a type argument) | hx4s-core | Implemented (`hx4s.annotation.islands`; the type argument can be omitted) |
| Props validation `JsShape` (`validate` / `read`) | hx4s-core (JS) | Implemented (`hx4s.props`; [ADR 0007](adr/0007-props-validation-and-typed-export.md)) |
| HonoX island bridge `Island(component, props, componentName)` | hx4s-react | Implemented (`hx4s.react.Island`) |
| Generic Hono Context facade (Env as a type parameter) | hx4s-react | Implemented (`hx4s.hono.Context[Env, R]`, `HonoRequest`, `ExecutionContext`. Request / Response are scalajs-dom; U-6 decided. `executionCtx`, `html`, `set` / `get`, body reading and more were added during the user migration in Phase 4) |
| Typed props helpers (`IslandComponents.Xxx(props: P)`, typed export `IslandExport[P]`, validation before hydration) | hx4s-react / hx4s-codegen | Implemented. Type mismatches between server calls and client exports are compile errors, and codegen syntactically matches `@islands[P]` against the declared type of the export (U-24 decided) |
| Annotation analysis, generation of route TS / island TSX / `IslandComponents` | hx4s-codegen | Implemented (`hx4s.codegen`) |
| Safe output (banner protection, deleting stale generated files, case conflicts) | hx4s-codegen | Implemented |
| Additional output rules (skip identical content, normalize line endings, write after all checks, reject links including ancestors of the output root, replace via temporary files, order new writes → deletion of stale files) | hx4s-codegen | Implemented ([build-integration.md](build-integration.md) 3.3, [codegen.md](codegen.md)) |
| Avoiding identifier collisions in generated code (import aliases), and validating and escaping settings embedded in generated sources | hx4s-codegen | Implemented ([codegen.md](codegen.md)) |
| Stricter `@route` checks | hx4s-codegen | Rejecting `HEAD`, rejecting file names HonoX / `import.meta.glob` don't pick up, and detecting URL pattern conflicts between generated routes (`RoutePath`) are implemented. **Warnings for conflicts with hand-written routes are not implemented** ([routing-design.md](routing-design.md) 2.2) |
| CLI main (takes settings as arguments) | hx4s-codegen | Implemented (`hx4s.codegen.CodegenMain`) |
| Setting keys and `hx4sGenerate`, a dedicated configuration and forked execution | sbt-hx4s | Implemented (`Hx4sPlugin`, with scripted tests). Client-less (SSR-only) setups are handled too |
| Checking the dependency direction between client and server | sbt-hx4s | The sbt dependency graph is implemented (`hx4sCheckDependencies`, scripted `dependencies`). **Checking Vite's module graph is not implemented** ([module-boundaries.md](module-boundaries.md) chapter 2) |
| Registering Scala.js projects, serializing `buildStart`, the `scalajs:client.js` alias, honox's React settings, client build settings | hx4s-vite | Implemented (`hx4s()`. Client output names can be overridden with `client.output`. `client: false` handles setups without a client (API only, SSR without islands)) |
| Props validation before hydration (including islands inside templates) | hx4s-vite | Implemented (`islandPropsProblem` in `hx4s-vite/client`; [rendering-model.md](rendering-model.md) 2.4) |
| Node loader (`scalajs:client.js`, `hono/jsx` inside HonoX) | hx4s-vite | Implemented (`hx4s-vite/node-register`, environment variable `HX4S_CLIENT_JS_DIR`). How to register it alongside a user's loader, and an example of `Test / jsEnv`, are in [build-integration.md](build-integration.md) 2.3 |
| Local publish procedure | scripts | Implemented (`scripts/publish-local.mjs`. The version is `0.1.0-local.g<commit>`, with `.dirty-<content hash>` if there are uncommitted changes). Phase 4 added starting with `npm ci`, publish records split between sbt and npm with the completed record `hx4s-publish.json`, `--verify`, and starting processes without a shell ([local-dependency.md](local-dependency.md)) |
| `examples/minimal` (SSR + Islands, Cloudflare Workers) | examples | Implemented (resolved only from locally published artifacts. npm dependencies are pinned with the lockfile and installed with `scripts/setup-example.mjs`) |
| `examples/api-only` (JSON API only; no client or React) | examples | Implemented (`hx4s({ client: false })`. Tests call the built Worker on Node. Covered by CI and the independence check) |
| Compatibility tests | Each module | Implemented: SSR, hydration (simple, nested, element props, multiple instances, Scala.js client islands), props round trips (Unicode, `</script>`, quotes, nesting), version skew, island file names, route registration (GET / POST / HEAD), bundle strings of the build, and negative tests of output. Also implemented: matching URL conversion (`filePathToPath`), validating islands inside templates, identifier collisions in generated TS. **Not implemented: behavior with non-finite `Double`s and cyclic references, scheduled runs with `honox@latest`, checking dependency direction with Vite's module graph** |
| User documentation (for publishing as OSS) | README, `docs/`, CONTRIBUTING, CHANGELOG | Implemented (2026-11-16; [docs/README.md](../README.md)). Code examples are either the same as `examples/minimal` or were compiled and run in a working copy. The setup making the whole page one island is not in the examples (the API-only setup is `examples/api-only`). Release steps are not set up because the publishing method is undecided ([release.md](../release.md)) |
| CI | `.github/workflows/ci.yml` | The file is in place (Ubuntu / Windows; the generated-files check also looks at untracked files). Not yet run remotely (pushing is the user's decision) |

## 2. P1

| Feature | Module | Status |
| --- | --- | --- |
| Typed route definitions (shared), with parameter extraction and URL building from them | hx4s-routing | Planned ([routing-design.md](routing-design.md) chapter 4) |
| Client Router for SPA mode (an adapter for scalajs-react Router) | hx4s-routing | Planned |
| `@route` with the `ALL` method | hx4s-codegen | Planned (U-13) |
| npm CLI (`npx hx4s generate`, etc.) | hx4s-vite or a separate package | Planned ([build-integration.md](build-integration.md) chapter 4) |
| Project templates (templates, `sbt new` / `npm create`) | templates | Planned |
| `Test / jsEnv` helpers (Node loader and linking the client first) | sbt-hx4s | Planned. P0 stops at `hx4s-vite/node-register` and `hx4s-vite/node-loader` and setting examples (`build.sbt` of examples/minimal; an example of registering alongside a user's loader is in [build-integration.md](build-integration.md) 2.3) |
| Generating `.d.ts` for the virtual modules `scalajs:*.js` (removing `@ts-expect-error`) | hx4s-codegen | Planned (U-16) |
| Props via case classes + codecs derived with `Mirror` (additional API) | hx4s-react | Planned ([rendering-model.md](rendering-model.md) chapter 3) |
| Omitting the client script on pages without islands | hx4s-codegen / hx4s-react | Planned |

## 3. P2

| Feature | Status |
| --- | --- |
| `hx4s-cloudflare` (DO exports, generating RPC stubs / TS types, Bindings types) | Planned. 3ch's `GenerateDurableObject*` is the starting point |
| Splitting `hx4s-react` when adding renderers (`hx4s-honox` and `hx4s-react`) | Planned |
| Official support for SSR + Full Hydration (putting props in `<script type="application/json">`) | Planned ([rendering-model.md](rendering-model.md) 1.2) |
| Type checking and conversion of props with Scala 3 macros | Planned |
| Development experience with the Vite dev server (HMR) | Planned (U-17). 3ch's `npm run dev` is build + `wrangler dev` and doesn't use the Vite dev server |

## 4. Open questions

Things that need deciding, other than those already decided as design decisions ([ADR](README.md)). "Proposal" is the idea put forward in this design.

| ID | Question | Current idea |
| --- | --- | --- |
| U-1 | npm scope: `hx4s-vite` or `@hx4s/vite` | Proposal: `@hx4s/vite` if the `@hx4s` scope is available at publishing time, which makes adding `@hx4s/cli` and the like easy later. Otherwise `hx4s-vite` |
| U-2 | Finalizing the Maven organization | Provisionally `io.github.montereylangit`. Change it if a custom domain is acquired |
| U-3 | Dependency resolution in CI (sbt source dependencies via git / submodules / publishing to Maven Central and npm / GitHub Packages) | The comparison in [ADR 0006](adr/0006-one-way-dependency-and-local-publish.md). Proposal: submodule + `publishLocal` until publishing, Central / npm after |
| U-4 | License | **Decided (2026-11-16): Apache-2.0**. `LICENSE` and `NOTICE` are at the repository root ([compatibility.md](compatibility.md) chapter 4) |
| U-5 | Whether to carry over the git history of the code moved from 3ch | Proposal: bring only the history of the moved files (`codegen/.../framework/` and so on; the mapping in chapter 3 of extraction-plan.md) with `git filter-repo`, after confirming that no other 3ch history (which may contain secrets) is brought in |
| U-6 | Fetch API types: use scalajs-dom's `Request` / `Response`, or have hx4s own a facade | **Decided (Phase 3): scalajs-dom** (`hx4s.hono.Context` / `HonoRequest`) |
| U-7 | Whether to provide a helper equivalent to `app/client.ts` (e.g. `createReactClient()` in `hx4s-vite/client`) | **Decided (Phase 3 review K6)**: no helper wrapping `createClient`; only the validation function `islandPropsProblem(element)` is provided in `hx4s-vite/client`. The template writes the options of `createClient` as-is. Earlier proposal: template only in P0 (it is about 10 lines, and a helper would hide the options of HonoX's `createClient`) |
| U-8 | Whether `honox` / `@scala-js/vite-plugin-scalajs` of `hx4s-vite` are peerDependencies or dependencies | Proposal: peerDependencies. Leave the choice of versions to users, and declare the verified range |
| U-9 | (Decided) Where `IslandComponents.scala` is output | **Decided: `src_managed` (`sourceGenerators`)**. 3ch uses this approach as of `875a47b` (`generateIslandComponents` in `build.sbt`). The TS side (`app/`) is read by Vite, so it is output to the source tree as before |
| U-10 | Whether to cross-build `hx4s-codegen` for Scala 2.12 too and call it directly inside sbt. When to assume a move to sbt 2 | Proposal: P0 is Scala 3 only + fork. Cross-building for 2.12 constrains how the code is written, so avoid it |
| U-11 | How to write the `P` of `@islands[P]`: limit to fully qualified names, or resolve imports | Proposal: limit to fully qualified names in P0 (the same as the recommendation in 3ch's plan). Resolving imports is incomplete with syntax analysis |
| U-12 | Rendering when props `validate` fails | **Decided (Phase 3)**: `hydrate` in `app/client.ts` calls the validation function of `IslandExport` (through `islandPropsProblem` in `hx4s-vite/client`, including islands inside templates), and on mismatch logs `console.error` and keeps the SSR markup without hydrating ([ADR 0007](adr/0007-props-validation-and-typed-export.md)) |
| U-13 | Handling `HEAD` and the `ALL` method | Proposal: reject `HEAD` (GET answers it). `ALL` in P1 |
| U-14 | Method names of `IslandComponents`: keep the export name (`PostingFormJs`) or use a derived name (`PostingForm`) | Proposal: keep the export name. More rules mean more conflict checks. 3ch's existing calls don't need to change |
| U-15 | Reducing the number of sbt starts (sbt client / getting the output of several projects with one `print`) | Not investigated. 3ch's `v6:build` starts sbt four times. The time taken was not measured |
| U-16 | Generating type declarations for the virtual modules | Proposal: in P1, generate the equivalent of `scalajs-server.d.ts` from export names and remove `@ts-expect-error` from generated routes. Don't go as far as argument and return types (converting syntax to TS types is a broad scope) |
| U-17 | How far to support the Vite dev server (HMR) | Not investigated. The combination of the dev mode of `@scala-js/vite-plugin-scalajs` (fastLinkJS) and HonoX's devServer isn't used in 3ch |
| U-18 | Following `component-name` when HonoX's `islandDir` is changed | Proposal: fixed to `/app/islands` in P0. If made configurable, pass the same value to both codegen and hx4s-vite (together with U-25) |
| U-19 | Whether to also provide the Node loader as an sbt-hx4s setting (a `Test / jsEnv` helper) | Proposal: P1. P0 provides only the loader files and setting examples |
| U-20 | Checking for name collisions (whether `hx4s` is already used on Maven Central, npm or GitHub) | Not checked. Check before publishing |
| U-21 | Rights to the code moved from 3ch (3ch has no license set; parts derived from the reference implementation `ch3s-migration`) | Confirmation from the rights holders is needed |
| U-22 | The API shape when adding case class props (go through a codec when the `P` of `@islands[P]` is a case class, etc.) | Proposal: coexist with approach A. Decide in P1 / P2 |
| U-23 | Whether to reject or only warn about URL conflicts with hand-written routes | Proposal: warn, so as not to block intentional overrides by hand |
| U-24 | A typed export helper that ties the client export to the `P` of `@islands[P]` | **Decided (Phase 3): `IslandExport[P]`** ([ADR 0007](adr/0007-props-validation-and-typed-export.md)). The following is the Phase 0 text: the types guarantee only the server's calling side, with no guarantee that the client component reads the same `P` ([rendering-model.md](rendering-model.md) 2.3). The idea is a type like `IslandExport[P]` that makes exports not matching `P` a compile error |
| U-25 | A common setting that passes appDir, moduleIDs and the virtual module prefix to codegen and hx4s-vite with the same values | P0 treats `app/routes`, `app/islands` and moduleIDs `server` / `client` as the default and only verified combination ([build-integration.md](build-integration.md) 2.0). The common setting is decided in P1. HonoX's globs have `/app` fixed, so changing it also requires specifying options such as `ISLAND_FILES` on the HonoX side |
| U-27 | Finding mismatches between the `fields` of `IslandExport` and the props trait at compile time | Today they are written separately, so mismatched key names, types or optionality aren't found (codegen only checks that the type names of `@islands[P]` and `IslandExport[T]` match syntactically). Idea: derive `fields` from the trait. Scala 3's `Mirror` can't be used on JS traits, so macros or codegen. P1 ([reviews/phase4-feedback.md](reviews/phase4-feedback.md) 4.1) |
| U-28 | On JDK 25, sbt prints a `sun.misc.Unsafe` warning at every start | A known limitation. It comes from sbt's startup components and can't be removed from hx4s. The build starts sbt four times, so it appears four times (U-15). Impact on behavior not examined |
| U-29 | Examples of writing element props and nested islands with `IslandComponents` / `IslandExport`, and the kinds in `JsKind` | No examples. `JsKind` has no kinds for React elements or `null`, so element props can't be validated with `JsShape`. Adding examples and kinds is undecided |
| U-26 | Countermeasures for version skew between HTML and client assets: hashed entry names, cache control, keeping old assets, guidelines for props schema changes, verifying old/new combinations | P0 only detects mismatches with `validate` at the client entry, logging `console.error` and keeping the SSR markup. The client's output names can be changed with `client.output` of `hx4s()` (the default is a fixed name). Whether to make hashed names the default, how to pass them to the HTML, and everything else are undecided ([rendering-model.md](rendering-model.md) 2.4, [ADR 0003](adr/0003-typed-island-props.md)) |
