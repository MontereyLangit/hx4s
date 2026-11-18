# ビルド設定

hx4s のビルドは 3 つの部品でできている。

| 部品 | 役割 |
| --- | --- |
| sbt-hx4s（`Hx4sPlugin`） | 設定キーとタスク。`@route` / `@islands` から route・island の TS と `IslandComponents` を生成する |
| `hx4s-codegen` | 生成器本体。sbt-hx4s が fork した JVM で動かす（直接呼ぶこともできる） |
| `hx4s-vite`（`hx4s()`） | Scala.js のプロジェクトを Vite と HonoX につなぐ |

## sbt のキーとタスク

`Hx4sPlugin` は server プロジェクトで有効にする（`trigger` は `noTrigger`）。利用者が必ず書くのは `enablePlugins(Hx4sPlugin)` と `hx4sClientProject` だけで、残りは既定値で動く。

| キー | 型 | 既定値 | 説明 |
| --- | --- | --- | --- |
| `hx4sVersion` | 値（`String`） | プラグインの版 | `hx4s-react` などの依存に使う |
| `hx4sClientProject` | `ProjectReference` | 未設定 | `@islands` を持つ client プロジェクト。未設定なら client なし（SSR だけ）として扱い、`IslandComponents` は空になる |
| `hx4sServerSources` | `Seq[File]`（タスク） | このプロジェクトの `Compile / unmanagedSources` の `.scala` | `@route` を探すソース |
| `hx4sClientSources` | `Seq[File]`（タスク） | `hx4sClientProject` の `Compile / unmanagedSources` の `.scala` | `@islands` を探すソース |
| `hx4sProjectRoot` | `File` | `ThisBuild / baseDirectory` | 生成の基準ディレクトリ（Vite の root）。誤りの表示のパスもここからの相対 |
| `hx4sRouteDir` | `File` | `<hx4sProjectRoot>/app/routes` | route の出力先 |
| `hx4sIslandDir` | `File` | `<hx4sProjectRoot>/app/islands` | island の出力先 |
| `hx4sIslandIdPrefix` | `String` | `/app/islands/` | SSR で HonoX に渡す island 識別子（`component-name`）の接頭辞 |
| `hx4sServerModule` | `String` | `server` | server の `@JSExportTopLevel` の moduleID |
| `hx4sClientModule` | `String` | `client` | 既存設定との互換性のために残っている。Island の moduleID はアノテーションから取得する |
| `hx4sVirtualModulePrefix` | `String` | `scalajs:` | Scala.js のリンク成果物を指す仮想モジュールの接頭辞 |
| `hx4sIslandComponentsPackage` | `String` | `hx4s.generated` | 生成する Scala のパッケージ |
| `hx4sIslandComponentsObject` | `String` | `IslandComponents` | 生成する object の名前 |
| `hx4sIslandBridge` | `String` | `hx4s.react.Island` | island の境界を付ける関数の完全修飾名 |
| `hx4sIslandExportType` | `String` | `hx4s.react.IslandExport` | `@islands[P]` と照合する client の export の型 |
| `hx4sAnnotationPackage` | `String` | `hx4s.annotation` | `route` / `islands` のパッケージ |
| `hx4sBanner` | `Seq[String]` | 空（codegen の既定） | 生成ファイルの先頭行（`//` で始める 1 行ずつ） |
| `hx4sGeneratedMarker` | `Option[String]` | `None`（codegen の既定） | 生成ファイルを見分ける先頭行の接頭辞 |
| `hx4sCodegenVersion` | `String` | プラグインと同じ版 | 解決する `hx4s-codegen_3` の版 |
| `hx4sCodegenJavaOptions` | `Seq[String]` | 空 | codegen を実行する JVM のオプション |
| `hx4sCodegenClasspath` | `Seq[File]`（タスク） | 専用 configuration `hx4s-codegen` の解決結果 | codegen の実行時クラスパス |
| `hx4sCheckDependencies` | タスク | — | client と server の依存の向きを検査する（[ssr-hydration.md](ssr-hydration.md)） |
| `hx4sGenerate` | `Seq[File]`（タスク） | — | route / island の TS を生成し、生成した（内容が同じで据え置いた）ファイルの一覧を返す。毎回 codegen を起動する |
| `hx4sIslandComponents` | `Seq[File]`（タスク） | — | `IslandComponents.scala` を `Compile / sourceManaged` に生成する。`Compile / sourceGenerators` に登録済みで、入力が変わらなければ codegen を起動しない |

- `IslandComponents` の増分判定のキーは、client のソースの内容、codegen への引数、`javaHome`、`hx4sCodegenJavaOptions`、codegen のクラスパス（JAR の内容のハッシュを含む）。
- `hx4sGenerate` と `hx4sIslandComponents` は、先に `hx4sCheckDependencies` を実行する。
- `hx4s-codegen` は Scala 3 製で sbt 1.x（Scala 2.12）では動かせないので、専用 configuration で解決して fork する。失敗すると codegen の誤りの行（`[hx4s-codegen] error: <パス>:<行>: <理由>`）を 1 回ずつログに出し、要約（`hx4s-codegen app failed: 2 error(s)`）でタスクが失敗する。例外の `getMessage` は要約と誤りの行を含む。
- **既定値以外の appDir・moduleID・接頭辞は未検証。** `hx4sServerModule` / `hx4sClientModule` / `hx4sVirtualModulePrefix` / `hx4sIslandIdPrefix` / `hx4sRouteDir` / `hx4sIslandDir` を変えるときは、
  HonoX の固定値（`/app/routes`・`/app/islands`）と `hx4s()` の側も合わせる必要があり、組み合わせの検証は例の既定値だけ。

## `hx4s()` のオプション

```ts
import hx4s from "hx4s-vite";

const plugins = hx4s({ mode, sbt: { cwd: import.meta.dirname } });
```

| オプション | 既定値 | 説明 |
| --- | --- | --- |
| `mode`（必須） | — | Vite の `mode`。`"client"` ならブラウザ向け、それ以外は SSR（server）向け |
| `sbt.cwd` | `process.cwd()` | sbt を実行するディレクトリ |
| `server.projectID` | `"server"` | `@route` を持つ sbt プロジェクトの ID |
| `client` | `{}` | client の設定。`false` なら client を持たない構成（API だけ・island の無い SSR）。`hx4sClientProject` を設定しないときに使う |
| `client.projectID` | `"client"` | `@islands` を持つ sbt プロジェクトの ID |
| `client.module` | `"client"` | SSR で優先して client 側に解決する moduleID。他の moduleID は server の出力にファイルがなければ client の出力から解決する |
| `client.entry` | `"./app/client.ts"` | client ビルドの入口（文字列または配列） |
| `client.uriPrefix` | `"scalajs-client"` | server ビルドで client のリンク成果物を読む仮想モジュールの接頭辞 |
| `client.output.entryFileNames` | `"static/client.js"` | client の入口の出力名 |
| `client.output.chunkFileNames` | `"static/assets/[name]-[hash].js"` | chunk の出力名 |
| `client.output.assetFileNames` | `"static/assets/[name].[ext]"` | asset の出力名 |
| `honox` | `{}` | `honox()` にそのまま渡す追加の設定。client があれば `islandComponents.reactApiImportSource` の既定は `"react"`（`client: false` では何も足さない） |

戻り値は `PluginOption[]`。

| `mode` | 返すプラグイン |
| --- | --- |
| `"client"` | client の Scala.js プロジェクト、出力名と `emptyOutDir: false` を設定するプラグイン |
| それ以外 | `honox()`、server と client の Scala.js プロジェクト、SSR 時の Island モジュール解決 |
| それ以外（`client: false`） | `honox()` と server の Scala.js プロジェクトだけ |

`client: false` と `mode: "client"` を組み合わせると、`hx4s()` がエラーを投げる（client ビルドが無いため）。例は `examples/api-only`（[api.md](api.md)）。

そのほかの export:

| export | 内容 |
| --- | --- |
| `scalaProject(cwd, projectID, uriPrefix?)`（`hx4s-vite`） | `@scala-js/vite-plugin-scalajs` を包み、`buildStart` を直列にする。複数のプロジェクトで同時に sbt を起動しないため。`hx4s()` を使わない構成で直接使う |
| `islandPropsProblem(element)`（`hx4s-vite/client`） | hydrate の前の props の検証。理由があれば文字列、なければ `undefined`（[ssr-hydration.md](ssr-hydration.md)） |
| `hx4s-vite/node-register` | Node の `--import` に渡す。client 側の `scalajs:<module>.js` と honox 内の `hono/jsx` を解決する loader を登録する |
| `hx4s-vite/node-loader` | 上の loader 本体。利用側の loader と並べて登録するとき用 |

`node-loader` は環境変数 `HX4S_CLIENT_JS_DIR`（client の `fastLinkJS` の出力ディレクトリ）を読む。

## 2 モードのビルド

client と server は別々の `vite build` で作る。client を先にする（`client: false` なら `vite build` の 1 回だけ）。

```bash
vite build --mode client   # dist/client/static/client.js、dist/client/static/assets/*
vite build                 # dist/server/index.js（Worker）
```

- server のビルドは、server と client の両方の Scala.js プロジェクトをリンクする（SSR が client のリンク成果物を import するため）。client は別に静的配信用のバンドルとして作られる
- `@scala-js/vite-plugin-scalajs` は Vite の `mode` が `development` のときだけ `fastLinkJS`、それ以外は `fullLinkJS` を使う
- 出力先（`dist/client`・`dist/server`）は `vite.config.ts` の `build.outDir` と実行基盤のアダプタが決める。hx4s は固定しない

## 既知の制約

| 制約 | 内容 |
| --- | --- |
| sbt の起動回数 | `npm run build` は sbt を 4 回起動する（生成 1 回、client ビルド 1 回、server ビルドで server と client のリンクに 2 回。直列）。1 回あたり 15〜30 秒かかった（Phase 3 の検証、Windows 11。[hx4s/reviews/phase3-review.md](hx4s/reviews/phase3-review.md)）。減らす方法は未調査 |
| 開発サーバー | Vite の dev server（HMR）では動かさない。`npm run dev` はビルドして `wrangler dev` を起動する構成 |
| sbt の最終行 | `@scala-js/vite-plugin-scalajs` は `sbt print` の標準出力の最終行を出力ディレクトリとして使う。sbt が最後に警告などを出すと壊れる |
| JDK 25 の警告 | JDK 25 では sbt の起動時に `sun.misc.Unsafe` の警告が出る（動作への影響は見ていない） |
| 検証した設定 | 既定の appDir・moduleID・接頭辞だけ。変えた組み合わせは未検証 |

## 生成ファイルの扱い

### どれをコミットするか

| 生成物 | 場所 | コミット |
| --- | --- | --- |
| route の TS | `app/routes/**/*.ts` | する |
| island の TSX | `app/islands/**/*.tsx` | する |
| `IslandComponents.scala` | `server/target/.../src_managed/…` | しない（`target` の下。clone 直後でもコンパイルで生成される） |

TS をコミットするのは、Vite が読むファイルがソースツリーにあることと、変更をレビューで追えるため。`examples/minimal` の CI は、ビルド後に `app/` に差分や未追跡のファイルが無いことを確かめる。

### 生成マーカーと安全策

- 生成ファイルの先頭行は `// This file is generated by hx4s-codegen. Do not edit this file manually.`。先頭行がマーカー（`// This file is generated by hx4s-codegen`）で始まるファイルだけを生成物とみなす
- **マーカーの無い既存ファイルは上書きも削除もしない。** 手書きのファイルと同じパスに生成しようとすると、誤りとして止まる
- `app/routes`・`app/islands` の下の生成ファイル（`.ts` / `.tsx` / `.scala`）のうち、今回出力しないものは消える。`@route` / `@islands` を消せば、対応するファイルも消える
- 内容が同じなら書き直さない（更新時刻も変えない。改行コードの違いは同じ内容とみなす）
- 誤りはすべて書き込みの前に集める。1 件でもあれば何も書かない
- シンボリックリンクと Windows のジャンクションは辿らずに誤りにする（出力ルートの祖先、途中のディレクトリ、出力先、古い生成物を探すディレクトリ）
- 各ファイルは同じディレクトリの一時ファイル（`.<名前>.hx4s-tmp`）に書いてから置き換える。途中で失敗しても既存ファイルは元のまま残り、再実行で収束する
- 大文字小文字だけ違うパスは、出力どうしでも既存のファイルとの間でも拒否する

保証しないこと: 複数ファイルの間の原子性（途中で失敗すると一部だけ新しい世代になりうる）、並行実行の排他。

別のマーカーで生成していたプロジェクトから移るときは、`hx4sBanner` と `hx4sGeneratedMarker` をその値に設定すると、既存ファイルを置き換えられる（[migration.md](migration.md)）。

## `CodegenMain` の引数

sbt を使わずに codegen を呼ぶこともできる。`hx4s-codegen_3` のクラスパスで `hx4s.codegen.CodegenMain` を動かす。

```
java -cp <クラスパス> hx4s.codegen.CodegenMain app \
  --server-sources server/Main.scala --server-sources server/Api.scala --client-sources client/Main.scala
```

最初の引数はコマンド。

| コマンド | 生成するもの |
| --- | --- |
| `app` | `app/routes` の route TS と `app/islands` の island TSX |
| `island-components` | SSR 用の `IslandComponents.scala`（`--scala-output` が必須） |

続けて `--キー 値` を並べる。省いたキーは既定値になる。失敗すると誤りを標準エラーに `[hx4s-codegen] error: …` の形で出し、非ゼロで終わる。

| 引数 | 対応する sbt のキー | 既定値 |
| --- | --- | --- |
| `--project-root` | `hx4sProjectRoot` | カレントディレクトリ |
| `--server-sources` | `hx4sServerSources` | 空（1 ファイルずつ繰り返す。カンマでは分けない） |
| `--client-sources` | `hx4sClientSources` | 空（同上） |
| `--server-module` / `--client-module` | `hx4sServerModule` / `hx4sClientModule` | `server` / `client` |
| `--route-dir` / `--island-dir` | `hx4sRouteDir` / `hx4sIslandDir` | `app/routes` / `app/islands` |
| `--island-id-prefix` | `hx4sIslandIdPrefix` | `/app/islands/` |
| `--scala-output` | （`src_managed` に固定） | なし |
| `--scala-package` / `--scala-object` | `hx4sIslandComponentsPackage` / `hx4sIslandComponentsObject` | `hx4s.generated` / `IslandComponents` |
| `--island-bridge` / `--island-export-type` | `hx4sIslandBridge` / `hx4sIslandExportType` | `hx4s.react.Island` / `hx4s.react.IslandExport` |
| `--annotation-package` | `hx4sAnnotationPackage` | `hx4s.annotation` |
| `--virtual-module-prefix` | `hx4sVirtualModulePrefix` | `scalajs:` |
| `--banner` | `hx4sBanner` | 既定のバナー（1 行ずつ繰り返す。`//` で始める） |
| `--generated-marker` | `hx4sGeneratedMarker` | `// This file is generated by hx4s-codegen` |
| `--output-list` | （`hx4sGenerate` が内部で使う） | なし。書いたファイルと据え置いたファイルの絶対パスを書き出す |

同じキーを 2 回渡すとエラー（`--server-sources`・`--client-sources`・`--banner` を除く）。生成ソースへ埋め込む値は書き出しの前に確かめ、
識別子として使えない値、改行や引用符を含む値は拒否する（[hx4s/codegen.md](hx4s/codegen.md)）。
