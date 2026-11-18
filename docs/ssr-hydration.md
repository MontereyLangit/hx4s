# SSR and hydration

The default setup is **SSR + Islands**. Pages are rendered to HTML on the server, and only the parts that need interaction are hydrated in the browser as islands.
Static SSR, Islands and APIs can be mixed per route (HonoX's file routes are independent per route).

## Rendering flow

| Stage | Entry point | What happens |
| --- | --- | --- |
| 1. Request | `app/server.ts` (`createApp`) → generated route `app/routes/**/*.ts` → Scala handler | The handler builds a scalajs-react element and passes it to `c.renderPromise(element, props)` |
| 2. Renderer | `app/routes/_renderer.tsx` (`@hono/react-renderer`) | Scala's `renderShell` builds the whole HTML and places the client `<script>` |
| 3. Island SSR | `IslandComponents.CounterJs(props)` | Outputs HTML wrapped in `<honox-island component-name="/app/islands/counter.tsx" data-serialized-props="…">` |
| 4. Browser | `app/client.ts` (`createClient`) | Matches `component-name` against `app/islands/**/*.tsx` and hydrates with `JSON.parse(data-serialized-props)` as props |

`_renderer.tsx` and `app/client.ts` are hand-written and look almost the same as in the examples ([getting-started.md](getting-started.md)).
The client `<script>` is loaded whether or not the page has islands. Omitting it on pages without islands is not implemented.

## Hydration boundaries

Boundaries are decided by HonoX's `HonoXIsland`. hx4s's `hx4s.react.Island` is a thin bridge that just calls it, and `IslandComponents` uses it.

- When an island renders another island inside it, the inner one doesn't create a boundary and renders as a normal component. The hydration root is the single outer one
- When props contain React elements (or arrays of them), HonoX SSRs them into a `<template>` and the client restores them
- When the same island is placed several times, each boundary has its own props

## `app/client.ts` and props validation

Call `islandPropsProblem(element)` from `hx4s-vite/client` before hydrating.

```ts
hydrate: (element, root) => {
  const problem = islandPropsProblem(element);
  if (problem !== undefined) {
    console.error(`[hx4s] skipped hydration of ${root.getAttribute("component-name")}: ${problem}`);
    return;
  }
  hydrateRoot(root, element);
},
```

| Category | Content |
| --- | --- |
| Checked | The props of the island being hydrated, and the props of islands contained in its element props (children HonoX restores from `<template>`, including inside arrays). Targets are exports built with `IslandExport(label, fields)` |
| On mismatch | The whole boundary is not hydrated. Even if only an inner island is invalid, the outer one is left as SSR HTML too. The reason is returned with its location, like `props.children[1]: props.start: missing` |
| Not checked | `IslandExport.unchecked` and islands written in JS. Elements placed outside element props (inside ordinary values). The version of chunks loaded after hydration. Content that arrives later through HonoX's Suspense |

`hx4s-vite/client` does not import the Vite plugin itself, so Vite and HonoX's SSR code don't end up in the client bundle.

## Version skew between HTML and client

The SSR HTML and the client assets (`static/client.js` and island chunks) are served and cached separately. Around a deployment, the following combinations can happen:

- Old HTML (cached) with a new client
- New HTML with an old client (asset cache still alive)

If the shape of the props (fields of `P`) changed, the JSON the client receives doesn't match `P`. hx4s behaves as follows:

- When the validation above finds a mismatch, it logs `console.error` and **does not hydrate that island, keeping the SSR HTML**. It stays as static HTML, and the page doesn't break
- The old HTML with new client combination (missing props keys) is checked by the examples' `tests/hydration.test.js` (jsdom). New HTML with an old client is not verified

This is the minimum to keep the page from breaking when skew happens; it doesn't prevent the skew itself. The client entry has a fixed name by default (`static/client.js`), not a hashed one.
Cache control, keeping old assets and the procedure for changing props are undecided ([hx4s/roadmap.md](hx4s/roadmap.md) U-26, [hx4s/rendering-model.md](hx4s/rendering-model.md) 2.4).
In practice, avoid adding required keys or changing types, and receive added keys as `js.UndefOr`.

## When JavaScript is disabled

The SSR HTML is shown as-is. Islands show only what the server rendered (button labels, etc.), and interaction doesn't work.
The HTML of the `examples/minimal` top page has content readable without JavaScript (`tests/build-output.test.js`). For the setup that makes the whole page one island ([spa.md](spa.md)), a prototype confirmed that the SSR part is shown in a browser with JavaScript disabled.

## XSS and serialization

HonoX runs `JSON.stringify` on props and puts them in an HTML **attribute value** (`data-serialized-props`). React's SSR escapes attribute values, so strings containing `"`, `<`, `&` or `</script>`
don't become HTML structure. `examples/minimal` uses an item whose name contains `</script><b>tag</b> "quoted" 'single'` and props containing `</script><!--`, and checks that:

- No injected `<script>` elements are added to the output
- The values come back to the original strings as client props

Putting props in `<script type="application/json">` is not used (if it were, `<` would need separate escaping; [hx4s/rendering-model.md](hx4s/rendering-model.md) 2.2).
Body text is escaped by React as usual. If you use `dangerouslySetInnerHTML`, that's your responsibility.

## Keeping server code out of the client

Two mechanisms keep server handlers and secrets out of the browser bundle.

1. **Project split**: server and client are separate sbt projects, and the client doesn't depend on the server. The server uses the client only through Vite's virtual module `scalajs:client.js` (the island code).
   Scala.js builds the client as a separate link output.
2. **`hx4sCheckDependencies`**: checks the sbt dependency graph. `hx4sGenerate` and `hx4sIslandComponents` call it first, so a violation fails at generation time.

```
hx4s: the client project client must not depend on the server project server (client -> mid -> server). Put types shared by both, such as island props, in a project that neither depends on the other
```

It checks that the client (and its transitive `dependsOn`) does not depend on a project with `Hx4sPlugin` enabled, and that the server does not depend on the client.
Test-only dependencies such as `dependsOn(x % "test")` are excluded.

**Not checked**: Vite's module graph (including TypeScript imports). `examples/minimal` checks by string matching that the client bundle contains no server-only strings
(`tests/build-output.test.js`), but this doesn't guarantee the dependency direction.

## Testing

| Purpose | Method | Example |
| --- | --- | --- |
| SSR HTML | Call the built Worker's `fetch` on Node and read it with jsdom | `examples/minimal/tests/build-output.test.js` |
| Typed props | Check with `compileErrors` that wrong props fail to compile | `examples/minimal/server/src/test/.../TypedPropsSuite.scala` |
| Hydration | Build `app/client.ts`, run it on jsdom, and check that clicking changes state and that mismatched islands stay as SSR | `examples/minimal/tests/hydration.test.js` |
| Real browser | Check with `wrangler dev` and Playwright that no hydration errors occur | `examples/minimal/tests/e2e.test.js` |

### Running Scala.js tests on Node

Server tests can run on Node all the way through SSR (`ReactDOMServer.renderToStaticMarkup`). Run as-is, Node can't resolve `scalajs:client.js` in the link output or `hono/jsx` inside HonoX.
`hx4s-vite` provides a loader using Node's module hooks.

| Specifier | Resolves to |
| --- | --- |
| `scalajs:client.js` | `$HX4S_CLIENT_JS_DIR/client.js` (output directory of the client's `fastLinkJS`) |
| `hono/jsx` and `hono/jsx/jsx-runtime` inside HonoX | `react` and `react/jsx-runtime` (the same replacement `hx4s()` does in production builds) |

```scala
// build.sbt (server project; same as examples/minimal)
import org.scalajs.jsenv.nodejs.NodeJSEnv

Test / jsEnv := {
  val clientDir = (client / Compile / fastLinkJS / scalaJSLinkerOutputDirectory).value
  (client / Compile / fastLinkJS).value
  new NodeJSEnv(
    NodeJSEnv.Config()
      .withArgs(List("--import", "hx4s-vite/node-register"))
      .withEnv(Map("HX4S_CLIENT_JS_DIR" -> clientDir.getAbsolutePath))
  )
}
```

To stub runtime specifiers (such as `cloudflare:workers`), register `hx4s-vite/node-loader` alongside your own loader from your own register file instead of `hx4s-vite/node-register`.
See [hx4s/build-integration.md](hx4s/build-integration.md) 2.3 for the steps.
