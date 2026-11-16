# API

HTML を返さないハンドラも `@route` で書く。特別な API は無く、`Context` の応答メソッドで JSON やステータスを返す。
型付き API クライアントと OpenAPI の出力は**未実装**（[hx4s/roadmap.md](hx4s/roadmap.md)）。

以降のハンドラは `examples/minimal` に足すか `examples/api-only` にあるもので、ビルドした Worker の `fetch` を呼んで応答を確かめた。

```scala
object Api {
  type ApiContext = Context[js.Object, RenderProps]
  ...
}
```

## JSON を返す

```scala
@route("api/items/[id].ts")
@JSExportTopLevel("itemGet", "server")
def itemGet(c: ApiContext): Response = {
  val id = c.req.param("id").getOrElse("")
  Catalog.find(id) match {
    case Some(item) => c.json(new ItemProps { val id = item.id; val name = item.name; val tags = item.tags.toJSArray })
    case None => c.json(js.Dynamic.literal(error = s"item $id not found"), 404)
  }
}
```

`c.json(body, status)` の `body` は `js.Any`。`js.Dynamic.literal(...)`、JS の trait、`js.Array` を渡せる。Scala の case class や `List` は、先に JS の値に変換する。
応答は `content-type: application/json` になる。

`examples/minimal` の `api/items.ts` は `?tag=果物&tag=赤` のように繰り返したクエリを `c.req.queries("tag")` で読み、JSON の配列を返す。

## リクエストを読む

| 読むもの | 書き方 | 結果 |
| --- | --- | --- |
| パスのパラメータ | `c.req.param("id")` | `js.UndefOr[String]` |
| クエリ | `c.req.query("q")` / `c.req.queries("tag")` | `js.UndefOr[String]` / `js.UndefOr[js.Array[String]]` |
| ヘッダ | `c.req.header("user-agent")` | `js.UndefOr[String]` |
| JSON の本文 | `c.req.json()` | `js.Promise[js.Any]` |
| フォームの本文 | `c.req.formData()` | `js.Promise[FormData]`（scalajs-dom） |
| テキストの本文 | `c.req.text()` | `js.Promise[String]` |

本文は 1 種類だけ読める。Promise は `toFuture` で Future にして、`toJSPromise` で戻す。

```scala
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js.JSConverters.*

@route("api/echo.ts", "POST")
@JSExportTopLevel("echoPost", "server")
def echoPost(c: ApiContext): js.Promise[Response] =
  c.req.json().toFuture.map(body => c.json(body, 201)).toJSPromise     // 本文をそのまま 201 で返す

@route("api/items/[id].ts", "PUT")
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

本文の型は検証されない。受け取る JSON の形を確かめるなら、`JsShape.validate[P](raw, fields)`（[islands.md](islands.md)）が使える。

## ステータスとヘッダ

```scala
c.json(body, 404)                    // ステータスを指定して返す
c.status(404); c.renderPromise(…)    // 先にステータスを設定して描画する
c.header("cache-control", "no-store")
c.redirect("/", 302)
```

本文を持たない応答（`204`・`304`）は `c.body(null, status)` で返す。`c.text("", 204)` は使えない。Hono の `c.text` は空文字列でも本文ありの
`Response` を作り、Fetch API の `Response` は 204 / 304 に本文を許さないので、例外になって `500` が返る。

```scala
@route("api/notes/[id].ts", "DELETE")
@JSExportTopLevel("noteDelete", "server")
def noteDelete(c: ApiContext): Response = {
  val id = c.req.param("id").getOrElse("")
  if (Notes.find(id).isDefined) c.body(null, 204) else c.json(js.Dynamic.literal(error = s"note $id not found"), 404)
}
```

`c.body(data, status, headers)` の `data` は scalajs-dom の `BodyInit`（文字列・`Blob`・`FormData` など）か `null`、`headers` は `js.Dictionary[String]`。
`content-type` は付かないので、本文を返すときはヘッダで指定する。

`c.notFound()` はアプリの Not Found の応答（Hono の既定では `404` と本文 `404 Not Found`）を返す。戻り値は `Response | js.Promise[Response]` なので、ハンドラの戻り値の型もそれに合わせる。

```scala
@route("api/notes/[id].ts")
@JSExportTopLevel("noteGet", "server")
def noteGet(c: ApiContext): Response | js.Promise[Response] =
  Notes.find(c.req.param("id").getOrElse("")).fold(c.notFound())(note => c.json(toJs(note)))
```

この 2 つは `examples/api-only` にある。

## エラー応答

hx4s は API のエラー形式を決めない。業務上の拒否（検証の失敗・未検出）は、上の `404`・`400` のように自分で組み立てて返す。
ハンドラが例外を投げると、応答は `500 Internal Server Error`（プレーンテキスト）になる（`c.text("", 204)` の失敗で確かめた。`hx4s-react` の `ContextSuite`）。例外の形をそろえたい場合の仕組みは、hx4s では用意していない。

## React を使わない構成

API だけなら、client も React も要らない。`examples/api-only` がこの構成で、`react`・`react-dom`・`@hono/react-renderer` に依存しない。

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

`Context` は `hx4s-react` にあるので、依存は残す。island を使わなければ、React に関するコードはリンクされない。`hx4sClientProject` は設定しない（`IslandComponents` は空になる）。

```ts
// vite.config.ts
import { defineConfig } from "vite";
import buildCloudflareWorkers from "@hono/vite-build/cloudflare-workers";
import hx4s from "hx4s-vite";

export default defineConfig(({ mode }) => ({
  plugins: [...hx4s({ mode, client: false, sbt: { cwd: import.meta.dirname } }), buildCloudflareWorkers({ outputDir: "dist/server" })],
}));
```

`client: false` にすると、`hx4s()` は client の Scala.js プロジェクトのリンク・`scalajs:client.js` の別名・client ビルドを行わず、`honox()` に React の設定も足さない。
ビルドは `vite build` の 1 回だけで、`vite build --mode client` はエラーになる。

`app/server.ts` は `createApp()` だけ（[getting-started.md](getting-started.md)）。`package.json` の依存は `hono`・`honox`・`@hono/vite-build`・`@scala-js/vite-plugin-scalajs`・`vite`（`hx4s-vite` は tgz）。

## 未実装

- 型付き API クライアント（server の `@route` から client の呼び出しを型付きで作る）
- OpenAPI の出力
- API のエラー形式・ミドルウェアの補助（認証・CORS などは HonoX / Hono の手書きのミドルウェアで行う）
