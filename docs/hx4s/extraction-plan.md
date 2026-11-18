# Extraction plan

## 1. Approach

Proceed in the order **improve within 3ch → verify in 3ch → generalize within 3ch → split into hx4s → migrate 3ch to being a user of hx4s**.
No from-scratch reimplementation.

Reasons:

- The current integration layer runs in 3ch's production, and verification such as `tests/islands-*.test.js` is in place. Moving working code is less likely to break than rebuilding it.
- If improvements (typed props, generation safety) are made first in 3ch, they can be verified on the production screens. Improving after the split would mean fixing two places, hx4s and 3ch, at the same time.
- If generalization (extracting packages and settings) is finished within 3ch, the split itself becomes close to "moving files and swapping build settings", and the diffs are easier to follow.

Rejected approaches:

| Alternative | Reason for rejection |
| --- | --- |
| Write hx4s from scratch and port 3ch onto it later | Behavior already verified in 3ch's production (nested islands, element props, the KB-27 separation) would have to be rediscovered. Behavior differences during migration would be hard to isolate |
| Move to hx4s first and improve afterwards | Every improvement would require aligning the versions of both hx4s and 3ch. The period of local dependencies would be longer |
| Publish while keeping it inside 3ch (monorepo) | It would share a repository with 3ch's bulletin-board-specific code and secret settings, making the scope of what is published hard to manage |

## 2. Phases

| Phase | Content | Where |
| --- | --- | --- |
| 0 | Design (these documents) | hx4s |
| 1 | Improve within 3ch | 3ch |
| 2 | Generalize within 3ch (split into `io._3ch.codegen.framework` and turn settings into a case class; renaming the packages to `hx4s.*` is Phase 3) | 3ch |
| 3 | Move to the hx4s repository and rename to `hx4s.*`. Create `examples/minimal` | hx4s |
| 4 | Migrate 3ch to being a user of hx4s (local dependencies) | 3ch |
| 5 | Prepare for publishing (dependency resolution in CI, license, versioning, publishing) | hx4s / 3ch |

### Phase 0: Design

- Work: write these documents and the ADRs. Nothing is written to 3ch.
- Completion criteria: the documents in `docs/hx4s/` are complete and cross-links are correct. One commit on hx4s's `develop`.

### Phase 1: Improve within 3ch

Make the improvements to bring into hx4s, keeping 3ch's package names and structure. Each item is a separate commit.

| # | Work | Basis |
| --- | --- | --- |
| 1-1 | Typed props: `@islands[P]`, the generated API `IslandComponents.Xxx(props: P)`, the traits and `validate` in `io._3ch.common.islands`. Replace starting with the 3 islands of the board top | [ADR 0003](adr/0003-typed-island-props.md), 3ch `docs/island-props型付け計画.md` |
| 1-2 | Stricter `@route` checks: reject `HEAD`, reject file names HonoX ignores, detect URL pattern conflicts | [routing-design.md](routing-design.md) 2.2 |
| 1-3 | Output rules: skip identical content, normalize line endings, reject paths outside the output root (after normalization), all checks before writing | [build-integration.md](build-integration.md) 3.3 |
| 1-4 | Split `RouteTable`: `publicPatterns` to 3ch's domain side, templates to `codegen`. Clean up `RouteKind.PassThrough` | [module-boundaries.md](module-boundaries.md) 1.2 |
| 1-5 | Handler types: stop using `c: js.Any` + `asInstanceOf`, and make `HonoContextFacade` the argument type | [routing-design.md](routing-design.md) chapter 3 |
| 1-6 | Fix discrepancies in 3ch's documents (`handler-rendering.md`, the version and examples in `islands-ssr.md`, the marker name in `client-build-review.md`, the descriptions of hand-written routes and the admin screen in `AGENTS.md`) | [architecture.md](architecture.md) 2.5 |

Verification conditions:

- `sbt test`, `npm run v6:build`, `npm run v6:typecheck` and `npm test` succeed.
- For islands whose `data-serialized-props` shape changes on the board top (nesting of `board-details`), the SSR and hydration tests pass with updated expectations.
- Running regeneration twice in a row leaves the files' modification times unchanged the second time (1-3).
- No diffs in 3ch's public protocol (dat, subject.txt, Shift_JIS, error markers) or storage (3ch AGENTS.md 3.2).

Completion criteria: the six items above are done, and the local build and all tests (`sbt test`, `npm run v6:build`, `npm run v6:typecheck`, `npm test`) pass.
No deployment is done in this work. Merging into 3ch's `develop` and releasing to production are left to 3ch's decision (the user's decision).

Status (reference commit `875a47b`, [architecture.md](architecture.md) chapter 1): Phases 1 / 2 are in progress in 3ch, and the working tree may change.
Part of 1-3 (skipping identical content, normalizing line endings, writing after all checks), turning the generators into the `framework` package and settings into a case class in Phase 2 (2-3, 2-4),
and generating `IslandComponents` into `src_managed` ([build-integration.md](build-integration.md) 3.3, U-9) are already in 3ch.
1-4 is done up to moving the generation templates to `codegen` (`publicPatterns` remains in `RouteTable`). 1-1 (typed props), 1-2 (stricter `@route` checks), 1-5 and 1-6 are not done. Re-read 3ch's current state when starting.

### Phase 2: Generalize within 3ch

Within 3ch's build, bring the parts to be moved to hx4s into "a form without 3ch-specific names". **Don't create an `hx4s/` directory, and keep the packages as `io._3ch.*`.**
Renaming to `hx4s.*` is done in Phase 3 together with moving the files (leaving a rename-only diff in 3ch would mean rewriting every file twice).

3ch's current state (as of `875a47b`):

- The generic parts of the generator are in `codegen/src/main/scala/io/_3ch/codegen/framework/` (`AnnotationScanner`, `AppEntries`, `AppFileRenderer`, `Codegen`, `CodegenConfig`, `CodegenError`, `CodegenReport`, `OutputPath`, `OutputWriter`).
- 3ch-specific values (input paths, moduleIDs, output directories, package, fully qualified name of the bridge, banner) are passed as a `CodegenConfig` by `GenerateAppFiles.config`.
- Generation of DO TS types and RPC stubs remains directly under `codegen/.../` (3ch-specific).

| # | Work | Status |
| --- | --- | --- |
| 2-1 | Split `RouteTable.publicPatterns` off to the 3ch side, and add the type parameter of `islands[P]` to the annotation (1-1, 1-4). The annotations themselves (`common/.../routes/Annotations.scala`) move to `hx4s.annotation.{route, islands}` in Phase 3 | Partial |
| 2-2 | Make Env / Props of `Island` and the Hono Context facade type parameters. 3ch receives them through aliases like `HonoContext[WorkerEnv]` | In progress (working tree changes splitting into `HonoContext` / `HonoContextFacade`) |
| 2-3 | Split annotation scanning, templates, output and `IslandComponents` generation into the `framework` package, with settings in `CodegenConfig` | Done (`875a47b`) |
| 2-4 | Reduce 3ch's `codegen` to the DO generators and a thin main (`GenerateAppFiles`) that calls `framework` with settings | Done (`875a47b`) |
| 2-5 | Extract `scalaProject`, the alias, `honox()`'s React settings and the client build settings from `vite.config.ts` as functions that don't depend on 3ch. Tailwind and Cloudflare settings stay in 3ch | Not started |
| 2-6 | Split the Node loader: put the replacement of `scalajs:client.js` and `hono/jsx`, and the `cloudflare:workers` stub, in separate files. Rename `CH3S_CLIENT_JS_DIR` to a neutral name | Not started |
| 2-7 | Make `tests/islands-bridge.test.js` and `tests/islands-hydration.test.js` independent of 3ch's `vite.config.ts` and `bbs`'s test exports | Not started |
| 2-8 | Add a test checking that the generic parts (the `framework` package and the extracted TS, loader and tests) contain no `io._3ch`, `ch3s`, `3ch` or `bbs` strings (grep is enough). Strings passed as settings are excluded | Not started |

No sbt plugin is created at this stage. Keep the current approach of calling `codegen` with `runMain` from 3ch's `build.sbt`.

Verification conditions:

- All Phase 1 verification conditions are met.
- The generated files (`app/routes`, `app/islands`, `IslandComponents.scala`) are identical to those at the end of Phase 1.
- The 2-8 check passes.
- The tests of the `framework` package pass without referencing 3ch's sources (`bbs`, `bbs-client`, `common`).

Completion criteria: the items above are done, and the local build and all tests pass. Merging and deployment are left to 3ch's decision, as in Phase 1.

### Phase 3: Split into the hx4s repository

| # | Work |
| --- | --- |
| 3-1 | Move 3ch's `codegen/.../framework/` and the Scala / TS / loader / tests extracted in 2-2, 2-5, 2-6 and 2-7 to the hx4s repository, and **at the same time** rename the packages to `hx4s.*` and the 3ch defaults of the settings to hx4s defaults. Whether to carry over history (`git filter-repo`, etc.) is open question U-5. The files to move are in the mapping in chapter 3 |
| 3-2 | Create hx4s's `build.sbt`. Module names, organization and Scala version (3.3 LTS) follow [ADR 0001](adr/0001-name-and-coordinates.md) and [ADR 0002](adr/0002-initial-modules.md) |
| 3-3 | Create `sbt-hx4s`. Setting keys, `hx4sGenerate`, resolving `hx4s-codegen` through a dedicated configuration and running it in a fork ([build-integration.md](build-integration.md) 3.2) |
| 3-4 | Make `hx4s-vite` an npm package (`package.json`, type definitions, `node-loader`) |
| 3-1a | Design and implement the typed export helper (tying the client export to the `P` of `@islands[P]`; [ADR 0003](adr/0003-typed-island-props.md), U-24) |
| 3-5 | Create `examples/minimal`. A static page and the dynamic route `[id]`, a POST `@route`, two islands (typed props; multiple instances of the same island, props with arrays, nesting and Japanese). The default runtime is Cloudflare Workers (`@hono/vite-build/cloudflare-workers` + wrangler) (below) |
| 3-6 | hx4s's CI: `sbt test`, `sbt publishLocal`, building `examples/minimal` and the compatibility tests ([compatibility.md](compatibility.md) chapter 3) |

The runtime for 3-5 changed from the Phase 0 proposal (the Node adapter) to Cloudflare Workers (per the Phase 3 work instructions). Reason: the first user (3ch) runs on Workers,
and the same combination (`@hono/vite-build/cloudflare-workers`, Static Assets, workerd) can be verified on the hx4s side too. Only the examples'
`vite.config.ts`, `wrangler.jsonc` and test startup depend on Cloudflare; hx4s's modules don't reference Cloudflare APIs ([ADR 0005](adr/0005-core-independent-of-cloudflare.md)).
The built Worker's `fetch` can also be called from Node, so SSR and POST tests run on Node, and real-browser E2E runs on `wrangler dev`.

Phase 3 results (2026-11-16): 3-1 through 3-6 and 3-1a were implemented. U-5 (carrying over history) was skipped, and the files went in as commits that moved and renamed them
(because the original history may contain secrets. The original commits are not mentioned in each commit's body). Verification results and known issues are in [reviews/phase3-review.md](reviews/phase3-review.md).

Verification conditions:

- `examples/minimal` builds referencing hx4s through `publishLocal` and npm `file:`, and the SSR and hydration tests pass.
- `examples/minimal` and all hx4s modules contain no references to 3ch (the same check as 2-8).
- Negative tests confirming the scope of typed props guarantees (arguments other than `P` in server calls are compile errors; if the typed export helper is in, type mismatches in client exports are compile errors too), behavior when `validate` fails, and verification of version skew between HTML and client assets ([rendering-model.md](rendering-model.md) 2.3 and 2.4, [ADR 0003](adr/0003-typed-island-props.md)).
- Negative tests of output against symlinks / junctions ([build-integration.md](build-integration.md) 3.3).
- `examples/minimal` builds on both Windows and Linux (CI OS matrix; Windows confirmed at least locally).

Completion criteria: it is in hx4s's `develop`, and all verification passes locally (`sbt test`, `sbt sbtHx4s/scripted`, the `-no-indent` check, `npm test`, `npm run typecheck`,
`scripts/publish-local.mjs`, install, build, typecheck and test of examples/minimal from a clean state, `scripts/check-independence.mjs`).
Confirming that remote CI (Ubuntu / Windows) passes happens after the push (the user's decision). At this point 3ch still uses the built-in version from Phase 2.

### Phase 4: Migrate 3ch to being a user of hx4s

Prerequisites (publishing stays in Phase 5, but a reproducible verification path is needed from Phase 4; [ADR 0006](adr/0006-one-way-dependency-and-local-publish.md)):

- Script the steps to run `publishLocal` and prepare the npm package (`npm pack`, etc.) from an hx4s checkout pinned to a commit. Versions are explicit, like `0.1.0-local.N`, and the steps are written in 3ch's docs.
- 3ch passes its build and tests starting from **empty dependency caches** (empty coursier / ivy caches and `node_modules`).

| # | Work |
| --- | --- |
| 4-0 | Create the steps (scripts) for the prerequisites above, and pass 3ch's build and tests with empty dependency caches |
| 4-1 | Add `sbt-hx4s` to 3ch's `project/plugins.sbt`, and `hx4s-core` / `hx4s-react` to `build.sbt`. Versions are the ones published with `publishLocal` |
| 4-2 | Add `hx4s-vite` to 3ch's `package.json` with `file:`, and replace `vite.config.ts` with `hx4s()` |
| 4-3 | Delete the `framework` package of 3ch's `codegen` and the extracted TS, loader and tests. Change `npm run v6:generate` to `sbt hx4sGenerate` + 3ch's DO generation |
| 4-4 | Update 3ch's AGENTS.md and docs (structure, development commands, references to hx4s's documents) |

Verification conditions: all Phase 1 verification conditions are met, and the generated files are identical to those at the end of Phase 2 (except for the banner). The prerequisites above (building from empty dependency caches) are met.

Completion criteria: it is in 3ch's `develop`, and the local build and all tests pass (deployment is 3ch's decision).

### Phase 5: Prepare for publishing

| # | Work |
| --- | --- |
| 5-1 | Decide the dependency resolution method in CI that involves publishing (comparison in [ADR 0006](adr/0006-one-way-dependency-and-local-publish.md), U-3) |
| 5-2 | Decide the license and add LICENSE / NOTICE (U-4). Confirm rights to the code moved from 3ch (U-21) |
| 5-3 | Check for name collisions (`hx4s` on Maven Central, npm and GitHub) (U-20). Decide the npm scope (U-1) |
| 5-4 | Versioning policy (compatibility promises during 0.x, stating the verified honox version) |
| 5-5 | Publish to Maven Central / npm (whether to publish at all is also decided at this point) |

Completion criteria: 3ch's CI can resolve hx4s without a local `publishLocal`.

## 3. Mapping of moved files

See [module-boundaries.md](module-boundaries.md) for the reasons behind the classification. Paths are relative to the 3CH repository root. The reference is `875a47b`, and 3ch's file names may change as Phases 1 / 2 progress.
"Phase" shows when the file is organized within 3ch (2) and moved to hx4s (3). Packages are renamed from `io._3ch.codegen.framework` to `hx4s.codegen` in Phase 3.

| 3CH file | Destination (module / proposed path) | Phase |
| --- | --- | --- |
| `common/src/main/scala/io/_3ch/common/routes/Annotations.scala` | hx4s-core `core/src/main/scala/hx4s/annotation/{route,islands}.scala` | 2 (`islands[P]`) → 3 |
| `publicPatterns` in `common/.../routes/RouteTable.scala` | Stays in 3ch (`common`'s domain or `bbs`'s handler) | 1–2 |
| `common/src/test/.../routes/RouteTableSuite.scala` | Checks of `publicPatterns` stay in 3ch. Template checks are in the `framework` tests | 2 |
| `codegen/.../framework/AnnotationScanner.scala` | hx4s-codegen `hx4s/codegen/AnnotationScanner.scala` | 3 |
| `codegen/.../framework/AppEntries.scala` (`HttpMethod`, `RouteEntry`, `IslandEntry`) | hx4s-codegen `hx4s/codegen/AppEntries.scala` | 3 |
| `codegen/.../framework/AppFileRenderer.scala` | hx4s-codegen `hx4s/codegen/AppFileRenderer.scala` (templates for route / island / `IslandComponents`) | 3 |
| `codegen/.../framework/Codegen.scala` | hx4s-codegen `hx4s/codegen/Codegen.scala` | 3 |
| `codegen/.../framework/CodegenConfig.scala` | hx4s-codegen `hx4s/codegen/CodegenConfig.scala` (the public settings case class; the name is fixed in Phase 3) | 3 |
| `codegen/.../framework/{CodegenError,CodegenReport}.scala` | hx4s-codegen `hx4s/codegen/` | 3 |
| `codegen/.../framework/OutputPath.scala`, `OutputWriter.scala` | hx4s-codegen `hx4s/codegen/{OutputPath,OutputWriter}.scala` | 3 |
| `config` and `main` of `codegen/.../GenerateAppFiles.scala` | Stay in 3ch (a thin main calling `framework`). Replaced by sbt-hx4s's `hx4sGenerate` | 4 |
| `codegen/src/test/.../framework/{AnnotationScannerSuite,AppFileRendererSuite,OutputWriterSuite,TestConfig}.scala` | Tests of hx4s-codegen | 3 |
| `codegen/src/test/.../GenerateAppFilesSuite.scala` | Tests using 3ch's settings. Generic parts are moved into the `framework` tests | 3 |
| `bbs/.../external/honox/Island.scala` | hx4s-react `react/src/main/scala/hx4s/react/Island.scala` | 2 → 3 |
| `bbs/.../external/hono/HonoContext.scala`, `HonoContextFacade.scala` (except `RenderProps`) | hx4s-react `hx4s/hono/Context.scala` (Env as a type parameter). The 3ch-specific `RenderProps` and the `WorkerEnv` alias stay | 2 → 3 |
| `renderIslandTest` in `bbs/src/test/.../IslandTestExports.scala` | Test exports of hx4s-react | 2 → 3 |
| `scalaProject`, the alias, `honox()`'s React settings and client settings in `vite.config.ts` | hx4s-vite `vite/src/index.ts` | 2 → 3 |
| The `scalajs:client.js` / `hono/jsx` parts of `bbs/test-stubs/cloudflare-workers-loader.mjs`, and `register.mjs` | hx4s-vite `vite/node-loader.mjs` / `vite/node-register.mjs` | 2 → 3 |
| `tests/islands-bridge.test.js` | Tests of hx4s-vite (targeting `examples/minimal`) | 2 → 3 |
| `tests/islands-hydration.test.js` | Tests of hx4s-react / hx4s-vite | 2 → 3 |
| The `codegen` project and the `sourceGenerators` wiring of `generateIslandComponents` in `build.sbt` | sbt-hx4s (`hx4sGenerate` and `sourceGenerators`) | 3 |
| The `Test / jsEnv` mechanism in `build.sbt` | A helper in sbt-hx4s (P1) | 3 or later |
| The content of `app/client.ts` | `examples/minimal/app/client.ts` (copied as a template; 3ch keeps its own) | 3 |

## 4. What stays in 3ch

| Target | Reason |
| --- | --- |
| The declarations in `bbs.Main` / `bbsclient.Main`, `bbs-client/islands/*`, `bbs/components/*`, `RendererShell` | The bulletin board's features themselves |
| `app/server.ts`, `app/routes/_renderer.tsx`, hand-written routes, `app/renderer.d.ts`, `app/scalajs-server.d.ts` | 3ch's middleware, auth API, LegacyDispatch, renderer props |
| `RouteTable.publicPatterns` | v5-compatible public URLs |
| Generation of DO TS types and RPC stubs (`DurableObjectSources`, `GenerateDurableObject*`) and the `generateDurableObjectStubs` task | Cloudflare-specific. A future `hx4s-cloudflare` candidate ([ADR 0005](adr/0005-core-independent-of-cloudflare.md)) |
| `external/cloudflare/Bindings.scala`, `external/web/WebFacade.scala` | Runtime-specific / 3ch's hand-written facade |
| Tailwind, `@hono/vite-build/cloudflare-workers` and the DO `entryContentAfterHooks` in `vite.config.ts` | The choice of CSS and runtime belongs to the user |
| `bbs/test-stubs/cloudflare-workers-stub.mjs` and its replacement | Cloudflare-specific |
| Shift_JIS (`SjisEncoding`), `createSjisResponse` | Compatibility with dedicated browsers |
| `tests/{board-islands,board-top-hydration,client-bundle,worker-bundle}.test.js`, integration tests | Bulletin board UI, DOs, the KB-27 marker strings |
