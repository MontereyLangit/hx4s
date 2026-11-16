# ADR 0004: コード生成は scalameta の構文解析を継続する

- 状態: 承認（2026-11-16、Phase 0）
- 関連: [build-integration.md](../build-integration.md) 3 章、[routing-design.md](../routing-design.md) 1.1・2.2

## 背景

3ch の `codegen` は scalameta 4.17.4（`_2.13` 成果物を Scala 3 から利用）で `object Main` を構文解析し、アノテーションの文字列リテラルと
`@JSExportTopLevel` の引数から配線ファイルを作っている。生成物は Vite のビルドより前に必要で（HonoX が `app/routes` と `app/islands` を glob するため）、
Scala のコンパイル結果に依存させると順序が複雑になる。

## 決定

- 生成は scalameta の構文解析で行う。意味解析（型の解決、import の追跡）はしない。
- アノテーション引数は文字列リテラルに限る。`@islands[P]` の `P` は構文をそのまま写す。
- 生成物について次を守る。
  - 決定的な出力（並び順・改行コード `\n`）。
  - 内容が同じなら書き直さない（比較は改行を正規化してから）。
  - 生成バナーの無いファイルは上書き・削除しない。
  - 出力先ルートの外へ書かない（入力パスのセグメント検査に加え、正規化後のパスで検査）。
  - 大文字小文字だけが違うパスの衝突を検出する。
  - すべての検査を書き込みの前に済ませる。

## 却下案

| 案 | 理由 |
| --- | --- |
| Scala 3 マクロ | 型は解決できるが、コンパイル中に `app/` へ TS を書く副作用になる。Vite のビルド前に生成物が要るので、Scala のコンパイルを生成の前提にすると順序が崩れる |
| sbt の source generator（sbt 本体の中で解析） | sbt 1.x は Scala 2.12。生成器を 2.12 で書き直すか、2 系統を保守することになる。入口としては `sourceGenerators` から `hx4sGenerate` を呼ぶ形で使う |
| コンパイラプラグイン | Scala 3 のコンパイラ内部 API に依存し、版ごとの保守が重い |
| 正規表現でのソース走査 | 複数行のアノテーション・コメント・文字列内の偽陽性を扱えない |

## 影響

- 型エイリアス・import した別名の Props 型は解決できない。完全修飾名を求める（U-11）。
- 引数に定数（`final val`）や式を使えない。ルート定義を値として共有する P1 の `hx4s-routing` は、構文で追える DSL に限るか、マクロの再検討が要る（[routing-design.md](../routing-design.md) 4.2）。
- Props trait の中身（JSON 互換の型か）は codegen では検査できない。
- scalameta の `_2.13` 成果物を Scala 3 から使う構成を続ける。scalameta の Scala 3 ネイティブ版が出たら移行を検討する。
