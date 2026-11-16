# ロードマップ

2026-11-16 時点。Phase 3（hx4s リポジトリへの分離）の実装と、そのレビュー指摘の修正（[reviews/phase3-review.md](reviews/phase3-review.md) 5 章）、
Phase 4 で移動元の掲示板プロジェクト（3ch）が hx4s に乗り換えたときに分かった不足と Codex の指摘への対応（[reviews/phase4-feedback.md](reviews/phase4-feedback.md)）が入った状態を書く。
「実装済み」は hx4s のリポジトリにあり、テストが通ることを意味する。

## 1. P0（最初の分離で揃えるもの）

| 機能 | モジュール | 状態 |
| --- | --- | --- |
| `@route(filePath, method)` | hx4s-core | 実装済み（`hx4s.annotation.route`、JVM / JS） |
| `@islands[P](filePath)`（型引数で Props を指定） | hx4s-core | 実装済み（`hx4s.annotation.islands`。型引数は省ける） |
| props の検証 `JsShape`（`validate` / `read`） | hx4s-core（JS） | 実装済み（`hx4s.props`。[ADR 0007](adr/0007-props-validation-and-typed-export.md)） |
| HonoX Island ブリッジ `Island(component, props, componentName)` | hx4s-react | 実装済み（`hx4s.react.Island`） |
| 汎用 Hono Context facade（Env を型引数） | hx4s-react | 実装済み（`hx4s.hono.Context[Env, R]`、`HonoRequest`、`ExecutionContext`。Request / Response は scalajs-dom。U-6 決定。Phase 4 の利用側の移行で `executionCtx`・`html`・`set` / `get`・本文の読み取りなどを足した） |
| 型付き Props の補助（`IslandComponents.Xxx(props: P)`、typed export `IslandExport[P]`、hydrate 前の検証） | hx4s-react / hx4s-codegen | 実装済み。server の呼び出しと client の export の型違いはコンパイルエラー、`@islands[P]` と export の宣言型は codegen が構文で照合（U-24 決定） |
| アノテーション解析・route TS / island TSX / `IslandComponents` 生成 | hx4s-codegen | 実装済み（`hx4s.codegen`） |
| 安全な書き出し（バナー保護、古い生成物の削除、大文字小文字の衝突） | hx4s-codegen | 実装済み |
| 書き出しの追加規則（同内容なら書かない、改行の正規化、全検査後に書く、出力ルートの祖先を含むリンクの拒否、一時ファイル経由の置き換え、新規書き込み → 古い生成物の削除の順） | hx4s-codegen | 実装済み（[build-integration.md](build-integration.md) 3.3、[codegen.md](codegen.md)） |
| 生成コードの識別子の衝突回避（import の別名）と、生成ソースへ埋め込む設定値の検証・エスケープ | hx4s-codegen | 実装済み（[codegen.md](codegen.md)） |
| `@route` の検査強化 | hx4s-codegen | `HEAD` 拒否、HonoX / `import.meta.glob` が拾わないファイル名の拒否、生成ルート同士の URL パターンの衝突検出（`RoutePath`）は実装済み。**手書きルートとの衝突の警告は未実装**（[routing-design.md](routing-design.md) 2.2） |
| CLI main（引数で設定を受ける） | hx4s-codegen | 実装済み（`hx4s.codegen.CodegenMain`） |
| 設定キーと `hx4sGenerate`、専用 configuration と fork 実行 | sbt-hx4s | 実装済み（`Hx4sPlugin`。scripted テストあり）。client なし（SSR だけ）の構成も扱う |
| client と server の依存の向きの検査 | sbt-hx4s | sbt の依存グラフは実装済み（`hx4sCheckDependencies`、scripted の `dependencies`）。**Vite のモジュールグラフの検査は未実装**（[module-boundaries.md](module-boundaries.md) 2 章） |
| Scala.js プロジェクトの登録・`buildStart` 直列化・`scalajs:client.js` alias・honox の React 設定・client ビルド設定 | hx4s-vite | 実装済み（`hx4s()`。client の出力名は `client.output` で上書きできる。`client: false` で client の無い構成（API だけ・island の無い SSR）を扱う） |
| hydrate 前の props の検証（template 内の island を含む） | hx4s-vite | 実装済み（`hx4s-vite/client` の `islandPropsProblem`。[rendering-model.md](rendering-model.md) 2.4） |
| Node 用 loader（`scalajs:client.js`、HonoX 内の `hono/jsx`） | hx4s-vite | 実装済み（`hx4s-vite/node-register`、環境変数 `HX4S_CLIENT_JS_DIR`）。利用側の loader と並べる方法と `Test / jsEnv` の例は [build-integration.md](build-integration.md) 2.3 |
| ローカル公開の手順 | scripts | 実装済み（`scripts/publish-local.mjs`。版は `0.1.0-local.g<commit>`、未コミットの変更があれば `.dirty-<内容のハッシュ>`）。Phase 4 で `npm ci` から始めること、sbt・npm に分けた公開記録と完了の記録 `hx4s-publish.json`、`--verify`、シェルを介さない起動を足した（[local-dependency.md](local-dependency.md)） |
| `examples/minimal`（SSR + Islands、Cloudflare Workers） | examples | 実装済み（ローカル公開の成果物だけで解決する。npm 依存は lockfile で固定し、`scripts/setup-example.mjs` で入れる） |
| `examples/api-only`（JSON の API だけ。client・React なし） | examples | 実装済み（`hx4s({ client: false })`。ビルドした Worker を Node で呼ぶテスト。CI と独立性検査の対象） |
| 互換性テスト | 各モジュール | SSR・hydration（単純・入れ子・要素 props・複数インスタンス・Scala.js の client island）・Props の往復（Unicode・`</script>`・引用符・入れ子）・世代ずれ・island のファイル名・ルートの登録（GET / POST / HEAD）・ビルドのバンドル文字列・書き出しの負のテストは実装済み。URL 変換（`filePathToPath`）の照合、template 内の island の検証、生成 TS の識別子の衝突も実装済み。**非有限の `Double`・循環参照の挙動、`honox@latest` の定期実行、Vite のモジュールグラフによる依存方向の検査は未実装** |
| 利用者向けの文書（OSS 公開用） | README、`docs/`、CONTRIBUTING、CHANGELOG | 実装済み（2026-11-16。[docs/README.md](../README.md)）。コード例は `examples/minimal` と同じか、作業用の複製でコンパイル・実行して確かめたものだけ。ページ全体を 1 つの island にする構成は examples に含まれない（API だけの構成は `examples/api-only`）。リリースの手順は公開方式が未決のため未整備（[release.md](../release.md)） |
| CI | `.github/workflows/ci.yml` | ファイルを置いた（Ubuntu / Windows。生成物の確認は未追跡ファイルも見る）。リモートでは未実行（push はユーザーの判断） |

## 2. P1

| 機能 | モジュール | 状態 |
| --- | --- | --- |
| 型付きルート定義（shared）と、そこからのパラメータ取得・URL 組み立て | hx4s-routing | 構想（[routing-design.md](routing-design.md) 4 章） |
| SPA モードの Client Router（scalajs-react Router のアダプタ） | hx4s-routing | 構想 |
| `ALL` メソッドの `@route` | hx4s-codegen | 構想（U-13） |
| npm CLI（`npx hx4s generate` 等） | hx4s-vite か別パッケージ | 構想（[build-integration.md](build-integration.md) 4 章） |
| プロジェクトの雛形（templates、`sbt new` / `npm create`） | templates | 構想 |
| `Test / jsEnv` の補助（Node loader と client の先行リンク） | sbt-hx4s | 構想。P0 は `hx4s-vite/node-register`・`hx4s-vite/node-loader` と、設定例（examples/minimal の `build.sbt`、利用側の loader と並べる例は [build-integration.md](build-integration.md) 2.3）まで |
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
| U-4 | ライセンス | **決定（2026-11-16）: Apache-2.0**。リポジトリ直下に `LICENSE` と `NOTICE` を置いた（[compatibility.md](compatibility.md) 4 章） |
| U-5 | 3ch から移すコードの git 履歴を引き継ぐか | 提案: `git filter-repo` で移動対象（`codegen/.../framework/` など。extraction-plan.md 3 章の対応表）の履歴だけを持ってくる。3ch の他の履歴（秘匿値を含みうる）を持ち込まないことを確認してから |
| U-6 | Fetch API の型: scalajs-dom の `Request` / `Response` を使うか、hx4s が facade を持つか | **決定（Phase 3）: scalajs-dom**（`hx4s.hono.Context` / `HonoRequest`） |
| U-7 | `app/client.ts` 相当のヘルパー（`hx4s-vite/client` の `createReactClient()` 等）を提供するか | **決定（Phase 3 レビュー K6）**: `createClient` を包むヘルパーは作らず、検証の関数 `islandPropsProblem(element)` だけを `hx4s-vite/client` で提供する。雛形は `createClient` のオプションをそのまま書く。以前の提案: P0 では雛形のみ（中身が 10 行程度で、ヘルパーにすると HonoX の `createClient` のオプションを覆い隠すため） |
| U-8 | `hx4s-vite` の `honox` / `@scala-js/vite-plugin-scalajs` を peerDependencies にするか dependencies にするか | 提案: peerDependencies。版の決定を利用側に残し、検証済みの範囲を宣言する |
| U-9 | （決定）`IslandComponents.scala` の出力先 | **決定: `src_managed`（`sourceGenerators`）**。3ch は `875a47b` 時点でこの方式になっている（`build.sbt` の `generateIslandComponents`）。TS 側（`app/`）は Vite が読むので従来どおりソースツリーに出す |
| U-10 | `hx4s-codegen` を Scala 2.12 にもクロスビルドし、sbt の中で直接呼ぶか。sbt 2 への移行をいつ前提にするか | 提案: P0 は Scala 3 のみ + fork。2.12 クロスはコードの書き方を制約するので避ける |
| U-11 | `@islands[P]` の `P` の書き方: 完全修飾名に限るか、import を解決するか | 提案: P0 は完全修飾名に限る（3ch の計画の推奨と同じ）。import の解決は構文解析では不完全になる |
| U-12 | Props の `validate` に失敗したときの描画 | **決定（Phase 3）**: `IslandExport` の検証関数を `app/client.ts` の hydrate が（`hx4s-vite/client` の `islandPropsProblem` を通して、template 内の island も含めて）呼び、不一致なら `console.error` を出して hydrate せず SSR の markup を残す（[ADR 0007](adr/0007-props-validation-and-typed-export.md)） |
| U-13 | `HEAD` の扱いと `ALL` メソッド | 提案: `HEAD` は拒否（GET が応答する）。`ALL` は P1 |
| U-14 | `IslandComponents` のメソッド名: export 名（`PostingFormJs`）のままか、派生名（`PostingForm`）か | 提案: export 名のまま。規則を増やすと衝突検査も増える。3ch の既存呼び出しも変えずに済む |
| U-15 | sbt の起動回数を減らす（sbt client / 1 回の `print` で複数プロジェクトの出力を得る） | 未調査。3ch の `v6:build` は sbt を 4 回起動する。所要時間は測っていない |
| U-16 | 仮想モジュールの型宣言の生成 | 提案: P1 で `scalajs-server.d.ts` 相当を export 名から生成し、生成ルートの `@ts-expect-error` を外す。引数・戻り値の型までは出さない（構文から TS 型への変換は範囲が広い） |
| U-17 | Vite dev server（HMR）をどこまで支えるか | 未調査。`@scala-js/vite-plugin-scalajs` の dev モード（fastLinkJS）と HonoX の devServer の組み合わせは 3ch で使われていない |
| U-18 | HonoX の `islandDir` を変えた場合の `component-name` の追従 | 提案: P0 は `/app/islands` 固定。設定可能にするなら codegen と hx4s-vite の両方に同じ値を渡す（U-25 と合わせる） |
| U-19 | Node 用 loader を sbt-hx4s の設定（`Test / jsEnv` の補助）でも提供するか | 提案: P1。P0 は loader のファイルと設定例だけ |
| U-20 | 名前の衝突確認（`hx4s` が Maven Central・npm・GitHub で既に使われていないか） | 未確認。公開前に確認する |
| U-21 | 3ch から移すコードの権利（3ch のライセンス未設定、参照実装 `ch3s-migration` 由来の部分） | 権利者の確認が要る |
| U-22 | case class Props を追加するときの API の形（`@islands[P]` の `P` が case class なら codec 経由にする、など） | 提案: A 方式と共存させる。P1 / P2 で決める |
| U-23 | 手書きルートとの URL 衝突を拒否するか警告にとどめるか | 提案: 警告。手書きで意図的に上書きする運用を妨げないため |
| U-24 | client の export を `@islands[P]` の `P` に結び付ける typed export 補助 | **決定（Phase 3）: `IslandExport[P]`**（[ADR 0007](adr/0007-props-validation-and-typed-export.md)）。以下は Phase 0 の記述: 型が保証するのは server の呼び出し側だけで、client のコンポーネントが同じ `P` を読む保証が無い（[rendering-model.md](rendering-model.md) 2.3）。`IslandExport[P]` のような型で、`P` と一致しない export をコンパイルエラーにする案 |
| U-25 | appDir・moduleID・仮想モジュール接頭辞を codegen と hx4s-vite に同じ値で渡す共通設定 | P0 は `app/routes`・`app/islands`・moduleID `server` / `client` を既定かつ検証済みの唯一の組み合わせとする（[build-integration.md](build-integration.md) 2.0）。共通設定は P1 で決める。HonoX の glob が `/app` 固定なので、変更には HonoX 側の `ISLAND_FILES` などのオプション指定も要る |
| U-26 | HTML と client asset の世代ずれへの対策: entry のハッシュ名化、キャッシュ制御、旧 asset の保持、Props スキーマ変更の指針、新旧組み合わせの検証 | P0 は client の入口の `validate` で不一致を検出し、`console.error` と SSR の markup の維持にとどめる。client の出力名は `hx4s()` の `client.output` で変えられる（既定は固定名）。ハッシュ名を既定にするかとその HTML への受け渡し、それ以外は未決（[rendering-model.md](rendering-model.md) 2.4、[ADR 0003](adr/0003-typed-island-props.md)） |
