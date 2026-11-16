# ロードマップ

2026-11-16 時点。**hx4s 側の実装は無い**。「3ch で実装済み」は 3ch の中に同等の機能があることを意味し、hx4s として切り出し済みという意味ではない。

## 1. P0（最初の分離で揃えるもの）

| 機能 | モジュール | 状態 |
| --- | --- | --- |
| `@route(filePath, method)` | hx4s-core | 3ch で実装済み（`io._3ch.common.routes.route`）。hx4s は未実装 |
| `@islands[P](filePath)`（型引数で Props を指定） | hx4s-core | 型引数なしの `@islands(filePath)` は 3ch で実装済み。型引数は 3ch でも未実装（計画のみ） |
| HonoX Island ブリッジ `Island(component, props, componentName)` | hx4s-react | 3ch で実装済み（`external/honox/Island.scala`） |
| 汎用 Hono Context facade（Env を型引数） | hx4s-react | 3ch は `env: WorkerEnv` 固定で実装済み。型引数化は未実装 |
| 型付き Props の補助（`IslandComponents.Xxx(props: P)`、`validate` の書き方） | hx4s-react / hx4s-codegen | 未実装 |
| アノテーション解析・route TS / island TSX / `IslandComponents` 生成 | hx4s-codegen | 3ch で実装済み（入力パス・パッケージが固定）。設定 case class 化は未実装 |
| 安全な書き出し（バナー保護、古い生成物の削除、大文字小文字の衝突） | hx4s-codegen | 3ch で実装済み |
| 書き出しの追加規則（同内容なら書かない、改行の正規化、正規化後パスの検査） | hx4s-codegen | 未実装 |
| `@route` の検査強化（`HEAD` 拒否、HonoX が無視するファイル名、URL 衝突） | hx4s-codegen | 未実装 |
| CLI main（引数で設定を受ける） | hx4s-codegen | 3ch は引数なしの main（`GenerateAppFiles`）。未実装 |
| 設定キーと `hx4sGenerate`、専用 configuration と fork 実行 | sbt-hx4s | 未実装 |
| Scala.js プロジェクトの登録・`buildStart` 直列化・`scalajs:client.js` alias・honox の React 設定・client ビルド設定 | hx4s-vite | 3ch の `vite.config.ts` に実装済み。パッケージ化は未実装 |
| Node 用 loader（`scalajs:client.js`、HonoX 内の `hono/jsx`） | hx4s-vite | 3ch の `bbs/test-stubs` に実装済み（Cloudflare のスタブと同居）。未分離 |
| `examples/minimal`（3ch に依存しない SSR + Islands） | examples | 未実装 |
| 互換性テスト（SSR・hydration・Props の往復・ルート・URL 変換・ビルド） | 各モジュール | SSR・hydration は 3ch の `tests/islands-*.test.js` に実装済み。残りは未実装 |

## 2. P1

| 機能 | モジュール | 状態 |
| --- | --- | --- |
| 型付きルート定義（shared）と、そこからのパラメータ取得・URL 組み立て | hx4s-routing | 構想（[routing-design.md](routing-design.md) 4 章） |
| SPA モードの Client Router（scalajs-react Router のアダプタ） | hx4s-routing | 構想 |
| `ALL` メソッドの `@route` | hx4s-codegen | 構想（U-13） |
| npm CLI（`npx hx4s generate` 等） | hx4s-vite か別パッケージ | 構想（[build-integration.md](build-integration.md) 4 章） |
| プロジェクトの雛形（templates、`sbt new` / `npm create`） | templates | 構想 |
| `Test / jsEnv` の補助（Node loader と client の先行リンク） | sbt-hx4s | 構想 |
| 仮想モジュール `scalajs:*.js` の `.d.ts` 生成（`@ts-expect-error` をなくす） | hx4s-codegen | 構想（U-16） |
| case class + `Mirror` 導出 codec による Props（追加 API） | hx4s-react | 構想（[rendering-model.md](rendering-model.md) 3 章） |
| island を使わないページで client script を省く | hx4s-codegen / hx4s-react | 構想 |

## 3. P2

| 機能 | 状態 |
| --- | --- |
| `hx4s-cloudflare`（DO の export、RPC スタブ / TS 型の生成、Bindings の型） | 構想。3ch の `GenerateDurableObject*` が出発点 |
| Renderer の追加に伴う `hx4s-react` の分割（`hx4s-honox` と `hx4s-react`） | 構想 |
| SSR + Full Hydration の正式対応（props を `<script type="application/json">` に置く方式） | 構想（[rendering-model.md](rendering-model.md) 1.2） |
| Scala 3 マクロによる Props の型検査・変換 | 構想 |
| Vite dev server（HMR）での開発体験 | 構想（U-17）。3ch の `npm run dev` は build + `wrangler dev` で、Vite の dev server を使っていない |

## 4. 未決事項

設計判断として既に決まっている事項（[ADR](README.md)）以外で、決める必要があるもの。「提案」は本設計で出した案。

| ID | 事項 | 現時点の案 |
| --- | --- | --- |
| U-1 | npm のスコープ: `hx4s-vite` か `@hx4s/vite` か | 提案: 公開時に `@hx4s` スコープが取れるなら `@hx4s/vite`。将来 `@hx4s/cli` などを足しやすい。取れなければ `hx4s-vite` |
| U-2 | Maven organization の確定 | 仮に `io.github.montereylangit`。独自ドメインを持つなら変える |
| U-3 | CI での依存解決（git 参照の sbt source dependency / submodule / Maven Central・npm 公開 / GitHub Packages） | [ADR 0006](adr/0006-one-way-dependency-and-local-publish.md) の比較。提案: 公開までは submodule + `publishLocal`、公開後は Central / npm |
| U-4 | ライセンス | 提案: Apache-2.0（[compatibility.md](compatibility.md) 4 章） |
| U-5 | 3ch から移すコードの git 履歴を引き継ぐか | 提案: `git filter-repo` で `hx4s/` 配下の履歴だけを持ってくる。3ch の他の履歴（秘匿値を含みうる）を持ち込まないことを確認してから |
| U-6 | Fetch API の型: scalajs-dom の `Request` / `Response` を使うか、hx4s が facade を持つか | 提案: scalajs-dom。3ch は手書き facade を持つが、hx4s が独自に持つと利用側で型が 2 系統になる |
| U-7 | `app/client.ts` 相当のヘルパー（`hx4s-vite/client` の `createReactClient()` 等）を提供するか | 提案: P0 では雛形のみ。中身が 10 行程度で、ヘルパーにすると HonoX の `createClient` のオプションを覆い隠すため |
| U-8 | `hx4s-vite` の `honox` / `@scala-js/vite-plugin-scalajs` を peerDependencies にするか dependencies にするか | 提案: peerDependencies。版の決定を利用側に残し、検証済みの範囲を宣言する |
| U-9 | `IslandComponents.scala` の出力先: `src_managed`（sourceGenerators）か、ソースツリーか | 提案: `src_managed`。3ch の現行（ソースツリー、gitignore）は clone 直後にコンパイルできない可能性がある。TS 側（`app/`）は Vite が読むので従来どおりソースツリーに出す |
| U-10 | `hx4s-codegen` を Scala 2.12 にもクロスビルドし、sbt の中で直接呼ぶか。sbt 2 への移行をいつ前提にするか | 提案: P0 は Scala 3 のみ + fork。2.12 クロスはコードの書き方を制約するので避ける |
| U-11 | `@islands[P]` の `P` の書き方: 完全修飾名に限るか、import を解決するか | 提案: P0 は完全修飾名に限る（3ch の計画の推奨と同じ）。import の解決は構文解析では不完全になる |
| U-12 | Props の `validate` に失敗したときの描画 | 3ch の計画は「`console.error` を出し、JS 無し相当で描画」。hx4s は方針を文書で示すだけにし、実装は利用側 |
| U-13 | `HEAD` の扱いと `ALL` メソッド | 提案: `HEAD` は拒否（GET が応答する）。`ALL` は P1 |
| U-14 | `IslandComponents` のメソッド名: export 名（`PostingFormJs`）のままか、派生名（`PostingForm`）か | 提案: export 名のまま。規則を増やすと衝突検査も増える。3ch の既存呼び出しも変えずに済む |
| U-15 | sbt の起動回数を減らす（sbt client / 1 回の `print` で複数プロジェクトの出力を得る） | 未調査。3ch の `v6:build` は sbt を 4 回起動する。所要時間は測っていない |
| U-16 | 仮想モジュールの型宣言の生成 | 提案: P1 で `scalajs-server.d.ts` 相当を export 名から生成し、生成ルートの `@ts-expect-error` を外す。引数・戻り値の型までは出さない（構文から TS 型への変換は範囲が広い） |
| U-17 | Vite dev server（HMR）をどこまで支えるか | 未調査。`@scala-js/vite-plugin-scalajs` の dev モード（fastLinkJS）と HonoX の devServer の組み合わせは 3ch で使われていない |
| U-18 | HonoX の `islandDir` を変えた場合の `component-name` の追従 | 提案: P0 は `/app/islands` 固定。設定可能にするなら codegen と hx4s-vite の両方に同じ値を渡す |
| U-19 | Node 用 loader を sbt-hx4s の設定（`Test / jsEnv` の補助）でも提供するか | 提案: P1。P0 は loader のファイルと設定例だけ |
| U-20 | 名前の衝突確認（`hx4s` が Maven Central・npm・GitHub で既に使われていないか） | 未確認。公開前に確認する |
| U-21 | 3ch から移すコードの権利（3ch のライセンス未設定、参照実装 `ch3s-migration` 由来の部分） | 権利者の確認が要る |
| U-22 | case class Props を追加するときの API の形（`@islands[P]` の `P` が case class なら codec 経由にする、など） | 提案: A 方式と共存させる。P1 / P2 で決める |
| U-23 | 手書きルートとの URL 衝突を拒否するか警告にとどめるか | 提案: 警告。手書きで意図的に上書きする運用を妨げないため |
