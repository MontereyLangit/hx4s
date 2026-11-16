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

`IslandComponents.scala` はソースツリー（`bbs/src/main/scala/io/_3ch/bbs/generated/`、`.gitignore` 対象）に生成されるため、
新しく clone した直後は `v6:generate` を実行しないと `bbs` がコンパイルできないと考えられる（`pretest` は codegen を呼ばない。未検証）。

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
| `IslandComponents` を server のソースに入れる | ○（`sourceGenerators`、U-9） | — | — |
| Scala.js プロジェクトの登録（server / client）と `buildStart` の直列化 | — | ○ | projectID を渡す |
| `scalajs:client.js` の alias と 2 つ目の接頭辞 | — | ○ | — |
| `honox()` に `reactApiImportSource: "react"` を渡す | — | ○ | 他の HonoX オプション |
| client ビルドの `input` / 出力名の既定値 | — | ○ | 上書き可 |
| Node 用 loader（`scalajs:client.js` と `hono/jsx`） | （P1 で jsEnv の補助） | ○（`hx4s-vite/node-loader`） | 実行基盤固有のスタブを足す |
| 実行基盤（Cloudflare 等）のビルドプラグイン | — | — | ○ |
| CSS（Tailwind 等） | — | — | ○ |

### 2.1 hx4s-vite の API 案

```ts
import { defineConfig } from "vite";
import hx4s from "hx4s-vite";
import buildCloudflareWorkers from "@hono/vite-build/cloudflare-workers"; // 利用側の選択

export default defineConfig(({ mode }) => ({
  plugins: [
    hx4s({
      mode,                         // "client" | それ以外（server）
      sbt: { cwd: import.meta.dirname },
      server: { projectID: "server" },
      client: { projectID: "client", entry: "./app/client.ts" },
      honox: {},                    // honox() へそのまま渡す追加オプション
    }),
    ...(mode === "client" ? [] : [buildCloudflareWorkers({ /* … */ })]),
  ],
}));
```

- 戻り値は Plugin の配列。client モードでは client の Scala.js プラグインと Rollup 設定だけ、server モードでは `honox()`・server と client の Scala.js プラグイン・alias を返す。
- `mode` の名前（`client`）は HonoX の `honox-vite-client` の判定（`mode === "client"`）に合わせる。
- Scala.js プラグインは `@scala-js/vite-plugin-scalajs` をそのまま使い、`buildStart` の直列化だけを足す。フォークしない。

### 2.2 sbt-hx4s の設定案

```scala
// project/plugins.sbt
addSbtPlugin("io.github.montereylangit" % "sbt-hx4s" % "<version>")

// build.sbt
lazy val server = project.enablePlugins(ScalaJSPlugin, Hx4sServerPlugin)
  .settings(
    hx4sServerMain := (Compile / scalaSource).value / "app" / "Main.scala",
    hx4sClientMain := (client / Compile / scalaSource).value / "app" / "Main.scala",
    hx4sAppDir := (ThisBuild / baseDirectory).value / "app",
    hx4sIslandComponentsPackage := "app.generated",
  )
```

| キー | 既定値（案） | 説明 |
| --- | --- | --- |
| `hx4sServerMain` / `hx4sClientMain` | なし（必須） | アノテーションを読むソースファイル |
| `hx4sMainObject` | `"Main"` | 走査する object 名 |
| `hx4sServerModuleId` / `hx4sClientModuleId` | `"server"` / `"client"` | `@JSExportTopLevel` の moduleID。仮想モジュール名 `scalajs:<id>.js` にもなる |
| `hx4sAppDir` | `baseDirectory / "app"` | `routes/` と `islands/` の親 |
| `hx4sIslandComponentsPackage` | `"hx4s.generated"` | 生成 Scala のパッケージ |
| `hx4sCodegenVersion` | プラグインと同じ版 | 解決する `hx4s-codegen` の版 |
| `hx4sGenerate` | タスク | 生成を実行し、書いたファイルの一覧を返す |

## 3. codegen の方式比較

### 3.1 生成方式

| 方式 | 長所 | 短所 | 判断 |
| --- | --- | --- | --- |
| scalameta の構文解析（現行） | コンパイル前に動く。TS ファイルを Vite より先に出せる。実装が小さい（現行の `AnnotatedAppEntries` は約 90 行）。JVM で動くので fs が素直 | 型を解決できない。import の別名や型エイリアスを追えない。引数は文字列リテラルに限られる | **採用**（[ADR 0004](adr/0004-scalameta-codegen.md)） |
| Scala 3 マクロ | 型を解決できる。Props の中身も検査できる | コンパイル中にファイルを書く副作用になる。TS ファイルを Scala のコンパイル結果に依存させると、Vite → sbt → Vite の順序が複雑になる。Scala.js のコンパイル中に JVM の fs を使うのはマクロの本来の用途から外れる | P2 で型検査だけをマクロに足す案を残す |
| sbt の source generator（sbt 本体内で生成） | sbt のタスクグラフに素直に乗る | sbt 1.x は Scala 2.12。Scala 3 で書いた生成器をそのまま動かせない。生成器を 2.12 で書くと、利用側 Scala 3 の構文（`enum`、optional braces）の解析は scalameta の dialect で可能だが、生成器自体を 2 系統持つことになる | 呼び出しの入口としては使う（`sourceGenerators` に `hx4sGenerate` を繋ぐ）。生成器本体は分ける |
| コンパイラプラグイン | 型情報とコンパイルの一体化 | Scala 3 のコンパイラプラグインは不安定な API に依存し、版ごとの保守が重い | 不採用 |

### 3.2 sbt からの実行

`hx4s-codegen` は Scala 3、sbt 1.x の plugin は Scala 2.12 で動く。現行の 3ch は `codegen` を同じビルドのサブプロジェクトにして `runMain` で呼んでいるが、
利用側のビルドに生成器のサブプロジェクトを持たせるわけにはいかない。

方式は「専用 configuration で `hx4s-codegen_3` を解決し、そのクラスパスで別クラスローダか fork で main を呼ぶ」とする。

- **scalafmt 方式**（sbt-scalafmt / scalafmt-dynamic）: 実行時に指定版の成果物を取得し、隔離したクラスローダで読み込む。
- **ScalaPB 方式**（sbt-protoc / protocbridge）: 生成器を別の Scala 版でも動かせるように、専用 configuration で解決した成果物を隔離実行する。

P0 は実装の単純さを優先して **fork**（`Fork.java` で `java -cp <resolved> hx4s.codegen.Main <args>`）にする。JVM を 1 回余分に起動するが、
クラスローダの分離（Scala 2.12 と 3 の標準ライブラリの衝突）を考えなくてよい。起動の重さが問題になったら同一 JVM 内の隔離クラスローダに切り替える。
設定は case class `Hx4sCodegenConfig` を JSON（または引数列）にして渡す。どちらにするかは Phase 2 で決める。

sbt 2.x（Scala 3）に移れば同じクラスローダで呼べる見込みだが、sbt 2 の正式版への移行時期が読めないので前提にしない（U-10）。

### 3.3 生成物の書き出し規則

現行の `GenerateAppFiles.writeOutputs` を基礎に、次を P0 で満たす。

| 規則 | 現行 | P0 |
| --- | --- | --- |
| 決定的な出力（並び順・改行） | ルートはパスでソート、island は宣言順。改行は `\n` | 同じ。island の順も宣言順で固定する |
| 内容が同じなら書き直さない | 毎回書く | 既存の内容と比べ、同じなら書かない。比較は改行コードを `\n` に揃えてから行う（Windows の `core.autocrlf=true` で checkout された LF/CRLF の差で毎回書き直さないため） |
| 生成バナーの無いファイルは上書き・削除しない | ある（先頭行の接頭辞で判定） | 同じ。バナーの接頭辞は hx4s の固定文字列にし、3ch の旧バナーも移行期間は認める |
| 出力先ルート外への書き込み拒否 | 入力パスのセグメント検査で `..` などを拒否 | 加えて、解決後のパスを正規化し、出力ルートの配下にあることを確かめる（シンボリックリンク経由も含めて `toRealPath` の親で検査） |
| 大文字小文字違いの衝突検出 | ある | 同じ。既存ファイル（手書き含む）との衝突も検出する（NTFS / APFS の既定が大文字小文字を区別しないため） |
| 古い生成物の削除 | `routes/` と `islands/` の配下でバナー付きのものだけ | 同じ。`IslandComponents` を sourceGenerators に移した場合は sbt の `src_managed` に任せる |
| 書き込みの原子性 | 無し | 一時ファイルに書いてから置き換える。全出力の検査を書き込み前に済ませる（現行どおり） |

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
