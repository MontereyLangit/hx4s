# Getting started

This guide runs `examples/minimal` first, then walks through creating the same setup in your own project. hx4s is not published yet, so both require a local publish first ([installation.md](installation.md)).

## Prerequisites

| Tool | Version |
| --- | --- |
| JDK | 17 or later (CI uses 21) |
| sbt | 1.x (the examples use 1.13.0) |
| Node.js | 22.12 or later |
| Git | Used to check out hx4s and to verify the publish record |

Scala 3.3.8 and Scala.js 1.22.0 are used. Other versions have not been verified (see supported versions in the [README](../README.md)).

## Running examples/minimal

```bash
git clone <hx4s repository> hx4s && cd hx4s
node scripts/publish-local.mjs                 # publishes to ~/.ivy2/local and target/local/hx4s-vite.tgz
node scripts/setup-example.mjs minimal         # verifies the publish record, runs npm ci from the lockfile and installs the hx4s-vite tgz
cd examples/minimal
npm run build                                  # hx4sGenerate → client build → Worker build
npm run dev                                    # wrangler dev after the build. http://127.0.0.1:8787
npm test                                       # sbt test, plus SSR, POST, bundle, jsdom hydration and E2E
```

- The E2E tests in `npm test` use Playwright's Chromium. Run `npx playwright install chromium` first. `HX4S_SKIP_E2E=1` skips only the E2E tests.
- `npm run build` starts sbt four times (once for generation, once for the client build, and twice in the server build to link the server and the client).
  In the Phase 3 verification, each run took 15–30 seconds ([reviews/phase3-review.md](hx4s/reviews/phase3-review.md)).
- `npm run dev` is not the Vite dev server. It builds and then starts `wrangler dev`, so there is no HMR.

In the browser, you get HTML that is readable without JavaScript, and only the buttons (islands) are hydrated. `/items/1` is a dynamic route and `/api/items` is a JSON API. See [examples.md](examples.md) for the role of each file.

## Creating your own project

Create the following structure: three sbt projects (server, client, shared) and HonoX's `app/` in one directory.

```
my-app/
  build.sbt
  project/
    build.properties         sbt.version=1.13.0
    plugins.sbt
  package.json
  tsconfig.json
  vite.config.ts
  wrangler.jsonc
  app/
    server.ts                HonoX entry point (hand-written)
    client.ts                hydration entry point (hand-written)
    renderer.d.ts            props type of _renderer (hand-written)
    scalajs-server.d.ts      type declarations for scalajs:server.js (hand-written)
    routes/
      _renderer.tsx          calls the HTML shell from Scala (hand-written)
      index.ts               ← generated (@route)
    islands/
      counter.tsx            ← generated (@islands)
  shared/src/main/scala/app/shared/CounterProps.scala
  server/src/main/scala/app/server/Main.scala
  client/src/main/scala/app/client/Main.scala
```

The code below is the same code that was verified with this structure, both the build and the behavior in the browser (the count increases on click).

### 1. sbt

`project/plugins.sbt`. Use the version printed by `node scripts/publish-local.mjs`, or the value in hx4s's `target/local/hx4s-version.txt`.

```scala
addSbtPlugin("io.github.montereylangit" % "sbt-hx4s"    % "<hx4s version>")
addSbtPlugin("org.scala-js"             % "sbt-scalajs" % "1.22.0")
```

`build.sbt`. Only the server enables `Hx4sPlugin` and points to the client with `hx4sClientProject`. The server does not depend on the client (the client's islands are loaded through Vite's virtual module `scalajs:client.js`).

```scala
ThisBuild / scalaVersion := "3.3.8"

lazy val commonSettings = Seq(
  scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.ESModule) },
  libraryDependencies += "io.github.montereylangit" %%% "hx4s-react" % hx4sVersion
)

// Island props shared by server and client
lazy val shared = project
  .enablePlugins(ScalaJSPlugin)
  .settings(commonSettings)

// @route handlers and SSR
lazy val server = project
  .enablePlugins(ScalaJSPlugin, Hx4sPlugin)
  .dependsOn(shared)
  .settings(commonSettings)
  .settings(hx4sClientProject := client)

// Islands shipped to the browser
lazy val client = project
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(shared)
  .settings(commonSettings)
```

Name the projects `server` and `client` (the default `projectID` of `hx4s()`; see [build-configuration.md](build-configuration.md) to change them).
ES modules (`ModuleKind.ESModule`) are required.

### 2. npm

`package.json`. The versions are the same as in the examples (the verified combination). `hx4s-vite` is not published, so it is not listed here.

```json
{
  "name": "my-app",
  "private": true,
  "type": "module",
  "scripts": {
    "generate": "sbt --batch server/hx4sGenerate",
    "build:client": "vite build --mode client",
    "build:server": "vite build",
    "build": "npm run generate && npm run build:client && npm run build:server",
    "dev": "npm run build && wrangler dev",
    "typecheck": "tsc -p tsconfig.json"
  },
  "dependencies": {
    "@hono/react-renderer": "1.0.1",
    "hono": "4.13.18",
    "honox": "0.1.62",
    "react": "19.2.8",
    "react-dom": "19.2.8"
  },
  "devDependencies": {
    "@hono/vite-build": "1.11.1",
    "@scala-js/vite-plugin-scalajs": "1.1.0",
    "@types/node": "^22",
    "@types/react": "^19.3.0",
    "@types/react-dom": "^19.3.0",
    "typescript": "^5",
    "vite": "8.3.3",
    "wrangler": "4.162.0"
  },
  "overrides": {
    "@scala-js/vite-plugin-scalajs": {
      "vite": "$vite"
    }
  }
}
```

`overrides` is required. `@scala-js/vite-plugin-scalajs` 1.1.0 has dependencies that assume Vite 7 or earlier, so this forces it to use Vite 8 ([hx4s/compatibility.md](hx4s/compatibility.md) 1.3).

Install the dependencies, then add the hx4s-vite tgz.

```bash
npm install
npm install --no-save /path/to/hx4s/target/local/hx4s-vite.tgz
```

Packages installed with `--no-save` are removed by the next `npm install` / `npm ci`. Reinstall it each time (see [installation.md](installation.md) for why and how to pin it).

`tsconfig.json`:

```json
{
  "compilerOptions": {
    "target": "ESNext",
    "module": "ESNext",
    "moduleResolution": "Bundler",
    "jsx": "react-jsx",
    "jsxImportSource": "react",
    "strict": true,
    "types": ["vite/client", "node"],
    "esModuleInterop": true,
    "skipLibCheck": true,
    "noEmit": true
  },
  "include": ["app", "vite.config.ts"]
}
```

### 3. Vite

`vite.config.ts`. Add a runtime adapter (here, Cloudflare Workers) to the plugins returned by `hx4s()`. The client and the server are separated by `mode`.

```ts
import { defineConfig } from "vite";
import buildCloudflareWorkers from "@hono/vite-build/cloudflare-workers";
import hx4s from "hx4s-vite";

// client: dist/client/static/* (static assets), server: dist/server/index.js (Worker)
export default defineConfig(({ mode }) => {
  const plugins = hx4s({ mode, sbt: { cwd: import.meta.dirname } });
  if (mode === "client") {
    return { plugins, build: { outDir: "dist/client" } };
  }
  return { plugins: [...plugins, buildCloudflareWorkers({ outputDir: "dist/server" })] };
});
```

### 4. HonoX entry points

`app/server.ts`. HonoX assembles the file routes in `app/routes` (the files generated from `@route` and `_renderer.tsx`).

```ts
import { createApp } from "honox/server";

const app = createApp();

export default app;
```

`app/client.ts`. Pass a hydrate function to HonoX's `createClient`. It checks the props before hydrating, and keeps the SSR HTML if they don't match ([ssr-hydration.md](ssr-hydration.md)).
The final `data-hx4s-ready` is a marker the example tests use to wait for hydration to finish; remove it if your app doesn't need it.

```ts
import { createClient } from "honox/client";
import { islandPropsProblem } from "hx4s-vite/client";
import { createElement, type ElementType, type ReactElement } from "react";
import { hydrateRoot } from "react-dom/client";

void createClient<ReactElement>({
  hydrate: (element, root) => {
    // When the HTML and the client are from different builds and the props don't match, skip hydration and keep the SSR markup (islands inside templates are checked too)
    const problem = islandPropsProblem(element);
    if (problem !== undefined) {
      console.error(`[hx4s] skipped hydration of ${root.getAttribute("component-name")}: ${problem}`);
      return;
    }
    hydrateRoot(root, element);
  },
  createElement: (type: ElementType, props: Record<string, unknown>) => createElement(type, props),
}).then(() => document.documentElement.setAttribute("data-hx4s-ready", ""));
```

`app/routes/_renderer.tsx`. Building the whole HTML is left to Scala's `renderShell`. Files starting with `_` are reserved names in HonoX, so hx4s does not generate them.

```tsx
import { reactRenderer } from "@hono/react-renderer";
import { renderShell } from "scalajs:server.js";

const clientScriptSrc = import.meta.env.PROD ? "/static/client.js" : "/app/client.ts";

export default reactRenderer(({ children, title }) => renderShell(children, title, clientScriptSrc), { docType: true });
```

Two type declarations are needed.

```ts
// app/renderer.d.ts
import "@hono/react-renderer";

declare module "@hono/react-renderer" {
  interface Props {
    title?: string;
  }
}
```

```ts
// app/scalajs-server.d.ts
declare module "scalajs:server.js" {
  export function renderShell(
    children: import("react").ReactNode,
    title: string | undefined,
    clientScriptSrc: string,
  ): import("react").ReactElement;
}
```

### 5. Scala

Shared props (`shared/src/main/scala/app/shared/CounterProps.scala`).

```scala
package app.shared

import hx4s.props.{JsField, JsKind}
import hx4s.props.JsShape.required

import scala.scalajs.js

/** Counter props. Passed by the server, read by the client */
trait CounterProps extends js.Object {
  val label: String
  val start: Int
}

object CounterProps {
  /** Rules that check the props received by the client */
  val fields: List[JsField] = List(required("label", JsKind.Str), required("start", JsKind.Int))
}
```

The client island (`client/src/main/scala/app/client/Main.scala`).

```scala
package app.client

import app.shared.CounterProps
import hx4s.annotation.islands
import hx4s.react.IslandExport
import japgolly.scalajs.react.*
import japgolly.scalajs.react.vdom.html_<^.*

import scala.scalajs.js.annotation.JSExportTopLevel

object Main {
  final val Counter = ScalaFnComponent[CounterProps] { props =>
    for {
      count <- useState(props.start)
    } yield <.button(^.`type` := "button", ^.onClick --> count.modState(_ + 1), s"${props.label}: ${count.value}")
  }

  @islands[app.shared.CounterProps]("counter")
  @JSExportTopLevel("CounterJs", "client")
  val counter: IslandExport[CounterProps] = IslandExport("CounterJs", CounterProps.fields)(props => Counter(props))
}
```

The server route and HTML shell (`server/src/main/scala/app/server/Main.scala`).

```scala
package app.server

import app.shared.CounterProps
import hx4s.annotation.route
import hx4s.generated.IslandComponents
import hx4s.hono.Context
import japgolly.scalajs.react.facade.React
import japgolly.scalajs.react.vdom.VdomNode
import japgolly.scalajs.react.vdom.html_<^.*
import org.scalajs.dom.Response

import scala.scalajs.js
import scala.scalajs.js.annotation.JSExportTopLevel

/** Props received by app/routes/_renderer.tsx */
trait RenderProps extends js.Object {
  val title: js.UndefOr[String]
}

object Main {
  type AppContext = Context[js.Object, RenderProps]

  @route("index")
  @JSExportTopLevel("indexPage", "server")
  def indexPage(c: AppContext): js.Promise[Response] = {
    val page = <.main(
      <.h1("Hello hx4s"),
      IslandComponents.CounterJs(new CounterProps { val label = "clicks"; val start = 0 })
    )
    c.renderPromise(page.rawElement, new RenderProps { val title = "Hello" })
  }

  /** The whole HTML. Called by app/routes/_renderer.tsx */
  @JSExportTopLevel("renderShell", "server")
  def renderShell(children: React.Node, title: js.UndefOr[String], clientScriptSrc: String): React.Element =
    <.html(
      <.head(
        <.meta(VdomAttr("charSet") := "utf-8"),
        <.titleTag(title.getOrElse("hx4s")),
        <.script(^.`type` := "module", ^.src := clientScriptSrc)
      ),
      <.body(VdomNode(children))
    ).rawElement
}
```

- `IslandComponents` is generated by `hx4sIslandComponents` through `Compile / sourceGenerators` (`server/target/.../src_managed`). Don't commit it.
- The second argument of `@JSExportTopLevel` (the moduleID) must match the server module for `@route`. Each `@islands` declaration specifies its client module. See [Splitting Island code](islands.md#splitting-island-code).
- The client script is loaded whether or not the page has islands (the `<script>` in the shell).

### 6. Worker settings

`wrangler.jsonc`. See [deployment.md](deployment.md) for why `nodejs_compat` is needed.

```jsonc
{
  "$schema": "node_modules/wrangler/config-schema.json",
  "name": "my-app",
  "main": "dist/server/index.js",
  "compatibility_date": "2025-09-01",
  // react-dom/server imports node:async_hooks
  "compatibility_flags": ["nodejs_compat"],
  // Serve only the client output as static assets (the Worker code in dist/server is not exposed)
  "assets": {
    "directory": "dist/client"
  }
}
```

### 7. Build and run

```bash
npm run build      # generates app/routes/index.ts and app/islands/counter.tsx, then builds the client and the server
npm run dev        # http://127.0.0.1:8787
```

With JavaScript disabled, "Hello hx4s" and the button appear; with JavaScript enabled, the count increases each time you click the button.

The generated `app/routes/index.ts` and `app/islands/counter.tsx` start with `// This file is generated by hx4s-codegen`. Commit them and don't edit them by hand ([build-configuration.md](build-configuration.md)).
Add `node_modules/`, `target/`, `project/target/`, `dist/`, `.wrangler/` and so on to `.gitignore`.

## What to read next

| Goal | Document |
| --- | --- |
| Add more routes | [routing.md](routing.md) |
| Island props and types | [islands.md](islands.md) |
| Hydration and version skew | [ssr-hydration.md](ssr-hydration.md) |
| Making the whole page an island, APIs | [spa.md](spa.md), [api.md](api.md) |
