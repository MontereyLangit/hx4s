# hx4s 設計文書（Phase 0）

hx4s は、2ch 互換掲示板「3ch」の Scala.js × HonoX 連携基盤を独立した OSS メタフレームワークとして切り出す計画である。
この版は Phase 0（設計）の成果物で、**hx4s 側のコードはまだ 1 行も無い**。記述中の「現行実装」は 3ch リポジトリ
（`ch3s`、2026-11-16 時点の作業ツリー）を指す。

| 文書 | 内容 |
| --- | --- |
| [architecture.md](architecture.md) | 3ch の現行実装の調査結果、hx4s の全体像、HonoX との責務分担、HonoX 内部 API への依存箇所 |
| [module-boundaries.md](module-boundaries.md) | フレームワークと掲示板固有コードのファイル単位の分類、依存方向、公開 API と内部 API |
| [extraction-plan.md](extraction-plan.md) | Phase 0〜5 の手順・検証条件・完了条件、移動ファイルの対応表 |
| [rendering-model.md](rendering-model.md) | 描画方式の位置づけ、Hydration 境界、Props のシリアライズ制約、型付き Props 方式の比較 |
| [routing-design.md](routing-design.md) | `@route` の現状と拡張方針、検証範囲、型付きルート・Client Router の P1 案 |
| [build-integration.md](build-integration.md) | sbt + Vite の現状、hx4s-vite と sbt-hx4s の責務、codegen・CLI の方式比較 |
| [compatibility.md](compatibility.md) | 依存バージョン、HonoX アルファ版への追従方針、ライセンス |
| [roadmap.md](roadmap.md) | P0 / P1 / P2 の機能と状態、未決事項 |

ADR（1 判断 1 ファイル）:

| ADR | 判断 |
| --- | --- |
| [0001](adr/0001-name-and-coordinates.md) | 名前と公開座標（`hx4s`、`hx4s.*`、`io.github.montereylangit`、`hx4s-vite`） |
| [0002](adr/0002-initial-modules.md) | 初期モジュール構成（core / react / codegen / sbt-hx4s / hx4s-vite / examples） |
| [0003](adr/0003-typed-island-props.md) | 型付き Props は非ネイティブ JS trait + `@islands[P]` |
| [0004](adr/0004-scalameta-codegen.md) | コード生成は scalameta の構文解析を継続する |
| [0005](adr/0005-core-independent-of-cloudflare.md) | Core は Cloudflare 固有 API に依存しない |
| [0006](adr/0006-one-way-dependency-and-local-publish.md) | 3ch → hx4s の一方向依存と、分離直後のローカル依存 |

前提となる進め方（スクラッチ再実装をしない、3ch で改善・検証してから切り出す）は
[extraction-plan.md](extraction-plan.md) に、Effect System・DI を必須にしない方針は [architecture.md](architecture.md) の 3.4 に書いた。
