# ビルド統合

## 1. 3ch の現状

### 1.1 コマンド

| コマンド | 中身 | sbt の起動 |
| --- | --- | --- |
| `npm run v6:generate` | `sbt codegen/run`（`GenerateAppFiles.main`） | 1 回 |
| `npm run v6:build:client` | `vite build --mode client` | 1 回（`bbsClient/fullLinkJSOutput`） |
| `npm run v6:build:server` | `vite build --mode workers` | 2 回（`bbs/fullLinkJSOutput` → `bbsClient/fullLinkJSOutput`、直列） |
| `npm run v6:build` | 上の 3 つを順に | 計 4 回 |
| `npm run pretest` | `sbt bbs/Test/fastLinkJS bbsClient/fastLinkJS` | 1 回 |
| `npm test` | `node --test tests/*.test.js` | — |
| `sbt test` | munit（common JVM / JS、bbs、bbsClient、codegen） | — |

sbt を 1 回起動するごとの所要時間はこの調査では測っていない。`docs/client-build-review.md` に client 11.39 秒・server 14.61 秒
（起動・コンパイル・リンク込み）の記録があるが、変更前後の比較測定ではない。

`IslandComponents.scala` は、調査基準 commit `875a47b` の 3ch では `bbs` の `Compile / sourceGenerators`（`build.sbt` の `generateIslandComponents`）が
`GenerateAppFiles --island-components` を呼び、`src_managed` 配下に生成する。ソースツリーには置かれないので、clone 直後でも `bbs` のコンパイルで生成される
（以前はソースツリーに生成していた。未決事項 U-9 は決定済み）。TS 側の `app/routes`・`app/islands` は `npm run v6:generate` で生成し、Git 管理下に置く。

### 1.2 2 モードビルド

`vite.config.ts` は `mode` で 2 つの設定を返す。

| モード | プラグイン | 入口 | 出力 |
| --- | --- | --- | --- |
| `client` | `tailwindcss()`、`scalaProject("bbsClient")` | `./app/client.ts` | `dist/static/client.js`、`dist/static/assets/*`。`emptyOutDir: false` |
| `workers` | `honox({ islandComponents: { reactApiImportSource: "react" } })`、`buildCloudflareWorkers({ external: ["cloudflare:workers"], entryContentAfterHooks })`、`scalaProject("bbs")`、`scalaProject("bbsClient", "scalajs-client")` | `app/server.ts`（HonoX 既定） | `dist/index.js` |

workers モードでは `scalajs:client.js` を alias で `scalajs-client:client.js` に向け、2 つ目の Scala.js プラグイン（`uriPrefix: "scalajs-client"`）に解決させる。
`scalajs:` 接頭辞は 1 つ目（`bbs`）のプラグインが持つので、client の成果物を同じ接頭辞で読むために別名が要る。

`@scala-js/vite-plugin-scalajs` は `mode === "development"` のときだけ `fastLinkJSOutput`、それ以外は `fullLinkJSOutput` を使う。
`client` / `workers` はどちらも development ではないので、両方とも fullLinkJS になる。

### 1.3 `scalaProject` の `buildStart` 直列化

```ts
function scalaProject(projectID: string, uriPrefix?: string): Plugin {
  const plugin = scalaJSPlugin({ cwd: scalaCwd, projectID, uriPrefix });
  // buildStart を { sequential: true, handler } に包む
}
```

Rollup は各プラグインの `buildStart` を既定で並行に走らせる。`@scala-js/vite-plugin-scalajs` の `buildStart` は `sbt --batch "print <project>/fullLinkJSOutput"`
を子プロセスで起動するので、workers モードでは同じビルドに対して sbt が 2 つ同時に立ち上がる。`sequential: true` はこれを防ぐ。

直列化の理由は `vite.config.ts` にコメントが無い。`docs/既知不具合台帳.md` の KB-27 に「同時 sbt 起動の回避」の記述があり、
同じディレクトリで sbt を並行起動するとサーバー・ロック・`target/` の競合が起きるためと考えられる（推測）。
`tests/islands-bridge.test.js` は 2 つのプラグインの `buildStart.sequential === true` を検査している。

`@scala-js/vite-plugin-scalajs` は sbt の標準出力の **最終行** を出力ディレクトリとして使う。sbt が最後に警告などを出すと壊れる。
Windows では `spawn("sbt.bat", args.map(x => \`"${x}"\`), { shell: true })` で起動する。

### 1.4 テスト用 loader

`sbt test` と `npm test` の一部は Node 上で Scala.js のリンク成果物を動かす。Workers でも Vite でもないので、次の解決を module hooks で差し替える。

| 指定子 | 差し替え先 | 理由 |
| --- | --- | --- |
| `cloudflare:workers` | `bbs/test-stubs/cloudflare-workers-stub.mjs` | DO クラスが常に export されるので、テストのリンク成果物にも import が入る（3ch 固有） |
| `scalajs:client.js` | `$CH3S_CLIENT_JS_DIR/client.js` | `IslandComponents` が client の成果物を import する。`build.sbt` の `Test / jsEnv` が `bbsClient/fastLinkJS` を先に実行して渡す |
| HonoX 配下からの `hono/jsx`・`hono/jsx/jsx-runtime` | `react`・`react/jsx-runtime` | Vite の `reactApiImportSource` が本番でしている置換の再現 |

`npm test` の Vite を使うテスト（`islands-hydration` 等）は、loader ではなく Vite の `resolve.alias` と `islandComponents()` プラグインで同じことをしている。

## 2. hx4s での責務分担

| 責務 | sbt-hx4s | hx4s-vite | 利用側 |
| --- | --- | --- | --- |
| 設定キー（入力ソース、出力先、パッケージ名、moduleID） | ○ | — | 値を設定 |
| `hx4sGenerate`（route TS・island TSX・`IslandComponents` の生成） | ○ | — | ビルド前に実行 |
| `hx4s-codegen` の版の解決と隔離実行 | ○ | — | — |
| `IslandComponents` を server のソースに入れる | ○（`sourceGenerators`で `src_managed` へ。U-9 決定） | — | — |
| Scala.js プロジェクトの登録（server / client）と `buildStart` の直列化 | — | ○ | projectID を渡す |
| `scalajs:client.js` の alias と 2 つ目の接頭辞 | — | ○ | — |
| `honox()` に `reactApiImportSource: "react"` を渡す | — | ○ | 他の HonoX オプション |
| client ビルドの `input` / 出力名の既定値 | — | ○ | 上書き可 |
| Node 用 loader（`scalajs:client.js` と `hono/jsx`） | （P1 で jsEnv の補助） | ○（`hx4s-vite/node-loader`） | 実行基盤固有のスタブを足す |
| 実行基盤（Cloudflare 等）のビルドプラグイン | — | — | ○ |
| CSS（Tailwind 等） | — | — | ○ |

### 2.0 P0 で検証済みの唯一の組み合わせ

設定キー（`hx4sAppDir`・`hx4sServerModuleId` など）と Vite / HonoX 側の固定値は、片方だけを変えると食い違う。

| 固定値 | 所在 |
| --- | --- |
| route の探索 `/app/routes`、island の探索 `/app/islands`（`import.meta.glob` が `/app` 固定） | HonoX（`server/with-defaults.js`、`client/client.js`） |
| `component-name` の値 = Vite root からの絶対パス（`/app/islands/x.tsx`） | HonoX（H3）と `AppFileRenderer.islandComponents` の `islandIdPrefix` |
| 仮想モジュール名 `scalajs:<moduleID>.js`、client を読み替える `scalajs-client:` | `vite.config.ts`（hx4s-vite）と `CodegenConfig.virtualModulePrefix` |

P0 が **既定かつ検証済みの唯一の組み合わせ** とするのは次のとおり。これ以外は「未検証」であり、拒否するか、使うときは自己責任とする。

- appDir は `app`（`app/routes`、`app/islands`）。
- moduleID は server が `server`、client が `client`。
- 仮想モジュールの接頭辞は `scalajs:`（client を server ビルドから読むときの別名は `scalajs-client:`）。

設定可能にするには、codegen と hx4s-vite の両方に同じ値を渡す共通設定が要る。P0 では作らず、P1 の未決事項とする（[roadmap.md](roadmap.md) U-18、U-25）。
sbt-hx4s の設定キー（2.2）は、これらの既定値以外を受け付けるキーであっても、P0 では既定値以外を検証しない。既定値以外を使う統合テストを足すまで「未検証」と書く。

### 2.1 hx4s-vite の API（Phase 3 で実装）

```ts
import { defineConfig } from "vite";
import hx4s from "hx4s-vite";
import buildCloudflareWorkers from "@hono/vite-build/cloudflare-workers"; // 利用側の選択

export default defineConfig(({ mode }) => {
  const plugins = hx4s({
    mode,                         // "client" | それ以外（server）
    sbt: { cwd: import.meta.dirname },
    server: { projectID: "server" },                       // 既定値
    client: { projectID: "client", entry: "./app/client.ts" }, // 既定値。module・uriPrefix も変えられる
    honox: {},                    // honox() へそのまま渡す追加オプション
  });
  return mode === "client"
    ? { plugins, build: { outDir: "dist/client" } }
    : { plugins: [...plugins, buildCloudflareWorkers({ outputDir: "dist/server" })] };
});
```

- 戻り値は `PluginOption[]`。client モードでは client の Scala.js プラグインと、`config` フックで出力名（`static/client.js`・`static/assets/*`）と `emptyOutDir: false` を足すプラグインを返す。
  server モードでは `honox()`（`islandComponents.reactApiImportSource` の既定は `"react"`）・server と client の Scala.js プラグイン・`scalajs:client.js` → `scalajs-client:client.js` の別名を返す。
- `mode` の名前（`client`）は HonoX の `honox-vite-client` の判定（`mode === "client"`）に合わせる。
- Scala.js プラグインは `@scala-js/vite-plugin-scalajs` をそのまま使い、`buildStart` の直列化だけを足す（`scalaProject()` として export）。フォークしない。
- Node 用 loader は `hx4s-vite/node-register`（`--import` に渡す）。`scalajs:client.js` を `HX4S_CLIENT_JS_DIR/client.js` に、honox 内の `hono/jsx` を `react` に解決する。
- peerDependencies は検証した版だけにする（`honox` 0.1.62、`@scala-js/vite-plugin-scalajs` 1.1.0、`vite` ^8.3.3）。vite-plugin-scalajs が vite を `"4.1.4 - 7"` の dependencies に持つので、利用側は `overrides` で `"vite": "$vite"` を指定する（[compatibility.md](compatibility.md) 1.3）。

### 2.2 sbt-hx4s の設定（Phase 3 で実装）

```scala
// project/plugins.sbt
addSbtPlugin("io.github.montereylangit" % "sbt-hx4s" % "<version>")

// build.sbt
lazy val server = project.enablePlugins(ScalaJSPlugin, Hx4sPlugin).dependsOn(shared)
  .settings(
    hx4sClientProject := client,
    libraryDependencies += "io.github.montereylangit" %%% "hx4s-react" % hx4sVersion,
  )
```

利用者が書くのは `enablePlugins(Hx4sPlugin)` と `hx4sClientProject` だけで、残りは既定値で動く。AutoPlugin は `Hx4sPlugin` 1 つで、server プロジェクトで有効にする
（Phase 0 の案の `Hx4sServerPlugin` と、ソースを 1 ファイルずつ指定する `hx4sServerMain` / `hx4sClientMain`・`hx4sMainObject` は採らなかった。
codegen はトップレベル object の名前を問わずに読むので、プロジェクトの Scala ソースを全部渡せば足りる）。

| キー | 既定値 | 説明 |
| --- | --- | --- |
| `hx4sClientProject` | なし | `@islands` を持つ client プロジェクト。`hx4sClientSources` の既定の取り出し元 |
| `hx4sServerSources` / `hx4sClientSources` | このプロジェクト / `hx4sClientProject` の `Compile / unmanagedSources`（`.scala`） | アノテーションを読むソースファイル |
| `hx4sProjectRoot` | `ThisBuild / baseDirectory` | 生成の基準（Vite の root）。ソースのパスはここからの相対で codegen に渡し、誤りの表示もこの相対パスになる |
| `hx4sRouteDir` / `hx4sIslandDir` | `<root>/app/routes` / `<root>/app/islands` | 出力先 |
| `hx4sIslandIdPrefix`、`hx4sServerModule` / `hx4sClientModule`、`hx4sVirtualModulePrefix` | `/app/islands/`、`server` / `client`、`scalajs:` | 2.0 の固定値。変えるときは hx4s-vite と HonoX の側も合わせる（未検証） |
| `hx4sIslandComponentsPackage` / `hx4sIslandComponentsObject` | `hx4s.generated` / `IslandComponents` | 生成 Scala の名前 |
| `hx4sIslandBridge`、`hx4sIslandExportType`、`hx4sAnnotationPackage` | `hx4s.react.Island`、`hx4s.react.IslandExport`、`hx4s.annotation` | 生成物が参照する hx4s の名前 |
| `hx4sBanner` / `hx4sGeneratedMarker` | 空 / `None`（codegen の既定） | 生成物の先頭行 |
| `hx4sCodegenVersion` | プラグインと同じ版 | 解決する `hx4s-codegen_3` の版 |
| `hx4sCodegenJavaOptions` | 空 | codegen を動かす JVM のオプション |
| `hx4sVersion`（値） | プラグインの版 | `hx4s-core` / `hx4s-react` の依存に使う |
| `hx4sGenerate` | タスク | route / island の TS を生成し、出力先にある生成ファイルの一覧を返す |
| `hx4sIslandComponents` | タスク | `IslandComponents.scala` を `Compile / sourceManaged` に生成する。`Compile / sourceGenerators` に登録済み。入力（client のソースと引数）が変わらなければ codegen を起動しない |

設定キーの詳細と codegen の引数の対応は [codegen.md](codegen.md) の「設定」。

## 3. codegen の方式比較

### 3.1 生成方式

| 方式 | 長所 | 短所 | 判断 |
| --- | --- | --- | --- |
| scalameta の構文解析（現行） | コンパイル前に動く。TS ファイルを Vite より先に出せる。実装が小さい（現行の `AnnotationScanner` は約 175 行）。JVM で動くので fs が素直 | 型を解決できない。import の別名や型エイリアスを追えない。引数は文字列リテラルに限られる | **採用**（[ADR 0004](adr/0004-scalameta-codegen.md)） |
| Scala 3 マクロ | 型を解決できる。Props の中身も検査できる | コンパイル中にファイルを書く副作用になる。TS ファイルを Scala のコンパイル結果に依存させると、Vite → sbt → Vite の順序が複雑になる。Scala.js のコンパイル中に JVM の fs を使うのはマクロの本来の用途から外れる | P2 で型検査だけをマクロに足す案を残す |
| sbt の source generator（sbt 本体内で生成） | sbt のタスクグラフに素直に乗る | sbt 1.x は Scala 2.12。Scala 3 で書いた生成器をそのまま動かせない。生成器を 2.12 で書くと、利用側 Scala 3 の構文（`enum`、optional braces）の解析は scalameta の dialect で可能だが、生成器自体を 2 系統持つことになる | 呼び出しの入口としては使う（`sourceGenerators` に `hx4sGenerate` を繋ぐ）。生成器本体は分ける |
| コンパイラプラグイン | 型情報とコンパイルの一体化 | Scala 3 のコンパイラプラグインは不安定な API に依存し、版ごとの保守が重い | 不採用 |

### 3.2 sbt からの実行

`hx4s-codegen` は Scala 3、sbt 1.x の plugin は Scala 2.12 で動く。現行の 3ch は `codegen` を同じビルドのサブプロジェクトにして `runMain` で呼んでいるが、
利用側のビルドに生成器のサブプロジェクトを持たせるわけにはいかない。

方式は「専用 configuration で `hx4s-codegen_3` を解決し、そのクラスパスで別クラスローダか fork で main を呼ぶ」とする。

- **scalafmt 方式**（sbt-scalafmt / scalafmt-dynamic）: 実行時に指定版の成果物を取得し、隔離したクラスローダで読み込む。
- **ScalaPB 方式**（sbt-protoc / protocbridge）: 生成器を別の Scala 版でも動かせるように、専用 configuration で解決した成果物を隔離実行する。

P0 は実装の単純さを優先して **fork**（`Fork.java` で `java -cp <resolved> hx4s.codegen.CodegenMain <args>`）にする。JVM を 1 回余分に起動するが、
クラスローダの分離（Scala 2.12 と 3 の標準ライブラリの衝突）を考えなくてよい。起動の重さが問題になったら同一 JVM 内の隔離クラスローダに切り替える。

Phase 3 の実装:

- `hx4s-codegen_3` は専用 configuration `hx4s-codegen`（`hide`）の `libraryDependencies` にし、`update` の結果からクラスパスを取る（`hx4sCodegenClasspath`）。
- 設定は JSON ではなく `--key value` の引数列で渡す（`CodegenMain` が受ける形。sbt を使わない呼び出しと同じ）。引数はシェルを介さず `ProcessBuilder` に渡すので、空白や `[]` を含むパスもそのまま通る。
- fork した JVM の出力は sbt のロガーへ流す。終了コードが 0 でなければ、`[hx4s-codegen] error: <パス>:<行>: <理由>` の行を集めてタスクの失敗メッセージに含める。
- sbt 本体の JVM では codegen を動かさない（`System.exit` や Scala 3 の標準ライブラリの読み込みが sbt に及ばない）。
- 検証は `sbt-plugin/src/sbt-test/hx4s/` の scripted テスト（生成・再生成・古い生成物の削除・`sourceGenerators`、誤ったアノテーションでの失敗と行番号）。

sbt 2.x（Scala 3）に移れば同じクラスローダで呼べる見込みだが、sbt 2 の正式版への移行時期が読めないので前提にしない（U-10）。

### 3.3 生成物の書き出し規則

3ch の `codegen/.../framework/OutputWriter.scala`・`OutputPath.scala`（`875a47b`）を基礎に、次を P0 で満たす。「現行」は `875a47b` の実装を指す。

| 規則 | 現行（`875a47b`） | P0 |
| --- | --- | --- |
| 決定的な出力（並び順・改行） | ルートはパスでソート、island は宣言順。改行は `\n` | 同じ。island の順も宣言順で固定する |
| 内容が同じなら書き直さない | ある（`sameContent`。改行コードの違いは無視） | 同じ。比較は改行コードを `\n` に揃えてから行う（Windows の `core.autocrlf=true` の LF/CRLF 差で毎回書き直さないため） |
| 生成バナーの無いファイルは上書き・削除しない | ある（先頭行が `generatedMarker` で始まるか） | 同じ。既定の接頭辞は `// This file is generated by hx4s-codegen`。旧バナーの移行は、組み込みで複数のマーカーを認めるのではなく、利用側が `hx4sBanner` / `hx4sGeneratedMarker` を旧値にする（Phase 3 の判断。[codegen.md](codegen.md)） |
| 全検査を書き込み前に済ませる | ある（`plan` が誤りを集め、1 件でもあれば何も書かない） | 同じ |
| 出力先ルート外への書き込み拒否 | セグメント検査（`..`・予約名・文字種）と、字句的な正規化後のパスが出力ルート配下かの検査 | 同じ。リンクの扱いは下の「リンクの方針」で別に定める |
| 大文字小文字違いの衝突検出 | ある（出力同士、既存ファイル・ディレクトリとの差） | 同じ（NTFS / APFS の既定が大文字小文字を区別しないため） |
| 古い生成物の削除 | `routes/` と `islands/` の配下でバナー付きの通常ファイルだけ（リンクは `walkFiles` が対象外にする） | 同じ。`IslandComponents` は sbt の `src_managed` に任せる |

#### 実行順と、保証しないこと

**Phase 0 時点の現状**（`OutputWriter.write`）: 検査 → 古い生成物の削除 → 内容が違うファイルを `Files.write` で書く、の順だった。一時ファイル経由の置き換えはしていない。
削除が書き込みより先なので、書き込み中に失敗すると、古い生成物が消えて新しい生成物が一部しか無い状態になりえた。hx4s-codegen では下の方針の順に直してある。

**P0 の方針**: この文書では「原子性」という語を使わず、次の範囲だけを保証する。

- 保証する: 全検査が通ってから書き始める。同じ内容は書かない。
- 保証しない: 複数ファイルの間の原子性（途中失敗で TS と Scala が別世代になりうる）、並行実行の排他（同時に 2 つの生成を走らせた場合の結果）。

実行順は **新規書き込み → 古い生成物の削除** とする（現状の逆）。書き込みに失敗した場合は古い生成物を消さない。
途中で失敗しても、**再実行で収束する**（生成は決定的で、同内容は書かず、残った古い生成物は次回の削除で消える）ことを復旧の方法とする。
ファイル単位の置き換え（一時ファイルへ書いてから `Files.move`）は、1 ファイルが中途半端な内容で残らないために採用してよいが、これは複数ファイルの整合を保証しない。

検証条件: 書き込みの途中失敗（失敗注入）の後に、再実行で期待の状態に収束すること。並行実行は保証対象外なので、検証は「ドキュメントに書いた範囲」に限る。

#### リンクの方針（symlink / Windows junction）

**方針**: 生成器は出力先のリンクを **辿らず、拒否する**。次の 4 か所それぞれで定める。

| 対象 | 方針 |
| --- | --- |
| 出力ルート（`app/routes`・`app/islands`・`src_managed` 内の出力先） | ルート自体がリンク（symlink / junction）なら拒否 |
| 出力パスの途中のディレクトリ | リンクなら拒否（既存ディレクトリがリンクでないこと） |
| 末端のファイル | 既存のものが symlink なら拒否（`Files.write` はリンクを辿って外へ書く） |
| 削除対象 | リンクは削除しない（`walkFiles` の `!Files.isSymbolicLink` を維持。junction も同様に判定する） |

**実装状況**: 実装済み（移動元の Phase 1 で入り、Phase 3 で hx4s-codegen に移した）。判定は `Files.readAttributes(path, BasicFileAttributes, NOFOLLOW_LINKS)` の `isSymbolicLink || isOther`
（Java はジャンクションを `isSymbolicLink=false`・`isOther=true` として返す）。古い生成物の探索は `Files.walk` を使わず自前で走査し、リンクがあれば誤りにする。
実行順は「大文字小文字だけ違う古い生成物の削除 → 書き込み → 残りの古い生成物の削除」。下の負のテストは `OutputWriterSuite` にある。
Windows の手元ではジャンクションのテストが通り、シンボリックリンクのテストは作成権限が無く `assume` で飛ぶ（Linux の CI で走る）。

負のテスト（検証条件）: 次の 5 つで、外部へ書き込まれず削除もされず、エラーになること。Windows では junction のテストを手元で確認する（symlink の作成に権限が要る環境ではスキップ可）。

1. 出力ルートが外部ディレクトリへのリンク。
2. 出力パスの途中のディレクトリが外部へのリンク。
3. 末端の既存ファイルが外部ファイルへの symlink。
4. 管理対象のディレクトリ内に外部へのリンクがあるときに、古い生成物の削除がリンク先を消さない。
5. ディレクトリ junction（Windows）で 1〜2 と同じ。

## 4. CLI の方式比較

sbt を使わずに生成だけを実行したい場面（CI のチェック、Vite のプラグインからの呼び出し、エディタの保存時）がある。

| 方式 | 長所 | 短所 |
| --- | --- | --- |
| npm CLI（`npx hx4s generate`）が sbt を呼ぶ | JS 側のツールから使いやすい | 結局 sbt を起動する。sbt の起動の重さは変わらない |
| npm CLI が JVM の `hx4s-codegen` を直接起動（coursier で取得、または jar を同梱） | sbt を介さない | JDK が必要。jar の同梱は npm パッケージを大きくする。coursier の導入を利用者に求める |
| sbt plugin のタスクのみ（`sbt hx4sGenerate`） | 実装が最小。版の解決が sbt に一本化される | sbt の起動が要る |
| `hx4s-codegen` の main を直接（`cs launch io.github.montereylangit:hx4s-codegen_3:<v> -- …`） | 依存が少ない | 利用者が coursier の使い方を知る必要がある |
| Scala.js でビルドした codegen を npm で配る | Node だけで動く | scalameta の Scala.js 版の可用性と、fs の扱いを書き直す負担（未調査） |

P0 は **sbt plugin のタスク + `hx4s-codegen` の main（引数で設定を受ける）** の 2 つだけにする。npm CLI は P1 で、利用者が増えてから決める。

### 4.1 Windows への配慮

3ch の開発環境は Windows であり、既に次の対処がある。hx4s はこれを引き継ぐ。

- sbt の起動は `sbt.bat` を `shell: true` で、引数を `"` で囲む（vite-plugin-scalajs）。引数に `"` や `%` を含めない設計にする。
- `runMain` に渡すパスは `\` と `"` をエスケープしている（`build.sbt` の `quotedPath`）。sbt-hx4s は sbt のタスク内で `File` を直接渡し、文字列化しない。
- `[bbs]` のような角括弧入りのパスはシェルの glob と衝突しうる。生成器は Java の `Path` で扱い、シェルを介さない。
- 改行コード: 生成は常に LF。比較時は正規化する（3.3）。
- 大文字小文字を区別しないファイルシステムでの衝突（3.3）。
- パスの長さ: `app/routes` の深い階層で 260 文字を超えると Git for Windows / 一部ツールで問題になる（一般論。3ch で発生した記録は無い）。
