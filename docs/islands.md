# Islands

island は、SSR した HTML のうち、ブラウザで hydrate する部分。境界の検出・props のシリアライズ・hydrate は HonoX が行い（`<honox-island>`）、hx4s はそこへ Scala で書いたコンポーネントを型付きで載せる。
描画の流れは [ssr-hydration.md](ssr-hydration.md)。

island は次の 3 つで成り立つ。

| 部品 | 置き場所 | 役割 |
| --- | --- | --- |
| props の trait と検証の規則 | shared | server と client が共有する型 `P` |
| `@islands[P]` を付けた `IslandExport[P]` | client | ブラウザへ配るコンポーネント |
| 生成された `IslandComponents.<名前>(props: P)` | server（自動生成） | SSR でコンポーネントを置く |

最小の組は [getting-started.md](getting-started.md) と `examples/minimal` にある。

## props の trait

props は `js.Object` を継承した **非ネイティブの JS trait** にする。server は `new CounterProps { val label = ...; val start = ... }` で組み立て、client は同じ型で読む。変換は無く、JS のオブジェクトをそのまま渡す。

```scala
trait OwnerProps extends js.Object {
  val name: String
  val since: Int
}

trait ItemListProps extends js.Object {
  val title: String
  val owner: OwnerProps                 // 入れ子の props
  val items: js.Array[ItemProps]
  val note: js.UndefOr[String]          // 省略できる
}
```

### 使える型

props は JSON（HonoX が `JSON.stringify` して属性値に入れる）で受け渡されるので、JSON に往復できる型に限る。

| 型 | 備考 |
| --- | --- |
| `String` | |
| `Int` | |
| `Double` | 有限の値だけ。`NaN` と `Infinity` は `null` になる |
| `Boolean` | |
| `js.UndefOr[T]` | 値が無いときはキーごと落ちる。「キーが無い」と「`undefined`」は区別されない |
| `js.Array[T]` | 要素は使える型 |
| 別の props trait | 入れ子のオブジェクト |

使えない型と、その結果。

| 型 | 結果 |
| --- | --- |
| 関数（イベントハンドラなど） | 送れない（JSON から落ちる）。client のコンポーネントの中で定義する |
| `Option` / Scala のコレクション（`List`・`Map`）/ case class | JS から見えるプロパティが Scala の型と一致せず、壊れた JSON になるか情報が落ちる。`js.UndefOr` / `js.Array` / props trait に変換して渡す |
| `Long` | Scala.js の `Long` は JS の number ではなく、JSON に出せない。`Double` か文字列にする |
| `BigInt` / `Date` / Symbol | `BigInt` は例外、`Date` は文字列になる、Symbol は落ちる |
| 循環する構造 | `JSON.stringify` が例外を投げ、SSR が失敗する |
| 入れ子の trait の中の React 要素 | HonoX が要素として扱うのは props のトップレベルの値とその配列だけで、入れ子の中の要素は壊れる |

## `@islands[P]`

```scala
import hx4s.annotation.islands
import hx4s.react.IslandExport

@islands[example.shared.CounterProps]("counter.tsx")
@JSExportTopLevel("CounterJs", "client")
val counter: IslandExport[CounterProps] = IslandExport("CounterJs", CounterProps.fields)(props => Counter.Component(props))
```

| 項目 | 規則 |
| --- | --- |
| 対象 | 設定したソース（client プロジェクト）のトップレベル object の `def` / `val` |
| 型引数 `P` | props の型の**完全修飾名**。import した短い名前は書けない（`must be a fully qualified class name`）。省くと `js.Object` |
| `filePath` | `app/islands` からの相対パスで `.tsx` で終わる文字列リテラル |
| `@JSExportTopLevel` | ちょうど 1 つ。moduleID は `"client"`。export 名は JS の識別子で、プロジェクトの中で一意 |
| 1 つのメンバの `@islands` | 1 つまで |

### ファイル名の規則

HonoX の `createClient` は basename が `[A-Za-z0-9-]+\.tsx` のファイルだけを hydrate の対象にする。一致しないファイルは SSR では境界が出ても hydrate されないので、hx4s は生成の時点で拒否する。

| 例 | 結果 |
| --- | --- |
| `thread-view.tsx`、`Foo2.tsx`、`a/b-c.tsx` | 使える |
| `foo_bar.tsx`、`foo.bar.tsx`、`foo bar.tsx`、`foo.island.tsx`、`$foo.tsx` | 拒否: `island basename of bad_name.tsx must match [A-Za-z0-9-]+\.tsx for HonoX discovery` |

`.` で始まるディレクトリと `node_modules` を含むパスも拒否される。大文字小文字だけ違うパスは重複として拒否される。

## `IslandExport[P]`

client が公開する island の型。`@islands[P]` の `P` と同じ型にする。

| 作り方 | 動作 |
| --- | --- |
| `IslandExport(label, fields)(render)` | `fields`（`JsField` のリスト）で受け取った props を確かめてから `render` で描画する。不正なら `console.error("[label] invalid props: …")` を出して何も描画しない。hydrate 前の検証にも使われる |
| `IslandExport.unchecked(label)(render)` | 検証せずに描画する。props の形は server と client の約束だけで保たれる。hydrate 前の検証の対象にならない |

`render` は `P => VdomNode`。`label` は React の表示名と、エラーメッセージの接頭辞に使う。

### 型の保証の範囲

| 保証 | 方法 |
| --- | --- |
| server が `P` 以外を渡したらコンパイルエラー | 生成される `IslandComponents.CounterJs(props: P)` の引数の型 |
| client の export の型と `@islands[P]` が食い違ったらエラー | codegen が export の宣言型 `IslandExport[T]` の `T` と `P` を構文で照合する。例: `IslandExport[CounterProps] does not match @islands[example.shared.ItemListProps]` |
| 宣言型と `render` の props の型が食い違ったらコンパイルエラー | Scala のコンパイラ |
| 受信した JSON が `P` の形か | 実行時に `JsShape` で検証する（`fields`） |

**保証されない点**: `fields` と trait は別々に書くので、食い違いはコンパイルで見つからない。`val start: Int` の trait に `required("start", JsKind.Str)` と書いても通る。
codegen の照合は型名の構文的な一致で、型エイリアスや import の別名は追えない。

### `JsShape` と `JsKind`

`fields` は `JsShape.required(name, kind)` と `JsShape.optional(name, kind)` で作る。

| `JsKind` | 意味 |
| --- | --- |
| `Str` | 文字列 |
| `Num` | 有限の数 |
| `Int` | `Int` の範囲の整数 |
| `Bool` | 真偽値 |
| `Obj(fields)` | 入れ子のオブジェクト |
| `Arr(element)` | 配列 |

```scala
private val owner = JsKind.Obj(List(required("name", JsKind.Str), required("since", JsKind.Int)))
val fields: List[JsField] = List(
  required("title", JsKind.Str), required("owner", owner), required("items", JsKind.Arr(item)), optional("note", JsKind.Str)
)
```

`JsShape.validate[P](raw, fields)` は誤りをすべて集めて `Left(一覧の文字列)`、`JsShape.read` は失敗時に `console.error` を出して `None` を返す。
誤りの位置は `props.owner.since: expected number, got string` のように示される。

## 生成される `IslandComponents`

`sbt server/compile` のたびに（入力が変わったときだけ）`server/target/.../src_managed/hx4s/generated/IslandComponents.scala` が生成される。

```scala
import hx4s.generated.IslandComponents

IslandComponents.CounterJs(new CounterProps { val label = "A"; val start = 0 })   // VdomElement
```

- メソッド名は `@JSExportTopLevel` の名前（`CounterJs`）。引数の型は `@islands[P]` の `P`、型引数を省くと `js.Object`
- 戻り値は `VdomElement`。HonoX の island の境界（`<honox-island component-name="/app/islands/counter.tsx" data-serialized-props=…>`）で包まれる
- パッケージとオブジェクト名は `hx4sIslandComponentsPackage` / `hx4sIslandComponentsObject` で変えられる（[build-configuration.md](build-configuration.md)）
- island が 1 つも無ければ空の `object IslandComponents {}` になる
- コミットしない（`target` の下）

型の検査は `examples/minimal` の `TypedPropsSuite` が `compileErrors` で確かめている。`IslandComponents.CounterJs(new js.Object)` や別の island の props を渡す呼び出しは、コンパイルできない。

## 生成される island のファイル

`@islands` ごとに、`app/islands/<filePath>` に client の export を再公開するファイルが生成される。

```tsx
// This file is generated by hx4s-codegen. Do not edit this file manually.
// @ts-expect-error scalajs:client.js は @scala-js/vite-plugin-scalajs が解決する仮想モジュール（.d.ts無し）。
import { CounterJs as __hx4s_island } from "scalajs:client.js";
export default __hx4s_island;
```

HonoX は `app/islands/**/*.tsx` の default export を island として探す。実体は 1 つにリンクされた Scala.js の client モジュールの中にあり、island ごとのファイルは再 export だけ。

## 複数のインスタンス・入れ子・要素 props

- **同じ island を複数置く**: できる。`examples/minimal` のトップページはカウンターを 2 つ置き、それぞれが別の props を持つ（`tests/build-output.test.js`、E2E で確かめている）
- **island の中に別の island を描画する**: HonoX の仕様で、内側は境界を作らず通常のコンポーネントとして描画される。hydration root は外側の 1 つ
- **React 要素を props に渡す**（`children` のような）: HonoX は要素を `data-serialized-props` から外して `<template>` に SSR し、client が復元する。
  hx4s の互換性テストは、この 3 つ（単純・入れ子・要素 props）を JS のコンポーネントと Scala の `Island` で確かめている（`tests/islands-hydration.test.js`）

ただし、**要素 props を持つ `P` を `IslandComponents` と `IslandExport` で書く例は examples に無く、検証していない**。`JsKind` には要素を表す種類が無いので、`fields` で検証できない。
必要なときは、まず `examples/minimal` と同じ形で動かしてから試す。

## やってはいけないこと

| 操作 | 理由 |
| --- | --- |
| 関数を props で渡す | JSON に出せない。コールバックは client のコンポーネントの中で作る |
| `Option` / `List` / `Map` / case class / `Long` を props の型にする | 上の表のとおり壊れる |
| `P` のフィールドを変えて server だけ先にデプロイする | 古い client が新しい props を受け取る。必須キーの追加や型の変更は避け、追加は `js.UndefOr` にする。不一致は hydrate 前の検証が止めるが、island は静的な HTML のままになる |
| 生成された `app/islands/*.tsx`・`app/routes/*.ts` を編集する | 次の生成で上書きされる（生成マーカー付き） |
| client から server のモジュールを import する | server のコードがブラウザのバンドルに入る。[ssr-hydration.md](ssr-hydration.md) の「server のコードを client に入れない」 |
| basename が規則に合わない island を手書きで置く | HonoX が hydrate しない |
