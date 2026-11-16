# 描画モデル

## 1. 描画方式の位置づけ

| 方式 | 位置づけ | 3ch での状態 | hx4s での状態 |
| --- | --- | --- | --- |
| SSR（静的） | hydration しない HTML。専ブラ互換・Shift_JIS 応答もここ | 実装済み。`publicHandler` 経由の既存画面、`RendererShell.legacyReact` / `createSjisResponse` | 特別な支援は不要（scalajs-react の `renderToStaticMarkup` と Hono の Response で書ける）。Shift_JIS は 3ch に残す |
| SSR + Islands | 既定の方式。ページは SSR、対話部分だけを island として hydrate | 実装済み。板トップ（`site-header`・`board-details`・`thread-list`）、認証・アカウント画面 | P0 の中心。未実装 |
| SSR + Full Hydration | ページ全体を 1 つの island にする | 未実装 | 構想（P2）。下記 1.2 |
| SPA + API | 静的な HTML シェル + クライアント側ルーティング + JSON API | 未実装（管理画面の SPA 化は `docs/admin-spa-auth-plan.md` で計画中・未着手） | 構想（P1 の Client Router と合わせて検討） |
| API-only | HTML を返さないハンドラ | 実装済み（`/auth/*` の API、dat / subject.txt） | `@route` の生成ルートで書ける。特別な支援は不要 |
| Hybrid | 上記をルートごとに混在 | 実装済み（SSR 静的・Islands・API が同じ Worker に同居） | 既定で成立する（HonoX のファイルルートがルート単位で独立しているため） |

### 1.1 SSR + Islands のエントリポイントと境界

| 段階 | エントリポイント | 内容 |
| --- | --- | --- |
| サーバー | `app/server.ts`（`createApp`） → 生成ルート `app/routes/**/*.ts` → Scala のハンドラ | ハンドラが scalajs-react の要素を作り `c.render` に渡す |
| renderer | `app/routes/_renderer.tsx`（`@hono/react-renderer`） | HTML のシェルを組み、client script を置く |
| island の SSR | 生成 `IslandComponents.Xxx(props)` → `hx4s.react.Island` → `React.createElement(HonoXIsland, …)` | `<honox-island component-name=… data-serialized-props=…>` で包む |
| クライアント | `app/client.ts` → `createClient({ hydrate, createElement })` | `import.meta.glob('/app/islands/**/*.tsx')` のキーと `component-name` を突き合わせる |
| island のクライアント | 生成 `app/islands/x.tsx`（`export default XxxJs`） → `scalajs:client.js` の chunk | `JSON.parse(data-serialized-props)` を props にして `hydrateRoot` |

Hydration 境界は `HonoXIsland` が決める（H10）。

- island の中で別の island を描画すると、内側は境界を作らず通常のコンポーネントとして描画される。hydration root は外側の 1 つだけになる。
- props に React 要素（または要素の配列）が含まれると、その key は `data-serialized-props` から外れ、`<template data-hono-template="key">` に SSR される。
  クライアントは template の DOM から要素を組み立て直し、中に island があればそれを import して差し込む（`client/runtime.js` の `buildCreateChildrenFn`）。
- 3ch の `tests/islands-hydration.test.js` が「単純・入れ子・要素 props」の 3 通りについて、境界の数、SSR DOM の再利用、hydration エラーが無いこと、
  クリックで状態が変わることを確認している。hx4s はこのテストを互換性テストとして引き継ぐ。

client script は常に読み込む前提とする。HonoX の `HasIslands` は、ルートファイルから island TSX への import を依存木で探して判定するが、
hx4s の生成ルートは `scalajs:server.js` を import するだけなので、この判定は効かない見込み（未検証）。
island を使わないページで script を省く最適化は P1 以降に回す（ルート単位で `IslandComponents` の利用を codegen が判定できるかは未調査）。

### 1.2 SSR + Full Hydration（構想）

ページのルートコンポーネントを 1 つの island として宣言すれば、既存の仕組みのままで成立する。
ただし次の問題があり、P0 では推奨しない。

- ページの全データが `data-serialized-props` に入り、HTML 本体と二重に送られる。
- 属性値の HTML エスケープで `"` が `&quot;` になり、JSON の文字列が多いほど膨らむ。
- ページ単位のコード分割が client チャンクの分割に依存する。

Full Hydration を正式に扱う場合は、props を `<script type="application/json">` に置く方式を検討する。その場合は下記 2.2 の `</script>` 対策が必須になる。

### 1.3 SPA + API（構想）

静的 HTML シェルを返すルートと、JSON を返す `@route` を組み合わせれば現行の仕組みで書ける。
クライアント側ルーティングとサーバールートの二重管理を避ける方法は [routing-design.md](routing-design.md) 4 章で扱う。

## 2. Props のシリアライズ

### 2.1 HonoX の実装

`honox/dist/vite/components/honox-island.js`（0.1.62）は次のように props を分ける。

```js
const isElementPropValue = (value) => Array.isArray(value) ? value.some(isElementPropValue)
  : typeof value === "object" && isValidElement(value);
// 要素 → elementProps（<template> へ）、それ以外 → restProps
[DATA_SERIALIZED_PROPS]: JSON.stringify(restProps)
```

つまり、要素以外の props は **`JSON.stringify` の結果をそのまま HTML 属性値として** React に渡している。
クライアントは `JSON.parse(element.attributes.getNamedItem("data-serialized-props")?.value ?? "{}")` で戻す。

### 2.2 XSS・`</script>`・Unicode

| 項目 | 扱い | 根拠 |
| --- | --- | --- |
| HTML 属性への注入 | React の SSR が属性値をエスケープする（`"` は `&quot;` など）。3ch のテストで `日本語 "<&>` を含む文字列が往復することを確認済み | `tests/islands-hydration.test.js`、`tests/islands-bridge.test.js` の `&quot;` の期待値 |
| `</script>` | props は `<script>` 内ではなく属性値に入るので、`</script>` で文脈を抜けられる経路は無い。ただし hx4s が将来 props を `<script type="application/json">` に置く場合は、`<` を `<` に置換するなどの対策が要る | `honox-island.js` |
| JSON として無効な値 | `undefined`・関数・Symbol のプロパティは黙って落ちる。`NaN` / `Infinity` は `null` になる。`BigInt` は例外。`Date` は ISO 文字列になり、戻すと文字列のまま | `JSON.stringify` の仕様 |
| Scala の値 | case class・`Option`・Scala コレクション・`Long`（Scala.js では JS の number ではない）は、JS から見えるプロパティ名やオブジェクト構造が Scala の型と一致しない。直接渡すと壊れた JSON になるか、情報が落ちる | Scala.js の表現。3ch の `docs/islands-ssr.md` も「case class やコレクションを直接渡さない」と明記 |
| Unicode | 非 ASCII 文字はエスケープされずに UTF-8 のまま出る。サロゲートの片割れは `\udXXX` の形で出力され、`JSON.parse` で元に戻る（ES2019 以降の `JSON.stringify`）。U+2028 / U+2029 は属性値の中では問題にならない | `JSON.stringify` の仕様。ページの文字コードは UTF-8 前提（Shift_JIS のページで island を使わない） |
| サイズ | props は属性値として HTML に入り、SSR の markup とは別に送られる。上限は設けられていない | — |
| 関数（イベントハンドラ） | 送れない。クライアントのコンポーネント内で定義する | — |

hx4s の規約:

- island の Props に使える型は `String`・`Double`・`Int`・`Boolean`・`js.UndefOr[T]`・`js.Array[T]`・別の Props trait・React 要素（template 扱い）に限る。
- `js.UndefOr` のフィールドは、値が無いときにキーごと落ちる。クライアントは「キーが無い」と「`undefined`」を区別しない前提で書く。
- Props の検証（必須キーと型）はクライアントの入口で行う。サーバー側は型で保証する（[ADR 0003](adr/0003-typed-island-props.md)）。
- codegen は P0 では Props trait の中身を検査しない（構文ベースで型を解決しないため。[ADR 0004](adr/0004-scalameta-codegen.md)）。
  使える型の制限は文書と、可能なら P1 で Scala の型レベルの制約（`IslandProps` の上限境界など）で補う。

## 3. 型付き Props 方式の比較

3ch の現行は `js.Object` / `js.Dynamic.literal` で組み立て、クライアントで `selectDynamic` で読む。
キー名の食い違いがコンパイルで検出されず、欠落が空文字や 0 に化ける（`docs/island-props型付け計画.md` 1 章）。

| 方式 | 実装の単純さ | バンドルサイズ | 実行時コスト | Scala.js 互換性 | エラー検出性 |
| --- | --- | --- | --- | --- | --- |
| A. 非ネイティブ JS trait を shared に置き、`@islands[P]` の型引数を生成 API の引数型にする（**P0 採用**） | 高い。codegen は型引数の構文を文字列で写すだけ | 追加コード無し（trait は JS オブジェクトの形の宣言） | 変換なし。サーバーは JS オブジェクトを直接作り、クライアントは `asInstanceOf` | 標準機能。`js.UndefOr` / `js.Array` の扱いは Scala.js の規則どおり | サーバー側のフィールド過不足・型違いはコンパイルエラー。クライアントの受信値は未検査なので手書き `validate` が要る |
| B. case class + Scala 3 `Mirror` で codec を導出 | 中。codec の導出とエラー報告を書く必要がある。`Option`・enum・入れ子の規則も決める | codec の分だけ増える（量は未計測） | SSR とクライアントで変換が 1 回ずつ走る | Mirror はコンパイル時の機能で Scala.js でも動く。外部ライブラリ（upickle / circe 等）を使うとその分の依存とサイズが増える | 両側とも型付き。デコード時に欠落を `Either` で検出できる |
| C. `js.Dynamic` 継続 | 最も単純（現状維持） | 追加なし | 変換なし | 問題なし | 検出できない。キーの typo・欠落が実行時に空表示になる |
| D. Scala 3 マクロで case class から JS オブジェクトへの変換と検証を生成 | 低い。マクロの保守と、エラーメッセージの品質の確保が要る | 生成コードの分だけ増える（量は未計測。B より小さくできる可能性はある） | 変換が 1 回ずつ走る | マクロは Scala 3 の標準機能だが、`js.Object` 周りの扱いは Scala.js 固有の制約に注意がいる | B と同等。コンパイル時に使えない型を拒否できる |

A を採る理由は、ランタイムの追加がゼロで codec 依存が要らず、3ch の既存計画（`docs/island-props型付け計画.md`）とそのまま一致するため。
弱点の「クライアント側で受信値が検証されない」は、shared に置く `validate(raw): Either[String, P]` で補う。これは手書きになる。

B（と D）は Props を Scala の値として扱いたい利用者向けの追加 API として P1 / P2 の候補にする。導入する場合も A を置き換えず、
`@islands[P]` の `P` が case class なら codec を経由する、という形で共存させる案が有力（未決事項 U-22）。
