# テスト

hx4s 自体のテストの構成と実行方法。アプリのテストの書き方は [ssr-hydration.md](ssr-hydration.md) と [examples.md](examples.md)。

## 実行

```bash
npm ci                        # 先に 1 回。react のテストは node_modules の react と honox を使う
sbt test                      # munit（core / react / codegen）
sbt sbtHx4s/scripted          # sbt-hx4s の scripted テスト
sbt 'set ThisBuild / scalacOptions += "-no-indent"' Test/compile   # インデント構文が残っていないことの確認
npm test                      # Node のテスト（hx4s-vite のビルド、SSR・hydration、独立性など）
npm run typecheck             # hx4s-vite の型検査
node scripts/check-independence.mjs   # 独立性の検査だけ
```

examples は [examples.md](examples.md)。CI（`.github/workflows/ci.yml`）は Ubuntu と Windows で上の全部と、ローカル公開から examples の `npm test` までを通す。

## 構成

| 層 | 場所 | 内容 |
| --- | --- | --- |
| core | `modules/core/shared/src/test`、`modules/core/js/src/test` | アノテーション（`AnnotationsSuite`）、`JsShape` の検証（`JsShapeSuite`） |
| codegen | `modules/codegen/src/test` | アノテーションの読み取りと検査（`AnnotationScannerSuite`）、生成コードの描画（`AppFileRendererSuite`）、引数の解釈（`CodegenMainSuite`）、書き出しの安全策（`OutputWriterSuite`）、URL 変換（`RoutePathSuite`） |
| react | `modules/react/src/test` | `Island`、`IslandExport`、`Context`。実物の `Hono` に Scala のハンドラを登録して `fetch` する（`ContextSuite`）。Node 上で動く（`Test / jsEnv` が `vite/node-register.mjs` を `--import` する） |
| sbt-hx4s | `sbt-plugin/src/sbt-test/hx4s/` | scripted。`generate`（生成・再生成・古い生成物の削除・client なしの構成・`sourceGenerators`）、`error`（誤ったアノテーションでの失敗と行番号、誤りの行がログに 1 回だけ出ること）、`dependencies`（禁止された依存の検出と経路） |
| Node | `tests/*.test.js` | 下の表 |
| 独立性 | `scripts/check-independence.mjs`（`tests/independence.test.js` が呼ぶ） | 移動元のプロジェクトの名前がファイルに無いこと（`docs/hx4s/`・`LICENSE`・`NOTICE` を除く）。examples が hx4s のソースツリーや絶対パスを参照していないこと |

### Node のテスト（`npm test`）

`pretest` が `hx4s-vite` をビルドし、react のテスト用リンク（`react/Test/fastLinkJS`）と生成 TS の fixture を作る。

| ファイル | 内容 |
| --- | --- |
| `islands-bridge.test.js` | `hx4s()` のプラグイン構成（moduleID・接頭辞・直列化・出力名・`client: false`）、HonoX が island を React で SSR して props をシリアライズすること |
| `islands-hydration.test.js` | 実際の Scala の `Island` で SSR し、jsdom で hydrate する。単純・入れ子・要素 props、危険な文字列と複数インスタンス、Scala.js の client island（`IslandExport`）、props の不一致で hydrate せず SSR のままになること |
| `generated-ts.test.js` | 識別子が衝突しうる export 名でも、生成 TS が構文として正しく、各メソッドが対応するハンドラを呼ぶこと |
| `route-paths.test.js` | `RoutePathSuite` と同じ表を HonoX の `createApp` に通し、登録される URL パターンが一致すること |
| `node-loader.test.js` | `hx4s-vite/node-loader` を利用側の loader と並べて登録できること |
| `scripts-command.test.js` | スクリプトが空白と `&` を含むパスでも npm / sbt を起動できること |
| `independence.test.js` | 独立性の検査 |
| `version.test.js` | `vite/package.json`・`package-lock.json` の版が `version.txt` と同じこと、`build.sbt` とローカル公開の版が `version.txt` から作られること |

## テストの方針

- 互換性の依存先は HonoX の内部の挙動（`component-name` の形式、`hono/jsx` の置換、`filePathToPath` など）に多い。HonoX の版を上げるときは、これらのテストで挙動を照合する（[hx4s/compatibility.md](hx4s/compatibility.md) 3 章）
- 型付き props のコンパイルの検査は、Scala の `compileErrors` で書く
- 書き出しの負のテスト（リンク・衝突・途中失敗からの収束）は `OutputWriterSuite` にある。Windows ではジャンクションのテストが走り、シンボリックリンクのテストは作成の権限が無いと `assume` で飛ぶ

## 未実装のテスト

| 項目 | 状態 |
| --- | --- |
| `honox@latest`・`vite@latest` での定期実行 | 未実装。検証は固定した版だけ |
| Vite のモジュールグラフによる依存方向の検査 | 未実装（sbt の依存グラフと、バンドルの文字列検査だけ） |
| 非有限の `Double`・循環参照を含む props の挙動 | 未実装 |
| CI のリモートでの実行 | ワークフローは置いてあるが、リモートでは未実行 |
