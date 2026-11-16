# ルーティング

hx4s は独自のルート表を持たない。`@route` は「どのファイルを生成するか」の宣言で、URL への対応・パラメータの解析・ルートの優先順位・`_renderer` や `_middleware` の適用は
HonoX のファイルルート（`app/routes`）に任せる。生成物は普通の HonoX のルートなので、読めば挙動が分かり、手書きのルートとも併用できる。

## `@route`

```scala
import hx4s.annotation.route
import scala.scalajs.js.annotation.JSExportTopLevel

object Main {
  @route("items/[id].ts")                      // GET
  @JSExportTopLevel("itemPage", "server")
  def itemPage(c: AppContext): js.Promise[Response] = ...

  @route("items/[id].ts", "POST")
  @JSExportTopLevel("itemUpdate", "server")
  def itemUpdate(c: AppContext): js.Promise[Response] = ...
}
```

| 項目 | 規則 |
| --- | --- |
| 対象 | 設定したソースのトップレベル object（パッケージの中を含む。入れ子の object は対象外）の `def` / `val`。object の名前は問わない |
| `filePath` | `app/routes` からの相対パス。`.ts` で終わる文字列リテラル |
| `method` | `GET`（既定）・`POST`・`PUT`・`DELETE`・`PATCH`・`OPTIONS`。文字列リテラル |
| `@JSExportTopLevel` | ちょうど 1 つ。名前と moduleID はリテラルで、moduleID は `"server"` |
| 複数の `@route` | 1 つのメンバに複数付けられる（同じ関数を複数のパス・メソッドに割り当てる） |
| 引数 | すべて文字列リテラル。名前付きで書くときは宣言の順（`filePath`、`method`） |

`@route` は型ではなく名前（`hx4s.annotation.route`）で見分けるので、import の別名は使えない。完全修飾名（`@hx4s.annotation.route(...)`）は書ける。

同じ `filePath` の複数のメソッドは 1 つの TS ファイルにまとまる。GET は `export default`、それ以外は `export const POST = ...` になる（`examples/minimal/app/routes/items/[id].ts` を参照）。

## ファイルパスと URL

URL は HonoX の `createApp` の規則で決まる。hx4s の codegen は衝突の検査のために同じ変換を Scala で持つ（`RoutePath`。HonoX の実装との一致は `tests/route-paths.test.js` が確かめる）。

| `filePath` | URL | 備考 |
| --- | --- | --- |
| `index.ts` | `/` | |
| `about.ts` | `/about` | |
| `blog/index.ts` | `/blog` | `blog.ts` と同じ URL になる |
| `items/[id].ts` | `/items/:id` | 動的パラメータ。`c.req.param("id")` で読む |
| `docs/[...slug].ts` | `/docs/:slug{.+}` | 残りのパス全体。`/docs/a/b/c` なら `slug` は `a/b/c` |
| `api/items.ts` | `/api/items` | |

末尾スラッシュの扱いは `createApp({ trailingSlash })`（HonoX）に従う。

使える `filePath` の文字は `A-Za-z0-9_.$[]-` と `/` だけ。`(group)` のようなディレクトリは書けない。

## HTTP メソッド

`GET`・`POST`・`PUT`・`DELETE`・`PATCH`・`OPTIONS` を書ける。HonoX の `createApp` がこの 6 つしか登録しないためで、`HEAD` は GET のハンドラが答える
（`examples/minimal` の `tests/build-output.test.js` が確かめている）。`ALL` は未実装。

同じ URL に複数のメソッドを置けるのは、**同じ `filePath`** にまとめたときだけ。別のファイルが同じ URL に同じメソッドを割り当てることは拒否される（次節）。

## codegen が拒否するもの

誤りは書き込みの前にまとめて検出され、1 件でもあれば何も書かない。`sbt server/hx4sGenerate` の失敗メッセージには次の形で出る。

```
[error] [hx4s-codegen] error: server/src/main/scala/example/server/Bad.scala:8: HEAD is not registered by HonoX createApp (GET handlers also answer HEAD)
[error] (server / hx4sGenerate) hx4s-codegen app failed: 1 error(s)
```

`<ソースのパス>:<行>: <理由>` の形で、パスは `hx4sProjectRoot` からの相対。

| 誤り | メッセージの例 |
| --- | --- |
| `HEAD` | `HEAD is not registered by HonoX createApp (GET handlers also answer HEAD)` |
| 未対応のメソッド | `unsupported HTTP method ALL` |
| `_` で始まるファイル名 | `route basename _hidden.ts is reserved by HonoX (leading _)` |
| 使えない文字（空白など） | `unsafe output path a b.ts: unsupported characters in a b.ts` |
| `@JSExportTopLevel` が無い | `annotated member needs exactly one @JSExportTopLevel` |
| 別のファイルが同じ URL・同じメソッド | `route files map GET to the same URL: items/[id].ts (/items/:id), items/[slug].ts (/items/:slug)` |

ほかに次も拒否する。

- HonoX が経路として拾わないファイル名: basename が `-` / `$` で始まる、`*.test.ts` / `*.spec.ts`、`-` で始まるディレクトリ、`.` で始まる名前や `node_modules`（先頭の `.well-known/` は許す）
- パスの誤り: 空の区切り、`.` / `..`、絶対パス、バックスラッシュ、末尾のドット、Windows の予約名（`CON` など）
- 重複: 同じ export 名を複数のメンバに付ける、同じ route のメソッドを 2 回、大文字小文字だけ違うパス
- moduleID が `server` でない、引数が文字列リテラルでない

パラメータ名だけ違う `items/[id].ts` と `items/[slug].ts` も、HonoX では同じ URL パターンになるので衝突として拒否される。
固定の区切りと動的パラメータ（`items/new.ts` と `items/[id].ts`）は HonoX の並び順で両立するので、拒否しない。

## `Context`

ハンドラの引数は、Hono の Context の facade `hx4s.hono.Context[Env, R]`。`Env` は `c.env` の型、`R` は `c.render` に渡す renderer の props の型（`js.Object` の派生）。

```scala
type AppContext = Context[js.Object, RenderProps]

trait Bindings extends js.Object {
  val GREETING: js.UndefOr[String]
}

@route("hello.ts")
@JSExportTopLevel("helloPage", "server")
def helloPage(c: Context[Bindings, js.Object]): Response =
  c.html(s"<p>${c.env.GREETING.getOrElse("hello")}</p>")
```

戻り値は `org.scalajs.dom.Response` か `js.Promise[Response]`。`Request` / `Response` / `FormData` は scalajs-dom の型。

| 対象 | メンバ |
| --- | --- |
| `c.req`（`HonoRequest`） | `raw`（scalajs-dom の `Request`）、`method`、`url`、`path`、`param(name)`、`query(name)`、`queries(name)`、`header(name)`、`text()`、`json()`、`formData()` |
| 応答 | `c.text(body, status)`、`c.json(body, status)`、`c.html(body, status)`、`c.body(data, status, headers)`、`c.redirect(location, status)`、`c.notFound()`、`c.status(code)`、`c.header(name, value)`、`c.res` |
| 描画 | `c.render(children, props)`、拡張メソッド `c.renderPromise(children, props)`（戻り値を `Promise` にそろえる） |
| 変数 | `c.set(key, value)`、`c.get(key)`、拡張メソッド `setVariable` / `getVariable`（`ContextVariable[A]`） |
| 実行コンテキスト | `c.executionCtx`、拡張メソッド `executionCtxOption`（`waitUntil`、`passThroughOnException`） |
| 環境 | `c.env`（型は `Env`） |

`param`・`query`・`header` は `js.UndefOr[String]` を返す。拡張メソッドを使うには `import hx4s.hono.Context.*` が要る。

- **本文は 1 種類だけ読める**: `text()` / `json()` / `formData()` は Hono が結果をキャッシュするが、`c.req.raw` の本文を直接読んだあとには使えない
- **`executionCtx`**: 実行基盤が渡していないと Hono が例外を投げる。Node 上の検証など渡さない呼び出しでも動かすなら `executionCtxOption` を使う
- **本文の無い応答**（`204`・`304`）: `c.body(null, 204)` で返す。`c.text("", 204)` は空文字列でも本文ありになり、Response の規則で失敗する
- **Not Found**: `c.notFound()` はアプリの Not Found ハンドラの応答を返す。戻り値は `Response | js.Promise[Response]`（[api.md](api.md)）
- **Hono の Context にあるが facade に無いもの**: 自分で `@js.native` の facade を書くか、手書きの TS のルートで扱う

### 変数（`ContextVariable`）

手書きのミドルウェアが `c.set` した値を Scala から型付きで読む例。名前を 1 か所に定義して、書く側と読む側で共有する。

TypeScript の `c.set` は、Hono の `ContextVariableMap` に宣言したキーしか受け付けない。Scala の `ContextVariable` は TypeScript の宣言にはならないので、
`tsconfig.json` の `include` に入る宣言ファイルで足す。

```ts
// app/env.d.ts（手書き）
import "hono";

declare module "hono" {
  interface ContextVariableMap {
    requestId: string;
  }
}
```

```ts
// app/routes/api/_middleware.ts（手書き）
import { createRoute } from "honox/factory";

export default createRoute(async (c, next) => {
  c.set("requestId", "req-1");
  await next();
});
```

```scala
val RequestId: ContextVariable[String] = ContextVariable("requestId")

@route("api/whoami.ts")
@JSExportTopLevel("whoami", "server")
def whoami(c: ApiContext): Response =
  c.json(js.Dynamic.literal(requestId = c.getVariable(RequestId).orUndefined))
```

`getVariable` は未設定なら `None`。型の保証は呼び出し側の約束で、実行時には検査しない（TypeScript の宣言と Scala の `ContextVariable` の型は、自分でそろえる）。
この TypeScript の 2 つのファイルは、hx4s の `npm run typecheck` が `tests/docs-typecheck` で型検査している。

## 手書きのルートとの併用

`app/routes` には手書きの TS のファイルを置ける（`createRoute` を使う普通の HonoX のルート、`_renderer.tsx`、`_middleware.ts`）。

- 生成ファイルを見分けるのは先頭の生成マーカー行。マーカーが無いファイルは、上書きも削除もしない
- 手書きファイルが生成するパスと同じ場所にあると、生成は `refusing to overwrite a file without the generated marker` で失敗する
- **手書きのルートと生成ルートの URL の衝突は検査しない**（警告も出さない。未実装）。同じ URL に両方を置いたときの挙動は検証していないので、置かない

## 未実装

- 型付きルート（shared に URL を宣言して、パラメータの取得と URL の組み立てを型で行う）
- Client Router（クライアント側のルーティング）
- `ALL` メソッド
- 手書きルートとの衝突の警告

見込みは [hx4s/routing-design.md](hx4s/routing-design.md) 4 章と [hx4s/roadmap.md](hx4s/roadmap.md)。
