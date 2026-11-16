# examples

## minimal

`examples/minimal` は SSR + Islands の最小のアプリ。Cloudflare Workers（wrangler）で動かし、hx4s はローカル公開した成果物だけで解決する（ソースツリーを参照しない）。
動かし方は [getting-started.md](getting-started.md)。

### 画面と API

| URL | 内容 | 使っている機能 |
| --- | --- | --- |
| `/` | 静的な SSR のページに、カウンターの island 2 つ（別々の props）と、配列・入れ子・省略できるキーを持つ一覧の island 1 つ | `IslandComponents`、`IslandExport`、`JsShape` |
| `/items/:id` | 動的ルート。無い ID は 404。名前を変更するフォームを持つ | `@route("items/[id].ts")`、`c.req.param`、`c.status` |
| `POST /items/:id` | フォームの値を検証し、保存した名前またはエラーを描画する（保存はしない） | `@route("items/[id].ts", "POST")`、`c.req.formData()` |
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

`npm test` で全部が走る（E2E は `HX4S_SKIP_E2E=1` で飛ばせる）。CI は Ubuntu と Windows で、ローカル公開から `npm test` までを通す（`.github/workflows/ci.yml`）。

### examples に無いもの

次は examples に含まれず、この文書群でも検証が限られている。

- API だけの構成、ページ全体を 1 つの island にする構成（[api.md](api.md)、[spa.md](spa.md) で試作して確かめた範囲）
- 要素 props を持つ island、island の入れ子を `IslandComponents` で書く例
- Cloudflare 以外の実行基盤
