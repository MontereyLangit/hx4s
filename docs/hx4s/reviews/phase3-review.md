# Verification and self-review of Phase 3 (splitting into the hx4s repository)

- Date: 2026-11-16
- Target: the Phase 3 commits on hx4s `develop` (core / codegen / react / vite / sbt-hx4s / publish-local / examples/minimal / CI / documentation)
- Environment: Windows 11, JDK 25.0.1, sbt 1.13.0, Node.js 22.18.0, npm 10.9.3
- The original bulletin board project was only read; nothing was written to it or built

## 1. Verification performed

| Command | Result |
| --- | --- |
| `sbt test` | Passed. coreJVM 3, coreJS 9, codegen 44 (2 symbolic link tests skipped for lack of permission to create them; 1 junction test passed), react 7 |
| `sbt sbtHx4s/scripted` | Passed (`hx4s/generate`, `hx4s/error`) |
| `sbt 'set ThisBuild / scalacOptions += "-no-indent"' Test/compile` | Passed (sbt-hx4s is Scala 2.12, so it is excluded with `scalacOptions :=`) |
| `npm test` (root) | Passed. 4 hx4s-vite plugin settings, 1 HonoX SSR wrapping, 3 hydration (simple, nested, element props / dangerous strings and multiple instances / Scala.js client islands and version skew), 1 independence |
| `npm run typecheck` (hx4s-vite) | Passed |
| `node scripts/publish-local.mjs` | Passed (5 sbt artifacts to `~/.ivy2/local`, `target/local/hx4s-vite.tgz`) |
| examples/minimal: `npm install` → `npm run build` → `npm run typecheck` → `npm test` | Passed. sbt test 3 (including compile checks of typed props), Node 6 (SSR, dynamic routes and 404, POST validation, client bundle, jsdom hydration and version skew, Playwright + Chromium E2E) |

### Bundle sizes of examples/minimal (as shown by `vite build`)

| Output | Size | gzip |
| --- | --- | --- |
| `dist/server/index.js` (Worker) | 686.34 kB | 200.52 kB |
| `dist/client/static/client.js` (client entry) | 209.01 kB | 67.61 kB |
| `dist/client/static/assets/client-*.js` (link output of the Scala.js client) | 178.24 kB | 50.39 kB |
| `dist/client/static/assets/counter-*.js` / `item-list-*.js` (island chunks) | 0.07 kB each | 0.08 kB |
| `dist/client/static/assets/runtime-*.js` (HonoX's template handling) | 1.99 kB | 1.00 kB |

Island chunks are just re-exports of the generated TSX; the actual code goes into the shared `client-*.js` (because the Scala.js client is linked as one module).

## 2. Found and fixed along the way

| Content | Response |
| --- | --- |
| With route errors, island errors weren't reported (short-circuited in a `for`) | Server and client errors are now returned together; tests added |
| Source paths passed from sbt used `\` separators on Windows, so error messages varied by OS | Normalized to `/` separators |
| The built Worker couldn't resolve `node:async_hooks` on workerd and didn't start (invisible in tests on Node; found by E2E) | Added `nodejs_compat` to the examples' `wrangler.jsonc` |
| Importing the built Worker on Node kept the process alive via React scheduler's MessagePort | Added `--test-force-exit` to the examples' `node --test` |
| NOTICE wasn't included in the `npm pack` output | Added it to `files` in the staged `package.json` |

## 3. Self-review (against the working conventions)

| Aspect | Result |
| --- | --- |
| Consistency with the original project's conventions | Uses braces and no optional braces (checked with `-no-indent`). Scaladoc is 1–2 lines. Business rejections are returned as `Either` / `CodegenError`, and become exceptions only at the sbt boundary |
| Choice of language | Generation and validation logic is in Scala. TS is limited to Vite wiring (hx4s-vite), the Node loader, the `app/*.ts` templates and tests |
| FP / mutable state | Codegen keeps pure planning separate from I/O. The mutable buffer of `RecordingLogger` in sbt-hx4s is limited to the boundary implementation that collects the fork's output |
| scalajs-react | Components are `ScalaFnComponent`s with Hooks. Values holding components are `final val` |
| Dependencies | The only additions are scalajs-dom (Fetch types; the same version as scalajs-react's transitive dependency) and, in the examples, `@hono/react-renderer`, wrangler, Playwright and jsdom. No Effect System, DI or state management |
| Dependency on Cloudflare | None in hx4s's modules. Only the examples' `vite.config.ts`, `wrangler.jsonc` and E2E startup |
| Test coverage | Chapter 1 above. What isn't covered is in chapter 4 |

## 4. Known issues and not implemented

- Detecting URL pattern conflicts of `@route` and warning about conflicts with hand-written routes ([routing-design.md](../routing-design.md) 2.2) weren't implemented in the original project either, and aren't in hx4s.
- The dependency direction (no client → server) is only checked through the examples' project definitions and string checks of the bundle; there is no automatic check of the sbt dependency graph or Vite's module graph.
- Matching `filePathToPath`, behavior with non-finite `Double`s and cyclic references, and scheduled runs with `honox@latest` ([compatibility.md](../compatibility.md) chapter 3) are not implemented.
- The symbolic link negative tests are skipped locally on Windows for lack of permission (junctions pass). They are expected to run in Linux CI, but CI hasn't been run remotely.
- examples/minimal doesn't commit `package-lock.json` (because the integrity hash of the local tgz changes on every publish). Versions of transitive dependencies aren't pinned.
- `npm run build` in the examples starts sbt four times (once for generation, once for the client build, and twice sequentially in the server build to link the server and the client). Each takes 15–30 seconds (U-15).
- `npm install` reports moderate npm audit findings (9 in the workspace). Their content hasn't been checked.

Of the items in chapter 4, detecting URL pattern conflicts, checking the sbt dependency graph, matching `filePathToPath`, the examples' lockfile and checking npm audit were addressed by the fixes in chapter 5 (chapter 4 is kept as the record at the time).

## 5. Phase 3 review and responses (2026-11-16)

- Target: `a58e5d7` on `develop` (the last Phase 3 commit)
- Fixes: `5b78cbf`–`c806b18` and the commit of this record
- Review methods: self-review, Codex CLI 0.163.0 (static review with line numbers as evidence), the CLI inspect of IntelliJ IDEA 2026.1 (profile scala)

### 5.1 Codex's findings

All were accepted. None were factually wrong (each item was checked against the code).

| # | Severity | Finding | Decision / response |
| --- | --- | --- | --- |
| K1 | High | Symlinks / junctions among ancestors of the output root are missed (if `app` is a junction and `app/routes` a regular directory, it passes) | Fixed. `OutputWriter.write` is given a base directory (`projectRoot`), and ancestors of output roots below it (including ancestors of the Scala output) are also rejected if they are links. `projectRoot` itself and above, and ancestors of output roots outside `projectRoot`, are documented as out of scope in codegen.md (setups where sbt's `target` is a link become errors). Added a junction negative test (passes on Windows) and a symlink version (skipped for lack of permission) |
| K2 | Medium | A failure midway through writing loses the marker, and rerunning doesn't converge | Fixed. Files are written to `.<name>.hx4s-tmp` in the same directory and then replaced with `ATOMIC_MOVE` (`REPLACE_EXISTING` if not possible). Leftover temporary files aren't considered hand-written and are deleted on the next run. Added failure injection when temporary files can't be created (existing files keep their original content and marker, and removing the fault and rerunning converges) and tests for cleaning up leftovers. Failure injection with read-only files was replaced with this approach, because the replacement is a rename and doesn't fail on Linux |
| K3 | Medium | Export names collide with identifiers in generated TS (`createRoute`, `POST`, etc.) | Fixed. Imports get generator-specific aliases (`__hx4s_createRoute`, `__hx4s_handler_<name>`, `__hx4s_island`). Added `tests/generated-ts.test.js`, which checks generated TS with the export names `createRoute`, `POST`, `GET`, `default`, `class` and `__hx4s_createRoute` through TypeScript's syntax check and loading on Node (replacing modules with stubs and checking each method calls the right handler). Before the fix, even an export named `c` broke as `(c) => c(c)`. Method names of `IslandComponents` were already wrapped in backquotes, with a test for a Scala reserved word (`match`) |
| K4 | Medium | An empty source list fails, and stale output can't be deleted | Fixed. `--server-sources` / `--client-sources` are empty if omitted. `island-components` doesn't require server sources. sbt-hx4s treats an unset `hx4sClientProject` as "no client" (not an error). Added a scripted test where deleting the last island removes the stale TSX and leaves an empty Scala API, and a scripted test for the client-less setup |
| K5 | Medium | The incremental key doesn't detect changes in the content of codegen's JARs | Fixed. The cache input of `hx4sIslandComponents` includes content hashes of each JAR on the classpath (for a directory, the files inside it). Also decided to include `javaHome` and `hx4sCodegenJavaOptions` in the key (they often don't affect the output, but including them costs at most one extra regeneration). Documented in build-integration.md 2.2 |
| K6 | Medium | Islands inside templates can't stop the outer hydration even when their props don't match | Fixed. `islandPropsProblem(element)` in `hx4s-vite/client` walks the element props (children restored from templates) and calls the inner `hx4sValidate` too. The `app/client.ts` template calls it, keeping the options of `createClient` visible. Added a test (jsdom, real Scala.js islands) where an invalid inner island leaves the outer one unhydrated with the SSR markup intact, and documented the scope of the guarantee in rendering-model.md 2.4. Updated U-7 and ADR 0007 |
| K7 | Medium | No dependency graph check preventing server code from leaking into the client | Fixed for the sbt dependency graph. `hx4sCheckDependencies` looks for paths from the client (and its transitive `dependsOn`) to a project with Hx4sPlugin enabled, and from the server to the client, and fails printing the path (test-only dependencies are excluded). Added the scripted `dependencies` test. Checking Vite's module graph wasn't implemented this time and is documented as not implemented in module-boundaries.md and roadmap.md |
| K8 | Medium | Generated routes converting to the same URL are accepted | Fixed. Copied honox 0.1.62's `filePathToPath` and the rule that `createApp` mounts per-directory sub apps with `app.route` (hono's `mergePath`) into `RoutePath`, and made it a `CodegenError` if the shape without parameter names and the method are the same. The conversion table is matched through `RoutePathSuite` and `tests/route-paths.test.js`, which runs it through `createApp` from `honox/server/server`. Added as HonoX internal dependency H11. `(group)` is in the conversion but isn't generated because of the allowed characters of output paths. `[[optional]]` doesn't exist in HonoX, so it isn't handled. Conflicts with hand-written routes still produce no warning (stated as not implemented) |
| K9 | Medium | The CI check for generated files misses untracked files | Fixed. Checks that `git status --porcelain --untracked-files=all -- examples/minimal/app` is empty |
| K10 | Medium | The example's CI doesn't pin dependencies | Fixed. Committed `examples/minimal/package-lock.json` and removed hx4s-vite from package.json. `scripts/setup-example.mjs` installs `target/local/hx4s-vite.tgz` with `npm install --no-save` after `npm ci` (decoupling the tgz's integrity hash from the lockfile). Aligned CI and the README |
| K11 | Medium | Setting strings are embedded into generated sources unprocessed | Fixed. moduleIDs, the virtual module prefix and the island ID prefix are validated by character class; package names, object names, the bridge, the export type and the annotation package as Scala identifiers and against reserved words; banners and markers reject U+2028 / U+2029 as line separators too. Values put into string literals go through escaping shared by TS and Scala. Added negative tests for quotes, newlines, backslashes, `*/`, spaces and U+2028 |
| K12 | Low | Source paths are re-split on commas | Fixed. Only repeated options now. Added a test with paths containing commas and spaces, and `client app/.../Other, part.scala` to the scripted `generate` |
| K13 | Low | The local publish version doesn't identify the dirty content | Fixed. If the published sources (excluding tests) have tracked changes or untracked files, an 8-character hash of the paths and content is appended to the version as `.dirty-<hash>` (semver numeric identifiers don't allow leading zeros, so it is one alphanumeric identifier). Aligned ADR 0006 and the README |
| K14 | Low | Old ADRs contradict the implementation (0005's Node adapter, 0002's Pure core, 0001's automatic acceptance of the old marker) | Fixed. Added the later decisions to each ADR as "Updated (2026-11-16)" (keeping the original text). Following K6, ADRs 0006 and 0007 were updated too |

Points Codex found no problem with: arguments with paths containing spaces from sbt are passed to `Fork.java` as an argument list, so no quoting is missing. Props go through HonoX / React's attribute value escaping, and there is no direct XSS path.
The examples' `wrangler.jsonc` limits static serving to `dist/client`, and the Worker is separate in `dist/server`. core / codegen have no compile dependency on Cloudflare.
The original project's proper names remain nowhere in executable code other than design and history explanations.

### 5.2 IntelliJ inspect

| Inspection | Count | Decision / response |
| --- | --- | --- |
| ComparingLength | 7 | Accepted. Changed to `.sizeIs` (AnnotationScanner 5, CodegenMain 1, OutputWriter 1) |
| ConvertibleToMethodValue | 1 | Accepted. Changed to `managed.exists(sibling.startsWith)` (same behavior) |
| MapGetOrElse | 1 | Accepted. Changed to `IO.relativize(...).fold(...)(...)` in sbt-hx4s (same behavior) |
| LegacyStringFormatting | 6 | Changed the 3 in main (AppFileRenderer 2, OutputWriter 1) and the 2 simple ones in AnnotationScannerSuite (lines 141 and 149, of the same shape) to interpolation. Skipped line 121 of AnnotationScannerSuite, because more escaping would make it harder to read |
| ScalaDocParserErrorInspection / ScalaDocUnknownTag | 1 / 1 | Accepted. Reworded `@route / @islands` in Hx4sPlugin's Scaladoc so it isn't parsed as tags ("route / islands annotations") |
| VulnerableLibrariesLocal | 2 | Accepted. hono 4.13.11 → 4.13.18 (root and examples/minimal). hx4s-vite doesn't have hono as a peerDependency (honox's peer is `>=4.*`), so no range changed |
| SomeToOption | 29 | Rejected. None of the values passed to `Some(...)` can be null (literals, generated strings, parsed values) |
| AbstractValueInTrait | 12 | Rejected. As designed for Scala.js non-native JS traits (the shape of props) and native traits |
| ScalaWeakerAccess | 8 | Rejected. Public API (values of `HttpMethod`, `Codegen.appOutputs`, `CodegenConfig.DefaultGeneratedMarker` / `DefaultBanner`, members of `hx4s.hono.Context`, the configuration in `Hx4sPlugin.autoImport`) |
| scala2InSource3 | 4 | Rejected. sbt-hx4s is Scala 2.12, and `import sbt._` and the like are the 2.12 way of writing |
| ScalaUnusedSymbol | 1 | Rejected. `hx4sVersion` is a value used by users' build.sbt |
| SbtDependencyVersionInspection | 1 | Rejected. sbt-scalajs is pinned to the verified 1.22.0 |
| VulnerableLibrariesGlobal | 4 | Rejected. log4j-api 2.25.4, jline-reader / jline-builtins 3.27.1 and jansi 2.4.1 are brought in by sbt 1.13.0. Confirmed that the dependencies in the published sbt-hx4s ivy.xml are only `sbt` 1.13.0 (`provided`) and those for scripted, not dependencies of hx4s's artifacts |

npm audit (after upgrading hono): 8 moderate findings in both the workspace and examples/minimal, 0 high or above. All come from honox 0.1.62's dependencies
(double decoding of paths in serveStatic of `@hono/vite-dev-server` → `@hono/node-server`, GHSA-rmxm-3fg6-px4f; ReDoS in `precinct` → `detective-typescript` →
`@typescript-eslint/typescript-estree` → `minimatch`, GHSA-g3hv-75wg-m249; and others). `npm audit fix --force` would be a breaking change downgrading honox to 0.1.54, so it wasn't run, pending upstream.
Both are used by Vite's dev server and build-time dependency analysis, and don't end up in the Worker or client bundles.

How inspect was run: the hx4s repository has no `.idea` imported from sbt by the IDE, so `.idea` (modules and libraries) was assembled by hand before running `idea64.exe inspect`.
The `gen_idea.py` in the scratchpad used for the assembly copies the libraries of the original project's `.idea` and writes hx4s's module structure; it depends on the original checkout, so it wasn't added to `scripts/`.
Opening hx4s once in IntelliJ as an sbt project creates `.idea`, so use that next time.

### 5.3 Self-review

| # | Finding | Decision / response |
| --- | --- | --- |
| Z1 | sbt-hx4s duplicates codegen's default generated marker (hard-coded in `generateTask`) | Fixed. Added `--output-list` to codegen, which writes the list of written and kept files. `hx4sGenerate` reads and returns it, and the plugin doesn't hold the marker |
| Z2 | The output names of hx4s-vite's client build are fixed | Fixed. They can be overridden with `client.output` (`entryFileNames`, `chunkFileNames`, `assetFileNames`). The defaults are unchanged. Hashed entry names remain undecided (U-26); build-integration.md and rendering-model.md note that version skew is detected by the K6 validation |
| Z3 | The Phase 3 completion criterion "CI passes" in extraction-plan.md depends on pushing, which is the user's decision | Fixed. Split into "all verification passes locally" and "confirm remote CI after the push" |

Noticed and fixed along the way: scripted's `$ copy-file` / `$ delete` can't pass paths with spaces in quotes (`InvalidPathException`), so operations on paths with spaces were done in build.sbt tasks.
In the dirty check of `publish-local.mjs`, trimming the output of `git status` removed the leading space (the marker for unstaged changes) and cut the first character of the path, so it was changed to extraction without trimming.

### 5.4 Verification after the fixes (run at `c806b18`; the commit of this record is documentation only)

| Command | Result |
| --- | --- |
| `sbt test` | Passed. coreJVM 3, coreJS 9, codegen 55 (52 passed; 3 symbolic link tests skipped for lack of permission to create them; 2 junction tests passed), react 7 |
| `sbt sbtHx4s/scripted` | Passed (`hx4s/dependencies`, `hx4s/error`, `hx4s/generate`) |
| `sbt 'set ThisBuild / scalacOptions += "-no-indent"' Test/compile` | Passed |
| `npm test` (root) | Passed. 16 (generated TS identifiers 2, independence 1, hx4s-vite settings 4, HonoX SSR wrapping 1, hydration 4 (including validation of islands inside templates), URL conversion matching 1, etc.) |
| `npm run typecheck` (hx4s-vite) | Passed |
| `node scripts/publish-local.mjs` | Passed (version `0.1.0-local.gc806b18238`; the working tree is clean, so no dirty marker) |
| examples/minimal: delete node_modules, target and dist, then `node scripts/setup-example.mjs minimal` → `npx playwright install chromium` → `npm run build` → `npm run typecheck` → `npm test` | Passed. The lockfile didn't change. sbt test 3, Node 6 (including Playwright + Chromium E2E; nothing skipped) |
| `node scripts/check-independence.mjs` | Passed |

Bundle sizes of examples/minimal: `dist/server/index.js` 688.15 kB (gzip 201.20 kB), `dist/client/static/client.js` 209.63 kB (gzip 67.77 kB; 0.62 kB larger due to `hx4s-vite/client`),
`client-*.js` (the Scala.js client) 178.24 kB (gzip 50.39 kB), island chunks 0.07 kB each, `runtime-*.js` 1.99 kB.

### 5.5 Remaining not implemented

- Checking the dependency direction with Vite's module graph (the rest of K7).
- Warning about URL conflicts with hand-written files in `app/routes` (U-23).
- Behavior with non-finite `Double`s and cyclic references, scheduled runs with `honox@latest` ([compatibility.md](../compatibility.md) chapter 3).
- The symbolic link negative tests are skipped locally on Windows. Remote CI (Linux) is checked after the push.
- The 8 moderate npm audit findings are pending updates to honox's dependencies.
