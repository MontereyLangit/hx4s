# ADR 0002: Initial module structure

- Status: Accepted (2026-11-16, Phase 0)
- Updated (2026-11-16, Phase 3): `hx4s-core` became a Full crossProject instead of Pure, to put props validation `hx4s.props.JsShape` on the JS side ([ADR 0007](0007-props-validation-and-typed-export.md)).
  The JVM side has only the annotations
- Related: [architecture.md](../architecture.md) 3.2, [module-boundaries.md](../module-boundaries.md)

## Context

3ch's integration layer is scattered across `common` (annotations and templates), `codegen` (generators), `bbs` (the island bridge and Hono facade) and `vite.config.ts` (build settings).
The units to extract need to be decided. Splitting finely increases version combinations and published artifacts at the P0 stage, and also increases user settings.

## Decision

| Module | Kind | Content |
| --- | --- | --- |
| `hx4s-core` | crossProject (Pure, JVM + JS) | Renderer-independent declarations such as `@route` / `@islands[P]` |
| `hx4s-react` | Scala.js | The HonoX island bridge (`React.createElement(HonoXIsland, …)`), a generic Hono Context facade (Env as a type parameter), helpers for typed props |
| `hx4s-codegen` | JVM (Scala 3, scalameta) | Annotation analysis, generating route / island TS(X) and the Scala `IslandComponents`, safe output. Settings are a case class; it also has a CLI main |
| `sbt-hx4s` | sbt plugin (Scala 2.12) | Setting keys and `hx4sGenerate`. Codegen is resolved through a dedicated configuration and run in a separate class loader or a fork |
| `hx4s-vite` | npm | Wraps `@scala-js/vite-plugin-scalajs` and bundles the server / client registration, the `scalajs:client.js` alias, honox's React settings and the client build settings |
| `examples/minimal` | Example | A minimal SSR + Islands app that doesn't depend on 3ch. A required independence test |

The honox and react integrations stay in a single `hx4s-react` for now. When another renderer is added (e.g. using hono/jsx from Scala), split into `hx4s-honox` and `hx4s-react`.
`hx4s-routing` (typed URLs, Client Router), a CLI and templates are P1.

## Rejected alternatives

| Alternative | Reason |
| --- | --- |
| Put everything into one artifact | JVM codegen and Scala.js runtime code can't be in the same artifact. The sbt plugin must also be separate |
| Split `hx4s-honox` (bridge) and `hx4s-react` (React helpers) from the start | Right now React is the only renderer, and the two are always used together. Splitting only adds dependency lines for users |
| Put the annotations in `hx4s-react` and skip `hx4s-core` | The annotations may be referenced from the JVM (codegen tests, a future JVM server). In a Scala.js-only module they can't be used from the JVM |
| Put codegen inside the sbt plugin | sbt 1.x is Scala 2.12. The code using scalameta in Scala 3 and the parsing of users' Scala 3 syntax would have to be rewritten for 2.12. It also could no longer be used standalone as a CLI |
| Include `hx4s-cloudflare` in P0 | Deferred to P2, given the policy of keeping Core independent of the runtime ([ADR 0005](0005-core-independent-of-cloudflare.md)) and that verifying it requires a Cloudflare environment |

## Consequences

- The user's Scala.js projects depend on `hx4s-react` (and transitively `hx4s-core`), and the build includes `sbt-hx4s` and `hx4s-vite`.
- `hx4s-react` depends on scalajs-react's `core`. Users who don't use scalajs-react are not considered (P0).
- Adding or splitting modules changes users' dependency lines, so it is done in a minor version.
