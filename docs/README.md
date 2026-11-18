# hx4s documentation

English | [日本語](../ja/docs/README.md)

hx4s is a meta-framework for using [HonoX](https://github.com/honojs/honox) and React from Scala.js (0.1 in development, not published). See the [README](../README.md) for an overview.

## User guides

| Document | Content |
| --- | --- |
| [Getting started](getting-started.md) | Running `examples/minimal`. Setting up your own project (sbt, npm, Vite, Scala code) |
| [Installation](installation.md) | Coordinates, local publishing, pinning the version and commit |
| [Routing](routing.md) | `@route`, file paths and URLs, what codegen rejects, `Context` |
| [Islands](islands.md) | `@islands[P]`, the props trait and supported types, `IslandExport`, `IslandComponents` |
| [SSR and hydration](ssr-hydration.md) | Rendering flow, validation before hydration, version skew, keeping server code out of the client, testing |
| [SPA](spa.md) | Current state of SPAs (no Client Router yet) and setups possible today |
| [API](api.md) | API-only routes, reading requests, setups without React |
| [Build configuration](build-configuration.md) | sbt keys, `hx4s()` options, `CodegenMain` arguments, handling generated files |
| [Deployment](deployment.md) | Cloudflare Workers. Other runtimes are not verified |
| [Migration](migration.md) | Migrating from a hand-written Scala.js × HonoX setup |
| [Examples](examples.md) | `examples/minimal` (SSR + Islands) and `examples/api-only` (API only) |
| [Testing](testing.md) | How hx4s's own tests are organized and run |
| [Release](release.md) | Versioning and verification steps. Publishing steps are not yet defined |

Related documents: [CONTRIBUTING](../CONTRIBUTING.md), [CHANGELOG](../CHANGELOG.md).

## Design and history

[hx4s/](hx4s/README.md) holds the design and history documents. The parts users need while reading the guides are linked from the guides.

| Document | Content |
| --- | --- |
| [architecture](hx4s/architecture.md) | Overall picture, division of responsibilities with HonoX, places that depend on HonoX's internal APIs |
| [module-boundaries](hx4s/module-boundaries.md) | Module boundaries and dependency direction, public and internal APIs |
| [rendering-model](hx4s/rendering-model.md) | Where each rendering mode stands, hydration boundaries, props serialization, version skew |
| [routing-design](hx4s/routing-design.md) | Design of `@route`, and plans for typed routes and a Client Router |
| [build-integration](hx4s/build-integration.md) | Design of the sbt and Vite integration |
| [codegen](hx4s/codegen.md) | Codegen declaration rules, output rules, error format, settings |
| [compatibility](hx4s/compatibility.md) | Dependency versions, policy for tracking the HonoX alpha, licenses |
| [local-dependency](hx4s/local-dependency.md) | Local publish records and how to pin them in your project |
| [roadmap](hx4s/roadmap.md) | Feature status (implemented, planned) and open questions |
| [adr/](hx4s/README.md) | Records of design decisions (one decision per file) |
| [reviews/](hx4s/README.md) | Records of verification and reviews |
