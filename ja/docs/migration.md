# 既存の構成からの移行

Scala.js と HonoX を手書きでつないでいるプロジェクトを、hx4s に移すときの一般的な手順を書く。
典型的な移行元は、次のような構成。

- Scala.js の server が `@JSExportTopLevel("name", "server")` でハンドラを export し、`app/routes/**/*.ts` の薄い TS がそれを `scalajs:server.js` から import している（手書き、または自作の生成器）
- island の境界を付ける `HonoXIsland` の facade を自前で持ち、props は `js.Object` / `js.Dynamic` で組み立てている
- `vite.config.ts` に、Scala.js のプラグインの登録・`scalajs:client.js` の別名・client ビルドの出力名が書いてある
- Hono の Context の facade を自前で持つ

移行は段階に分けて、各段階でビルドとテストを通す。

## 1. 依存と sbt の構成

1. hx4s をローカル公開し、`sbt-hx4s` と `hx4s-react` を入れる（[installation.md](installation.md)）
2. server・client・shared の 3 つのプロジェクトに分ける。server が `Hx4sPlugin` を有効にして `hx4sClientProject := client` とする
3. **依存の向きを整える**: client は server に依存しない。server も client に依存しない（island の実体は仮想モジュール `scalajs:client.js` 経由）。
   両方が使う型（props）は、どちらにも依存しない shared に置く。違反すると `hx4sCheckDependencies` が経路を示して失敗する
4. プロジェクト名を `server` と `client` にするか、`hx4s()` の `server.projectID` / `client.projectID` で指定する

## 2. route を `@route` に置き換える

1. ハンドラに `@route("<app/routes からのパス>", "<メソッド>")` を付ける。`@JSExportTopLevel` はそのまま（moduleID は `"server"`）
2. 手書きの TS のラッパーを**削除**してから `sbt server/hx4sGenerate` を実行する。生成マーカーの無いファイルは上書きしないので、残っていると
   `refusing to overwrite a file without the generated marker` で止まる
3. 生成された `app/routes/**/*.ts` と、移行前のラッパーを比べる。URL が変わっていないことを確かめる

次の点は、移行元の生成器より厳しいことがある。

| hx4s の規則 | 内容 |
| --- | --- |
| `HEAD` を拒否 | HonoX の `createApp` は登録しない。GET のハンドラが HEAD にも答える |
| HonoX が拾わないファイル名を拒否 | basename が `_` / `-` / `$` で始まる、`*.test.ts` / `*.spec.ts` など |
| URL の衝突を拒否 | 別のファイルが同じ URL・同じメソッドになるもの（`foo.ts` と `foo/index.ts`、`[a].ts` と `[b].ts`） |
| `@route` の引数は文字列リテラル | 変数や連結は書けない |
| `@route` は名前で見分ける | import の別名は使えない |

### 旧い生成マーカーを使っていたとき

自作の生成器が先頭行に別の文言（バナー）を付けていたなら、そのファイルは hx4s から見ると手書きになり、上書きされない。次のどちらかを選ぶ。

- `hx4sBanner` と `hx4sGeneratedMarker` に旧い値を設定し、旧い生成物を置き換える。設定は残す（外すと、旧いマーカーのファイルが再び手書き扱いになる）
- 旧い生成ファイルを一度削除してから、既定のバナーで生成し直す

## 3. island と props

1. client の island を `IslandExport[P]` として export し、`@islands[P]("<basename>.tsx")` を付ける。`P` は完全修飾名。basename は `[A-Za-z0-9-]+\.tsx`
2. props を `js.Dynamic` / `js.Object` から、shared の JS trait に置き換える。trait と一緒に `JsField` のリストを書く（[islands.md](islands.md)）
3. server の呼び出しを、自前の island 呼び出しから生成された `IslandComponents.<export 名>(props)` に替える。メソッド名は `@JSExportTopLevel` の名前そのまま
4. 自前の `HonoXIsland` の facade は、`IslandComponents` が使う `hx4s.react.Island` に置き換わるので、使わなくなる

props に使える型は限られる（`String`・`Int`・`Double`・`Boolean`・`js.UndefOr`・`js.Array`・props trait）。`Option`・Scala のコレクション・case class・`Long` を渡していた箇所は、変換が要る。
古い island と新しい HTML が混在する期間がある場合は、フィールドの追加を `js.UndefOr` にする（[ssr-hydration.md](ssr-hydration.md)）。

## 4. Context

自前の Context の facade を `hx4s.hono.Context[Env, R]` に置き換えられる。

- `Env` は `c.env` の型。基盤の Bindings の型（trait）を自分で定義して渡す
- `R` は `c.render` に渡す renderer の props の型
- **Request / Response / FormData は scalajs-dom の型**。独自の型や `js.Dynamic` で受けていたコードは、scalajs-dom の型に合わせる
- `c.executionCtx` は基盤が渡さないと例外になるので、渡さない呼び出しがあるなら `executionCtxOption` を使う
- `204` / `304` のような本文の無い応答は `c.body(null, 204)`、Not Found は `c.notFound()` で返す（[api.md](api.md)）
- 足りないメンバ（`parseBody`・`arrayBuffer` など）は facade に無い。自前の facade を残すか、`@js.native` の trait を追加する

## 5. Vite と client の入口

1. `vite.config.ts` の Scala.js のプラグインの登録と `scalajs:client.js` の別名、client ビルドの出力名の設定を、`hx4s({ mode, sbt: { cwd } })` に置き換える
2. 実行基盤のアダプタ（`@hono/vite-build/...`）と、その固有のオプションは、`hx4s()` が返す配列に足して残す
3. HonoX の設定は `hx4s({ honox: { ... } })` で渡す。`islandComponents.reactApiImportSource` の既定は `"react"`
4. `package.json` に `overrides` の `"@scala-js/vite-plugin-scalajs": { "vite": "$vite" }` を入れ、`hx4s-vite` を入れる
5. `app/client.ts` の hydrate で `islandPropsProblem` を呼ぶ（[getting-started.md](getting-started.md)）
6. client の出力名を変えている場合は `client.output` で指定する（[build-configuration.md](build-configuration.md)）

`hx4s()` は server モードで client の sbt プロジェクトもリンクする。client の無い構成（API だけ・island の無い SSR）は `hx4s({ mode, client: false, ... })` にし、sbt 側は `hx4sClientProject` を設定しない（[api.md](api.md)、[build-configuration.md](build-configuration.md)、`examples/api-only`）。

## 6. Node 上のテスト

Node で Scala.js のテストを動かしているなら、`scalajs:client.js` と HonoX 内の `hono/jsx` を解決する loader が要る。hx4s の loader（`hx4s-vite/node-register`）に替える。
環境変数は `HX4S_CLIENT_JS_DIR`（client の `fastLinkJS` の出力ディレクトリ）。基盤固有の指定子（`cloudflare:workers` など）の loader が別にあるなら、
`hx4s-vite/node-loader` と並べて登録する（[ssr-hydration.md](ssr-hydration.md)、[hx4s/build-integration.md](hx4s/build-integration.md) 2.3）。

## 7. 移行の確認

- 生成された route・island の TS が、移行前と同じ URL・同じ island を指していること（バナーと import の別名を除く）
- 既存のテスト（SSR の HTML、hydration、API の応答）が通ること
- client のバンドルに server だけのコードが入っていないこと。依存の向きの検査は sbt のグラフだけなので、バンドルの文字列でも確かめる
- 生成ファイルをコミットし、CI で再生成して差分が無いことを確かめる（`examples/minimal` の CI が同じ方式）
