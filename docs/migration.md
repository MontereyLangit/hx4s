# Migrating from an existing setup

This describes general steps for moving a project that wires Scala.js and HonoX together by hand to hx4s.
A typical starting point looks like this:

- The Scala.js server exports handlers with `@JSExportTopLevel("name", "server")`, and thin TS files in `app/routes/**/*.ts` import them from `scalajs:server.js` (hand-written or produced by a home-made generator)
- It has its own facade for `HonoXIsland`, which marks island boundaries, and builds props with `js.Object` / `js.Dynamic`
- `vite.config.ts` registers the Scala.js plugins, aliases `scalajs:client.js` and sets the output names of the client build
- It has its own facade for Hono's Context

Migrate in stages, and make the build and tests pass at each stage.

## 1. Dependencies and sbt setup

1. Publish hx4s locally and add `sbt-hx4s` and `hx4s-react` ([installation.md](installation.md))
2. Split into three projects: server, client and shared. The server enables `Hx4sPlugin` and sets `hx4sClientProject := client`
3. **Fix the dependency direction**: the client doesn't depend on the server. The server doesn't depend on the client either (island code goes through the virtual module `scalajs:client.js`).
   Put types used by both (props) in shared, which depends on neither. Violations make `hx4sCheckDependencies` fail and show the path
4. Name the projects `server` and `client`, or specify them with `server.projectID` / `client.projectID` of `hx4s()`

## 2. Replacing routes with `@route`

1. Add `@route("<path from app/routes>", "<method>")` to the handlers. Keep `@JSExportTopLevel` as-is (the moduleID is `"server"`)
2. **Delete** the hand-written TS wrappers, then run `sbt server/hx4sGenerate`. Files without the generated marker are not overwritten, so if they remain, generation stops with
   `refusing to overwrite a file without the generated marker`
3. Compare the generated `app/routes/**/*.ts` with the wrappers from before the migration. Check that the URLs haven't changed

The following points may be stricter than your previous generator.

| hx4s rule | Content |
| --- | --- |
| Rejects `HEAD` | HonoX's `createApp` doesn't register it. The GET handler also answers HEAD |
| Rejects file names HonoX doesn't pick up | A basename starting with `_` / `-` / `$`, `*.test.ts` / `*.spec.ts`, etc. |
| Rejects URL conflicts | Different files mapping to the same URL and method (`foo.ts` and `foo/index.ts`, `[a].ts` and `[b].ts`) |
| `@route` arguments are string literals | Variables and concatenation can't be used |
| `@route` is recognized by name | Import aliases can't be used |

### If you used an old generated marker

If your generator put a different line (banner) at the top, hx4s sees those files as hand-written and won't overwrite them. Choose one of the following:

- Set `hx4sBanner` and `hx4sGeneratedMarker` to the old values and replace the old generated files. Keep the settings (removing them makes files with the old marker count as hand-written again)
- Delete the old generated files once, then regenerate with the default banner

## 3. Islands and props

1. Export the client islands as `IslandExport[P]` and add `@islands[P]("<basename>.tsx")`. `P` is a fully qualified name. The basename is `[A-Za-z0-9-]+\.tsx`
2. Replace props built from `js.Dynamic` / `js.Object` with JS traits in shared. Write a list of `JsField`s together with each trait ([islands.md](islands.md))
3. Replace the server's own island calls with the generated `IslandComponents.<export name>(props)`. The method name is exactly the `@JSExportTopLevel` name
4. Your own `HonoXIsland` facade is replaced by `hx4s.react.Island`, which `IslandComponents` uses, so it is no longer needed

The types usable in props are limited (`String`, `Int`, `Double`, `Boolean`, `js.UndefOr`, `js.Array`, props traits). Places that passed `Option`, Scala collections, case classes or `Long` need conversion.
If there will be a period when old islands and new HTML coexist, make added fields `js.UndefOr` ([ssr-hydration.md](ssr-hydration.md)).

## 4. Context

Your own Context facade can be replaced with `hx4s.hono.Context[Env, R]`.

- `Env` is the type of `c.env`. Define the runtime's Bindings type (a trait) yourself and pass it
- `R` is the type of the renderer props passed to `c.render`
- **Request / Response / FormData are scalajs-dom types**. Code that received them as custom types or `js.Dynamic` needs to use the scalajs-dom types
- `c.executionCtx` throws if the runtime doesn't provide it, so use `executionCtxOption` if some calls don't provide one
- Return responses without a body, such as `204` / `304`, with `c.body(null, 204)`, and Not Found with `c.notFound()` ([api.md](api.md))
- Missing members (`parseBody`, `arrayBuffer`, etc.) are not in the facade. Keep your own facade, or add a `@js.native` trait

## 5. Vite and the client entry

1. Replace the Scala.js plugin registration, the `scalajs:client.js` alias and the client build output names in `vite.config.ts` with `hx4s({ mode, sbt: { cwd } })`
2. Keep the runtime adapter (`@hono/vite-build/...`) and its options by adding them to the array returned by `hx4s()`
3. Pass HonoX settings with `hx4s({ honox: { ... } })`. `islandComponents.reactApiImportSource` defaults to `"react"`
4. Add `"@scala-js/vite-plugin-scalajs": { "vite": "$vite" }` under `overrides` in `package.json`, and install `hx4s-vite`
5. Call `islandPropsProblem` in the hydrate function of `app/client.ts` ([getting-started.md](getting-started.md))
6. If you changed the client's output names, specify them with `client.output` ([build-configuration.md](build-configuration.md))

In server mode, `hx4s()` also links the client sbt project. For setups without a client (API only, SSR without islands), use `hx4s({ mode, client: false, ... })` and don't set `hx4sClientProject` on the sbt side ([api.md](api.md), [build-configuration.md](build-configuration.md), `examples/api-only`).

## 6. Tests on Node

If you run Scala.js tests on Node, you need a loader that resolves `scalajs:client.js` and `hono/jsx` inside HonoX. Switch to hx4s's loader (`hx4s-vite/node-register`).
The environment variable is `HX4S_CLIENT_JS_DIR` (output directory of the client's `fastLinkJS`). If you have a separate loader for runtime-specific specifiers (such as `cloudflare:workers`),
register it alongside `hx4s-vite/node-loader` ([ssr-hydration.md](ssr-hydration.md), [hx4s/build-integration.md](hx4s/build-integration.md) 2.3).

## 7. Checking the migration

- The generated route and island TS point to the same URLs and the same islands as before the migration (except for the banner and import aliases)
- Existing tests (SSR HTML, hydration, API responses) pass
- The client bundle contains no server-only code. The dependency direction check covers only the sbt graph, so also check the bundle's strings
- Commit the generated files, and check in CI that regenerating produces no diff (the CI of `examples/minimal` does the same)
