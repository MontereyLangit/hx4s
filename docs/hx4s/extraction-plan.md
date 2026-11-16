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
| 2 | 3ch 内で汎用化（hx4s 相当のサブプロジェクトを 3ch のビルド内に切る） | 3ch |
| 3 | hx4s リポジトリへ分離、`examples/minimal` を作る | hx4s |
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

完了条件: 上の 6 項目が 3ch の `develop` に入り、本番にデプロイされて板トップ・認証画面が動いている。

### Phase 2: 3ch 内で汎用化

3ch のビルドの中に、hx4s のモジュールと同じ形のサブプロジェクトを作る。パッケージは最初から `hx4s.*` にする。

```
3ch/
  hx4s/                      ← Phase 3 でそのまま切り出すディレクトリ
    core/                    hx4s-core（crossProject Pure）
    react/                   hx4s-react（Scala.js）
    codegen/                 hx4s-codegen（JVM, Scala 3）
    vite/                    hx4s-vite（TS。3ch の vite.config.ts から import する）
  common/ bbs/ bbs-client/   3ch 本体（hx4s/* に依存）
  codegen/                   3ch 固有の生成器（DO の型・スタブ）だけが残る
```

| # | 作業 |
| --- | --- |
| 2-1 | アノテーションを `hx4s/core` の `hx4s.annotation.{route, islands}` へ移す。3ch の `Main` の import を変える |
| 2-2 | `Island`・Hono Context facade（Env を型引数に）を `hx4s/react` へ移す。3ch は `type HonoContextFacade = hx4s.hono.Context[WorkerEnv]` などの別名で受ける |
| 2-3 | `AnnotatedAppEntries`・テンプレート・書き出し・`IslandComponents` 生成を `hx4s/codegen` へ移し、入力パス・`Main` の名前・moduleID・出力パッケージ・bridge の完全修飾名・バナーの文言を設定 case class にする。CLI main は引数で設定を受ける |
| 2-4 | 3ch の `codegen` は DO の生成器と、`hx4s/codegen` を設定付きで呼ぶ薄い main だけにする |
| 2-5 | `vite.config.ts` の `scalaProject`・alias・`honox()` の React 設定・client ビルド設定を `hx4s/vite` の `hx4s()` に移す。Tailwind と Cloudflare の設定は 3ch に残す |
| 2-6 | Node 用 loader を分ける: `scalajs:client.js` と `hono/jsx` の置換を `hx4s/vite/node-loader.mjs` へ、`cloudflare:workers` を 3ch の loader へ。`CH3S_CLIENT_JS_DIR` を `HX4S_CLIENT_JS_DIR` に改める |
| 2-7 | `tests/islands-bridge.test.js` と `tests/islands-hydration.test.js` を、3ch の `vite.config.ts` に依存しない形にして `hx4s/` 配下へ移す |
| 2-8 | `hx4s/` 配下に `io._3ch`・`ch3s`・`3ch`・`bbs` の文字列が無いことを検査するテストを足す（grep で十分） |

この段階では sbt plugin は作らない。3ch の `build.sbt` から `hx4s/codegen` を `runMain` で呼ぶ現行の方式を続ける。

検証条件:

- Phase 1 の検証条件をすべて満たす。
- 生成物（`app/routes`・`app/islands`・`IslandComponents.scala`）が、バナーとパッケージ名の変更を除いて Phase 1 完了時と同一。
- 2-8 の検査が通る。
- `hx4s/` 配下のテストが、3ch のソース（`bbs`・`bbs-client`・`common`）を参照せずに通る。

完了条件: 3ch の `develop` に入り、本番で Phase 1 と同じく動いている。

### Phase 3: hx4s リポジトリへ分離

| # | 作業 |
| --- | --- |
| 3-1 | 3ch の `hx4s/` 配下を hx4s リポジトリへ移す。履歴を引き継ぐか（`git filter-repo` など）は未決事項 U-5 |
| 3-2 | hx4s の `build.sbt` を作る。モジュール名・organization・Scala の版（3.3 LTS）を [ADR 0001](adr/0001-name-and-coordinates.md)・[ADR 0002](adr/0002-initial-modules.md) どおりにする |
| 3-3 | `sbt-hx4s` を作る。設定キー・`hx4sGenerate`・専用 configuration での `hx4s-codegen` 解決と fork 実行（[build-integration.md](build-integration.md) 3.2） |
| 3-4 | `hx4s-vite` を npm パッケージにする（`package.json`、型定義、`node-loader`） |
| 3-5 | `examples/minimal` を作る。SSR のページ 1 つ、island 1 つ（型付き Props）、POST の `@route` 1 つ。Node で動く Hono アダプタ（`@hono/vite-build/node` など）を使い、Cloudflare に依存しない |
| 3-6 | hx4s の CI: `sbt test`、`sbt publishLocal`、`examples/minimal` のビルドと互換性テスト（[compatibility.md](compatibility.md) 3 章） |

検証条件:

- `examples/minimal` が、hx4s を `publishLocal` と npm の `file:` で参照してビルドでき、SSR と hydration のテストが通る。
- `examples/minimal` と hx4s の全モジュールに 3ch への参照が無い（2-8 と同じ検査）。
- Windows と Linux の両方で `examples/minimal` のビルドが通る（CI の OS 行列。Windows は少なくとも手元で確認）。

完了条件: hx4s の `develop` に入り、CI が通る。この時点で 3ch はまだ Phase 2 の内蔵版を使っている。

### Phase 4: 3ch を hx4s の利用側へ移行

| # | 作業 |
| --- | --- |
| 4-1 | 3ch の `project/plugins.sbt` に `sbt-hx4s`、`build.sbt` に `hx4s-core` / `hx4s-react` を追加。版は `publishLocal` したもの |
| 4-2 | 3ch の `package.json` に `hx4s-vite` を `file:` で追加し、`vite.config.ts` を `hx4s()` に差し替える |
| 4-3 | 3ch の `hx4s/` ディレクトリと、`codegen` の hx4s 呼び出し部分を削除する。`npm run v6:generate` を `sbt hx4sGenerate` + 3ch の DO 生成に変える |
| 4-4 | 3ch の AGENTS.md・docs を更新する（構成、開発コマンド、hx4s 側の文書への参照） |

検証条件: Phase 1 の検証条件をすべて満たし、生成物が Phase 2 完了時と同一（バナーを除く）。

完了条件: 3ch の `develop` に入り、本番にデプロイされて動いている。

### Phase 5: 公開の準備

| # | 作業 |
| --- | --- |
| 5-1 | CI での依存解決方式を決める（[ADR 0006](adr/0006-one-way-dependency-and-local-publish.md) の比較、U-3） |
| 5-2 | ライセンスを決め、LICENSE / NOTICE を置く（U-4）。3ch から移したコードの権利を確認する（U-21） |
| 5-3 | 名前の衝突を確認する（Maven Central・npm・GitHub 上の `hx4s`）（U-20）。npm のスコープを決める（U-1） |
| 5-4 | 版付けの方針（0.x の間の互換性の約束、honox の検証版の明記） |
| 5-5 | Maven Central / npm へ公開する（公開するかどうか自体も、この時点で判断する） |

完了条件: 3ch の CI がローカルの `publishLocal` なしで hx4s を解決できる。

## 3. 移動するファイルの対応表

分類の理由は [module-boundaries.md](module-boundaries.md) を参照。パスは 3CH のリポジトリルートからの相対。

| 3CH のファイル | 移す先（モジュール / パス案） | Phase |
| --- | --- | --- |
| `common/src/main/scala/io/_3ch/common/routes/Annotations.scala` | hx4s-core `core/src/main/scala/hx4s/annotation/{route,islands}.scala` | 2 |
| `common/.../routes/RouteTable.scala` の `RouteKind` / `RouteEntry` / `IslandEntry` / `render*` / バナー | hx4s-codegen `codegen/src/main/scala/hx4s/codegen/{Entries,Templates}.scala` | 1（codegen へ）→ 2 |
| `common/.../routes/RouteTable.scala` の `publicPatterns` | 3ch に残す（`common` の domain か `bbs` の handler） | 1 |
| `common/src/test/.../routes/RouteTableSuite.scala` | hx4s-codegen のテスト（テンプレート部分） | 2 |
| `codegen/.../AnnotatedAppEntries.scala` | hx4s-codegen `hx4s/codegen/AnnotatedEntries.scala` | 2 |
| `codegen/.../GenerateAppFiles.scala` の `writeOutputs` / `isGenerated` / `resolveUnderApp` | hx4s-codegen `hx4s/codegen/OutputWriter.scala` | 2 |
| `codegen/.../GenerateAppFiles.scala` の `main` | hx4s-codegen `hx4s/codegen/Main.scala`（設定は引数）と、3ch の薄い main | 2 |
| `codegen/.../GenerateIslandComponents.scala` | hx4s-codegen `hx4s/codegen/IslandComponentsTemplate.scala` | 2 |
| `codegen/src/test/.../{AnnotatedAppEntries,GenerateAppFiles,GenerateIslandComponents}Suite.scala` | hx4s-codegen のテスト | 2 |
| `bbs/.../external/honox/Island.scala` | hx4s-react `react/src/main/scala/hx4s/react/Island.scala` | 2 |
| `bbs/.../external/hono/HonoFacade.scala`（`RenderProps` 以外） | hx4s-react `hx4s/hono/Context.scala` | 2 |
| `bbs/src/test/.../IslandTestExports.scala` の `renderIslandTest` | hx4s-react のテスト用 export | 2 |
| `vite.config.ts` の `scalaProject`・alias・`honox()` の React 設定・client 設定 | hx4s-vite `vite/src/index.ts` | 2 |
| `bbs/test-stubs/cloudflare-workers-loader.mjs` の `scalajs:client.js` / `hono/jsx` 部分、`register.mjs` | hx4s-vite `vite/node-loader.mjs` / `vite/node-register.mjs` | 2 |
| `tests/islands-bridge.test.js` | hx4s-vite のテスト（`examples/minimal` を対象に） | 2 → 3 |
| `tests/islands-hydration.test.js` | hx4s-react / hx4s-vite のテスト | 2 → 3 |
| `build.sbt` の `codegen` プロジェクト・`Compile / mainClass` | sbt-hx4s（`hx4sGenerate`） | 3 |
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
