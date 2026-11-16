# SSR と hydration

既定の構成は **SSR + Islands**。ページは server で HTML にし、対話が要る部分だけを island としてブラウザで hydrate する。
ルートごとに静的な SSR・Islands・API を混在させられる（HonoX のファイルルートがルート単位で独立しているため）。

## 描画の流れ

| 段階 | 入口 | 内容 |
| --- | --- | --- |
| 1. リクエスト | `app/server.ts`（`createApp`）→ 生成ルート `app/routes/**/*.ts` → Scala のハンドラ | ハンドラが scalajs-react の要素を作り、`c.renderPromise(element, props)` に渡す |
| 2. renderer | `app/routes/_renderer.tsx`（`@hono/react-renderer`） | Scala の `renderShell` が HTML 全体を組み、client の `<script>` を置く |
| 3. island の SSR | `IslandComponents.CounterJs(props)` | `<honox-island component-name="/app/islands/counter.tsx" data-serialized-props="…">` で包んだ HTML を出す |
| 4. ブラウザ | `app/client.ts`（`createClient`） | `component-name` と `app/islands/**/*.tsx` を突き合わせ、`JSON.parse(data-serialized-props)` を props にして hydrate する |

`_renderer.tsx` と `app/client.ts` は手書きで、examples とほぼ同じ内容になる（[getting-started.md](getting-started.md)）。
client の `<script>` は island の有無にかかわらず読み込む。island の無いページで省く最適化は未実装。

## hydration の境界

境界は HonoX の `HonoXIsland` が決める。hx4s の `hx4s.react.Island` はそれを呼ぶだけの薄いブリッジで、`IslandComponents` が使う。

- island の中で別の island を描画すると、内側は境界を作らず通常のコンポーネントとして描画される。hydration root は外側の 1 つ
- props に React 要素（またはその配列）があると、HonoX はそれを `<template>` に SSR し、client が復元する
- 同じ island を複数置いたときは、境界ごとに別の props を持つ

## `app/client.ts` と props の検証

`hx4s-vite/client` の `islandPropsProblem(element)` を hydrate の前に呼ぶ。

```ts
hydrate: (element, root) => {
  const problem = islandPropsProblem(element);
  if (problem !== undefined) {
    console.error(`[hx4s] skipped hydration of ${root.getAttribute("component-name")}: ${problem}`);
    return;
  }
  hydrateRoot(root, element);
},
```

| 区分 | 内容 |
| --- | --- |
| 確かめる | hydrate する island の props。その要素 props（HonoX が `<template>` から復元する子。配列の中も辿る）に含まれる island の props。対象は `IslandExport(label, fields)` で作った export |
| 不一致のとき | その境界全体を hydrate しない。内側だけが不正でも外側を含めて SSR の HTML のまま残す。理由は `props.children[1]: props.start: missing` のように位置付きで返る |
| 確かめない | `IslandExport.unchecked` や JS で書いた island。要素 props 以外（普通の値の中）に入った要素。hydrate の後に読み込まれる chunk の世代。HonoX の Suspense で後から届く内容 |

`hx4s-vite/client` は Vite のプラグイン本体を import しないので、client のバンドルに Vite や HonoX の SSR 用のコードは入らない。

## HTML と client の世代ずれ

SSR の HTML と client の asset（`static/client.js` と island の chunk）は別々に配信され、別々にキャッシュされる。デプロイの前後で次の組み合わせが起こりうる。

- 古い HTML（キャッシュ済み）と新しい client
- 新しい HTML と古い client（asset のキャッシュが残っている）

props の形（`P` のフィールド）が変わっていると、client が受け取る JSON が `P` に合わない。hx4s の挙動は次のとおり。

- 上の検証が不一致を見つけると、`console.error` を出して**その island を hydrate せず、SSR の HTML を残す**。見た目は静的な HTML のままで、画面は壊れない
- 旧 HTML と新 client の組み合わせ（props のキーが足りない）は、examples の `tests/hydration.test.js`（jsdom）で確かめている。新 HTML と旧 client は検証していない

これは世代ずれが起きたときに画面を壊さないための最低限で、ずれ自体は防がない。client の入口は既定で固定名（`static/client.js`）で、ハッシュ名にしていない。
キャッシュ制御・旧 asset の保持・props を変えるときの手順は未決（[hx4s/roadmap.md](hx4s/roadmap.md) U-26、[hx4s/rendering-model.md](hx4s/rendering-model.md) 2.4）。
実務上は、必須キーの追加や型の変更を避け、追加するキーは `js.UndefOr` で受ける。

## JavaScript が無効なとき

SSR の HTML がそのまま表示される。island の部分も、server が描画した内容（ボタンの文言など）が出るだけで、対話は動かない。
`examples/minimal` のトップページの HTML は、JavaScript なしで読める内容を持つ（`tests/build-output.test.js`）。ページ全体を 1 つの island にする構成（[spa.md](spa.md)）でも、JavaScript を無効にしたブラウザで SSR の部分が表示されることを試作で確かめた。

## XSS とシリアライズ

props は HonoX が `JSON.stringify` して HTML の**属性値**（`data-serialized-props`）に入れる。属性値は React の SSR がエスケープするので、`"`・`<`・`&` や `</script>` を含む文字列も
HTML の構造にならない。`examples/minimal` は、名前に `</script><b>タグ</b> "引用" 'single'` を含む品目と、`</script><!--` を含む props を使い、次を確かめている。

- 出力に注入された `<script>` 要素が増えない
- 値が client の props として元の文字列に戻る

props を `<script type="application/json">` に置く方式は採っていない（採る場合は `<` のエスケープが別に要る。[hx4s/rendering-model.md](hx4s/rendering-model.md) 2.2）。
本文のテキストは React が通常どおりエスケープする。`dangerouslySetInnerHTML` を使う場合は自分で責任を持つ。

## server のコードを client に入れない

server のハンドラや秘匿値がブラウザのバンドルに入らないように、次の 2 つで守る。

1. **プロジェクトの分割**: server と client を別の sbt プロジェクトにし、client は server に依存しない。server が client を使うのは、Vite の仮想モジュール `scalajs:client.js`（island の実体）だけ。
   Scala.js は client を別のリンク成果物として作る。
2. **`hx4sCheckDependencies`**: sbt の依存グラフを検査する。`hx4sGenerate` と `hx4sIslandComponents` が先に呼ぶので、違反があると生成の時点で失敗する。

```
hx4s: the client project client must not depend on the server project server (client -> mid -> server). Put types shared by both, such as island props, in a project that neither depends on the other
```

検査するのは、client（と推移的な `dependsOn`）が `Hx4sPlugin` を有効にしたプロジェクトに依存していないこと、server が client に依存していないこと。
`dependsOn(x % "test")` のようなテストだけの依存は対象外。

**検査していないこと**: Vite のモジュールグラフ（TypeScript の import を含む）。`examples/minimal` は、client のバンドルに server だけの文字列が入っていないことを文字列で検査している
（`tests/build-output.test.js`）が、依存方向の保証ではない。

## テスト

| 目的 | 方法 | 例 |
| --- | --- | --- |
| SSR の HTML | ビルドした Worker の `fetch` を Node で呼び、jsdom で読む | `examples/minimal/tests/build-output.test.js` |
| 型付き props | `compileErrors` で、誤った props のコンパイルエラーを確かめる | `examples/minimal/server/src/test/.../TypedPropsSuite.scala` |
| hydration | `app/client.ts` をビルドして jsdom 上で動かし、クリックで状態が変わること・不一致の island が SSR のままであることを確かめる | `examples/minimal/tests/hydration.test.js` |
| 実ブラウザ | `wrangler dev` と Playwright で、hydrate の誤りが出ないことを確かめる | `examples/minimal/tests/e2e.test.js` |

### Node 上で Scala.js のテストを動かす

server のテストは Node 上で、SSR（`ReactDOMServer.renderToStaticMarkup`）まで動かせる。そのまま動かすと、リンク成果物の `scalajs:client.js` と HonoX 内の `hono/jsx` を Node が解決できない。
`hx4s-vite` が Node の module hooks の loader を提供する。

| 指定子 | 解決先 |
| --- | --- |
| `scalajs:client.js` | `$HX4S_CLIENT_JS_DIR/client.js`（client の `fastLinkJS` の出力ディレクトリ） |
| HonoX 内の `hono/jsx`・`hono/jsx/jsx-runtime` | `react`・`react/jsx-runtime`（`hx4s()` が本番ビルドでしている置換と同じ） |

```scala
// build.sbt（server プロジェクト。examples/minimal と同じ）
import org.scalajs.jsenv.nodejs.NodeJSEnv

Test / jsEnv := {
  val clientDir = (client / Compile / fastLinkJS / scalaJSLinkerOutputDirectory).value
  (client / Compile / fastLinkJS).value
  new NodeJSEnv(
    NodeJSEnv.Config()
      .withArgs(List("--import", "hx4s-vite/node-register"))
      .withEnv(Map("HX4S_CLIENT_JS_DIR" -> clientDir.getAbsolutePath))
  )
}
```

実行基盤の指定子（`cloudflare:workers` など）をスタブにしたい場合は、`hx4s-vite/node-register` の代わりに自分の register ファイルから `hx4s-vite/node-loader` と自分の loader を並べて登録する。
手順は [hx4s/build-integration.md](hx4s/build-integration.md) 2.3。
