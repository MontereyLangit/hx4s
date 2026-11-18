# Compatibility

## 1. 3ch's dependency versions (as of 2026-11-16)

### 1.1 Scala / sbt

Sources: `build.sbt`, `project/plugins.sbt`, `project/build.properties`. Transitive dependencies were checked in the POMs in the coursier cache.

| Target | Version | Source / notes |
| --- | --- | --- |
| sbt | 1.13.0 | `project/build.properties` |
| Scala | 3.3.8 (LTS) | `scala3Version` in `build.sbt` |
| Scala.js (sbt-scalajs) | 1.22.0 | `project/plugins.sbt` |
| sbt-scalajs-crossproject | 1.3.2 | Same as above |
| scalajs-react `core` | 4.0.0 | `libraryDependencies` of `bbs` and `bbsClient`. According to the POM, built with Scala 3.3.7 / Scala.js 1.20.1 |
| scalajs-dom | 2.8.1 (transitive) | The POM of scalajs-react 4.0.0. The final resolved version is to be confirmed in Phase 1 with `sbt bbsClient/dependencyTree` or similar (sbt was not run in this investigation) |
| scalameta | 4.17.4 (the `_2.13` artifact used with `CrossVersion.for3Use2_13`) | `libraryDependencies` of `codegen` |
| munit | 1.0.0 | `commonSettings` |
| JDK | Not pinned | `docs/client-build-review.md` records Java 25's Unsafe warning. The CI version is undecided |

ScalablyTyped is not used (the comment "hand-written @js.native facades only" in `project/plugins.sbt`).

### 1.2 npm

Sources: `package.json` (ranges) and `package-lock.json` (lockfileVersion 3, resolved versions).

| Package | `package.json` | Resolved | License |
| --- | --- | --- | --- |
| hono | 4.13.11 | 4.13.11 | MIT |
| honox | ^0.1.62 | 0.1.62 | MIT |
| @hono/react-renderer | 1.0.1 | 1.0.1 | MIT |
| @hono/vite-build | 1.11.1 | 1.11.1 | MIT |
| @hono/vite-dev-server | (honox's dependency ^0.25.1) | 0.25.3 | MIT |
| vite | 8.3.3 | 8.3.3 | MIT |
| rolldown | (vite's dependency) | 1.2.16 | MIT |
| esbuild | (transitive) | 0.28.2 | MIT |
| react / react-dom | 19.2.8 | 19.2.8 | MIT |
| @scala-js/vite-plugin-scalajs | 1.1.0 | 1.1.0 | Apache-2.0 |
| @tailwindcss/vite / tailwindcss | 4.3.3 | 4.3.3 | MIT |
| typescript | ^5 | 5.9.3 | Apache-2.0 |
| wrangler | ^4.146.0 | 4.158.0 | MIT OR Apache-2.0 |
| workerd | (wrangler's dependency) | 1.20261104.1 | Apache-2.0 |
| jsdom | ^29.1.1 | 29.1.1 | MIT |
| @types/react / @types/react-dom | ^19.3.0 | 19.3.0 (react) | MIT |
| @types/node | ^22 | 22.20.4 | MIT |

Node in the investigation environment was v22.18.0. 3ch's `package.json` has no `engines`.

### 1.3 Caveats

- **vite-plugin-scalajs and Vite 8**: `@scala-js/vite-plugin-scalajs` 1.1.0 has `vite` as a `"4.1.4 - 7"` **dependency**. 3ch makes it use Vite 8
  with `"vite": "$vite"` in `overrides`. Until upstream supports Vite 8, hx4s-vite must either ask users for the same override
  or declare compatibility on the hx4s-vite side.
- **Rolldown and `sequential`**: Vite 8 uses Rolldown. `{ sequential: true, handler }` for `buildStart` is originally a Rollup feature,
  and Rolldown's type definitions (`rolldown/dist/shared/define-config-*.d.mts`) also have `sequential`. That the real 3ch build is serialized is only checked
  by `tests/islands-bridge.test.js` looking at the setting; the actual start order was not measured.
- **The honox range**: `^0.1.62` is a 0.x caret, so `>=0.1.62 <0.2.0`. Patch updates may change internal APIs ([architecture.md](architecture.md) chapter 4).
  hx4s verifies against an exact honox version and recommends that users lock it too.
- **scalajs-react and the Scala version**: scalajs-react 4.0.0 is built with Scala 3.3.7, and 3ch uses it with 3.3.8. Within the Scala 3 LTS series, this isn't expected to be a problem.
  hx4s-react is also built with Scala 3.3 LTS so that it can be used from newer Scala 3 versions (Scala 3's TASTy is backward compatible; newer compilers can read older libraries).

## 2. HonoX being an alpha

HonoX's README describes itself as an alpha (0.1.x). hx4s depends on HonoX's internal APIs in 12 places (H1–H12 in chapter 4 of [architecture.md](architecture.md);
H11's `honox/server/server` and `honox/server/utils/file`, and H12's template restoration were added in the Phase 3 review).
In particular, H1 (the props shape of `HonoXIsland`), H3 (the format of `component-name`), H4 (string substitution of `hono/jsx`) and H10 (handling of nesting) are the kind where
changes on the HonoX side silently break hx4s's SSR (no hydration; it stays static HTML).

Countermeasures:

1. **Gather the dependencies in one place**: H1 is confined to a `private` facade in `hx4s-react`, H3 and H5 to constants in `hx4s-codegen`, and H4 to the default settings of `hx4s-vite`.
2. **Declare narrow version ranges**: each hx4s release states the verified honox version (e.g. `0.1.62`). The peerDependencies of `hx4s-vite` list only the verified range.
3. **Run compatibility tests** (chapter 3) in hx4s's CI.
4. **Relationship with upstream**: changes meaningful to non-Scala users too (making `HonoXIsland` a public API, documenting the mapping between `islandDir` and `component-name`, etc.)
   are proposed to HonoX through issues / PRs. Whether they will be accepted is unknown, so hx4s's design doesn't assume they will.

## 3. Compatibility test policy

The existing tests in 3ch's `tests/` are the foundation. They share running the real HonoX with Vite, but **what each test checks differs**. It is broken down as follows (reference commit `875a47b`).

| Existing test | What it actually checks | What it doesn't check |
| --- | --- | --- |
| `tests/islands-bridge.test.js` (1st) | Reads `vite.config.ts` and checks the settings of the `scalajs:client.js` alias and `buildStart.sequential` of the two Scala.js plugins | The actual sbt start order |
| `tests/islands-bridge.test.js` (2nd) | Puts a **JS stand-in export** (`Probe`) in a fixture, SSR-builds with Vite, and checks the HTML string of `renderToString`. `<honox-island>`, `component-name`, `data-serialized-props`, the difference from rendering `rawElement` directly. As the comment at the top of the file says, it isn't a Scala compiler test | Hydration on jsdom. The real Scala.js link output |
| `tests/islands-hydration.test.js` | SSRs with **the actually linked Scala bridge** (`renderIslandTest` in `bbs-test-fastopt/island-test.js`), and **hydrates on jsdom** JS fixture components with `createClient` from `honox/client` (three cases: simple, nested, element props). Number of boundaries, reuse of the SSR DOM, no hydration errors, clicks change state | Real client islands (Scala.js). Typed props |
| `tests/board-top-hydration.test.js` | Hydrates the real SSR HTML of the board top (the test export of `bbs`) on jsdom with `createClient`, using the linked client | The generic parts of hx4s (it depends on 3ch's screens) |
| `tests/board-islands.test.js` | Checks the behavior of linked client islands on jsdom (some include individual `hydrateRoot` calls) | HonoX's discovery and SSR wrapping |
| `tests/client-bundle.test.js` | Scans the JS in `dist/static/` and checks that there are no server-only strings and that islands are included (requires `dist/`) | Module graph and dependency direction (string checks only; [module-boundaries.md](module-boundaries.md) chapter 2) |

hx4s's compatibility tests reorganize these as follows. Compile verification of typed props (arguments other than `P` are compile errors) isn't in any test, so it is **separately mandatory**.

| Test | What it checks | Corresponding dependencies |
| --- | --- | --- |
| Island SSR | `<honox-island>`, `component-name="/app/islands/x.tsx"` and `data-serialized-props` are output. They aren't when `rawElement` is rendered directly (the scope of the current 2nd `islands-bridge` test) | H1, H2, H3, H4 |
| Hydration | In the three cases simple, nested and element props: number of boundaries, no hydration errors, reuse of the SSR DOM, clicks change state (the scope of the current `islands-hydration`; hydration with real Scala.js client islands is the scope of `board-top-hydration`) | H2, H5, H6, H10 |
| Props round trip | `"<&>`, non-ASCII, lone surrogates, `undefined` keys and empty arrays come back as expected through `JSON.parse`. With non-finite `Double`s, cyclic references and React elements inside nested objects, SSR fails or hydration mismatches, and the behavior in that case is as specified ([rendering-model.md](rendering-model.md) 2.3) | H2 |
| Typed props (compile) | Passing something other than `P` to `IslandComponents.Xxx(props: P)` is a compile error. If the typed export helper is in, a client export not matching `P` is a compile error too ([ADR 0003](adr/0003-typed-island-props.md)). Written as Scala compiler tests | — |
| Version skew | With both old HTML + new client and new HTML + old client, the client's `validate` detects the mismatch, logs `console.error`, and the SSR markup remains ([rendering-model.md](rendering-model.md) 2.4) | H2 |
| Island file names | Allowed names (`[A-Za-z0-9-]+\.tsx`) go through SSR → discovery → hydration. `foo_bar.tsx`, `foo.bar.tsx` and the like are rejected at generation time ([rendering-model.md](rendering-model.md) 1.4) | H5 |
| Script embedding (future) | Only when putting props in `<script type="application/json">` is introduced: round-tripping values containing `</script><script>…`, and that injected scripts don't run ([rendering-model.md](rendering-model.md) 1.2) | — |
| Routes | GET / POST of generated routes are registered by `createApp`. `HEAD` is rejected at generation time. File names starting with `_` are rejected | H7, H8 |
| URL conversion | hx4s-codegen's equivalent of `filePathToPath` matches HonoX's implementation (the same input table goes through both). Implemented: `modules/codegen/src/test/resources/route-paths.tsv` goes through `RoutePathSuite` and `tests/route-paths.test.js` (the paths HonoX's `createApp` registers) | H7, H11 |
| Build | In both modes of `hx4s-vite`, no server modules end up in the client's transitive dependencies (sbt dependency graph) or Vite's module graph. The sbt dependency graph is implemented with `hx4sCheckDependencies` (scripted `dependencies`); Vite's module graph is not. String checks that no server-side marker strings end up in the client output are a supplement (a generalization of 3ch's KB-27 check; [module-boundaries.md](module-boundaries.md) chapter 2) | H9 |
| Output | No external writes or deletions through symlinks / junctions ([build-integration.md](build-integration.md) 3.3). Rerunning after failure injection converges | — |

What the tests run against:

- **Pinned versions**: the combination of honox / hono / vite / react verified for the hx4s release. Required in CI.
- **Latest versions**: run with `honox@latest` and `vite@latest` periodically (weekly, etc.) without failing the whole CI. Open an issue when it breaks.
- **examples/minimal**: much of the above is checked by actually building `examples/minimal`. This doubles as the check that it doesn't depend on 3ch ([extraction-plan.md](extraction-plan.md)).

## 4. Licenses

### 4.1 Licenses of the existing code and dependencies

| Target | License |
| --- | --- |
| 3ch (3CH) | The repository has no LICENSE file. Whether and how it is published is decided by the rights holder. 3ch's `build.sbt` says it "follows" the reference implementation `ch3s-migration`; if code moved to hx4s includes parts from that repository, its rights also need to be confirmed (U-21) |
| Scala 3 / Scala.js / scalajs-react / munit / vite-plugin-scalajs | Apache-2.0 |
| scalajs-dom | MIT |
| scalameta | BSD (as written in the POM; understood to be BSD-3-Clause) |
| hono / honox / React / Vite / Rolldown | MIT |

hx4s doesn't copy HonoX's code (it only depends on it). Parts that rewrite HonoX's internal rules in Scala (the file name regex, the `filePathToPath` conversion)
are reimplementations of the specification, not copies of the code. Sources are cited in `docs/`, not in comments.

### 4.2 MIT vs. Apache-2.0

| Aspect | MIT | Apache-2.0 |
| --- | --- | --- |
| Length and readability of the text | Short | Long |
| Patent grant | Not explicit | An explicit patent grant, with a clause terminating it upon litigation |
| Alignment with dependencies | Same as Hono / HonoX / React / Vite | Same as Scala / Scala.js / scalajs-react / vite-plugin-scalajs |
| Obligations on redistribution | Keep the copyright notice and the permission text | The same, plus keeping the NOTICE file and stating changes |
| Combining with other licenses | Almost no restrictions | Incompatible with GPLv2 (compatible with GPLv3) |
| Accepting contributions | CLA / DCO decided separately | Section 5 makes contributions subject to the same terms |

### 4.3 Proposal

**Apache-2.0** (decided 2026-11-16). Reasons:

- hx4s's users are in the Scala.js ecosystem. Scala, Scala.js, scalajs-react and vite-plugin-scalajs are all Apache-2.0, so it lines up.
- The patent grant is explicit, making adoption decisions easier for corporate users.
- Using MIT dependencies (Hono / HonoX / React) from an Apache-2.0 project is not a problem.

The advantages of MIT would be easier alignment of licenses for proposals (PRs) to HonoX, and its short text.
Either way, the rights holders of the code moved from 3ch need to agree to publishing it under that license. Confirming rights to the moved code remains open question U-21.
