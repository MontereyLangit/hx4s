# Changelog

This file records changes per version in the [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) format.
Versions follow [SemVer](https://semver.org/) (during `0.x`, a minor version may change compatibility). See [docs/release.md](docs/release.md) for the publishing status.

## [Unreleased]

0.1.0 is in development. It is not published to Maven Central or npm and can be used only through a local publish (`scripts/publish-local.mjs`).

### Added

- **hx4s-core**: annotations `@route(filePath, method)` and `@islands[P](filePath)` (JVM and Scala.js). On the JS side, props validation `JsShape` (`JsKind`, `JsField`, `validate` / `read`)
- **hx4s-react**:
  - `hx4s.react.Island`, which marks a HonoX island boundary
  - `IslandExport[P]`, which binds a client export to the props type `P` (`apply` validates props and renders; `unchecked` does not validate)
  - A facade for Hono's Context, `hx4s.hono.Context[Env, R]` and `HonoRequest` (Request / Response are scalajs-dom types)
  - `executionCtx` (`ExecutionContext`) and the extension method `executionCtxOption`, `html`, `set` / `get` and typed `ContextVariable[A]` (`setVariable` / `getVariable`),
    `HonoRequest.queries`, `text()`, `json()`, `formData()`
  - `body(data, status, headers)`, which returns the body as-is (a `null` body can return 204 / 304), and `notFound()`
- **hx4s-codegen**:
  - Reads `@route` / `@islands` with scalameta and generates `app/routes/**/*.ts`, `app/islands/**/*.tsx` and `IslandComponents.scala` for SSR
  - `CodegenMain`, which takes settings as `--key value` (`app` / `island-components`)
  - Checks: rejects `HEAD` and file names HonoX does not pick up, rejects routes in different files that map to the same URL and method, island file name rules, matching `@islands[P]` against the export type `IslandExport[T]`. Server and client errors are reported together, and even when some declarations have errors, the remaining declarations are checked for duplicates and URL conflicts in the same run
  - Output safeguards: never overwrites or deletes hand-written files, writes after all checks, skips writes with identical content, replaces via temporary files, does not follow links, rejects paths that differ only in case
  - Avoids identifier collisions in generated code (import aliases) and validates settings embedded in generated sources
- **sbt-hx4s**: `Hx4sPlugin`. Setting keys (`hx4sClientProject` and others), `hx4sGenerate`, generation of `IslandComponents` through `Compile / sourceGenerators` (not regenerated when inputs are unchanged),
  `hx4sCheckDependencies` (checks the dependency direction between client and server), client-less (SSR-only) setups. Codegen is resolved through a dedicated configuration and runs in a forked JVM; on failure, the error lines are printed once and the task fails with a summary
- **hx4s-vite**: the `hx4s()` Vite plugin (registers the server / client Scala.js projects and serializes `buildStart`, the `scalajs:client.js` alias, honox's React settings, output names of the client build),
  `client: false` for setups without a client (API only, SSR without islands),
  props validation before hydration, `islandPropsProblem` in `hx4s-vite/client` (including islands inside templates), Node loader `hx4s-vite/node-loader` and `node-register`
- **scripts**: `publish-local.mjs` (local publish, publish record, `--verify`), `setup-example.mjs`, `check-independence.mjs`
- The version is defined in one place, `version.txt` (the sbt default and the local publish version derive from it, and a test detects mismatches with `vite/package.json`)
- **examples/minimal**: a minimal SSR + Islands app (dynamic routes, POST, a JSON API, two kinds of islands). Runs on Cloudflare Workers
- **examples/api-only**: a minimal JSON-API-only app (`hx4s({ client: false })`; does not depend on React)
- CI (Ubuntu and Windows; verifies sbt, npm and the examples)
- Documentation: design and plans (`docs/hx4s/`), user guides (`docs/`; Japanese translation in `ja/`) and a contributing guide
- License: Apache-2.0

### Fixed

- **scripts**: the local publish record includes the root `package.json` / `package-lock.json` (working tree state) and the SHA-256 of ivy's `ivy.xml`, JARs, sources, javadoc and pom.
  `--verify` checks that the artifacts exist and match, that the entries of the completed record match the set of artifacts, and that it agrees with the partial records
- Documentation: added a declaration for Hono's `ContextVariableMap` to the variable middleware example and made it a type-check target (`tests/docs-typecheck`). Fixed outdated statements that `body` / `notFound` / `client: false` don't exist,
  the explanation of re-reading the request body, and the examples steps in the contributing guide
