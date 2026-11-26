# Routing

hx4s has no route table of its own. `@route` declares "which file to generate"; mapping to URLs, parsing parameters, route priority and applying `_renderer` and `_middleware`
are left to HonoX's file routes (`app/routes`). The generated files are ordinary HonoX routes, so reading them tells you how they behave, and they can be mixed with hand-written routes.

## `@route`

```scala
import hx4s.annotation.route
import scala.scalajs.js.annotation.JSExportTopLevel

object Main {
  @route("items/[id]")                      // GET
  @JSExportTopLevel("itemPage", "server")
  def itemPage(c: AppContext): js.Promise[Response] = ...

  @route("items/[id]", "POST")
  @JSExportTopLevel("itemUpdate", "server")
  def itemUpdate(c: AppContext): js.Promise[Response] = ...
}
```

| Item | Rule |
| --- | --- |
| Target | A `def` / `val` in a top-level object of the configured sources (including inside packages; nested objects are not targets). The object's name doesn't matter |
| `filePath` | Path relative to `app/routes`. A string literal with an optional `.ts` suffix |
| `method` | `GET` (default), `POST`, `PUT`, `DELETE`, `PATCH`, `OPTIONS`. A string literal |
| `@JSExportTopLevel` | Exactly one. The name and moduleID are literals, and the moduleID is `"server"` |
| Multiple `@route` | A member can have several (assigning the same function to several paths and methods) |
| Arguments | All string literals. When named, write them in declaration order (`filePath`, `method`) |

`@route` is recognized by name (`hx4s.annotation.route`), not by type, so import aliases can't be used. The fully qualified name (`@hx4s.annotation.route(...)`) works.

Several methods for the same generated path, such as `items/[id]` and `items/[id].ts`, are combined into one TS file. GET becomes `export default` and the others become `export const POST = ...` (see `examples/minimal/app/routes/items/[id].ts`).

## File paths and URLs

URLs are determined by the rules of HonoX's `createApp`. hx4s's codegen has the same conversion in Scala for conflict checks (`RoutePath`; `tests/route-paths.test.js` checks that it matches HonoX's implementation).

| `filePath` | URL | Notes |
| --- | --- | --- |
| `index.ts` | `/` | |
| `about.ts` | `/about` | |
| `blog/index.ts` | `/blog` | Same URL as `blog.ts` |
| `items/[id].ts` | `/items/:id` | Dynamic parameter. Read it with `c.req.param("id")` |
| `docs/[...slug].ts` | `/docs/:slug{.+}` | The whole rest of the path. For `/docs/a/b/c`, `slug` is `a/b/c` |
| `api/items.ts` | `/api/items` | |

Trailing slashes follow `createApp({ trailingSlash })` (HonoX).

The only characters allowed in `filePath` are `A-Za-z0-9_.$[]-` and `/`. Directories like `(group)` can't be used.

## HTTP methods

`GET`, `POST`, `PUT`, `DELETE`, `PATCH` and `OPTIONS` can be used, because HonoX's `createApp` registers only these six. `HEAD` is answered by the GET handler
(`tests/build-output.test.js` in `examples/minimal` checks this). `ALL` is not implemented.

Several methods can share a URL only when they are combined in the **same `filePath`**. Different files assigning the same method to the same URL are rejected (next section).

## What codegen rejects

Errors are detected all together before writing, and if there is even one, nothing is written. They appear in the failure message of `sbt server/hx4sGenerate` like this:

```
[error] [hx4s-codegen] error: server/src/main/scala/example/server/Bad.scala:8: HEAD is not registered by HonoX createApp (GET handlers also answer HEAD)
[error] (server / hx4sGenerate) hx4s-codegen app failed: 1 error(s)
```

The format is `<source path>:<line>: <reason>`, with the path relative to `hx4sProjectRoot`.

| Error | Example message |
| --- | --- |
| `HEAD` | `HEAD is not registered by HonoX createApp (GET handlers also answer HEAD)` |
| Unsupported method | `unsupported HTTP method ALL` |
| File name starting with `_` | `route basename _hidden.ts is reserved by HonoX (leading _)` |
| Disallowed characters (spaces, etc.) | `unsafe output path a b.ts: unsupported characters in a b.ts` |
| No `@JSExportTopLevel` | `annotated member needs exactly one @JSExportTopLevel` |
| Different files with the same URL and method | `route files map GET to the same URL: items/[id].ts (/items/:id), items/[slug].ts (/items/:slug)` |

The following are also rejected:

- File names HonoX does not pick up as routes: a basename starting with `-` / `$`, `*.test.ts` / `*.spec.ts`, directories starting with `-`, names starting with `.` and `node_modules` (a leading `.well-known/` is allowed)
- Path errors: empty segments, `.` / `..`, absolute paths, backslashes, trailing dots, Windows reserved names (`CON`, etc.)
- Duplicates: the same export name on several members, the same route method twice, paths that differ only in case
- A moduleID other than `server`, arguments that are not string literals

`items/[id].ts` and `items/[slug].ts`, which differ only in the parameter name, become the same URL pattern in HonoX, so they are rejected as a conflict.
A static segment and a dynamic parameter (`items/new.ts` and `items/[id].ts`) coexist thanks to HonoX's ordering, so they are not rejected.

## `Context`

The handler's argument is `hx4s.hono.Context[Env, R]`, a facade for Hono's Context. `Env` is the type of `c.env`, and `R` is the type of the renderer props passed to `c.render` (a subtype of `js.Object`).

```scala
type AppContext = Context[js.Object, RenderProps]

trait Bindings extends js.Object {
  val GREETING: js.UndefOr[String]
}

@route("hello")
@JSExportTopLevel("helloPage", "server")
def helloPage(c: Context[Bindings, js.Object]): Response =
  c.html(s"<p>${c.env.GREETING.getOrElse("hello")}</p>")
```

The return type is `org.scalajs.dom.Response` or `js.Promise[Response]`. `Request` / `Response` / `FormData` are scalajs-dom types.

| Target | Members |
| --- | --- |
| `c.req` (`HonoRequest`) | `raw` (scalajs-dom `Request`), `method`, `url`, `path`, `param(name)`, `query(name)`, `queries(name)`, `header(name)`, `text()`, `json()`, `formData()` |
| Responses | `c.text(body, status)`, `c.json(body, status)`, `c.html(body, status)`, `c.body(data, status, headers)`, `c.redirect(location, status)`, `c.notFound()`, `c.status(code)`, `c.header(name, value)`, `c.res` |
| Rendering | `c.render(children, props)`, extension method `c.renderPromise(children, props)` (always returns a `Promise`) |
| Variables | `c.set(key, value)`, `c.get(key)`, extension methods `setVariable` / `getVariable` (`ContextVariable[A]`) |
| Execution context | `c.executionCtx`, extension method `executionCtxOption` (`waitUntil`, `passThroughOnException`) |
| Environment | `c.env` (type `Env`) |

`param`, `query` and `header` return `js.UndefOr[String]`. The extension methods require `import hx4s.hono.Context.*`.

- **Re-reading the body**: Hono caches the body in the form first read by `text()` / `json()` / `formData()`, and it can be read again in a different form (`text()` after `formData()` returns a string re-encoded as multipart).
  If you read the body of `c.req.raw` directly, it can no longer be read through `c.req`, and `raw` can't be read after reading through `c.req` ([api.md](api.md))
- **`executionCtx`**: Hono throws if the runtime didn't provide it. To run in calls that don't provide one, such as tests on Node, use `executionCtxOption`
- **Responses without a body** (`204`, `304`): return them with `c.body(null, 204)`. `c.text("", 204)` has a body even with an empty string and fails due to Response's rules
- **Not Found**: `c.notFound()` returns the response of the app's Not Found handler. The return type is `Response | js.Promise[Response]` ([api.md](api.md))
- **Things in Hono's Context but not in the facade**: write your own `@js.native` facade, or handle them in a hand-written TS route

### Variables (`ContextVariable`)

An example of reading, typed from Scala, a value set with `c.set` by hand-written middleware. Define the name in one place and share it between the writer and the reader.

TypeScript's `c.set` only accepts keys declared in Hono's `ContextVariableMap`. Scala's `ContextVariable` does not produce a TypeScript declaration,
so add one in a declaration file included by `include` in `tsconfig.json`.

```ts
// app/env.d.ts (hand-written)
import "hono";

declare module "hono" {
  interface ContextVariableMap {
    requestId: string;
  }
}
```

```ts
// app/routes/api/_middleware.ts (hand-written)
import { createRoute } from "honox/factory";

export default createRoute(async (c, next) => {
  c.set("requestId", "req-1");
  await next();
});
```

```scala
val RequestId: ContextVariable[String] = ContextVariable("requestId")

@route("api/whoami")
@JSExportTopLevel("whoami", "server")
def whoami(c: ApiContext): Response =
  c.json(js.Dynamic.literal(requestId = c.getVariable(RequestId).orUndefined))
```

`getVariable` returns `None` if the value is not set. The type guarantee is a promise made by the caller and is not checked at run time (keep the TypeScript declaration and the type of the Scala `ContextVariable` in sync yourself).
These two TypeScript files are type-checked by hx4s's `npm run typecheck` in `tests/docs-typecheck`.

## Mixing with hand-written routes

You can put hand-written TS files in `app/routes` (ordinary HonoX routes using `createRoute`, `_renderer.tsx`, `_middleware.ts`).

- Generated files are recognized by the generated-marker line at the top. Files without the marker are never overwritten or deleted
- If a hand-written file sits at a path that would be generated, generation fails with `refusing to overwrite a file without the generated marker`
- **URL conflicts between hand-written and generated routes are not checked** (no warning either; not implemented). The behavior when both are placed at the same URL is not verified, so don't do it

## Not implemented

- Typed routes (declare URLs in shared, and get parameters and build URLs with types)
- Client Router (client-side routing)
- The `ALL` method
- Warnings for conflicts with hand-written routes

See chapter 4 of [hx4s/routing-design.md](hx4s/routing-design.md) and [hx4s/roadmap.md](hx4s/roadmap.md) for plans.
