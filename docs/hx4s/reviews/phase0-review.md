# Phase 0 設計文書のレビュー記録

- 日付: 2026-11-16
- 対象: hx4s `develop` の `ddfafdb`（`docs: Phase 0 の設計文書を追加する`）の `docs/hx4s/`。照合先の 3ch は commit `875a47b` と当時の作業ツリー（HonoX 0.1.62）
- レビュー手段: セルフレビュー（S1〜S2）、Codex CLI 0.163.0（`codex exec -s read-only`。C1〜C12。ファイル変更・ビルド・テストの実行なし）
- 各指摘は、記載の根拠ファイルを読み直して事実を確かめたうえで反映した。事実と違った指摘は無く、不採用は無い。
  Codex が挙げた根拠の行番号は、3ch の作業ツリーが変わると一致しなくなる。

重大度は Codex の区分（高・中・低）に合わせた。セルフレビューの指摘にも同じ区分を付けた。

## 1. セルフレビュー

| # | 重大度 | 指摘 | 判断・対応（反映先） |
| --- | --- | --- | --- |
| S1 | 中 | Phase 1 の完了条件が「本番にデプロイされて…」になっているが、今回の作業ではデプロイしない。3ch の `develop` へのマージもユーザーの判断である | 採用。Phase 1 / 2 の完了条件を「ローカルのビルドと全テスト（`sbt test`、`npm run v6:build`、`npm run v6:typecheck`、`npm test`）が通る」に改め、マージとデプロイは 3ch 側の判断と明記（`extraction-plan.md` Phase 1・2） |
| S2 | 中 | Phase 2 を「3ch のビルド内に `hx4s/` ディレクトリを作り、最初から `hx4s.*` にする」としているが、実際は 3ch の中で `io._3ch.codegen.framework` への分離と `CodegenConfig` 化で進んでいる。`hx4s.*` への改名は Phase 3 の移動と同時に行う方が差分が小さい | 採用。Phase 2 を実際の手順に書き換え、対応表を `AnnotationScanner`・`AppEntries`・`AppFileRenderer`・`Codegen`・`CodegenConfig`・`CodegenError`・`CodegenReport`・`OutputPath`・`OutputWriter` に更新。調査基準 commit と「進行中で作業ツリーは変わりうる」を `architecture.md` 1 章に追記。`IslandComponents` が `src_managed`（`sourceGenerators`）に生成されることを反映し、U-9 を決定済みに移した（`extraction-plan.md` 2・3 章、`architecture.md` 1・2 章、`module-boundaries.md` 1 章、`build-integration.md` 1.1・2・2.2・3.3、`roadmap.md` U-9） |

## 2. Codex CLI のレビュー

| # | 重大度 | 指摘 | 判断・対応（反映先） |
| --- | --- | --- | --- |
| C1 | 高 | 将来の script 埋め込み向けの XSS 対策の記述が誤っている（`<` を `<` に置換、では `</script>` を無害化できない）。現行の属性値方式にこの問題は無い | 採用。`honox-island.js` を確かめた（属性値へ `JSON.stringify`）。`<` を 6 文字の文字列 `<` に置換する、と書き直し、現行方式には当てはまらないことを明記。script 方式の必須検証（`</script><script>…` を含む値の往復と非実行）を追加（`rendering-model.md` 1.2・2.2、`compatibility.md` 3 章） |
| C2 | 高 | 依存方向が `server → client` 禁止だけで、`client → server`・`shared → server` が無い。検査が文字列検査に頼っている | 採用。`tests/client-bundle.test.js` は文字列の正規表現検査であることを確かめた。`client → server`・`shared → server`・`shared → client` の禁止を明記し、検査は sbt の依存グラフと Vite のモジュールグラフ、文字列検査は補助とした（`module-boundaries.md` 2 章、`compatibility.md` 3 章） |
| C3 | 高 | ADR 0003 の「同じデプロイで更新されるので互換性の問題は無い」が誤り。HTML と client asset の世代ずれは起こりうる | 採用。client の入口が固定名（`vite.config.ts` の `entryFileNames: "static/client.js"`）であることを確かめた。記述を撤回し、P0 の方針（client の入口の `validate` で検出、`console.error`、SSR の markup を残す）と未決事項 U-26 を追加（`adr/0003`、`rendering-model.md` 2.4、`roadmap.md` U-26、`compatibility.md` 3 章） |
| C4 | 中 | 現行実装の説明が古い（廃止された `AnnotatedAppEntries` 等を前提にしている） | 採用。`GenerateAppFiles.scala` が `framework` を使い、`build.sbt` が `src_managed` への生成を登録していることを確かめた。S2 とまとめて対応（上記 S2） |
| C5 | 中 | Props 型を server と client の間で照合する仕組みが無い | 採用。保証範囲を「server の呼び出し側の引数型」に限定して書き、typed export 補助を Phase 3 の設計項目 3-1a と未決事項 U-24 に追加（`rendering-model.md` 2.3・3 章、`adr/0003`、`extraction-plan.md` Phase 3、`roadmap.md` U-24、`compatibility.md` 3 章） |
| C6 | 中 | 許可された Props 型でも JSON 往復が保証されない（非有限の Double、循環参照、入れ子オブジェクト内の React 要素など） | 採用。`honox-island.js` の `isElementPropValue` がトップレベルの値と配列にだけ効くことを確かめた。Scala の型保証と JSON の保証を分け、非有限値・循環参照・`undefined`・入れ子の要素・`Long` の扱いを表にし、SSR 失敗・hydration 不一致の検証項目を追加（`rendering-model.md` 2.3、`compatibility.md` 3 章） |
| C7 | 中 | 書き込み先の親の実パス検査だけでは末端ファイルの symlink を防げない。削除対象や Windows junction の扱いが無い | 採用。`OutputPath.resolve` が字句的な正規化だけで、`OutputWriter` が `Files.write` で書くことを確かめた。出力ルート・途中ディレクトリ・末端ファイル・削除対象のそれぞれで「リンクは辿らず拒否」と定め、負のテストを検証条件に追加。実装は未了と明記（`build-integration.md` 3.3、`extraction-plan.md` Phase 3、`compatibility.md` 3 章、`roadmap.md`） |
| C8 | 中 | 「書き込みの原子性」の語が過大。複数ファイル間の整合・並行実行が未定義 | 採用。`OutputWriter.write` が検査 → 古い生成物の削除 → 書き込みの順であることを確かめた（削除が先）。「原子性」の語をやめ、保証する範囲（全検査後に書き始める、同内容は書かない）と保証しない範囲（複数ファイル間、並行実行）を分け、方針の実行順を「新規書き込み → 古い生成物の削除」とし、現状と方針を分けて書いた。復旧は再実行で収束させる（`build-integration.md` 3.3） |
| C9 | 中 | 設定可能な moduleID・appDir と、固定された Vite / HonoX 側の配線が整合しない | 採用。HonoX の glob が `/app` 固定であることを確かめた。P0 は `app/routes`・`app/islands`・moduleID `server` / `client` を既定かつ検証済みの唯一の組み合わせとし、それ以外は未検証と明記。共通設定は P1 の未決事項 U-25 に追加（`build-integration.md` 2.0、`roadmap.md` U-18・U-25） |
| C10 | 中 | island の basename 制約が設計・検証条件に落ちていない | 採用。`client/client.js` の探索正規表現と、3ch の `AnnotationScanner` の `IslandBasename` を確かめた。P0 の公開規則とし、拒否例と検証条件を追加（`rendering-model.md` 1.4、`routing-design.md` 1.1・2.2、`compatibility.md` 3 章、`architecture.md` H5） |
| C11 | 中 | CI での依存解決を Phase 5 に回すと、Phase 4 の移行時に再現可能な検証経路が無い | 採用。公開は Phase 5 のままとし、Phase 4 の前提に「commit 固定の checkout からの `publishLocal` と npm パッケージ準備の手順（スクリプト）」「空の依存キャッシュから 3ch をビルドできること」を追加（`extraction-plan.md` Phase 4、`adr/0006`） |
| C12 | 低 | 互換性テストの既存カバレッジの説明が過大（`islands-bridge` は jsdom hydration をしない） | 採用。`islands-bridge`・`islands-hydration`・`board-top-hydration`・`board-islands`・`client-bundle` を実ファイルで読み、確かめていること・いないことを分けて表にした。型付き Props のコンパイル検証は既存テストに無いので別に必須とした（`compatibility.md` 3 章） |

## 3. 確かめられなかった点

- 3ch の作業ツリーは調査中に変わっていた（`HonoFacade.scala` の分割、`io._3ch.common.islands` と `validation` の追加）。文書は commit `875a47b` を基準にし、未コミットの変更は「進行中」と書いた。
- 3ch のビルド・テストは実行していない（書き込み・sbt・npm は禁止）。Phase 1 / 2 の実装状況（`1-3` の一部、`2-3`、`2-4` が入っていること）は、ソースの読解による。
- Windows junction を `Files.isSymbolicLink` 等で検出できるかは未確認。実装時に確定する（`build-integration.md` 3.3）。
- `typed export 補助` の型の形（`IslandExport[P]` など）は案であり、Scala の型として成立するかは未検証。
