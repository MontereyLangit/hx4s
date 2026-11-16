# 互換性

## 1. 3ch の依存バージョン（2026-11-16 時点）

### 1.1 Scala / sbt

出典: `build.sbt`、`project/plugins.sbt`、`project/build.properties`。推移的依存は coursier キャッシュ上の POM で確認した。

| 対象 | 版 | 出典・備考 |
| --- | --- | --- |
| sbt | 1.13.0 | `project/build.properties` |
| Scala | 3.3.8（LTS） | `build.sbt` の `scala3Version` |
| Scala.js（sbt-scalajs） | 1.22.0 | `project/plugins.sbt` |
| sbt-scalajs-crossproject | 1.3.2 | 同上 |
| scalajs-react `core` | 4.0.0 | `bbs` と `bbsClient` の `libraryDependencies`。POM 上は Scala 3.3.7 / Scala.js 1.20.1 でビルドされている |
| scalajs-dom | 2.8.1（推移的） | scalajs-react 4.0.0 の POM。最終的な解決版は `sbt bbsClient/dependencyTree` などで Phase 1 に確定する（本調査では sbt を実行していない） |
| scalameta | 4.17.4（`_2.13` 成果物を `CrossVersion.for3Use2_13` で利用） | `codegen` の `libraryDependencies` |
| munit | 1.0.0 | `commonSettings` |
| JDK | 未固定 | `docs/client-build-review.md` に Java 25 の Unsafe 警告の記録がある。CI の版は未決 |

ScalablyTyped は使っていない（`project/plugins.sbt` のコメント「hand-written @js.native facades only」）。

### 1.2 npm

出典: `package.json`（範囲指定）と `package-lock.json`（lockfileVersion 3、解決版）。

| パッケージ | `package.json` | 解決版 | ライセンス |
| --- | --- | --- | --- |
| hono | 4.13.11 | 4.13.11 | MIT |
| honox | ^0.1.62 | 0.1.62 | MIT |
| @hono/react-renderer | 1.0.1 | 1.0.1 | MIT |
| @hono/vite-build | 1.11.1 | 1.11.1 | MIT |
| @hono/vite-dev-server | （honox の依存 ^0.25.1） | 0.25.3 | MIT |
| vite | 8.3.3 | 8.3.3 | MIT |
| rolldown | （vite の依存） | 1.2.16 | MIT |
| esbuild | （推移的） | 0.28.2 | MIT |
| react / react-dom | 19.2.8 | 19.2.8 | MIT |
| @scala-js/vite-plugin-scalajs | 1.1.0 | 1.1.0 | Apache-2.0 |
| @tailwindcss/vite / tailwindcss | 4.3.3 | 4.3.3 | MIT |
| typescript | ^5 | 5.9.3 | Apache-2.0 |
| wrangler | ^4.146.0 | 4.158.0 | MIT OR Apache-2.0 |
| workerd | （wrangler の依存） | 1.20261104.1 | Apache-2.0 |
| jsdom | ^29.1.1 | 29.1.1 | MIT |
| @types/react / @types/react-dom | ^19.3.0 | 19.3.0（react） | MIT |
| @types/node | ^22 | 22.20.4 | MIT |

調査環境の Node は v22.18.0。`engines` の指定は 3ch の `package.json` に無い。

### 1.3 注意点

- **vite-plugin-scalajs と Vite 8**: `@scala-js/vite-plugin-scalajs` 1.1.0 は `vite` を `"4.1.4 - 7"` の **dependencies** に持つ。3ch は
  `overrides` で `"vite": "$vite"` にして Vite 8 を使わせている。上流が Vite 8 に対応するまで、hx4s-vite も同じ override を利用者に求めるか、
  hx4s-vite 側で互換性を宣言する必要がある。
- **Rolldown と `sequential`**: Vite 8 は Rolldown を使う。`buildStart` の `{ sequential: true, handler }` はもとは Rollup の機能で、
  Rolldown の型定義（`rolldown/dist/shared/define-config-*.d.mts`）にも `sequential` がある。3ch の実ビルドで直列になっていることは
  `tests/islands-bridge.test.js` が設定値を見ているだけで、実際の起動順は測っていない。
- **honox の範囲指定**: `^0.1.62` は 0.x の caret なので `>=0.1.62 <0.2.0`。パッチ版の更新で内部 API（[architecture.md](architecture.md) 4 章）が変わりうる。
  hx4s は honox を厳密な版で検証し、利用側にも lock を勧める。
- **scalajs-react と Scala の版**: scalajs-react 4.0.0 は Scala 3.3.7 でビルドされ、3ch は 3.3.8 で使っている。Scala 3 の LTS 系列の範囲では問題にならない想定。
  hx4s-react も Scala 3.3 LTS でビルドし、利用側の Scala 3 の新しい版から使えるようにする（Scala 3 の TASTy は後方互換で、新しいコンパイラが古いライブラリを読める）。

## 2. HonoX がアルファである点

HonoX の README は自身をアルファ版と位置づけている（0.1.x）。hx4s は HonoX の内部 API に 10 か所依存する（[architecture.md](architecture.md) 4 章の H1〜H10）。
特に H1（`HonoXIsland` の props の形）、H3（`component-name` の形式）、H4（`hono/jsx` の文字列置換）、H10（入れ子の扱い）は、
HonoX 側の変更で hx4s の SSR が黙って壊れる（hydration されない、静的 HTML のままになる）種類のものである。

対策:

1. **依存箇所を 1 か所に集める**: H1 は `hx4s-react` の `private` な facade に、H3・H5 は `hx4s-codegen` の定数に、H4 は `hx4s-vite` の既定設定に閉じ込める。
2. **版の範囲を狭く宣言する**: hx4s の各リリースは、検証した honox の版（例: `0.1.62`）を明記する。`hx4s-vite` の peerDependencies は検証済みの範囲だけにする。
3. **互換性テスト**（3 章）を hx4s の CI で回す。
4. **上流との関係**: Scala 以外の利用者にも意味のある変更（`HonoXIsland` の公開 API 化、`islandDir` と `component-name` の対応の明文化など）は、
   HonoX へ issue / PR で提案する。受け入れられるかは分からないので、hx4s の設計はそれを前提にしない。

## 3. 互換性テストの方針

3ch の `tests/` の既存テストを土台にする。実際の HonoX を Vite で動かす点は共通だが、**確かめている範囲はテストごとに異なる**。次のとおり分けて書く（調査基準 commit `875a47b`）。

| 既存のテスト | 実際に確かめていること | 確かめていないこと |
| --- | --- | --- |
| `tests/islands-bridge.test.js`（1 件目） | `vite.config.ts` を読み、`scalajs:client.js` の alias と、2 つの Scala.js プラグインの `buildStart.sequential` の設定値を検査する | 実際の sbt の起動順 |
| `tests/islands-bridge.test.js`（2 件目） | **JS の代替 export**（`Probe`）を fixture に置き、Vite で SSR ビルドして `renderToString` した HTML の文字列を検査する。`<honox-island>`・`component-name`・`data-serialized-props`・`rawElement` 直描画との違い。ファイル冒頭のコメントのとおり Scala コンパイラのテストではない | jsdom での hydration。実際の Scala.js のリンク成果物 |
| `tests/islands-hydration.test.js` | **実際にリンクした Scala の bridge**（`bbs-test-fastopt/island-test.js` の `renderIslandTest`）で SSR し、JS の fixture コンポーネントを `honox/client` の `createClient` で **jsdom 上で hydrate**（単純・入れ子・要素 props の 3 通り）。境界の数、SSR DOM の再利用、hydration エラー無し、クリックで状態が変わる | 実際の client island（Scala.js）。型付き Props |
| `tests/board-top-hydration.test.js` | 板トップの実 SSR HTML（`bbs` のテスト用 export）を、リンクした client を使って `createClient` で jsdom 上で hydrate する | hx4s の汎用部分（3ch の画面に依存する） |
| `tests/board-islands.test.js` | リンクした client の island の挙動を jsdom で確かめる（個別の `hydrateRoot` を含むものがある） | HonoX の探索・SSR の包み |
| `tests/client-bundle.test.js` | `dist/static/` の JS を走査し、サーバ専用の文字列が無いことと island が入っていることを確かめる（要 `dist/`） | モジュールグラフ・依存方向（文字列検査のみ。[module-boundaries.md](module-boundaries.md) 2 章） |

hx4s の互換性テストは、これらを次のように分けて整える。型付き Props のコンパイル検証（`P` 以外の引数がコンパイルエラーになること）は、どのテストにも無いので **別に必須** とする。

| テスト | 確かめること | 対応する依存 |
| --- | --- | --- |
| island の SSR | `<honox-island>`、`component-name="/app/islands/x.tsx"`、`data-serialized-props` が出る。`rawElement` を直接描画すると出ない（現行の `islands-bridge` 2 件目の範囲） | H1・H2・H3・H4 |
| hydration | 単純・入れ子・要素 props の 3 通りで、境界の数、hydration エラー無し、SSR DOM の再利用、クリックで状態が変わる（現行の `islands-hydration` の範囲。実 Scala.js の client island での hydration は `board-top-hydration` の範囲） | H2・H5・H6・H10 |
| Props の往復 | `"<&>`、非 ASCII、サロゲートの片割れ、`undefined` のキー、空配列が `JSON.parse` で期待どおりに戻る。非有限の `Double`・循環参照・入れ子オブジェクト内の React 要素で SSR が失敗する、または hydration が不一致になること、その場合の挙動が仕様どおりであること（[rendering-model.md](rendering-model.md) 2.3） | H2 |
| 型付き Props（コンパイル） | `IslandComponents.Xxx(props: P)` に `P` 以外を渡すとコンパイルエラー。typed export 補助を入れた場合は client の export が `P` と合わないこともコンパイルエラー（[ADR 0003](adr/0003-typed-island-props.md)）。Scala のコンパイラテストとして書く | — |
| 世代ずれ | 旧 HTML + 新 client、新 HTML + 旧 client の両方で、client の `validate` が不一致を検出し、`console.error` を出して SSR の markup が残る（[rendering-model.md](rendering-model.md) 2.4） | H2 |
| island のファイル名 | 許可名（`[A-Za-z0-9-]+\.tsx`）は SSR → 探索 → hydration まで通る。`foo_bar.tsx`・`foo.bar.tsx` などは生成時に拒否される（[rendering-model.md](rendering-model.md) 1.4） | H5 |
| script 埋め込み（将来） | props を `<script type="application/json">` に置く方式を入れるときに限り、`</script><script>…` を含む値の往復と、注入したスクリプトが実行されないこと（[rendering-model.md](rendering-model.md) 1.2） | — |
| ルート | 生成ルートの GET / POST が `createApp` で登録される。`HEAD` が生成時に拒否される。`_` 始まりのファイル名が拒否される | H7・H8 |
| URL 変換 | hx4s-codegen の `filePathToPath` 相当が HonoX の実装と一致する（同じ入力表を両方に通す） | H7 |
| ビルド | `hx4s-vite` の 2 モードで、client の推移的依存（sbt の依存グラフ）と Vite のモジュールグラフに server のモジュールが入らない。client 成果物に server 側のマーカー文字列が入らないことの文字列検査は補助（3ch の KB-27 検査の一般化。[module-boundaries.md](module-boundaries.md) 2 章） | H9 |
| 書き出し | symlink / junction 経由の外部書き込み・削除が起きない（[build-integration.md](build-integration.md) 3.3）。失敗注入の後に再実行で収束する | — |

テストの実行対象:

- **固定版**: hx4s のリリースで検証した honox / hono / vite / react の組み合わせ。CI の必須。
- **最新版**: `honox@latest`・`vite@latest` での実行を定期的に（週次など）回し、失敗しても CI 全体は落とさない。壊れた時点で issue にする。
- **examples/minimal**: 上記の多くは `examples/minimal` を実際にビルドして確かめる。3ch に依存しないことの検査を兼ねる（[extraction-plan.md](extraction-plan.md)）。

## 4. ライセンス

### 4.1 既存コードと依存のライセンス

| 対象 | ライセンス |
| --- | --- |
| 3ch（3CH） | リポジトリに LICENSE ファイルが無い。公開の可否・条件は権利者が決める。3ch の `build.sbt` は参照実装 `ch3s-migration` を「踏襲する」と書いており、hx4s へ移すコードに同リポジトリ由来の部分がある場合は、その権利関係も確認が要る（U-21） |
| Scala 3 / Scala.js / scalajs-react / munit / vite-plugin-scalajs | Apache-2.0 |
| scalajs-dom | MIT |
| scalameta | BSD（POM の表記。BSD-3-Clause と理解している） |
| hono / honox / React / Vite / Rolldown | MIT |

hx4s は HonoX のコードを複製しない（依存するだけ）。HonoX の内部規則（ファイル名の正規表現、`filePathToPath` の変換）を Scala で書き直す部分は、
コードの写しではなく仕様の再実装とする。出典はコメントではなく `docs/` に書く。

### 4.2 MIT と Apache-2.0 の比較

| 観点 | MIT | Apache-2.0 |
| --- | --- | --- |
| 文面の短さ・理解しやすさ | 短い | 長い |
| 特許の許諾 | 明示なし | 明示的な特許許諾と、訴訟時の許諾終了条項がある |
| 依存先との並び | Hono / HonoX / React / Vite と同じ | Scala / Scala.js / scalajs-react / vite-plugin-scalajs と同じ |
| 再配布時の義務 | 著作権表示と許諾文の保持 | 同左に加え、NOTICE ファイルの保持と変更の明示 |
| 他ライセンスとの組み合わせ | ほぼ制約なし | GPLv2 と非互換（GPLv3 とは互換） |
| 貢献の受け入れ | CLA / DCO を別途決める | 第 5 条で貢献物も同じ条件になると定めている |

### 4.3 提案

**Apache-2.0** とする（2026-11-16 決定）。理由:

- hx4s の利用者は Scala.js のエコシステムの側にいる。Scala・Scala.js・scalajs-react・vite-plugin-scalajs がすべて Apache-2.0 で、並びが揃う。
- 特許の許諾が明示されているので、企業の利用者が採用を判断しやすい。
- MIT の依存（Hono / HonoX / React）を Apache-2.0 のプロジェクトから使うことに問題は無い。

MIT にする利点は、HonoX 側への提案（PR）とライセンスを揃えやすいことと、文面の短さ。
どちらにしても、3ch から移すコードの権利者がそのライセンスで公開することに同意している必要がある。移すコードの権利確認は未決事項 U-21 に残る。
