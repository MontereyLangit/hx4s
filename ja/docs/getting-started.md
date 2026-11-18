# はじめに

`examples/minimal` を動かしてから、同じ構成を自分のプロジェクトに作る手順を書く。hx4s は未公開なので、どちらも先にローカル公開（[installation.md](installation.md)）が要る。

## 前提

| 道具 | 版 |
| --- | --- |
| JDK | 17 以上（CI は 21） |
| sbt | 1.x（examples は 1.13.0） |
| Node.js | 22.12 以上 |
| Git | hx4s の checkout と、公開記録の照合に使う |

Scala 3.3.8、Scala.js 1.22.0 を使う。ほかの版は検証していない（[README](../README.md) の対応バージョン）。

## examples/minimal を動かす

```bash
git clone <hx4s のリポジトリ> hx4s && cd hx4s
node scripts/publish-local.mjs                 # ~/.ivy2/local と target/local/hx4s-vite.tgz へ公開する
node scripts/setup-example.mjs minimal         # 公開記録を照合し、lockfile どおりに npm ci して hx4s-vite の tgz を入れる
cd examples/minimal
npm run build                                  # hx4sGenerate → client ビルド → Worker ビルド
npm run dev                                    # build のあと wrangler dev。http://127.0.0.1:8787
npm test                                       # sbt test と、SSR・POST・バンドル・jsdom の hydration・E2E
```

- `npm test` の E2E は Playwright の Chromium を使う。先に `npx playwright install chromium` を実行する。`HX4S_SKIP_E2E=1` で E2E だけ飛ばせる。
- `npm run build` は sbt を 4 回起動する（生成で 1 回、client ビルドで 1 回、server ビルドで server と client のリンクに 2 回）。
  Phase 3 の検証では 1 回あたり 15〜30 秒だった（[reviews/phase3-review.md](hx4s/reviews/phase3-review.md)）。
- `npm run dev` は Vite の dev server ではない。ビルドしてから `wrangler dev` を起動するので、HMR は無い。

ブラウザで開くと、JavaScript なしでも読める HTML が出て、ボタン（island）だけが hydrate される。`/items/1` は動的ルート、`/api/items` は JSON の API。各ファイルの役割は [examples.md](examples.md)。

## 自分のプロジェクトを作る

次の構成を作る。server・client・shared の 3 つの sbt プロジェクトと、HonoX の `app/` を 1 つのディレクトリに置く。

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
    server.ts                HonoX の入口（手書き）
    client.ts                hydrate の入口（手書き）
    renderer.d.ts            _renderer の props の型（手書き）
    scalajs-server.d.ts      scalajs:server.js の型宣言（手書き）
    routes/
      _renderer.tsx          HTML のシェルを Scala から呼ぶ（手書き）
      index.ts               ← 生成（@route）
    islands/
      counter.tsx            ← 生成（@islands）
  shared/src/main/scala/app/shared/CounterProps.scala
  server/src/main/scala/app/server/Main.scala
  client/src/main/scala/app/client/Main.scala
```

以降のコードは、この構成でビルドとブラウザでの動作（クリックで数が増える）を確かめたものと同じ。

### 1. sbt

`project/plugins.sbt`。版は `node scripts/publish-local.mjs` の出力、または hx4s の `target/local/hx4s-version.txt` の値にする。

```scala
addSbtPlugin("io.github.montereylangit" % "sbt-hx4s"    % "<hx4s の版>")
addSbtPlugin("org.scala-js"             % "sbt-scalajs" % "1.22.0")
```

`build.sbt`。server だけが `Hx4sPlugin` を有効にし、`hx4sClientProject` で client を指す。server は client に依存しない（client の island は Vite の仮想モジュール `scalajs:client.js` で読む）。

```scala
ThisBuild / scalaVersion := "3.3.8"

lazy val commonSettings = Seq(
  scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.ESModule) },
  libraryDependencies += "io.github.montereylangit" %%% "hx4s-react" % hx4sVersion
)

// server と client が共有する island の props
lazy val shared = project
  .enablePlugins(ScalaJSPlugin)
  .settings(commonSettings)

// @route のハンドラと SSR
lazy val server = project
  .enablePlugins(ScalaJSPlugin, Hx4sPlugin)
  .dependsOn(shared)
  .settings(commonSettings)
  .settings(hx4sClientProject := client)

// ブラウザへ配る island
lazy val client = project
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(shared)
  .settings(commonSettings)
```

プロジェクト名は `server` と `client` にする（`hx4s()` の既定の `projectID`。変えるときは [build-configuration.md](build-configuration.md)）。
ES モジュール（`ModuleKind.ESModule`）は必須。

### 2. npm

`package.json`。版は examples と同じ（検証した組み合わせ）。`hx4s-vite` は未公開なので、ここには書かない。

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

`overrides` は必須。`@scala-js/vite-plugin-scalajs` 1.1.0 は Vite 7 までを前提にした依存を持つので、Vite 8 を使わせる（[hx4s/compatibility.md](hx4s/compatibility.md) 1.3）。

依存を入れて、hx4s-vite の tgz を足す。

```bash
npm install
npm install --no-save /path/to/hx4s/target/local/hx4s-vite.tgz
```

`--no-save` で入れたパッケージは、次の `npm install` / `npm ci` で消える。そのたびに入れ直す（理由と固定の方法は [installation.md](installation.md)）。

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

`vite.config.ts`。`hx4s()` が返すプラグインに、実行基盤のアダプタ（ここでは Cloudflare Workers）を足す。client と server は `mode` で分ける。

```ts
import { defineConfig } from "vite";
import buildCloudflareWorkers from "@hono/vite-build/cloudflare-workers";
import hx4s from "hx4s-vite";

// client: dist/client/static/*（静的配信）、server: dist/server/index.js（Worker）
export default defineConfig(({ mode }) => {
  const plugins = hx4s({ mode, sbt: { cwd: import.meta.dirname } });
  if (mode === "client") {
    return { plugins, build: { outDir: "dist/client" } };
  }
  return { plugins: [...plugins, buildCloudflareWorkers({ outputDir: "dist/server" })] };
});
```

### 4. HonoX の入口

`app/server.ts`。`app/routes` のファイルルート（`@route` の生成物と `_renderer.tsx`）を HonoX が組み立てる。

```ts
import { createApp } from "honox/server";

const app = createApp();

export default app;
```

`app/client.ts`。HonoX の `createClient` に hydrate を渡す。hydrate の前に props を確かめ、合わなければ SSR の HTML を残す（[ssr-hydration.md](ssr-hydration.md)）。
最後の `data-hx4s-ready` は examples のテストが hydrate の完了を待つための印で、アプリでは不要なら外してよい。

```ts
import { createClient } from "honox/client";
import { islandPropsProblem } from "hx4s-vite/client";
import { createElement, type ElementType, type ReactElement } from "react";
import { hydrateRoot } from "react-dom/client";

void createClient<ReactElement>({
  hydrate: (element, root) => {
    // HTML と client の世代がずれて props が合わないときは hydrate せず、SSR の markup を残す（template 内の island も確かめる）
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

`app/routes/_renderer.tsx`。HTML 全体の組み立ては Scala の `renderShell` に任せる。`_` で始まるファイルは HonoX の予約名なので、hx4s は生成しない。

```tsx
import { reactRenderer } from "@hono/react-renderer";
import { renderShell } from "scalajs:server.js";

const clientScriptSrc = import.meta.env.PROD ? "/static/client.js" : "/app/client.ts";

export default reactRenderer(({ children, title }) => renderShell(children, title, clientScriptSrc), { docType: true });
```

型の宣言が 2 つ要る。

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

shared の props（`shared/src/main/scala/app/shared/CounterProps.scala`）。

```scala
package app.shared

import hx4s.props.{JsField, JsKind}
import hx4s.props.JsShape.required

import scala.scalajs.js

/** カウンターの props。server が渡し、client が読む */
trait CounterProps extends js.Object {
  val label: String
  val start: Int
}

object CounterProps {
  /** client が受け取った props を確かめる規則 */
  val fields: List[JsField] = List(required("label", JsKind.Str), required("start", JsKind.Int))
}
```

client の island（`client/src/main/scala/app/client/Main.scala`）。

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

  @islands[app.shared.CounterProps]("counter.tsx")
  @JSExportTopLevel("CounterJs", "client")
  val counter: IslandExport[CounterProps] = IslandExport("CounterJs", CounterProps.fields)(props => Counter(props))
}
```

server のルートと HTML シェル（`server/src/main/scala/app/server/Main.scala`）。

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

/** app/routes/_renderer.tsx が受け取る props */
trait RenderProps extends js.Object {
  val title: js.UndefOr[String]
}

object Main {
  type AppContext = Context[js.Object, RenderProps]

  @route("index.ts")
  @JSExportTopLevel("indexPage", "server")
  def indexPage(c: AppContext): js.Promise[Response] = {
    val page = <.main(
      <.h1("Hello hx4s"),
      IslandComponents.CounterJs(new CounterProps { val label = "clicks"; val start = 0 })
    )
    c.renderPromise(page.rawElement, new RenderProps { val title = "Hello" })
  }

  /** HTML 全体。app/routes/_renderer.tsx が呼ぶ */
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

- `IslandComponents` は `hx4sIslandComponents` が `Compile / sourceGenerators` で生成する（`server/target/.../src_managed`）。コミットしない。
- `@JSExportTopLevel` の 2 つ目の引数（moduleID）は、`@route` では server の設定と合わせる。`@islands` では Island ごとに指定できる。[Island のコード分割](islands.md#island-のコード分割)を参照。
- client の script は island の有無にかかわらず読み込む（シェルの `<script>`）。

### 6. Worker の設定

`wrangler.jsonc`。`nodejs_compat` の理由は [deployment.md](deployment.md)。

```jsonc
{
  "$schema": "node_modules/wrangler/config-schema.json",
  "name": "my-app",
  "main": "dist/server/index.js",
  "compatibility_date": "2025-09-01",
  // react-dom/server が node:async_hooks を import する
  "compatibility_flags": ["nodejs_compat"],
  // client の成果物だけを静的配信する（dist/server の Worker のコードは公開しない）
  "assets": {
    "directory": "dist/client"
  }
}
```

### 7. ビルドと実行

```bash
npm run build      # app/routes/index.ts と app/islands/counter.tsx を生成し、client と server をビルドする
npm run dev        # http://127.0.0.1:8787
```

JavaScript を無効にしても「Hello hx4s」とボタンが出て、有効にするとボタンをクリックするたびに数が増える。

生成した `app/routes/index.ts` と `app/islands/counter.tsx` は先頭に `// This file is generated by hx4s-codegen` が付く。コミットして、手で編集しない（[build-configuration.md](build-configuration.md)）。
`.gitignore` には `node_modules/`、`target/`、`project/target/`、`dist/`、`.wrangler/` などを入れる。

## 次に読むもの

| 目的 | 文書 |
| --- | --- |
| ルートを増やす | [routing.md](routing.md) |
| island の props と型 | [islands.md](islands.md) |
| hydration と世代ずれ | [ssr-hydration.md](ssr-hydration.md) |
| ページ全体を island にする構成、API | [spa.md](spa.md)、[api.md](api.md) |
