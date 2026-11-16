# モジュール境界

[architecture.md](architecture.md) の現行実装を、フレームワーク（hx4s）と掲示板固有（3ch）に分ける。移す時期と手順は [extraction-plan.md](extraction-plan.md) に書く。
ファイル名は調査基準 commit `875a47b` のもの（`io._3ch.codegen.framework` パッケージを含む）。3ch の Phase 1 / 2 は進行中なので、ファイル名は変わりうる。

## 1. 分類表（ファイル単位）

区分の意味:

- **hx4s**: そのまま、または 3ch 固有の名前・パスを引数に出して移す。
- **分割**: ファイル内に両方が混ざっている。
- **3ch**: 3ch に残す。
- **3ch（将来候補）**: 当面 3ch に残し、ロードマップ上の別モジュール候補とする。

### 1.1 Scala

| 3CH のファイル | 区分 | 移す先 / 残す理由 |
| --- | --- | --- |
| `common/.../routes/Annotations.scala` | hx4s | `hx4s-core`。`hx4s.annotation.route` / `hx4s.annotation.islands[P]`。`islands` に型引数を足す（[ADR 0003](adr/0003-typed-island-props.md)） |
| `common/.../routes/RouteTable.scala` | 3ch | `publicPatterns` のみ。下表 1.2 |
| `codegen/.../framework/AnnotationScanner.scala`・`AppEntries.scala` | hx4s | `hx4s-codegen`。`object Main` 固定の走査・moduleID・アノテーションの完全修飾名（`CodegenConfig.annotationPackage`）は既に設定値になっている |
| `codegen/.../framework/AppFileRenderer.scala` | hx4s | `hx4s-codegen`。route / island / `IslandComponents` のテンプレート。出力パッケージ・bridge の完全修飾名は `CodegenConfig` の値 |
| `codegen/.../framework/{OutputPath,OutputWriter,Codegen,CodegenConfig,CodegenError,CodegenReport}.scala` | hx4s | `hx4s-codegen`。書き出し規則・設定・誤りの型 |
| `codegen/.../GenerateAppFiles.scala` | 3ch | 3ch の `CodegenConfig`（入力パスの固定値、バナー）と、DO の TS 型生成の呼び出し。sbt-hx4s の `hx4sGenerate` が置き換える |
| `codegen/.../DurableObjectSources.scala` | 3ch（将来候補） | DO 専用。`hx4s-cloudflare` 候補 |
| `codegen/.../GenerateDurableObjectTypes.scala` | 3ch（将来候補） | 同上 |
| `codegen/.../GenerateDurableObjectStubs.scala` / `GenerateDurableObjectStubsMain.scala` | 3ch（将来候補） | 同上。パッケージ `io._3ch.bbs.external.cloudflare` を固定で持つ |
| `codegen/src/test/.../framework/*Suite.scala`・`TestConfig.scala` | hx4s | `hx4s-codegen` のテスト |
| `codegen/src/test/.../GenerateAppFilesSuite.scala` | 分割 | 3ch の設定を使う部分は 3ch。汎用の部分は `framework` のテストへ寄せて移す |
| `codegen/src/test/.../GenerateDurableObject*Suite.scala` | 3ch | DO 生成器に付随 |
| `common/src/test/.../routes/RouteTableSuite.scala` | 3ch | `publicPatterns` の検査。テンプレートの検査は `framework` のテストに移っている |
| `bbs/.../external/honox/Island.scala` | hx4s | `hx4s-react`（`hx4s.react.Island`） |
| `bbs/.../external/hono/HonoContext.scala`・`HonoContextFacade.scala` | 分割 | `HonoRequestFacade` / `HonoContextFacade` / `renderPromise` は `hx4s-react`（Env を型引数にする）。`RenderProps` の `title` / `authModal` は 3ch |
| `bbs/.../external/web/WebFacade.scala` | 3ch | hx4s は scalajs-dom の Fetch 型（`org.scalajs.dom.Request` / `Response`）を使う案（未決事項 U-6）。3ch の手書き facade は 3ch に残す |
| `bbs/.../external/cloudflare/Bindings.scala` | 3ch（将来候補） | `WorkerEnv` など。`hx4s-cloudflare` 候補 |
| `bbs/.../Main.scala` | 3ch | 宣言そのもの。hx4s は宣言の書き方を規定するだけ |
| `bbs-client/.../Main.scala` と `islands/*` | 3ch | 掲示板の UI |
| `bbs/.../generated/IslandComponents.scala` | 3ch（生成物） | 生成器は hx4s、出力は利用側のプロジェクトに置く |
| `bbs/.../handler/RendererShell.scala` | 3ch | `<html lang="ja">`、CSS パス、認証モーダル、Shift_JIS |
| `bbs/.../components/*` | 3ch | `IslandComponents` の利用側 |
| `bbs/src/test/.../IslandTestExports.scala` | 分割 | `renderIslandTest` は `hx4s-react` のテスト支援へ。`renderBoardTopTest` は 3ch |

### 1.2 `common` に混在しているもの

`common/.../routes/RouteTable.scala` は、`875a47b` 時点で `publicPatterns` だけを持つ。生成テンプレート（かつての `RouteKind` / `RouteEntry` / `IslandEntry` / `render*` / バナー）は
`codegen/.../framework/`（`AppEntries`・`AppFileRenderer`）へ移動済みである。

| 要素 | 区分 | 理由 |
| --- | --- | --- |
| `publicPatterns` | 3ch | v5 互換の dat / subject.txt / SETTING.TXT の URL パターン。`PublicRoutes` だけが使う。3ch の `common` か `bbs` の handler 側へ移す（Phase 1 の 1-4） |
| `Annotations.scala`（`route` / `islands`） | hx4s-core | `common` の `routes` に置かれたまま。Phase 3 で `hx4s.annotation` へ移す |

テンプレートを `codegen` へ移したので、`bbs` の Scala.js リンク対象にテンプレート文字列が入る問題は解消している（`RouteTable` が `publicPatterns` だけのため）。

### 1.3 TypeScript / JavaScript / 設定

| 3CH のファイル | 区分 | 移す先 / 残す理由 |
| --- | --- | --- |
| `vite.config.ts` の `scalaProject()` | hx4s | `hx4s-vite`。`buildStart` を `sequential: true` にするラッパー |
| `vite.config.ts` の `resolve.alias["scalajs:client.js"]`、2 つ目の Scala.js プラグイン（`uriPrefix: "scalajs-client"`） | hx4s | `hx4s-vite` |
| `vite.config.ts` の `honox({ islandComponents: { reactApiImportSource: "react" } })` | hx4s | `hx4s-vite` が既定値として渡す |
| `vite.config.ts` の client モードの `rollupOptions`（`input`・出力名） | hx4s | `hx4s-vite` の既定値（上書き可能） |
| `vite.config.ts` の `tailwindcss()` | 3ch | CSS の選択は利用側 |
| `vite.config.ts` の `@hono/vite-build/cloudflare-workers` と DO の `entryContentAfterHooks` | 3ch（将来候補） | 実行基盤固有 |
| `app/server.ts` | 3ch | ミドルウェア・認証 API・LegacyDispatch・DO の export。`examples/minimal` は最小の `server.ts` を持つ |
| `app/client.ts` | 3ch（雛形は hx4s） | 内容は汎用。`examples/minimal` に同じ形を置く。ヘルパー化は未決事項 U-7 |
| `app/routes/_renderer.tsx` / `app/renderer.d.ts` / `app/scalajs-server.d.ts` | 3ch | renderer の Props と Scala 側 export の型は 3ch 固有 |
| `app/routes/**` の生成物 / `app/islands/**` の生成物 | 3ch（生成物） | — |
| `bbs/test-stubs/cloudflare-workers-stub.mjs` | 3ch | Cloudflare 固有 |
| `bbs/test-stubs/cloudflare-workers-loader.mjs` | 分割 | `scalajs:client.js` の解決と HonoX 内の `hono/jsx` → `react` の置換は `hx4s-vite` の Node 用 loader へ。`cloudflare:workers` は 3ch。環境変数 `CH3S_CLIENT_JS_DIR` は `HX4S_CLIENT_JS_DIR` に改める |
| `bbs/test-stubs/register.mjs` | 分割 | 同上 |
| `build.sbt` の `Test / jsEnv`（`--import register.mjs`、client の fastLinkJS を先に実行） | 分割 | 仕組みは `sbt-hx4s` の補助（P1）。`cloudflare:workers` の差し替えは 3ch |
| `build.sbt` の `codegen` プロジェクトと `Compile / mainClass` | hx4s | `sbt-hx4s` の `hx4sGenerate` が置き換える |
| `build.sbt` の `generateDurableObjectStubs` | 3ch（将来候補） | DO 固有 |
| `package.json` の `v6:*` スクリプト | 3ch | 中身を `sbt hx4sGenerate` と hx4s-vite 前提に書き換える |
| `tests/islands-bridge.test.js` | hx4s | `hx4s-vite` / `hx4s-react` の互換性テスト。3ch の `vite.config.ts` を読む部分は examples に置き換える |
| `tests/islands-hydration.test.js` | hx4s | 同上。`bbs-test-fastopt/island-test.js` の代わりに hx4s-react のテスト用 export を使う |
| `tests/board-islands.test.js` / `tests/board-top-hydration.test.js` | 3ch | 掲示板 UI |
| `tests/client-bundle.test.js` | 3ch（原則は hx4s） | 文字列は 3ch 固有。「client 成果物に server のコードが入らない」検査は examples にも置く |
| `tests/worker-bundle.test.js` | 3ch | DO の export |

## 2. 依存方向の規則

1. 3ch → hx4s の一方向。hx4s のどのモジュールも 3ch（`io._3ch.*`、`ch3s`、3ch のファイルパス・文言）を参照しない（[ADR 0006](adr/0006-one-way-dependency-and-local-publish.md)）。
2. `hx4s-react` → `hx4s-core`。`hx4s-core` は他の hx4s モジュールに依存しない。
3. `hx4s-codegen` は `hx4s-core` にコンパイル依存しない（アノテーションは名前で照合する）。scalameta 以外の重い依存を足さない。
4. `sbt-hx4s` は `hx4s-codegen` を **実行時に解決する**（ライブラリ依存にしない）。
5. `hx4s-vite` は `@scala-js/vite-plugin-scalajs` と `honox` を peerDependencies とし、版の決定は利用側に任せる（案。U-8）。
6. どのモジュールも Cloudflare 固有 API（`cloudflare:workers`、DO、Bindings、`@hono/vite-build/cloudflare-workers`）に依存しない（[ADR 0005](adr/0005-core-independent-of-cloudflare.md)）。
7. 利用側のプロジェクト間の依存方向を次のとおりにする。
   - server → shared、client → shared は可。
   - **server → client は禁止**（server に client の実装が混ざる）。
   - **client → server は禁止**（client の成果物にサーバ処理が混入する。3ch の KB-27 の本体）。
   - **shared → server、shared → client は禁止**（shared 経由で server のコードが client に届く）。
   両者が共有する型（island の Props）は shared に置く。これらは hx4s の規約として文書化する。
   検査は、sbt の依存グラフ（client の推移的依存に server が入らないこと）と、Vite のモジュールグラフ（client ビルドに server のモジュールが入らないこと）で行う。
   バンドルの文字列検査（3ch の `tests/client-bundle.test.js`）は、これらの検査をすり抜けたものを拾う補助であって、主たる保証ではない。

## 3. 公開 API と内部 API

| モジュール | 公開 API（互換性を守る） | 内部 API（予告なく変える） |
| --- | --- | --- |
| hx4s-core | `hx4s.annotation.route`、`hx4s.annotation.islands[P]` の名前・引数の順と名前。JS 側の `hx4s.props.{JsShape, JsKind, JsField}` | — |
| hx4s-react | `hx4s.react.Island.apply`、`hx4s.react.IslandExport[P]`（`apply` / `unchecked`）と export に付ける検証関数の名前 `hx4sValidate`、`hx4s.hono.Context[Env, R]` / `HonoRequest` の facade、`renderPromise` | `HonoXIsland` の facade と `IslandProps`（H1）。`private` にしてある |
| hx4s-codegen | 設定 case class（3ch の `CodegenConfig` が原型。名前は Phase 3 で確定）、CLI の引数、生成物の形（バナー・ファイル配置・`IslandComponents` のメソッド名規則） | `AppEntries` などの中間表現、`AppFileRenderer` のテンプレート関数 |
| sbt-hx4s | 設定キー（`hx4s*`）と `hx4sGenerate` タスク | 専用 configuration の名前、隔離実行の方式 |
| hx4s-vite | `hx4s()` プラグイン関数のオプション、Node 用 loader のエントリ | 内部で組む Vite 設定の細部（alias の接頭辞など） |

生成物の形（TS の import 文、バナー文言）は「公開」に含める。利用者のリポジトリにコミットされるため、変えると差分が全ファイルに出る。
変える場合はマイナー版を上げ、変更点を CHANGELOG に書く。
