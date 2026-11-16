# ADR 0007: props の検証は hx4s-core の JS 側、typed export は hx4s-react の `IslandExport[P]`

- 状態: 承認（2026-11-16、Phase 3）
- 関連: [ADR 0003](0003-typed-island-props.md)、[rendering-model.md](../rendering-model.md) 2.3・2.4、[roadmap.md](../roadmap.md) U-12・U-24・U-26、Phase 0 レビュー C5

## 背景

ADR 0003 の型付き Props は、server の呼び出し `IslandComponents.Xxx(props: P)` だけを型で守り、client の island が同じ `P` を読む保証が無かった（C5、U-24）。
client の受信値は手書きの検証（`validate(raw): Either[String, P]`）に頼る方針で、移動元では `JsShape` という検証器が shared の Scala.js 側にあった。
Phase 3 でこれをどのモジュールに置くか、client の export を `P` にどう結び付けるかを決める。

条件:

- client のバンドルに、HonoX の SSR 用 import（`honox/vite/components` の `HonoXIsland`）や server のコードを引き込まない。
- `hx4s-core` は React に依存しない（[module-boundaries.md](../module-boundaries.md) 2 章）。
- 依存を増やさない。マクロや codec の導出は入れない（ADR 0003 の却下案）。

## 決定

### JsShape の置き場所

`hx4s.props.{JsShape, JsKind, JsField}` を **`hx4s-core` の JS 側**（`modules/core/js`）に置く。`hx4s-core` は crossProject（Full）になり、JVM 側はアノテーションだけを持つ。

- JsShape は `scala.scalajs.js` だけに依存し、React にも HonoX にも依存しない。
- shared の Props trait と同じ場所（`hx4s-core` だけに依存する shared プロジェクト）で `fields` を書ける。shared が `hx4s-react` に依存する必要が無い。

### typed export 補助

`hx4s-react` に次を置く。

```scala
@js.native sealed trait IslandExport[P <: js.Object] extends js.Object

object IslandExport {
  def apply[P <: js.Object](label: String, fields: List[JsField])(render: P => VdomNode): IslandExport[P]
  def unchecked[P <: js.Object](label: String)(render: P => VdomNode): IslandExport[P]
}
```

client の宣言は次の形にする。

```scala
@islands[example.shared.CounterProps]("counter.tsx")
@JSExportTopLevel("CounterJs", "client")
val counter: IslandExport[CounterProps] = IslandExport("CounterJs", CounterProps.fields)(props => Counter.Component(props))
```

保証の鎖:

| 区間 | 誰が確かめるか |
| --- | --- |
| `@islands[P]` の `P` と、export の宣言型 `IslandExport[T]` の `T` | hx4s-codegen が構文で照合する。`T` が完全修飾名なら `_root_` を補って一致、単純名なら `P` の最後の名前と一致することを求める。宣言型が `IslandExport[T]` なのに `@islands` に型引数が無い場合も誤り。宣言型が `IslandExport` でなければ照合しない（`js.Any` の export も従来どおり書ける） |
| 宣言型 `IslandExport[T]` と描画関数の props の型 | Scala コンパイラ。`IslandExport` は不変で `sealed` なので、別の `P` の export や描画関数は代入できない |
| 受信した JSON と `P` | `IslandExport.apply` が `JsShape` で確かめる。不正なら `console.error` を出して何も描画しない |

`IslandExport` は `sealed` な native JS trait で、実体は scalajs-react の `toJsComponent.raw`（JS の関数コンポーネント）である。`@JSExportTopLevel` の値としてそのまま公開でき、
生成した island TSX の `export default` と HonoX の `createClient` からは普通の React コンポーネントに見える。

### hydrate の前の検証（世代ずれ）

`IslandExport.apply` は、export した関数に検証関数 `hx4sValidate(props): string | undefined` を付ける。
`app/client.ts` の `hydrate` で `element.type.hx4sValidate?.(element.props)` を呼び、理由が返れば `console.error` を出して `hydrateRoot` を呼ばない。
これで ADR 0003 / rendering-model 2.4 の P0 の方針（不一致なら hydrate せず SSR の markup を残す）を実装した。

`app/client.ts` の雛形（`examples/minimal/app/client.ts`）にこの数行を置き、ヘルパーにはしない（U-7 の方針どおり）。
検証関数の名前 `hx4sValidate` は雛形と `IslandExport` の間の約束で、公開 API に含める。

## 却下案

| 案 | 理由 |
| --- | --- |
| JsShape を `hx4s-react` に置く | shared が props の `fields` を書くために `hx4s-react`（scalajs-react）に依存することになる |
| JsShape を別モジュール（`hx4s-props`）にする | 成果物と依存行が増える。core の JS 側で依存の条件を満たせる |
| `type IslandExport[P] = React.ComponentType[P]` の別名 | 検証を通さない任意のコンポーネント（`toJsComponent.raw`）を代入できてしまう。`ComponentType` は関数型の反変性で、`P` の上位型の props を受けるものも通る |
| export の宣言型を codegen だけで照合し、Scala の型は付けない | 宣言型と実装の食い違いを検出できない |
| `hx4sValidate` を使わず、検証失敗時に空を描画するだけ | hydrate で SSR の markup が消え、画面が空になる（移動元の挙動）。P0 の方針に合わない |

## 影響

- 構文での照合なので、型エイリアスや import の別名で書いた `T` は追えない（ADR 0004）。単純名の `T` は最後の名前の一致だけを見るので、同名の別の型は見逃す。
- `fields` と `P` の対応は手書きで、ずれてもコンパイルは通る（JsShape の検証が実行時に拾う）。マクロでの導出は P2 の候補のまま。
- client のプロジェクトは `hx4s-react` に依存する。`IslandExport` は `Island`（`HonoXIsland` の `@JSImport`）を参照しないので、Scala.js のリンクで `honox/vite/components` は client に入らない。
  examples/minimal の `tests/build-output.test.js` が client のバンドルに `HonoXIsland` が無いことを確かめる。
