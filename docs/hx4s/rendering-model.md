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

Full Hydration を正式に扱う場合は、props を `<script type="application/json">` に置く方式を検討する。その場合は下記 2.2 の `</script>` 対策（`<` を `\u003c` に置換）と、
その検証が必須になる。必須検証: `</script><script>…</script>` や `<!--`、U+2028 / U+2029 を含む文字列値を、SSR → HTML → クライアントの `JSON.parse` で往復させ、(1) 値が元どおりに戻ること、(2) 注入したスクリプトが実行されないことを jsdom で確かめる。

### 1.3 SPA + API（構想）

静的 HTML シェルを返すルートと、JSON を返す `@route` を組み合わせれば現行の仕組みで書ける。
クライアント側ルーティングとサーバールートの二重管理を避ける方法は [routing-design.md](routing-design.md) 4 章で扱う。

### 1.4 island のファイル名規則（P0 の公開規則）

HonoX の `createClient` は island の候補を `import.meta.glob('/app/islands/**/*.tsx')` で集め、さらに basename が `/\/[a-zA-Z0-9-]+\.tsx$/` に一致するものだけを hydrate 対象にする
（`client/client.js`、0.1.62）。一致しないファイルは SSR で `<honox-island>` が出ても、クライアントが探索しないので hydrate されない。

hx4s は次を **P0 の公開規則** とする。`@islands` の `filePath` の basename は `[A-Za-z0-9-]+\.tsx` に一致すること。
3ch の `AnnotationScanner` は `IslandBasename` としてすでに検査している（`875a47b`）。

| 例 | 結果 |
| --- | --- |
| `thread-view.tsx`、`Foo2.tsx`、`a/b-c.tsx` | 許可 |
| `foo_bar.tsx`、`foo.bar.tsx`、`foo bar.tsx`、`foo.island.tsx`、`$foo.tsx` | 生成時に拒否（HonoX が別規則で拾う `*.island.tsx` / `$*.tsx` も P0 では使わない） |

検証条件（[compatibility.md](compatibility.md) 3 章に追加）: 許可名は SSR → 探索 → hydration まで通ること。拒否名は生成時にエラーになること。


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
| `</script>` | 現行の方式（属性値）には当てはまらない。props は `<script>` 内ではなく属性値に入るので、`</script>` で文脈を抜けられる経路は無い。将来 props を `<script type="application/json">` に置く場合は、JSON 文字列中の `<` を **6 文字の文字列 `\u003c` に置換**して出力する（置換後は JSON として元の `<` に戻る。HTML の文脈では `<` が現れないので `</script>` を作れない）。`<` を別の文字に置き換える、`<` をそのまま出す、`</script` だけを対象にする、のいずれも不十分とする | `honox-island.js` |
| JSON として無効な値 | `undefined`・関数・Symbol のプロパティは黙って落ちる。`NaN` / `Infinity` は `null` になる。`BigInt` は例外。`Date` は ISO 文字列になり、戻すと文字列のまま | `JSON.stringify` の仕様 |
| Scala の値 | case class・`Option`・Scala コレクション・`Long`（Scala.js では JS の number ではない）は、JS から見えるプロパティ名やオブジェクト構造が Scala の型と一致しない。直接渡すと壊れた JSON になるか、情報が落ちる | Scala.js の表現。3ch の `docs/islands-ssr.md` も「case class やコレクションを直接渡さない」と明記 |
| Unicode | 非 ASCII 文字はエスケープされずに UTF-8 のまま出る。サロゲートの片割れは `\udXXX` の形で出力され、`JSON.parse` で元に戻る（ES2019 以降の `JSON.stringify`）。U+2028 / U+2029 は属性値の中では問題にならない | `JSON.stringify` の仕様。ページの文字コードは UTF-8 前提（Shift_JIS のページで island を使わない） |
| サイズ | props は属性値として HTML に入り、SSR の markup とは別に送られる。上限は設けられていない | — |
| 関数（イベントハンドラ） | 送れない。クライアントのコンポーネント内で定義する | — |

hx4s の規約:

- island の Props に使える型は `String`・`Double`・`Int`・`Boolean`・`js.UndefOr[T]`・`js.Array[T]`・別の Props trait・React 要素（template 扱い）に限る。
- `js.UndefOr` のフィールドは、値が無いときにキーごと落ちる。クライアントは「キーが無い」と「`undefined`」を区別しない前提で書く。
- Props の検証（必須キーと型）はクライアントの入口の `validate` で行う。サーバー側は、呼び出し側の引数が `P` に合うことを型で保証する（保証の範囲は 2.3、[ADR 0003](adr/0003-typed-island-props.md)）。
- `validate` が失敗したとき、クライアントは `console.error` を出して hydration せず、SSR の markup を残す。これを P0 の方針とする（U-12）。
- codegen は P0 では Props trait の中身を検査しない（構文ベースで型を解決しないため。[ADR 0004](adr/0004-scalameta-codegen.md)）。
  使える型の制限は文書と、可能なら P1 で Scala の型レベルの制約（`IslandProps` の上限境界など）で補う。

### 2.3 型付き Props の保証範囲と JSON 往復の制約

Scala の型が保証することと、JSON が保証することを分けて書く。

**Scala の型が保証すること（P0）**: `IslandComponents.Xxx(props: P)` の呼び出し側の引数が `P`（非ネイティブ JS trait）に合うこと。つまり **server のコードの引数型だけ**である。
client の island コンポーネントが同じ `P` を読む保証は無い。`@islands[P]` の `P` は注釈の型引数として生成 API に写されるだけで、
`@JSExportTopLevel` された client のコンポーネントの引数型とは結び付いていない。したがって client が別の型（`Q`）で `props` を読んでいても、コンパイルは通る。
対策候補として、client の export を `P` に結び付ける **typed export 補助**（例: `def XxxJs: IslandExport[P]` のような型で、`P` と一致しない実装をコンパイルエラーにする）を Phase 3 の設計項目にする（[extraction-plan.md](extraction-plan.md) 3-1a、未決事項 U-24）。
それまでは、shared の `validate` と、client 側の読み取りコードのレビューで補う。

**Phase 3 で実装した**: `hx4s.react.IslandExport[P]`（[ADR 0007](adr/0007-props-validation-and-typed-export.md)）。export の宣言型 `IslandExport[T]` と `@islands[P]` を codegen が構文で照合し、
宣言型と描画関数の props の型はコンパイラが照合する。受信した JSON は `JsShape` で確かめ、hydrate の前にも同じ検証関数を呼んで不一致なら SSR の markup を残す（2.4 の P0 の方針）。

**JSON 往復が保証しないこと**: `P` に適合する値でも、次は `JSON.stringify` を通すと失われる、または失敗する。

| 項目 | 挙動 | 対応 |
| --- | --- | --- |
| 非有限の `Double`（`NaN`・`±Infinity`） | `null` になる。client の `validate` が数値を要求していれば不一致になる | server で渡さない。`validate` で検出する |
| 循環参照 | `JSON.stringify` が例外を投げ、SSR が失敗する（500 になりうる） | Props に循環する構造を作らない。検証項目に入れる |
| `undefined` | キーごと落ちる（配列の中では `null`）。`js.UndefOr` の「値なし」と一致する | 「キーが無い」と「`undefined`」を区別しない（2.2） |
| 入れ子オブジェクトの中の React 要素 | HonoX の要素判定（`isElementPropValue`）は **props のトップレベルの値と、その配列** にしか効かない。入れ子のオブジェクト（別の Props trait のフィールド）に入れた要素は `<template>` にならず、`JSON.stringify` に渡って壊れる | 要素 props は `P` のトップレベルのフィールドか、その配列に限る |
| `Long` | Scala.js の `Long` は JS の number ではなく、JSON に直接出せない | `Props` に `Long` を使わない（`Double` か文字列にする）。2.2 の「使える型」に含まれない |
| 関数・Symbol・`BigInt` | 関数と Symbol は落ち、`BigInt` は例外（2.2） | 使わない |

検証項目（[compatibility.md](compatibility.md) 3 章に追加）: 非有限の `Double`・循環参照・入れ子の要素を含む Props で、SSR が失敗する、または hydration が不一致になることを確かめ、
その場合の挙動（`console.error` と SSR の markup の維持、2.2）が仕様どおりであること。

### 2.4 HTML と client asset の世代ずれ

SSR の HTML（server が出す `data-serialized-props`）と、client の asset（`static/client.js` と island の chunk）は、別々に配信され、別々にキャッシュされる。
**同じデプロイで作られるから一致する、とは言えない。** 次の組み合わせが起こりうる。

- 古い HTML（キャッシュ済み、デプロイ前に取得）+ 新しい client asset
- 新しい HTML + 古い client asset（asset のキャッシュが残っている、CDN の反映が遅れている）
- 読み込み途中でのデプロイ（`static/client.js` は新しく、遅延 import される chunk は古い、またはその逆）

3ch の client の入口は固定名（`vite.config.ts` の `entryFileNames: "static/client.js"`）で、HTML から参照される名前に世代の情報が無い。
この状況で Props のスキーマ（`P` のフィールド）を変えると、世代ずれの間は client が受け取る JSON が `P` に合わなくなる。

P0 の方針: client の入口で `validate` を行い、不一致なら `console.error` を出し、hydration せずに SSR の markup を残す（見た目は静的 HTML のまま。2.2）。
これは世代ずれが起きたときに画面を壊さないための最低限であり、ずれ自体を防ぐものではない。

未決事項（[roadmap.md](roadmap.md) U-26）:

- entry のハッシュ名化（`client.[hash].js`）と、HTML がハッシュ付きの名前を参照する方法（HonoX は manifest を使う方法を持つが、3ch の client ビルドは `honox()` を使っていない）。
- キャッシュ制御（`static/client.js` を再検証させる `Cache-Control` など）。実行基盤側の設定。
- 旧 asset の保持期間（デプロイ後も一定期間、旧世代の chunk を配信し続けるか）。
- Props スキーマ変更の指針: フィールドの追加は `js.UndefOr` で受ける、削除は 2 段階にする、必須フィールドの追加は避ける、など。
- 新旧の組み合わせの検証: 旧 HTML + 新 client、新 HTML + 旧 client の両方で、`validate` が不一致を検出して SSR の markup が残ること。

## 3. 型付き Props 方式の比較

3ch の現行は `js.Object` / `js.Dynamic.literal` で組み立て、クライアントで `selectDynamic` で読む。
キー名の食い違いがコンパイルで検出されず、欠落が空文字や 0 に化ける（`docs/island-props型付け計画.md` 1 章）。

| 方式 | 実装の単純さ | バンドルサイズ | 実行時コスト | Scala.js 互換性 | エラー検出性 |
| --- | --- | --- | --- | --- | --- |
| A. 非ネイティブ JS trait を shared に置き、`@islands[P]` の型引数を生成 API の引数型にする（**P0 採用**） | 高い。codegen は型引数の構文を文字列で写すだけ | 追加コード無し（trait は JS オブジェクトの形の宣言） | 変換なし。サーバーは JS オブジェクトを直接作り、クライアントは `asInstanceOf` | 標準機能。`js.UndefOr` / `js.Array` の扱いは Scala.js の規則どおり | サーバーの呼び出し側のフィールド過不足・型違いはコンパイルエラー（client のコンポーネントが同じ `P` を読む保証は無い。2.3）。クライアントの受信値は未検査なので手書き `validate` が要る |
| B. case class + Scala 3 `Mirror` で codec を導出 | 中。codec の導出とエラー報告を書く必要がある。`Option`・enum・入れ子の規則も決める | codec の分だけ増える（量は未計測） | SSR とクライアントで変換が 1 回ずつ走る | Mirror はコンパイル時の機能で Scala.js でも動く。外部ライブラリ（upickle / circe 等）を使うとその分の依存とサイズが増える | 両側とも型付き。デコード時に欠落を `Either` で検出できる |
| C. `js.Dynamic` 継続 | 最も単純（現状維持） | 追加なし | 変換なし | 問題なし | 検出できない。キーの typo・欠落が実行時に空表示になる |
| D. Scala 3 マクロで case class から JS オブジェクトへの変換と検証を生成 | 低い。マクロの保守と、エラーメッセージの品質の確保が要る | 生成コードの分だけ増える（量は未計測。B より小さくできる可能性はある） | 変換が 1 回ずつ走る | マクロは Scala 3 の標準機能だが、`js.Object` 周りの扱いは Scala.js 固有の制約に注意がいる | B と同等。コンパイル時に使えない型を拒否できる |

A を採る理由は、ランタイムの追加がゼロで codec 依存が要らず、3ch の既存計画（`docs/island-props型付け計画.md`）とそのまま一致するため。
弱点の「クライアント側で受信値が検証されない」は、shared に置く `validate(raw): Either[String, P]` で補う。これは手書きになる。

B（と D）は Props を Scala の値として扱いたい利用者向けの追加 API として P1 / P2 の候補にする。導入する場合も A を置き換えず、
`@islands[P]` の `P` が case class なら codec を経由する、という形で共存させる案が有力（未決事項 U-22）。
