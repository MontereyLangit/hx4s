# API

Handlers that don't return HTML are also written with `@route`. There is no special API; return JSON and statuses with the response methods of `Context`.
A typed API client and OpenAPI output are **not implemented** ([hx4s/roadmap.md](hx4s/roadmap.md)).

The handlers below were either added to `examples/minimal` or exist in `examples/api-only`, and their responses were checked by calling the built Worker's `fetch`.

```scala
object Api {
  type ApiContext = Context[js.Object, RenderProps]
  ...
}
```

## Returning JSON

```scala
@route("api/items/[id]")
@JSExportTopLevel("itemGet", "server")
def itemGet(c: ApiContext): Response = {
  val id = c.req.param("id").getOrElse("")
  Catalog.find(id) match {
    case Some(item) => c.json(new ItemProps { val id = item.id; val name = item.name; val tags = item.tags.toJSArray })
    case None => c.json(js.Dynamic.literal(error = s"item $id not found"), 404)
  }
}
```

The `body` of `c.json(body, status)` is `js.Any`. You can pass `js.Dynamic.literal(...)`, JS traits and `js.Array`. Convert Scala case classes and `List`s to JS values first.
The response has `content-type: application/json`.

`api/items.ts` in `examples/minimal` reads a repeated query such as `?tag=fruit&tag=red` with `c.req.queries("tag")` and returns a JSON array.

## Reading the request

| What | How | Result |
| --- | --- | --- |
| Path parameter | `c.req.param("id")` | `js.UndefOr[String]` |
| Query | `c.req.query("q")` / `c.req.queries("tag")` | `js.UndefOr[String]` / `js.UndefOr[js.Array[String]]` |
| Header | `c.req.header("user-agent")` | `js.UndefOr[String]` |
| JSON body | `c.req.json()` | `js.Promise[js.Any]` |
| Form body | `c.req.formData()` | `js.Promise[FormData]` (scalajs-dom) |
| Text body | `c.req.text()` | `js.Promise[String]` |

Hono caches the body in the form first read (one of `text()`, `json()`, `formData()`), so it can be read any number of times in the same request, and in other forms too.
`text()` and `json()` are built from the same body string. If `formData()` is read first, a later `text()` returns a string re-encoded as multipart, which differs from the original body.
If you read the body of `c.req.raw` directly, it can no longer be read through `c.req` (and conversely, `raw` can't be read after reading through `c.req`).

Convert a Promise to a Future with `toFuture`, and back with `toJSPromise`.

```scala
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js.JSConverters.*

@route("api/echo", "POST")
@JSExportTopLevel("echoPost", "server")
def echoPost(c: ApiContext): js.Promise[Response] =
  c.req.json().toFuture.map(body => c.json(body, 201)).toJSPromise     // returns the body as-is with 201

@route("api/items/[id]", "PUT")
@JSExportTopLevel("itemPut", "server")
def itemPut(c: ApiContext): js.Promise[Response] =
  c.req.json().toFuture.map { body =>
    val name = body.asInstanceOf[js.Dynamic].name.asInstanceOf[js.UndefOr[String]].getOrElse("")
    Catalog.rename(name).fold(
      reason => c.json(js.Dynamic.literal(error = reason), 400),
      renamed => c.json(js.Dynamic.literal(name = renamed))
    )
  }.toJSPromise
```

The body's type is not validated. To check the shape of the received JSON, use `JsShape.validate[P](raw, fields)` ([islands.md](islands.md)).

## Status and headers

```scala
c.json(body, 404)                    // return with a status
c.status(404); c.renderPromise(…)    // set the status first, then render
c.header("cache-control", "no-store")
c.redirect("/", 302)
```

Return responses without a body (`204`, `304`) with `c.body(null, status)`. `c.text("", 204)` doesn't work: Hono's `c.text` creates a `Response` with a body even for an empty string,
and the Fetch API's `Response` doesn't allow a body for 204 / 304, so it throws and `500` is returned.

```scala
@route("api/notes/[id]", "DELETE")
@JSExportTopLevel("noteDelete", "server")
def noteDelete(c: ApiContext): Response = {
  val id = c.req.param("id").getOrElse("")
  if (Notes.find(id).isDefined) c.body(null, 204) else c.json(js.Dynamic.literal(error = s"note $id not found"), 404)
}
```

The `data` of `c.body(data, status, headers)` is a scalajs-dom `BodyInit` (string, `Blob`, `FormData`, etc.) or `null`, and `headers` is a `js.Dictionary[String]`.
No `content-type` is added, so set it in the headers when returning a body.

`c.notFound()` returns the app's Not Found response (with Hono's default, `404` and the body `404 Not Found`). Its return type is `Response | js.Promise[Response]`, so match the handler's return type to it.

```scala
@route("api/notes/[id]")
@JSExportTopLevel("noteGet", "server")
def noteGet(c: ApiContext): Response | js.Promise[Response] =
  Notes.find(c.req.param("id").getOrElse("")).fold(c.notFound())(note => c.json(toJs(note)))
```

These two are in `examples/api-only`.

## Error responses

hx4s doesn't define an error format for APIs. Build and return business rejections (validation failures, not found) yourself, like the `404` and `400` above.
If a handler throws, the response is `500 Internal Server Error` (plain text) (checked with the failure of `c.text("", 204)` in `ContextSuite` of `hx4s-react`). hx4s provides no mechanism for shaping exceptions uniformly.

## Setups without React

For APIs only, you need neither a client nor React. `examples/api-only` uses this setup and does not depend on `react`, `react-dom` or `@hono/react-renderer`.

```scala
// build.sbt
ThisBuild / scalaVersion := "3.3.8"

lazy val server = project
  .enablePlugins(ScalaJSPlugin, Hx4sPlugin)
  .settings(
    scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.ESModule) },
    libraryDependencies += "io.github.montereylangit" %%% "hx4s-react" % hx4sVersion
  )
```

`Context` lives in `hx4s-react`, so keep that dependency. If you don't use islands, no React code is linked. Don't set `hx4sClientProject` (`IslandComponents` is empty).

```ts
// vite.config.ts
import { defineConfig } from "vite";
import buildCloudflareWorkers from "@hono/vite-build/cloudflare-workers";
import hx4s from "hx4s-vite";

export default defineConfig(({ mode }) => ({
  plugins: [...hx4s({ mode, client: false, sbt: { cwd: import.meta.dirname } }), buildCloudflareWorkers({ outputDir: "dist/server" })],
}));
```

With `client: false`, `hx4s()` doesn't link the client Scala.js project, doesn't add the `scalajs:client.js` alias or the client build, and doesn't add React settings to `honox()`.
The build is a single `vite build`, and `vite build --mode client` is an error.

`app/server.ts` is just `createApp()` ([getting-started.md](getting-started.md)). The `package.json` dependencies are `hono`, `honox`, `@hono/vite-build`, `@scala-js/vite-plugin-scalajs` and `vite` (`hx4s-vite` comes from the tgz).

## Not implemented

- A typed API client (typed client calls built from the server's `@route`)
- OpenAPI output
- API error formats and middleware helpers (authentication, CORS, etc. are done with hand-written HonoX / Hono middleware)
