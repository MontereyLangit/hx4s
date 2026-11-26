# ADR 0003: 型付き Props は非ネイティブ JS trait + `@islands[P]`

- 状態: 承認（2026-11-16、Phase 0）
- 関連: [rendering-model.md](../rendering-model.md) 2・3 章、3ch `docs/island-props型付け計画.md`

## 背景

3ch の island の props は、サーバーが `js.Dynamic.literal` で組み立て、クライアントが `selectDynamic` で読み戻している。
生成 API も `IslandComponents.XxxJs(props: js.Object)` で型を持たない。キー名の食い違いや欠落がコンパイルで検出されず、空の画面として現れる。
props は HonoX が `JSON.stringify` して HTML 属性に入れるので、JSON 互換の JS オブジェクトである必要がある。

## 決定

- island の Props は、server / client の両方が参照する shared の Scala.js モジュールに **非ネイティブ JS trait**（`trait XxxProps extends js.Object`）として置く。
- アノテーションを `final class islands[P](val filePath: String) extends StaticAnnotation` にする。`P` に境界は付けない（`hx4s-core` は JVM でもコンパイルされ、`js.Object` が無いため）。
- codegen は `@islands[a.b.XxxProps]("x")` の型引数の **構文** を読み、`IslandComponents.XxxJs(props: a.b.XxxProps): VdomElement` を生成する。型引数を省いた場合は従来どおり `js.Object`。
- `P` は P0 では完全修飾名に限る（U-11）。
- クライアントは `raw.asInstanceOf[P]` で受け、必須キーの検証（`validate(raw): Either[String, P]`）を shared に手書きする。

## 却下案

| 案 | 理由 |
| --- | --- |
| case class + Scala 3 `Mirror` で codec を導出 | codec のコードがバンドルに入り、SSR とクライアントで変換が走る。`Option` や enum の表現規則も決める必要がある。P1 / P2 の追加 API 候補とする |
| `js.Dynamic` を続ける | 型の食い違いを検出できないという問題そのものが残る |
| Scala 3 マクロで変換・検証を生成 | 実装と保守が重い。codegen が構文ベース（[ADR 0004](0004-scalameta-codegen.md)）である以上、型引数を写す以上のことはマクロ側の仕事になり、P0 の範囲を超える |
| `P <: js.Object` の境界を付ける | `hx4s-core` が JVM でもコンパイルできなくなる。JS 専用のアノテーションを別に用意する案もあるが、宣言が 2 つに分かれる |

比較表（実装の単純さ・バンドルサイズ・実行時コスト・Scala.js 互換性・エラー検出性）は [rendering-model.md](../rendering-model.md) 3 章。

## 影響

- 型が保証するのは **server の呼び出し側の引数**（`IslandComponents.Xxx(props: P)`）だけである。client の island コンポーネントが同じ `P` を読む保証は無く、
  `@JSExportTopLevel` された client の export の型は `P` と結び付いていない。クライアント側は手書きの `validate` に頼る。
  client の export を `P` に結び付ける typed export 補助は Phase 3 の設計項目にする（未決事項 U-24、[rendering-model.md](../rendering-model.md) 2.3）。
- 利用側は shared の Scala.js プロジェクトを持つ必要がある（server と client の両方がそれに依存し、server は client に依存しない）。
- 使える型が JSON 互換のものに限られる（[rendering-model.md](../rendering-model.md) 2.2）。codegen はこれを検査しない。`P` に適合する値でも、非有限の `Double`・循環参照・入れ子オブジェクト内の React 要素は JSON 往復で壊れる（同 2.3）。
- 3ch では Phase 1 で板トップの island から置き換え、`data-serialized-props` の形が変わる（`board-details` の入れ子化）。
  **「同じデプロイで更新されるので互換性の問題は無い」とは言えない。** SSR の HTML と client の asset は別々にキャッシュされ、デプロイをまたいで読み込まれるので、
  古い HTML + 新しい client、新しい HTML + 古い client の組み合わせは起こりうる（3ch の client の入口は固定名 `static/client.js`）。
- P0 の方針: client の入口で `validate` を行い、不一致なら `console.error` を出し、hydration せずに SSR の markup を残す（画面は壊れず、静的 HTML のままになる）。
- 未決事項（[roadmap.md](../roadmap.md) U-26）: entry のハッシュ名化、キャッシュ制御、旧 asset の保持、Props スキーマ変更の指針、新旧組み合わせの検証。詳細は [rendering-model.md](../rendering-model.md) 2.4。
