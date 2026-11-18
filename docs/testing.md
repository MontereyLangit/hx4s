# Testing

How hx4s's own tests are organized and run. For how to test your app, see [ssr-hydration.md](ssr-hydration.md) and [examples.md](examples.md).

## Running

```bash
npm ci                        # once first. The react tests use react and honox from node_modules
sbt test                      # munit (core / react / codegen)
sbt sbtHx4s/scripted          # scripted tests for sbt-hx4s
sbt 'set ThisBuild / scalacOptions += "-no-indent"' Test/compile   # checks that no indentation syntax remains
npm test                      # Node tests (hx4s-vite build, SSR and hydration, independence, etc.)
npm run typecheck             # type-check hx4s-vite and the TypeScript examples in the docs (tests/docs-typecheck)
node scripts/check-independence.mjs   # independence check only
```

See [examples.md](examples.md) for the examples. CI (`.github/workflows/ci.yml`) runs all of the above on Ubuntu and Windows, plus everything from the local publish to `npm test` of the examples.

## Structure

| Layer | Location | Content |
| --- | --- | --- |
| core | `modules/core/shared/src/test`, `modules/core/js/src/test` | Annotations (`AnnotationsSuite`), `JsShape` validation (`JsShapeSuite`) |
| codegen | `modules/codegen/src/test` | Reading and checking annotations (`AnnotationScannerSuite`), rendering generated code (`AppFileRendererSuite`), argument parsing (`CodegenMainSuite`), output safeguards (`OutputWriterSuite`), URL conversion (`RoutePathSuite`) |
| react | `modules/react/src/test` | `Island`, `IslandExport`, `Context`. Registers Scala handlers on a real `Hono` and calls `fetch` (`ContextSuite`). Runs on Node (`Test / jsEnv` passes `vite/node-register.mjs` to `--import`) |
| sbt-hx4s | `sbt-plugin/src/sbt-test/hx4s/` | scripted. `generate` (generation, regeneration, deleting stale generated files, client-less setups, `sourceGenerators`), `error` (failure and line numbers with wrong annotations, error lines logged only once), `dependencies` (detecting forbidden dependencies and their paths) |
| Node | `tests/*.test.js` | Table below |
| Independence | `scripts/check-independence.mjs` (called by `tests/independence.test.js`) | Files don't contain the name of the original project (except `docs/hx4s/`, `ja/docs/hx4s/`, `LICENSE` and `NOTICE`). The examples don't reference the hx4s source tree or absolute paths |

### Node tests (`npm test`)

`pretest` builds `hx4s-vite`, and creates the link for the react tests (`react/Test/fastLinkJS`) and the fixtures of generated TS.

| File | Content |
| --- | --- |
| `islands-bridge.test.js` | The plugin setup of `hx4s()` (moduleID, prefixes, serialization, output names, `client: false`), and that HonoX SSRs islands with React and serializes props |
| `islands-hydration.test.js` | SSRs with the real Scala `Island` and hydrates on jsdom. Simple, nested and element props, dangerous strings and multiple instances, Scala.js client islands (`IslandExport`), and that mismatched props are not hydrated and stay as SSR |
| `generated-ts.test.js` | Even with export names whose identifiers could collide, the generated TS is syntactically valid and each method calls the corresponding handler |
| `route-paths.test.js` | Runs the same table as `RoutePathSuite` through HonoX's `createApp` and checks that the registered URL patterns match |
| `node-loader.test.js` | `hx4s-vite/node-loader` can be registered alongside a user's loader |
| `scripts-command.test.js` | The scripts can start npm / sbt even with paths containing spaces and `&` |
| `publish-record.test.js` | Local publish records and `--verify` checks (with git, ivy and records in a temporary directory). Fails on lockfile-only changes, deleted or replaced ivy JARs, sources or poms, missing entries, empty arrays, extra entries or mismatched artifact sets in the completed record, and mismatches with the partial records |
| `independence.test.js` | The independence check |
| `version.test.js` | The versions in `vite/package.json` and `package-lock.json` equal `version.txt`, and the `build.sbt` and local publish versions derive from `version.txt` |

## Testing policy

- Many compatibility dependencies are on HonoX's internal behavior (the `component-name` format, the `hono/jsx` replacement, `filePathToPath`, etc.). When upgrading HonoX, check the behavior with these tests ([hx4s/compatibility.md](hx4s/compatibility.md) chapter 3)
- Compile checks for typed props are written with Scala's `compileErrors`
- Negative tests for output (links, conflicts, converging after a failure midway) are in `OutputWriterSuite`. On Windows the junction tests run, and the symbolic link tests are skipped with `assume` if there is no permission to create them

## Tests not implemented

| Item | State |
| --- | --- |
| Scheduled runs with `honox@latest` and `vite@latest` | Not implemented. Only pinned versions are verified |
| Checking the dependency direction with Vite's module graph | Not implemented (only the sbt dependency graph and string checks of the bundle) |
| Behavior of props with non-finite `Double`s or cyclic references | Not implemented |
| Running CI remotely | The workflow is in place but has not been run remotely |
