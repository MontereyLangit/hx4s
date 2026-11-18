# hx4s-codegen（`@route` / `@islands` の配線生成）

`hx4s-codegen` は Scala のソースを scalameta で構文として読み、HonoX の route / island ファイルと、SSR 用の Scala API（`IslandComponents`）を生成する。
方式の判断は [ADR 0004](adr/0004-scalameta-codegen.md)、sbt からの呼び出しは [build-integration.md](build-integration.md) 2.2・3.2 を参照。

| ファイル（`modules/codegen/src/main/scala/hx4s/codegen/`） | 内容 |
| --- | --- |
| `AnnotationScanner` | アノテーションと `@JSExportTopLevel` の解析、パス・メソッド・型引数・export の宣言型の検査 |
| `AppEntries` | 中間表現（`RouteEntry`・`IslandEntry`・`HttpMethod`） |
| `AppFileRenderer` | route TS・island TSX・`IslandComponents.scala` のテンプレート |
| `RoutePath` | route ファイルのパスから HonoX が登録する URL パターンへの変換（衝突の検査に使う） |
| `OutputPath` / `OutputWriter` | 出力パスの検査と書き出し |
| `CodegenConfig` / `Codegen` / `CodegenMain` | 設定、生成の入口、引数で設定を受ける main |

アノテーションは型ではなく名前（設定したパッケージ名＋`route` / `islands`）で見分けるので、`hx4s-codegen` は `hx4s-core` に依存しない。

## 宣言の規則

- 設定したソースファイルの、トップレベル object（パッケージ内を含む。入れ子の object は対象外）の `def` / `val` に付いたアノテーションを読む。object の名前は問わない。
- `@route` / `@islands` を付けたメンバには、リテラルの名前と moduleID を持つ `@JSExportTopLevel` がちょうど 1 つ必要。`@route` は server の moduleID、`@islands` は client の moduleID でなければならない。
- `@route("x.ts")` は GET、`@route("x.ts", "POST")` は named export。同じメンバに複数の `@route` を付けられ、同じパスのメソッドは 1 ファイルにまとまる。
  メソッドは GET, POST, PUT, DELETE, PATCH, OPTIONS。HonoX の `createApp` はこの 6 つしか登録しないので `HEAD` は誤り（GET のハンドラが HEAD にも答える）。
- `@islands[P]("x.tsx")` の P は props の型の完全修飾名。省くと `js.Object`。1 つのメンバに付けられる `@islands` は 1 つまで。
- export の宣言型が `IslandExport[T]`（設定 `islandExportType`）なら、`T` が `P` と同じ型名でなければならない（[ADR 0007](adr/0007-props-validation-and-typed-export.md)）。
- 引数はすべて文字列リテラル。名前付き引数は宣言の順に限る。

検出して拒否するもの:

- 出力パスの誤り: 空の区切り、`.` / `..`、絶対パス・ドライブレター・バックスラッシュ（使える文字は `A-Za-z0-9_.$[]-` だけ）、末尾のドット・空白、Windows の予約名（`CON` など。拡張子付きも）
- HonoX が拾わない route: basename が `_` / `-` / `$` で始まる、`*.test.ts` / `*.spec.ts`、`-` で始まるディレクトリ、ドットで始まる名前と `node_modules`（先頭の `.well-known/` は許す）
- HonoX が拾わない island: basename が `[A-Za-z0-9-]+\.tsx` でない、ドットで始まるディレクトリや `node_modules` を含む
- 重複: 同じ export 名を複数のメンバに付ける、1 つのメンバに `@islands` を複数付ける、同じ route のメソッドを 2 回、大文字小文字だけ違う route / island のパス
- URL の衝突: 別々の route ファイルが HonoX で同じ URL パターンになり、同じメソッドを持つ（下の「URL の衝突」）

### URL の衝突

route ファイルのパスを HonoX の `createApp` と同じ規則で URL パターンにし（`RoutePath`）、パラメータ名を除いた形が同じでメソッドも同じなら誤りにする。
規則は honox 0.1.62 の `server/utils/file.js` の `filePathToPath` を、ディレクトリ部分とファイル名に分けて当て、hono の `mergePath` でつなぐ（`createApp` がディレクトリごとの sub app を `app.route` で載せるため）。

| 例 | URL パターン |
| --- | --- |
| `foo.ts` / `foo/index.ts` / `foo/index/index.ts` | `/foo`（3 つとも同じ） |
| `items/[id].ts` / `items/[slug].ts` | `/items/:id` / `/items/:slug`（パラメータ名だけ違うので衝突） |
| `docs/[...slug].ts` | `/docs/:slug{.+}`（`docs/[id].ts` とは別のパターン） |
| `page.md.ts` | `/page`（`.ts` の後に `.md` も外れるので `page.ts` と衝突） |

`(group)` のグループは変換規則には入っているが、出力パスの文字種（`()` を含まない）のため生成ルートには現れない。`[[name]]` のような任意パラメータは HonoX に無い。
固定の区切りとパラメータ（`items/new.ts` と `items/[id].ts`）は HonoX の並び順（`[` で始まる名前を後ろにする）で両立するので衝突としない。
変換表は `modules/codegen/src/test/resources/route-paths.tsv` に置き、`RoutePathSuite` と、HonoX の `createApp` に同じ表を通す `tests/route-paths.test.js` の両方で確かめる。
`app/routes` の手書きファイルとの衝突は検査しない（警告も出さない。未実装。[routing-design.md](routing-design.md) 2.2）。

server と client の誤りはまとめて返す（片方を直すと次が出る、ということが無いように）。宣言ごとの検査（構文、アノテーションの引数、`@JSExportTopLevel`、メソッド、ファイル名、
`IslandExport[T]` の照合）で誤りのあった宣言は除き、残りの宣言で export 名の重複、route の大文字小文字の違い・同じ URL・同じメソッドの重複、island のパスの重複を確かめる。
誤った宣言を除くので、それが原因の重複・衝突は報告しない（直したあとに初めて出ることはある）。読めないソースも同じく除いて続ける。
server / client のソースは空でもよい（route だけ、island だけ、SSR だけの構成）。空なら解析結果も空で、古い生成物は消え、`IslandComponents` は空の object になる。

## 出力の規則

- 生成 TS は import した名前に生成器専用の別名を付ける（`createRoute as __hx4s_createRoute`、`<export 名> as __hx4s_handler_<export 名>`、island は `as __hx4s_island`）。
  export 名が `createRoute`・`POST`・`GET`・JS の予約語（`default`・`class` など）でも、生成コードの識別子と衝突しない。`IslandComponents` のメソッド名はバッククォートで囲むので Scala の予約語でもよい。
- 出力順は入力の並びに依存しない。route ファイルはパス順、ファイル内のメソッドは上の固定順（GET が先頭）、island と `IslandComponents` のメソッドはファイルパス順。
- 内容が同じなら書き直さず、更新時刻も変えない。改行コードの違いは同じ内容とみなす。
- 生成マーカー（`generatedMarker` で始まる先頭行）の無い既存ファイルは上書きも削除もしない。
- route / island の出力ディレクトリにある生成ファイル（`.ts` / `.tsx` / `.scala`）のうち今回出力しないものは消す。
- 大文字小文字だけ違うパスは、出力どうしでも既存のファイル・ディレクトリとの間でも拒否する。大文字小文字だけ違う古い生成ファイルは消してから新しい名前で書く。
- ファイルとディレクトリの衝突は計画の段階で拒否する。
- 誤りはすべて書き込みの前に集める。1 件でもあれば何も書かない。
- 出力ルートの祖先（`projectRoot` より下）・出力ルート・途中のディレクトリ・出力先・古い生成ファイルを探す出力ディレクトリの中に、シンボリックリンクか Windows のジャンクションがあれば、辿らずに誤りにする。
  判定は `BasicFileAttributes`（`NOFOLLOW_LINKS`）の `isSymbolicLink || isOther`。Java はジャンクションを `isOther` として返す。
  境界: `projectRoot` 自体とその上は確かめない。`projectRoot` の外にある出力ルート（`--scala-output` を外に置いた場合など）は、ルートから下だけを確かめる。
  sbt の `target` をリンクにしている構成では、`src_managed` の祖先がリンクになるので `IslandComponents` の生成が誤りになる。
- 各ファイルは同じディレクトリの一時ファイル `.<名前>.hx4s-tmp` に書き終えてから、`Files.move`（`ATOMIC_MOVE`、できなければ `REPLACE_EXISTING`）で置き換える。
  書き込みの途中で失敗しても、既存の生成ファイルは元の内容（生成マーカーを含む）のまま残る。一時ファイルの名前はドットで始まり生成対象の拡張子で終わらないので、
  HonoX の glob にも古い生成ファイルの判定にも掛からない。前回の失敗で残った一時ファイルは、出力ディレクトリと出力先の親ディレクトリから次の実行で消す。
- 書き込みは「一時ファイルの残骸と、大文字小文字だけ違う古い生成ファイルの削除 → 内容が変わった出力の書き込み → 残りの古い生成ファイルの削除」の順。
  途中で失敗しても、再実行で収束する（生成は決定的で、同じ内容は書かず、残った古い生成物と一時ファイルは次回に消える）。

保証しないこと: 複数ファイル間の原子性（途中で失敗すると一部のファイルだけ新しい世代になりうる）、並行実行の排他、リンクを確かめてから書くまでの間の差し替え（[build-integration.md](build-integration.md) 3.3）。

## エラーの形式

解析と書き出しは例外を投げず `Either[List[CodegenError], A]` を返す。`CodegenMain` は誤りを標準エラーに次の形で出し、`CodegenFailure` を投げて非ゼロで終わる
（同じ JVM の `runMain` から呼ばれても sbt ごと終わらないよう `System.exit` は呼ばない）。

```
[hx4s-codegen] error: server/src/main/scala/app/Main.scala:7: HEAD is not registered by HonoX createApp (GET handlers also answer HEAD)
```

sbt-hx4s はこの行をタスクの失敗メッセージに含める。

## 設定

`CodegenConfig.default(projectRoot, serverSources, clientSources)` が残りを下の既定値にする。`CodegenMain` も省いたキーを既定値にする。

| 項目 | `CodegenMain` のキー | sbt-hx4s のキー | 既定値 |
| --- | --- | --- | --- |
| プロジェクトルート | `--project-root` | `hx4sProjectRoot` | カレントディレクトリ（sbt はビルドのルート） |
| server の走査ソース | `--server-sources`（1 ファイルずつ繰り返す。カンマでは分けない） | `hx4sServerSources` | 空（sbt はこのプロジェクトの Scala ソース） |
| client の走査ソース | `--client-sources`（同上） | `hx4sClientSources` / `hx4sClientProject` | 空（sbt は `hx4sClientProject` の Scala ソース。未設定なら空） |
| server / client の moduleID | `--server-module` / `--client-module` | `hx4sServerModule` / `hx4sClientModule` | `server` / `client` |
| route の出力ディレクトリ | `--route-dir` | `hx4sRouteDir` | `app/routes` |
| island の出力ディレクトリ | `--island-dir` | `hx4sIslandDir` | `app/islands` |
| SSR の island 識別子の接頭辞 | `--island-id-prefix` | `hx4sIslandIdPrefix` | `/app/islands/` |
| SSR 用 Scala API の出力先 | `--scala-output`（`island-components` で必須） | （`src_managed` 配下に固定） | 無し |
| そのパッケージ名 | `--scala-package` | `hx4sIslandComponentsPackage` | `hx4s.generated` |
| その object 名 | `--scala-object` | `hx4sIslandComponentsObject` | `IslandComponents` |
| island ブリッジの完全修飾名 | `--island-bridge` | `hx4sIslandBridge` | `hx4s.react.Island` |
| 照合する export の型 | `--island-export-type` | `hx4sIslandExportType` | `hx4s.react.IslandExport` |
| アノテーションのパッケージ | `--annotation-package` | `hx4sAnnotationPackage` | `hx4s.annotation` |
| 仮想モジュールの接頭辞 | `--virtual-module-prefix` | `hx4sVirtualModulePrefix` | `scalajs:` |
| 生成バナー | `--banner`（1 行ずつ繰り返す。`//` で始める） | `hx4sBanner` | `// This file is generated by hx4s-codegen. Do not edit this file manually.` |
| 生成マーカー | `--generated-marker`（バナーの 1 行目はこれで始める） | `hx4sGeneratedMarker` | `// This file is generated by hx4s-codegen` |
| 生成したファイルの一覧 | `--output-list`（書いたファイルと据え置いたファイルの絶対パスを 1 行ずつ書く） | （`hx4sGenerate` が内部で使う） | 書かない |

生成ソースへ埋め込む値は書き出しの前に確かめる。改行・引用符・バックスラッシュ・`*/` などを含む値は誤りにする。

| 値 | 許す形 |
| --- | --- |
| moduleID（server / client） | `[A-Za-z0-9_][A-Za-z0-9_.-]*` |
| 仮想モジュールの接頭辞 | `[A-Za-z0-9_@./:-]+` |
| island 識別子の接頭辞 | `[A-Za-z0-9_@./:~-]*` |
| パッケージ名・object 名・ブリッジ・export の型・アノテーションのパッケージ | Scala の識別子（`[A-Za-z_][A-Za-z0-9_]*`）を `.` でつないだもの。各部が Scala 3 の予約語なら誤り |
| バナー・生成マーカー | 1 行（`\n`・`\r`・U+2028・U+2029 を含まない）。バナーは `//` で始める |

あわせて、文字列リテラルに入れる値（import 元のモジュール名、`@JSImport` の引数、island の識別子）は TS と Scala の両方で同じ値になるエスケープを通す。

既定値以外の appDir・moduleID・接頭辞は hx4s-vite と HonoX の側も合わせる必要があり、P0 では検証していない（[build-integration.md](build-integration.md) 2.0）。

既存の生成物を別のマーカーで出していたプロジェクトは、`hx4sBanner` と `hx4sGeneratedMarker` をその値にすれば、既存ファイルを置き換えられる
（マーカーの違うファイルは手書きとみなして上書きしないため）。

入口は `CodegenMain app ...`（route / island の TS）と `CodegenMain island-components ...`（SSR 用 Scala API）。

```
java -cp <hx4s-codegen の実行時クラスパス> hx4s.codegen.CodegenMain app --server-sources server/Main.scala --server-sources server/Api.scala --client-sources client/Main.scala
```
