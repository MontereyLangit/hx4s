# hx4s

English | [日本語](ja/README.md)

hx4s is a meta-framework for using [HonoX](https://github.com/honojs/honox) and React from Scala.js. **It is not a re-implementation of HonoX.**
Routing, middleware, renderers, and island discovery and hydration stay in HonoX. hx4s takes on only two jobs:

- Generate files that follow HonoX's conventions, plus a Scala API for SSR, from Scala declarations (`@route`, `@islands[P]`)
- Connect the Scala.js build to Vite and HonoX

## Status

0.1 is in development and **not yet published to Maven Central or npm**. To use it, clone the hx4s repository and publish it locally
([docs/installation.md](docs/installation.md)). The API and settings may change during 0.x.

## Features

- **Routes written in Scala**: a function annotated with `@route("items/[id].ts", "POST")` becomes a HonoX file route. URL mapping follows HonoX's conventions as-is
- **Typed islands**: the `P` (props trait) of `@islands[P]("counter.tsx")` is checked at compile time against both the server call `IslandComponents.CounterJs(props)` and the client export
- **Props validation before hydration**: when the HTML and the client are from different builds and the props don't match, the island is not hydrated and the SSR HTML stays
- **Server/client separation**: the direction of dependencies is checked against the sbt dependency graph (`hx4sCheckDependencies`)
- **Safe generation**: never overwrites hand-written files, writes only after all checks pass, does not follow links
- Runtime-agnostic: a Cloudflare Workers example is included, but the hx4s modules do not depend on Cloudflare

## Minimal example

Props are a trait shared by server and client, together with rules that check the values the client receives.

```scala
// shared
trait CounterProps extends js.Object {
  val label: String
  val start: Int
}

object CounterProps {
  val fields: List[JsField] = List(required("label", JsKind.Str), required("start", JsKind.Int))
}
```

The client exports the island as an `IslandExport[P]` and publishes it to `app/islands` with `@islands[P]`.

```scala
// client
@islands[app.shared.CounterProps]("counter.tsx")
@JSExportTopLevel("CounterJs", "client")
val counter: IslandExport[CounterProps] = IslandExport("CounterJs", CounterProps.fields)(props => Counter(props))
```

A server handler becomes a HonoX route with `@route` and places the island through the generated `IslandComponents`.

```scala
// server
@route("index.ts")
@JSExportTopLevel("indexPage", "server")
def indexPage(c: AppContext): js.Promise[Response] = {
  val page = <.main(
    <.h1("Hello hx4s"),
    IslandComponents.CounterJs(new CounterProps { val label = "clicks"; val start = 0 })
  )
  c.renderPromise(page.rawElement, new RenderProps { val title = "Hello" })
}
```

`sbt server/hx4sGenerate` generates `app/routes/index.ts` and `app/islands/counter.tsx`.
See [docs/getting-started.md](docs/getting-started.md) for building and setting up a project, and `examples/minimal` for the full picture.

## Implemented and not implemented

| Status | Content |
| --- | --- |
| Implemented | `@route` (GET / POST / PUT / DELETE / PATCH / OPTIONS), `@islands[P]`, generation of `IslandComponents`, props validation with `IslandExport[P]` and `JsShape`, validation before hydration (`hx4s-vite/client`), a facade for Hono's `Context` |
| Implemented | sbt-hx4s (`hx4sGenerate`, dependency direction check, client-less setups), the `hx4s()` Vite plugin, a Node loader, local publish scripts, `examples/minimal` and `examples/api-only` |
| Not implemented | Typed routes, a Client Router (router for SPAs), the `ALL` method, an npm CLI, project templates, HMR in the dev server, a typed API client, OpenAPI, publishing to Maven Central / npm |
| Not verified | Runtimes other than Cloudflare Workers (Node, Deno, Bun, etc.), production deployment |

Plans for the unimplemented features are in [docs/hx4s/roadmap.md](docs/hx4s/roadmap.md).

## Supported versions

The combination verified by the examples and CI. Nothing else has been verified.

| Target | Version |
| --- | --- |
| Scala | 3.3.8 (LTS) |
| Scala.js (sbt-scalajs) | 1.22.0 |
| sbt | 1.13.0 (sbt-hx4s is an sbt 1.x plugin on Scala 2.12) |
| scalajs-react | 4.0.0 |
| scalajs-dom | 2.8.1 |
| HonoX / Hono | 0.1.62 / 4.13.18 |
| Vite / `@scala-js/vite-plugin-scalajs` | 8.3.3 / 1.1.0 |
| React | 19.2.8 |
| JDK | 17 or later (CI uses 21) |
| Node.js | 22.12 or later (`engines` of `hx4s-vite`; CI uses 22) |

HonoX is a 0.1.x alpha, and hx4s depends on some of HonoX's internal behavior. Pin `honox` to the verified version ([docs/hx4s/compatibility.md](docs/hx4s/compatibility.md)).

## Documentation

The user guides start at [docs/README.md](docs/README.md). A Japanese translation is in [ja/](ja/README.md).

| Document | Content |
| --- | --- |
| [Getting started](docs/getting-started.md) | Running `examples/minimal` and setting up your own project |
| [Installation](docs/installation.md) | Coordinates, local publishing, pinning the version and commit |
| [Routing](docs/routing.md) | `@route` and `Context` |
| [Islands](docs/islands.md) | `@islands[P]`, props types, `IslandExport` |
| [SSR and hydration](docs/ssr-hydration.md) | Rendering flow, version skew, testing |
| [SPA](docs/spa.md) / [API](docs/api.md) | Current state of SPA and API setups |
| [Build configuration](docs/build-configuration.md) | sbt keys, `hx4s()` options, codegen arguments |
| [Deployment](docs/deployment.md) | Cloudflare Workers |
| [Migration](docs/migration.md) | Migrating from a hand-written Scala.js × HonoX setup |
| [Examples](docs/examples.md) / [Testing](docs/testing.md) / [Release](docs/release.md) | |

Design and history documents are in [docs/hx4s/](docs/hx4s/README.md). See [CONTRIBUTING.md](CONTRIBUTING.md) for how to contribute and [CHANGELOG.md](CHANGELOG.md) for the change history.

## Build and test

```bash
npm ci                        # workspace dependencies (hx4s-vite and Node tests)
sbt test                      # munit for core / react / codegen
sbt sbtHx4s/scripted          # scripted tests for sbt-hx4s
npm test                      # hx4s-vite build, island SSR and hydration, independence check
npm run typecheck             # type-check hx4s-vite
```

See [docs/getting-started.md](docs/getting-started.md) for running the examples and [docs/testing.md](docs/testing.md) for what the tests cover.

## License

[Apache License 2.0](LICENSE)
