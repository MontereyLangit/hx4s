# examples

minimal と api-only は小規模プロジェクトの構成例。規模を広げる場合の構成と、minimal のハンドラからビューと island につながる流れは [Scala のソース構成](source-organization.md) を参照。

## minimal

`examples/minimal` は SSR + Islands の最小のアプリ。Cloudflare Workers（wrangler）で動かし、hx4s はローカル公開した成果物だけで解決する（ソースツリーを参照しない）。
動かし方は [getting-started.md](getting-started.md)。

### 画面と API

| URL | 内容 | 使っている機能 |
| --- | --- | --- |
| `/` | 静的な SSR のページに、カウンターの island 2 つ（別々の props）と、配列・入れ子・省略できるキーを持つ一覧の island 1 つ | `IslandComponents`、`IslandExport`、`JsShape` |
| `/items/:id` | 動的ルート。無い ID は 404。名前を変更するフォームを持つ | `@route("items/[id]")`、`c.req.param`、`c.status` |
| `POST /items/:id` | フォームの値を検証し、保存した名前またはエラーを描画する（保存はしない） | `@route("items/[id]", "POST")`、`c.req.formData()` |
| `/api/items` | 品目の JSON。`?tag=` を繰り返すと、そのタグをすべて持つ品目に絞る | `c.json`、`c.req.queries` |

### ファイルの役割

| パス | 役割 |
| --- | --- |
| `build.sbt` / `project/plugins.sbt` | `shared`・`server`・`client` の 3 プロジェクト。`hx4sClientProject` と、server の `Test / jsEnv`（Node の loader と client の先行リンク）。プラグインの版は環境変数 `HX4S_VERSION` か `target/local/hx4s-version.txt` から読む |
| `shared/.../Props.scala` | island の props の trait と `JsField` の検証規則（`CounterProps`、`ItemListProps` ほか） |
| `server/.../Main.scala` | `@route` のハンドラと `renderShell`（HTML のシェル） |
| `server/.../Pages.scala` | ページの SSR。island は生成された `IslandComponents` で置く |
| `server/.../Catalog.scala` | サンプルのデータと検証（保存はしない）。記号を含む名前で、エスケープの確認に使う |
| `server/.../Shell.scala` | HTML 全体 |
| `client/.../Main.scala` | `@islands[P]` を付けた `IslandExport` の export |
| `client/.../Counter.scala`・`ItemList.scala` | island のコンポーネント（`ScalaFnComponent` と hooks） |
| `app/server.ts`・`app/client.ts` | HonoX の入口。`client.ts` は `islandPropsProblem` を呼ぶ |
| `app/routes/_renderer.tsx` | `renderShell` を呼ぶ renderer（手書き） |
| `app/routes/**/*.ts`・`app/islands/*.tsx` | 生成物（先頭に生成マーカー。コミットしてある） |
| `app/renderer.d.ts`・`app/scalajs-server.d.ts` | 型の宣言（手書き） |
| `vite.config.ts` | `hx4s()` と Cloudflare Workers のアダプタ |
| `wrangler.jsonc` | Worker と静的配信の設定（`nodejs_compat`） |
| `package.json` / `package-lock.json` | 依存。`hx4s-vite` は載せず、tgz を `--no-save` で入れる |

### 確かめられること

| テスト | 内容 |
| --- | --- |
| `server/src/test/.../TypedPropsSuite.scala`（`sbt test`） | `IslandComponents` が `@islands[P]` の `P` だけを受け付けること（コンパイルエラーの確認）、SSR の境界と props、データ・検証の単体 |
| `tests/build-output.test.js` | ビルドした Worker を Node で呼び、SSR の HTML・動的ルートと 404・HEAD・POST の検証・JSON の API・client のバンドルに server だけの文字列が無いことを確かめる |
| `tests/hydration.test.js` | jsdom 上で `app/client.ts` を動かし、hydrate・クリック・props の不一致の island が SSR のままであることを確かめる |
| `tests/e2e.test.js` | `wrangler dev` と Playwright（Chromium）で、hydrate の誤りがなくクリックで状態が変わること、静的配信の範囲を確かめる |

`npm test` で全部が走る（E2E は `HX4S_SKIP_E2E=1` で飛ばせる）。CI は Ubuntu と Windows で、ローカル公開から `npm test` までを通す（`.github/workflows/ci.yml`。api-only も同じ）。

## api-only

`examples/api-only` は JSON の API だけのアプリ。client（island）を持たず、`react`・`react-dom`・`@hono/react-renderer` に依存しない。
`vite.config.ts` で `hx4s({ mode, client: false, … })` を使い、`vite build` の 1 回で Worker を作る（[api.md](api.md)）。hx4s の解決はローカル公開の成果物だけ（minimal と同じ）。

| URL | 内容 | 使っている機能 |
| --- | --- | --- |
| `GET /api/notes` | メモの一覧の JSON | `c.json` |
| `POST /api/notes` | JSON の本文を `JsShape` と値の規則で確かめ、201 か 400 を返す（保存はしない） | `c.req.json()`、`JsShape.validate` |
| `GET /api/notes/:id` | 1 件の JSON。無い ID は Not Found | `c.notFound()` |
| `DELETE /api/notes/:id` | 本文なしの 204。無い ID は 404 | `c.body(null, 204)` |

| パス | 役割 |
| --- | --- |
| `build.sbt` / `project/plugins.sbt` | `server` の 1 プロジェクト（`hx4sClientProject` は設定しない） |
| `server/.../Main.scala`・`Notes.scala` | `@route` のハンドラと、サンプルのデータ・検証 |
| `app/server.ts` | HonoX の入口（`createApp()` だけ。renderer は無い） |
| `app/routes/**/*.ts` | 生成物（コミットしてある） |
| `vite.config.ts` | `hx4s({ client: false })` と Cloudflare Workers のアダプタ |
| `tests/api.test.js` | ビルドした Worker を Node で呼び、各 API の応答と、Worker のバンドルに React が入っていないことを確かめる |

```bash
node scripts/publish-local.mjs
node scripts/setup-example.mjs api-only
cd examples/api-only && npm run build && npm test
```

`wrangler` は入れておらず、Workers（`wrangler dev`）での起動は確かめていない。テストは Node で Worker の `fetch` を呼ぶところまで。

## examples に無いもの

次は examples に含まれず、この文書群でも検証が限られている。

- ページ全体を 1 つの island にする構成（[spa.md](spa.md) で試作して確かめた範囲）
- 要素 props を持つ island、island の入れ子を `IslandComponents` で書く例
- Cloudflare 以外の実行基盤
