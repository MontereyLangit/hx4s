# Shortcomings found in Phase 4 (the first user migration), and responses to Codex's findings

- Date: 2026-11-16
- Sources: switching the original bulletin board app to a setup using hx4s ("the user migration" below), and a review of `scripts/publish-local.mjs` by Codex CLI 0.163.0
- Target: hx4s `develop` (from the commit after `d133d7c`)

## 1. Findings and responses

| ID | Finding | Source | Decision / response |
| --- | --- | --- | --- |
| F1-1 | `hx4s.hono.Context` has no `executionCtx`, and the user reads it by dropping to `js.Dynamic` | The user migration | Added `hx4s.hono.ExecutionContext` (a `@js.native` trait with only `waitUntil(promise)` and `passThroughOnException()`) and `Context.executionCtx`. Not a type parameter (it would require rewriting the existing `Context[Env, R]`, runtime-specific members are rarely used, and it doesn't depend on Cloudflare types). Reasons in [routing-design.md](../routing-design.md) chapter 3 |
| F1-2 | Hono's `c.executionCtx` is a getter that throws without an execution context | The user migration (checked in `dist/context.js` of hono 4.13.18) | Added the extension method `executionCtxOption: Option[ExecutionContext]`. Not `Either`, because the only failure reason is "not provided". The same handler runs in calls that don't provide an execution context, such as SSR verification on Node |
| F1-3 | Basic APIs missing from Context / HonoRequest | The user migration | From the real type definitions (`dist/types/context.d.ts`, `request.d.ts`), added the minimum needed by API-only routes and SSR routes: `Context.html`, `set` / `get`, and `HonoRequest.queries`, `text()`, `json()`, `formData()`. For `set` / `get`, also added the typed `ContextVariable[A]` and `setVariable` / `getVariable`. `c.json` / `c.text` / `c.redirect` / `c.status` / `req.path` / `req.method` / `req.url` already existed. `parseBody`, `c.body`, `c.notFound`, header listing and the like were not added |
| F1-4 | Verifying the added APIs | — | `ContextSuite` (registers Scala handlers on a real `Hono` and calls `fetch`) uses every member. examples/minimal uses `c.req.formData()` for POST, and `c.json` and `c.req.queries` in the `api/items` JSON route |
| F2 | `hx4s-vite/node-register` registers only hx4s's loader, and it isn't clear how to combine it with runtime stubs | The user migration | [build-integration.md](../build-integration.md) 2.3 now has an example register file registering `register("hx4s-vite/node-loader", import.meta.url)` alongside the user's loader, the meaning of the registration order, and an example of sbt's `Test / jsEnv`. `tests/node-loader.test.js` checks that this setup resolves both `scalajs:client.js` and `cloudflare:workers`. Helpers in sbt-hx4s settings remain P1 (U-19) |
| F3-1 | `publish-local` fails right after a clone (no `node_modules`) | The user migration | Runs `npm ci` before the npm publish. `--reuse-node-modules` skips it only when the SHA-256 of `package-lock.json` matches the one from when this script last ran `npm ci` (`node_modules/.hx4s-npm-ci.json`) |
| F3-2 | Even with `--skip-sbt` / `--skip-npm`, `hx4s-version.txt` is written at the end, so a partial publish looks like a full one | Codex | Split the sbt and npm records (`hx4s-publish-sbt.json` / `hx4s-publish-npm.json`), and write the completed record `hx4s-publish.json` and `hx4s-version.txt` only when both have the same version, commit and working tree state with all artifacts present. They are deleted first when publishing starts, so no stale completion marker remains after a failure or a partial publish |
| F3-3 | No machine-readable publish record | Codex | `hx4s-publish.json` (`format`, version, commit, `dirty` / `dirtyHash`, Maven artifacts (organization, name, version, path from `~/.ivy2/local`), tgz file name and SHA-256; no timestamps). `hx4s-version.txt` was kept because `project/plugins.sbt` can't read JSON |
| F3-4 | On Windows, paths with spaces or `&` break starting with `shell: true` | Codex | Gathered process starting into `scripts/lib/command.mjs`. npm runs npm-cli.js directly with `process.execPath`; sbt runs directly on POSIX, and on Windows sbt.bat is made an absolute path and passed to `cmd.exe /d /s /c` as a single pre-quoted line (`" % ! ^` and newlines are rejected). Having PATH search for sbt.bat by a quoted name made `%~dp0` inside the batch file become the working directory and fail to start, so it uses an absolute path. `tests/scripts-command.test.js` starts npm from a directory containing spaces and `&`, and passes arguments containing spaces, `&`, `|` and a trailing `\` to a batch file placed in a directory with the same name |
| F3-5 | Review `setup-example.mjs` and `check-independence.mjs` from the same angle | Codex | `setup-example` uses the same starting method, and verifies the publish record with `--verify` before installing. `check-independence` already used `execFileSync` (no shell), so starting wasn't changed; it now reads with `git ls-files -z` |
| F3-6 | No way for users to decide whether republishing can be skipped | Codex | `--verify [--version V] [--commit SHA]`. Checks the agreement of the completed and partial records, the expected values, HEAD and the working tree, `ivy.xml` in `~/.ivy2/local`, the SHA-256 of the tgz and `hx4s-version.txt`; on mismatch, prints the reason and exits with code 1 |
| F4 | Not documented: the `--no-save` tgz disappears on the user's `npm ci`, and how to pin the version and commit | The user migration | [local-dependency.md](../local-dependency.md) now has the caveats and examples of a lock file (full commit SHA and version), a setup script and `plugins.sbt`. Linked from the README and added to ADR 0006 |

## 2. Changes to the public API

- hx4s-react: `hx4s.hono.ExecutionContext`, `hx4s.hono.ContextVariable[A]`, `Context.executionCtx`, `html`, `set`, `get`,
  the extension methods `executionCtxOption`, `setVariable`, `getVariable`, and `HonoRequest.queries`, `text()`, `json()`, `formData()`. Existing members are unchanged.
- `scripts/publish-local.mjs`: the flags `--reuse-node-modules`, `--verify`, `--commit` (only together with `--verify`).
  Outputs `target/local/hx4s-publish.json`, `hx4s-publish-sbt.json`, `hx4s-publish-npm.json`. `hx4s-version.txt` is written only on completion.

## 3. Remaining

- `--verify` doesn't handle ivy locations other than `~/.ivy2/local`.
- Artifact verification goes only as far as the presence of `ivy.xml`; content hashes of JARs aren't recorded.
- A helper that sets up `Test / jsEnv` in sbt-hx4s settings (U-19) remains P1.

## 4. Inconsistencies found while writing the user documentation (2026-11-16)

While checking the code examples of the user documentation (`docs/`) against real code, inconsistencies on the implementation side were found. Each was reproduced before being fixed.

| ID | Inconsistency | Reproduction | Response |
| --- | --- | --- | --- |
| G1 | In server mode, `hx4s()` always links the client sbt project, so setups without a client (API only, SSR without islands) fail with `Not a valid key: client`. sbt-hx4s handles client-less setups, but the Vite side doesn't | `vite build` in an sbt build without a client | Added `hx4s({ client: false })`. It skips linking the client, the `scalajs:client.js` alias and the client build, and doesn't add React settings to `honox()` (not needed without islands; also confirmed that the build passes in a setup without React even when `reactApiImportSource: "react"` is passed, since honox's island components aren't loaded). Combining it with `mode: "client"` is an error. The default is a client, as before. Added a minimal example, `examples/api-only` (doesn't depend on React, react-dom or `@hono/react-renderer`; tests call the Worker on Node; covered by CI and the independence check) |
| G2 | On codegen failure, the same error line appears three times in sbt's output | scripted `error`. The forked JVM's stderr, and the message of `MessageOnlyException` (sbt prints it once for the task failure and once in the final `(server / hx4sGenerate) …`) | Error lines are only recorded and printed once to the task log, and the forked JVM's `Exception in thread "main" …CodegenFailure` line is dropped. The exception is now a `FeedbackProvidedException`, so sbt prints only the summary (`hx4s-codegen app failed: 2 error(s)`) on the last line. `getMessage` has the summary and the error lines. scripted `error` checks that lines aren't duplicated in the task log (`target/streams/…/out`) |
| G3 | `Context` has no `c.body` or `c.notFound`, so a 204 without a body can't be written straightforwardly. `c.text("", 204)` becomes a 500 | `dist/context.js` of hono 4.13.18: `c.text` creates `new Response(text, { status })` with a body even for an empty string, the Fetch API's `Response` doesn't allow a (non-`null`) body for 204 / 304, so it becomes a `TypeError`, and Hono's default error handling returns 500 | Added `body(data: BodyInit \| Null, status, headers)` and `notFound(): Response \| js.Promise[Response]`. `ContextSuite` checks 204 / 304 / body and headers / the 500 of `c.text("", 204)` / `notFound`. Used by DELETE and GET in `examples/api-only` |
| G4 | `version` in `vite/package.json` was fixed at `0.1.0-SNAPSHOT` and kept in sync with the sbt version by hand | — | Made `version.txt` (`X.Y.Z`) the single definition of the version. The sbt default `<version>-SNAPSHOT` and the local publish `<version>-local.g<commit>` derive from it (`scripts/lib/version.mjs`). npm doesn't use `-SNAPSHOT` (it passes as semver syntax, but npm has no notion of a mutable development version; it would just sort before `0.1.0`), so `vite/package.json` has the same version as `version.txt`, and `tests/version.test.js` detects mismatches among `package.json`, `package-lock.json` and `build.sbt` |
| G5 | Codegen reports errors in stages. If an individual declaration has an error, it doesn't get as far as checking duplicate export names or URL conflicts | A `HEAD` route and two routes with the same URL in the same run | Duplicates and conflicts are checked using only the entries that passed per-declaration checks (and the sources that could be read), and all errors are returned at once. Erroneous declarations are excluded from conflict checks, so secondary errors caused by them aren't reported (conflicts appearing only after fixing are possible). Added 2 tests to `AnnotationScannerSuite` |

### 4.1 Recorded without fixing

| Item | Content | Handling |
| --- | --- | --- |
| Mismatch between the `fields` of `IslandExport` and the props trait | The `fields` (a list of `JsField`) of `IslandExport("X", fields)` and the props trait are written separately, so mismatched key names, types or optionality aren't found at compile time. Codegen only checks that the type names of `@islands[P]` and `IslandExport[T]` match syntactically | P1. The idea is to derive `fields` from the trait. Scala 3's `Mirror` can't be used on JS traits, so it would be macros or codegen ([roadmap.md](../roadmap.md) U-27) |
| Number of sbt starts in the build, and the JDK 25 warning | `npm run build` starts sbt four times. On JDK 25, a `sun.misc.Unsafe` warning appears at every start (called by sbt's startup components; impact on behavior not examined) | Not addressed (U-15, U-28) |
| Examples of element props and nested islands | There are no examples writing element props or nested islands with `IslandComponents` / `IslandExport`. `JsKind` has no kinds for React elements or `null` | Not addressed (U-29) |
| Fresh resolution in getting-started | The step of running `npm install` without a lockfile from the documented `package.json` hadn't been run | **Checked**: 4.2 below |

### 4.2 Checking the getting-started steps

Looking only at "Creating your own project" in `docs/getting-started.md`, the same files were created in a working directory (not a copy of the examples), and the following passed (2026-11-16, Windows 11, Node 22.18.0, JDK 25.0.1, hx4s `0.1.0-local.g933e0f7e1b`).

1. `npm install` without a lockfile (138 packages freshly resolved)
2. `npm install --no-save <hx4s>/target/local/hx4s-vite.tgz`
3. `npm run build` (generates `app/routes/index.ts` and `app/islands/counter.tsx`, and builds the client and the Worker) and `npm run typecheck`
4. Started with `wrangler dev`; `/` returns HTML with `<h1>Hello hx4s</h1>` and an island (a `clicks: 0` button), and `/static/client.js` returns 200

Nothing differed from the documentation, so getting-started wasn't changed. Clicking in a browser wasn't checked at this time (the E2E of examples/minimal checks the same setup).

### 4.3 Changes to the public API

- hx4s-react: added `Context.body(data, status, headers)` and `Context.notFound()`. Existing members are unchanged.
- hx4s-vite: `Hx4sOptions.client` became `Hx4sClientOptions | false` (`Hx4sClientOptions` is exported). The behavior of existing settings (omitting `client` or passing an object) doesn't change.
- sbt-hx4s: the exception on codegen failure changed from `MessageOnlyException` to a `FeedbackProvidedException` whose `toString` is the summary. `getMessage` still contains the error lines, but its first line changed to `hx4s-codegen <command> failed: N error(s)`.
- Version: `version.txt` defines the version. `version` in `vite/package.json` is `0.1.0`.
