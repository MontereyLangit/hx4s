# API

HTML を返さないハンドラも `@route` で書く。特別な API は無く、`Context` の応答メソッドで JSON やステータスを返す。
型付き API クライアントと OpenAPI の出力は**未実装**（[hx4s/roadmap.md](hx4s/roadmap.md)）。

以降のハンドラは `examples/minimal` に足してビルドし、ビルドした Worker の `fetch` を呼んで応答を確かめたもの。

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

本文を持たない応答（`204`）は、`c.text("", 204)` では Response の規則で失敗する。scalajs-dom の `Response` を直接作る。

```scala
import org.scalajs.dom.{Response, ResponseInit}

@route("api/items/[id].ts", "DELETE")
@JSExportTopLevel("itemDelete", "server")
def itemDelete(c: ApiContext): Response = new Response(null, new ResponseInit { status = 204 })
```

## エラー応答

hx4s は API のエラー形式を決めない。業務上の拒否（検証の失敗・未検出）は、上の `404`・`400` のように自分で組み立てて返す。
ハンドラが例外を投げると、応答は `500 Internal Server Error`（プレーンテキスト）になる（`c.text("", 204)` の失敗で確かめた）。例外の形をそろえたい場合の仕組みは、hx4s では用意していない。

## React を使わない構成

API だけなら、client も React も要らない。次の構成で、`@route` のハンドラが JSON を返す Worker をビルドして Node から呼んで確かめた
（`react`・`react-dom`・`@hono/react-renderer` を `node_modules` から消した状態）。

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
import honox from "honox/vite";
import { scalaProject } from "hx4s-vite";

// hx4s() は client の Scala.js プロジェクトを要るので使わない
export default defineConfig({
  plugins: [honox(), scalaProject(import.meta.dirname, "server"), buildCloudflareWorkers({ outputDir: "dist/server" })],
});
```

```scala
object Main {
  type ApiContext = Context[js.Object, js.Object]

  @route("hello/[name].ts")
  @JSExportTopLevel("hello", "server")
  def hello(c: ApiContext): Response = c.json(js.Dynamic.literal(message = s"hello ${c.req.param("name").getOrElse("")}"))
}
```

`app/server.ts` は `createApp()` だけ（[getting-started.md](getting-started.md)）。`package.json` の依存は `hono`・`honox`・`@hono/vite-build`・`@scala-js/vite-plugin-scalajs`・`vite`・`wrangler`（`hx4s-vite` は tgz）。

**`hx4s()` は client が無い構成を扱えない。** server モードで client のプロジェクトも sbt でリンクするため、`client` が無いと `Not a valid key: client` で失敗する。
`scalaProject` と `honox()` を直接組み合わせるのが今の方法で、`hx4s()` 側の対応は未定。

## 未実装

- 型付き API クライアント（server の `@route` から client の呼び出しを型付きで作る）
- OpenAPI の出力
- API のエラー形式・ミドルウェアの補助（認証・CORS などは HonoX / Hono の手書きのミドルウェアで行う）
