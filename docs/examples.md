# Examples

## minimal

`examples/minimal` is a minimal SSR + Islands app. It runs on Cloudflare Workers (wrangler), and resolves hx4s only from locally published artifacts (it doesn't reference the source tree).
See [getting-started.md](getting-started.md) for how to run it.

### Pages and APIs

| URL | Content | Features used |
| --- | --- | --- |
| `/` | A static SSR page with two counter islands (with different props) and one list island with arrays, nesting and optional keys | `IslandComponents`, `IslandExport`, `JsShape` |
| `/items/:id` | A dynamic route. Unknown IDs return 404. Has a form for renaming | `@route("items/[id]")`, `c.req.param`, `c.status` |
| `POST /items/:id` | Validates the form values and renders the saved name or an error (nothing is saved) | `@route("items/[id]", "POST")`, `c.req.formData()` |
| `/api/items` | JSON of the items. Repeating `?tag=` narrows to items that have all those tags | `c.json`, `c.req.queries` |

### Role of each file

| Path | Role |
| --- | --- |
| `build.sbt` / `project/plugins.sbt` | Three projects: `shared`, `server` and `client`. `hx4sClientProject`, and the server's `Test / jsEnv` (the Node loader and linking the client first). The plugin version is read from the environment variable `HX4S_VERSION` or `target/local/hx4s-version.txt` |
| `shared/.../Props.scala` | Island props traits and `JsField` validation rules (`CounterProps`, `ItemListProps`, etc.) |
| `server/.../Main.scala` | `@route` handlers and `renderShell` (the HTML shell) |
| `server/.../Pages.scala` | Page SSR. Islands are placed with the generated `IslandComponents` |
| `server/.../Catalog.scala` | Sample data and validation (nothing is saved). Names with symbols are used to check escaping |
| `server/.../Shell.scala` | The whole HTML |
| `client/.../Main.scala` | `IslandExport` exports annotated with `@islands[P]` |
| `client/.../Counter.scala`, `ItemList.scala` | Island components (`ScalaFnComponent` and hooks) |
| `app/server.ts`, `app/client.ts` | HonoX entry points. `client.ts` calls `islandPropsProblem` |
| `app/routes/_renderer.tsx` | The renderer that calls `renderShell` (hand-written) |
| `app/routes/**/*.ts`, `app/islands/*.tsx` | Generated files (with the generated marker at the top; committed) |
| `app/renderer.d.ts`, `app/scalajs-server.d.ts` | Type declarations (hand-written) |
| `vite.config.ts` | `hx4s()` and the Cloudflare Workers adapter |
| `wrangler.jsonc` | Worker and static serving settings (`nodejs_compat`) |
| `package.json` / `package-lock.json` | Dependencies. `hx4s-vite` is not listed; the tgz is installed with `--no-save` |

### What is verified

| Test | Content |
| --- | --- |
| `server/src/test/.../TypedPropsSuite.scala` (`sbt test`) | That `IslandComponents` accepts only the `P` of `@islands[P]` (checked via compile errors), SSR boundaries and props, unit tests for data and validation |
| `tests/build-output.test.js` | Calls the built Worker on Node and checks the SSR HTML, dynamic routes and 404, HEAD, POST validation, the JSON API, and that the client bundle has no server-only strings |
| `tests/hydration.test.js` | Runs `app/client.ts` on jsdom and checks hydration, clicks, and that islands with mismatched props stay as SSR |
| `tests/e2e.test.js` | With `wrangler dev` and Playwright (Chromium), checks that hydration has no errors and clicks change state, and the scope of static serving |

`npm test` runs everything (`HX4S_SKIP_E2E=1` skips E2E). CI runs everything from the local publish to `npm test` on Ubuntu and Windows (`.github/workflows/ci.yml`; api-only too).

## api-only

`examples/api-only` is a JSON-API-only app. It has no client (islands) and doesn't depend on `react`, `react-dom` or `@hono/react-renderer`.
`vite.config.ts` uses `hx4s({ mode, client: false, … })` and builds the Worker with a single `vite build` ([api.md](api.md)). hx4s is resolved only from locally published artifacts (same as minimal).

| URL | Content | Features used |
| --- | --- | --- |
| `GET /api/notes` | JSON list of notes | `c.json` |
| `POST /api/notes` | Checks the JSON body with `JsShape` and value rules, and returns 201 or 400 (nothing is saved) | `c.req.json()`, `JsShape.validate` |
| `GET /api/notes/:id` | JSON of one note. Unknown IDs return Not Found | `c.notFound()` |
| `DELETE /api/notes/:id` | 204 without a body. Unknown IDs return 404 | `c.body(null, 204)` |

| Path | Role |
| --- | --- |
| `build.sbt` / `project/plugins.sbt` | A single `server` project (`hx4sClientProject` is not set) |
| `server/.../Main.scala`, `Notes.scala` | `@route` handlers, sample data and validation |
| `app/server.ts` | HonoX entry point (just `createApp()`; no renderer) |
| `app/routes/**/*.ts` | Generated files (committed) |
| `vite.config.ts` | `hx4s({ client: false })` and the Cloudflare Workers adapter |
| `tests/api.test.js` | Calls the built Worker on Node and checks each API's response, and that React is not in the Worker bundle |

```bash
node scripts/publish-local.mjs
node scripts/setup-example.mjs api-only
cd examples/api-only && npm run build && npm test
```

`wrangler` is not installed, and starting it on Workers (`wrangler dev`) has not been verified. The tests go as far as calling the Worker's `fetch` on Node.

## What the examples don't cover

The following are not included in the examples, and verification in these documents is limited.

- Making the whole page one island (verified only as far as the prototype in [spa.md](spa.md))
- Islands with element props, and examples of nested islands written with `IslandComponents`
- Runtimes other than Cloudflare
