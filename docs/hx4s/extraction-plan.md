# 分離計画

## 1. 進め方

**3ch 内で改善 → 3ch で検証 → 3ch 内で汎用化 → hx4s へ分離 → 3ch を hx4s の利用側へ移行** の順に進める。
スクラッチでの再実装はしない。

理由:

- 現行の連携基盤は 3ch の本番で動いており、`tests/islands-*.test.js` などの検証も揃っている。動いているコードを移す方が、作り直すより壊れにくい。
- 改善（型付き Props、生成の安全性）を先に 3ch で行えば、本番の画面で検証できる。分離後に改善すると、hx4s と 3ch の 2 か所で同時に直すことになる。
- 3ch 内で汎用化（パッケージ・設定値の切り出し）まで済ませておけば、分離の作業は「ファイルの移動とビルド設定の差し替え」に近づき、差分を追いやすい。

却下した進め方:

| 案 | 却下理由 |
| --- | --- |
| hx4s をスクラッチで書き、3ch を後から載せ替える | 3ch の本番で検証済みの挙動（入れ子 island、要素 props、KB-27 の分離）を再発見する手間がかかる。移行時に挙動差が出ても原因を切り分けにくい |
| 先に hx4s へ移してから改善する | 改善のたびに hx4s と 3ch の両方の版を合わせる必要がある。ローカル依存の期間が長くなる |
| 3ch の中に置いたまま公開する（モノレポ） | 3ch の掲示板固有のコード・秘匿設定と同じリポジトリになり、公開の範囲を管理しにくい |

## 2. Phase 一覧

| Phase | 内容 | 作業場所 |
| --- | --- | --- |
| 0 | 設計（この文書群） | hx4s |
| 1 | 3ch 内で改善 | 3ch |
| 2 | 3ch 内で汎用化（`io._3ch.codegen.framework` への分離と設定 case class 化。パッケージの `hx4s.*` への改名は Phase 3） | 3ch |
| 3 | hx4s リポジトリへ移動し、`hx4s.*` へ改名する。`examples/minimal` を作る | hx4s |
| 4 | 3ch を hx4s の利用側へ移行（ローカル依存） | 3ch |
| 5 | 公開の準備（CI での依存解決、ライセンス、版付け、公開） | hx4s / 3ch |

### Phase 0: 設計

- 作業: 本文書群と ADR を書く。3ch には書き込まない。
- 完了条件: `docs/hx4s/` の文書が揃い、相互リンクが正しい。hx4s の `develop` に 1 コミット。

### Phase 1: 3ch 内で改善

3ch のパッケージ名・構成のまま、hx4s に持ち込みたい改善を行う。各項目は独立したコミットにする。

| # | 作業 | 根拠 |
| --- | --- | --- |
| 1-1 | 型付き Props: `@islands[P]`、生成 API `IslandComponents.Xxx(props: P)`、`io._3ch.common.islands` の trait と `validate`。板トップの 3 island から置き換える | [ADR 0003](adr/0003-typed-island-props.md)、3ch `docs/island-props型付け計画.md` |
| 1-2 | `@route` の検査強化: `HEAD` の拒否、HonoX が無視するファイル名の拒否、URL パターンの衝突検出 | [routing-design.md](routing-design.md) 2.2 |
| 1-3 | 書き出し規則: 内容が同じなら書かない、改行の正規化、出力ルート外の拒否（正規化後のパス）、書き込み前の全検査 | [build-integration.md](build-integration.md) 3.3 |
| 1-4 | `RouteTable` の分割: `publicPatterns` を 3ch のドメイン側へ、テンプレートを `codegen` へ。`RouteKind.PassThrough` の整理 | [module-boundaries.md](module-boundaries.md) 1.2 |
| 1-5 | ハンドラの型: `c: js.Any` + `asInstanceOf` をやめ、`HonoContextFacade` を引数型にする | [routing-design.md](routing-design.md) 3 章 |
| 1-6 | 3ch の文書の食い違いを直す（`handler-rendering.md`、`islands-ssr.md` の版と例、`client-build-review.md` のマーカー名、`AGENTS.md` の手書きルートと管理画面の記述） | [architecture.md](architecture.md) 2.5 |

検証条件:

- `sbt test`、`npm run v6:build`、`npm run v6:typecheck`、`npm test` が成功する。
- 板トップの `data-serialized-props` の形が変わる island（`board-details` の入れ子化）について、SSR と hydration のテストが期待値を更新したうえで通る。
- 再生成を 2 回続けて実行し、2 回目にファイルの更新時刻が変わらない（1-3）。
- 3ch の公開プロトコル（dat、subject.txt、Shift_JIS、エラーマーカー）・ストレージに差分が無い（3ch AGENTS.md 3.2）。

完了条件: 上の 6 項目が済み、ローカルのビルドと全テスト（`sbt test`、`npm run v6:build`、`npm run v6:typecheck`、`npm test`）が通る。
今回の作業ではデプロイしない。3ch の `develop` へのマージと本番反映は 3ch 側の判断（ユーザー判断）に委ねる。

状況（調査基準 commit `875a47b`、[architecture.md](architecture.md) 1 章）: Phase 1 / 2 は 3ch で進行中で、作業ツリーは変わりうる。
1-3 の一部（内容が同じなら書かない、改行の正規化、全検査後に書く）、Phase 2 の生成器の `framework` パッケージ化と設定 case class 化（2-3、2-4）、
`IslandComponents` の `src_managed` への生成（[build-integration.md](build-integration.md) 3.3、U-9）は既に 3ch に入っている。
1-4 は生成テンプレートの `codegen` への移動までが済んでいる（`publicPatterns` は `RouteTable` に残る）。1-1（型付き Props）・1-2（`@route` の検査強化）・1-5・1-6 は未了である。着手時に 3ch の現状を読み直して確かめる。

### Phase 2: 3ch 内で汎用化

3ch のビルドの中で、hx4s に移す部分を「3ch 固有の名前を持たない形」にまで整える。**`hx4s/` ディレクトリは作らず、パッケージも `io._3ch.*` のまま**進める。
`hx4s.*` への改名はファイルの移動と同時に Phase 3 で行う（改名だけの差分を 3ch に残すと、全ファイルを 2 度書き換えることになるため）。

3ch の現在の状態（`875a47b` 時点）:

- 生成器の汎用部分は `codegen/src/main/scala/io/_3ch/codegen/framework/` にある（`AnnotationScanner`・`AppEntries`・`AppFileRenderer`・`Codegen`・`CodegenConfig`・`CodegenError`・`CodegenReport`・`OutputPath`・`OutputWriter`）。
- 3ch 固有の値（入力パス、moduleID、出力先、パッケージ、bridge の完全修飾名、バナー）は `GenerateAppFiles.config` が `CodegenConfig` として渡す。
- DO の TS 型・RPC スタブの生成は `codegen/.../` 直下に残る（3ch 固有）。

| # | 作業 | 状況 |
| --- | --- | --- |
| 2-1 | `RouteTable.publicPatterns` を 3ch 側へ分け、アノテーションに `islands[P]` の型引数を足す（1-1、1-4）。アノテーション自体（`common/.../routes/Annotations.scala`）は Phase 3 で `hx4s.annotation.{route, islands}` へ移す | 一部 |
| 2-2 | `Island`・Hono Context facade の Env / Props の型引数化。3ch は `HonoContext[WorkerEnv]` のような別名で受ける | 進行中（`HonoContext` / `HonoContextFacade` に分ける作業ツリー上の変更あり） |
| 2-3 | アノテーション走査・テンプレート・書き出し・`IslandComponents` 生成を `framework` パッケージへ分け、設定を `CodegenConfig` にする | 入っている（`875a47b`） |
| 2-4 | 3ch の `codegen` は DO の生成器と、`framework` を設定付きで呼ぶ薄い main（`GenerateAppFiles`）だけにする | 入っている（`875a47b`） |
| 2-5 | `vite.config.ts` の `scalaProject`・alias・`honox()` の React 設定・client ビルド設定を、3ch に依存しない関数として `vite.config.ts` の外へ切り出す。Tailwind と Cloudflare の設定は 3ch に残す | 未着手 |
| 2-6 | Node 用 loader を分ける: `scalajs:client.js` と `hono/jsx` の置換、`cloudflare:workers` のスタブを別ファイルにする。`CH3S_CLIENT_JS_DIR` を中立な名前に改める | 未着手 |
| 2-7 | `tests/islands-bridge.test.js` と `tests/islands-hydration.test.js` を、3ch の `vite.config.ts` と `bbs` のテスト用 export に依存しない形にする | 未着手 |
| 2-8 | 汎用部分（`framework` パッケージ、切り出した TS・loader・テスト）に `io._3ch`・`ch3s`・`3ch`・`bbs` の文字列が無いことを検査するテストを足す（grep で十分）。設定値として渡す文字列は対象外 | 未着手 |

この段階では sbt plugin は作らない。3ch の `build.sbt` から `codegen` を `runMain` で呼ぶ現行の方式を続ける。

検証条件:

- Phase 1 の検証条件をすべて満たす。
- 生成物（`app/routes`・`app/islands`・`IslandComponents.scala`）が、Phase 1 完了時と同一。
- 2-8 の検査が通る。
- `framework` パッケージのテストが、3ch のソース（`bbs`・`bbs-client`・`common`）を参照せずに通る。

完了条件: 上の項目が済み、ローカルのビルドと全テストが通る。マージとデプロイは Phase 1 と同じく 3ch 側の判断に委ねる。

### Phase 3: hx4s リポジトリへ分離

| # | 作業 |
| --- | --- |
| 3-1 | 3ch の `codegen/.../framework/` と、2-2・2-5・2-6・2-7 で切り出した Scala / TS / loader / テストを hx4s リポジトリへ移し、**同時に** パッケージを `hx4s.*` へ、設定の 3ch 既定値を hx4s の既定値へ改名する。履歴を引き継ぐか（`git filter-repo` など）は未決事項 U-5。移動対象は 3 章の対応表 |
| 3-2 | hx4s の `build.sbt` を作る。モジュール名・organization・Scala の版（3.3 LTS）を [ADR 0001](adr/0001-name-and-coordinates.md)・[ADR 0002](adr/0002-initial-modules.md) どおりにする |
| 3-3 | `sbt-hx4s` を作る。設定キー・`hx4sGenerate`・専用 configuration での `hx4s-codegen` 解決と fork 実行（[build-integration.md](build-integration.md) 3.2） |
| 3-4 | `hx4s-vite` を npm パッケージにする（`package.json`、型定義、`node-loader`） |
| 3-1a | typed export 補助の設計と実装（client の export を `@islands[P]` の `P` に結び付ける。[ADR 0003](adr/0003-typed-island-props.md)、U-24） |
| 3-5 | `examples/minimal` を作る。静的ページと動的ルート `[id]`、POST の `@route`、island 2 つ（型付き Props。同じ island の複数インスタンス、配列・入れ子・日本語の props）。実行基盤は Cloudflare Workers（`@hono/vite-build/cloudflare-workers` + wrangler）を既定にする（下記） |
| 3-6 | hx4s の CI: `sbt test`、`sbt publishLocal`、`examples/minimal` のビルドと互換性テスト（[compatibility.md](compatibility.md) 3 章） |

3-5 の実行基盤は、Phase 0 の案（Node アダプタ）から Cloudflare Workers に変えた（Phase 3 の作業指示による）。理由: 最初の利用者（3ch）が Workers で動いており、
同じ組み合わせ（`@hono/vite-build/cloudflare-workers`、Static Assets、workerd）を hx4s の側でも検証できる。Cloudflare に依存するのは examples の
`vite.config.ts`・`wrangler.jsonc`・テストの起動だけで、hx4s のモジュールは Cloudflare の API を参照しない（[ADR 0005](adr/0005-core-independent-of-cloudflare.md)）。
ビルドした Worker は Node からも `fetch` を呼べるので、SSR・POST のテストは Node で、実ブラウザの E2E は `wrangler dev` で行う。

Phase 3 の実施結果（2026-11-16）: 3-1〜3-6 と 3-1a を実装した。U-5（履歴の引き継ぎ）は見送り、ファイルを移して改名したコミットとして入れた
（移動元の履歴に秘匿値を含みうるため。移動元の commit は各コミットの本文に書いていない）。検証の結果と既知の問題は [reviews/phase3-review.md](reviews/phase3-review.md)。

検証条件:

- `examples/minimal` が、hx4s を `publishLocal` と npm の `file:` で参照してビルドでき、SSR と hydration のテストが通る。
- `examples/minimal` と hx4s の全モジュールに 3ch への参照が無い（2-8 と同じ検査）。
- 型付き Props の保証範囲を確かめる負のテスト（server の呼び出しで `P` 以外の引数がコンパイルエラーになる。typed export 補助を入れた場合は client の export の型違いもコンパイルエラーになる）、`validate` 失敗時の挙動、HTML と client asset の世代ずれの検証（[rendering-model.md](rendering-model.md) 2.3・2.4、[ADR 0003](adr/0003-typed-island-props.md)）。
- 生成物の書き出しの symlink / junction に対する負のテスト（[build-integration.md](build-integration.md) 3.3）。
- Windows と Linux の両方で `examples/minimal` のビルドが通る（CI の OS 行列。Windows は少なくとも手元で確認）。

完了条件: hx4s の `develop` に入り、ローカルで全検証（`sbt test`、`sbt sbtHx4s/scripted`、`-no-indent` の確認、`npm test`、`npm run typecheck`、
`scripts/publish-local.mjs`、クリーンな状態からの examples/minimal の install・build・typecheck・test、`scripts/check-independence.mjs`）が通る。
リモートの CI（Ubuntu / Windows）が通ることの確認は、push（ユーザーの判断）の後に行う。この時点で 3ch はまだ Phase 2 の内蔵版を使っている。

### Phase 4: 3ch を hx4s の利用側へ移行

前提（公開は Phase 5 のままだが、再現できる検証経路は Phase 4 から要る。[ADR 0006](adr/0006-one-way-dependency-and-local-publish.md)）:

- commit を固定した hx4s の checkout から、`publishLocal` と npm パッケージの準備（`npm pack` など）を行う手順をスクリプトにする。版は `0.1.0-local.N` のように明示し、手順は 3ch の docs に書く。
- **空の依存キャッシュ**（空の coursier / ivy キャッシュと `node_modules`）から、3ch がビルドとテストを通せること。

| # | 作業 |
| --- | --- |
| 4-0 | 上の前提の手順（スクリプト）を作り、空の依存キャッシュで 3ch のビルドとテストを通す |
| 4-1 | 3ch の `project/plugins.sbt` に `sbt-hx4s`、`build.sbt` に `hx4s-core` / `hx4s-react` を追加。版は `publishLocal` したもの |
| 4-2 | 3ch の `package.json` に `hx4s-vite` を `file:` で追加し、`vite.config.ts` を `hx4s()` に差し替える |
| 4-3 | 3ch の `codegen` の `framework` パッケージと、切り出した TS・loader・テストを削除する。`npm run v6:generate` を `sbt hx4sGenerate` + 3ch の DO 生成に変える |
| 4-4 | 3ch の AGENTS.md・docs を更新する（構成、開発コマンド、hx4s 側の文書への参照） |

検証条件: Phase 1 の検証条件をすべて満たし、生成物が Phase 2 完了時と同一（バナーを除く）。上の前提（空の依存キャッシュからのビルド）を満たす。

完了条件: 3ch の `develop` に入り、ローカルのビルドと全テストが通る（デプロイは 3ch 側の判断）。

### Phase 5: 公開の準備

| # | 作業 |
| --- | --- |
| 5-1 | 公開を伴う CI での依存解決方式を決める（[ADR 0006](adr/0006-one-way-dependency-and-local-publish.md) の比較、U-3） |
| 5-2 | ライセンスを決め、LICENSE / NOTICE を置く（U-4）。3ch から移したコードの権利を確認する（U-21） |
| 5-3 | 名前の衝突を確認する（Maven Central・npm・GitHub 上の `hx4s`）（U-20）。npm のスコープを決める（U-1） |
| 5-4 | 版付けの方針（0.x の間の互換性の約束、honox の検証版の明記） |
| 5-5 | Maven Central / npm へ公開する（公開するかどうか自体も、この時点で判断する） |

完了条件: 3ch の CI がローカルの `publishLocal` なしで hx4s を解決できる。

## 3. 移動するファイルの対応表

分類の理由は [module-boundaries.md](module-boundaries.md) を参照。パスは 3CH のリポジトリルートからの相対。調査基準は `875a47b` で、Phase 1 / 2 の進行に伴い 3ch 側のファイル名は変わりうる。
「Phase」は 3ch 内での整理（2）と、hx4s への移動（3）の時期を示す。パッケージは Phase 3 で `io._3ch.codegen.framework` から `hx4s.codegen` へ改名する。

| 3CH のファイル | 移す先（モジュール / パス案） | Phase |
| --- | --- | --- |
| `common/src/main/scala/io/_3ch/common/routes/Annotations.scala` | hx4s-core `core/src/main/scala/hx4s/annotation/{route,islands}.scala` | 2（`islands[P]`）→ 3 |
| `common/.../routes/RouteTable.scala` の `publicPatterns` | 3ch に残す（`common` の domain か `bbs` の handler） | 1〜2 |
| `common/src/test/.../routes/RouteTableSuite.scala` | `publicPatterns` の検査は 3ch に残す。テンプレートの検査は `framework` のテストにある | 2 |
| `codegen/.../framework/AnnotationScanner.scala` | hx4s-codegen `hx4s/codegen/AnnotationScanner.scala` | 3 |
| `codegen/.../framework/AppEntries.scala`（`HttpMethod`・`RouteEntry`・`IslandEntry`） | hx4s-codegen `hx4s/codegen/AppEntries.scala` | 3 |
| `codegen/.../framework/AppFileRenderer.scala` | hx4s-codegen `hx4s/codegen/AppFileRenderer.scala`（route / island / `IslandComponents` のテンプレート） | 3 |
| `codegen/.../framework/Codegen.scala` | hx4s-codegen `hx4s/codegen/Codegen.scala` | 3 |
| `codegen/.../framework/CodegenConfig.scala` | hx4s-codegen `hx4s/codegen/CodegenConfig.scala`（公開 API の設定 case class。名前は Phase 3 で確定） | 3 |
| `codegen/.../framework/{CodegenError,CodegenReport}.scala` | hx4s-codegen `hx4s/codegen/` | 3 |
| `codegen/.../framework/OutputPath.scala`・`OutputWriter.scala` | hx4s-codegen `hx4s/codegen/{OutputPath,OutputWriter}.scala` | 3 |
| `codegen/.../GenerateAppFiles.scala` の `config` と `main` | 3ch に残す（`framework` を呼ぶ薄い main）。sbt-hx4s の `hx4sGenerate` が置き換える | 4 |
| `codegen/src/test/.../framework/{AnnotationScannerSuite,AppFileRendererSuite,OutputWriterSuite,TestConfig}.scala` | hx4s-codegen のテスト | 3 |
| `codegen/src/test/.../GenerateAppFilesSuite.scala` | 3ch の設定を使うテスト。汎用部分は `framework` のテストに寄せて移す | 3 |
| `bbs/.../external/honox/Island.scala` | hx4s-react `react/src/main/scala/hx4s/react/Island.scala` | 2 → 3 |
| `bbs/.../external/hono/HonoContext.scala`・`HonoContextFacade.scala`（`RenderProps` 以外） | hx4s-react `hx4s/hono/Context.scala`（Env を型引数に）。3ch 固有の `RenderProps` と `WorkerEnv` の別名は残す | 2 → 3 |
| `bbs/src/test/.../IslandTestExports.scala` の `renderIslandTest` | hx4s-react のテスト用 export | 2 → 3 |
| `vite.config.ts` の `scalaProject`・alias・`honox()` の React 設定・client 設定 | hx4s-vite `vite/src/index.ts` | 2 → 3 |
| `bbs/test-stubs/cloudflare-workers-loader.mjs` の `scalajs:client.js` / `hono/jsx` 部分、`register.mjs` | hx4s-vite `vite/node-loader.mjs` / `vite/node-register.mjs` | 2 → 3 |
| `tests/islands-bridge.test.js` | hx4s-vite のテスト（`examples/minimal` を対象に） | 2 → 3 |
| `tests/islands-hydration.test.js` | hx4s-react / hx4s-vite のテスト | 2 → 3 |
| `build.sbt` の `codegen` プロジェクト、`generateIslandComponents` の `sourceGenerators` 配線 | sbt-hx4s（`hx4sGenerate` と `sourceGenerators`） | 3 |
| `build.sbt` の `Test / jsEnv` の仕組み | sbt-hx4s の補助（P1） | 3 以降 |
| `app/client.ts` の内容 | `examples/minimal/app/client.ts`（雛形として複製。3ch 側は残す） | 3 |

## 4. 3ch に残すもの

| 対象 | 理由 |
| --- | --- |
| `bbs.Main` / `bbsclient.Main` の宣言、`bbs-client/islands/*`、`bbs/components/*`、`RendererShell` | 掲示板の機能そのもの |
| `app/server.ts`、`app/routes/_renderer.tsx`、手書きルート、`app/renderer.d.ts`、`app/scalajs-server.d.ts` | 3ch のミドルウェア・認証 API・LegacyDispatch・renderer の Props |
| `RouteTable.publicPatterns` | v5 互換の公開 URL |
| DO の TS 型・RPC スタブ生成（`DurableObjectSources`、`GenerateDurableObject*`）と `generateDurableObjectStubs` タスク | Cloudflare 固有。将来 `hx4s-cloudflare` 候補（[ADR 0005](adr/0005-core-independent-of-cloudflare.md)） |
| `external/cloudflare/Bindings.scala`、`external/web/WebFacade.scala` | 実行基盤固有 / 3ch の手書き facade |
| `vite.config.ts` の Tailwind、`@hono/vite-build/cloudflare-workers`、DO の `entryContentAfterHooks` | CSS と実行基盤の選択は利用側 |
| `bbs/test-stubs/cloudflare-workers-stub.mjs` と、その差し替え | Cloudflare 固有 |
| Shift_JIS（`SjisEncoding`）、`createSjisResponse` | 専ブラ互換 |
| `tests/{board-islands,board-top-hydration,client-bundle,worker-bundle}.test.js`、統合テスト | 掲示板の UI・DO・KB-27 のマーカー文字列 |
