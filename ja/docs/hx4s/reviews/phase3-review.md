# Phase 3（hx4s リポジトリへの分離）の検証とセルフレビュー

- 日付: 2026-11-16
- 対象: hx4s `develop` の Phase 3 のコミット（core / codegen / react / vite / sbt-hx4s / publish-local / examples/minimal / CI / 文書）
- 環境: Windows 11、JDK 25.0.1、sbt 1.13.0、Node.js 22.18.0、npm 10.9.3
- 移動元の掲示板プロジェクトは読み取りだけで、書き込み・ビルドはしていない

## 1. 実行した検証

| コマンド | 結果 |
| --- | --- |
| `sbt test` | 成功。coreJVM 3 件、coreJS 9 件、codegen 44 件（シンボリックリンクの 2 件は作成権限が無く skip。ジャンクションの 1 件は通過）、react 7 件 |
| `sbt sbtHx4s/scripted` | 成功（`hx4s/generate`、`hx4s/error`） |
| `sbt 'set ThisBuild / scalacOptions += "-no-indent"' Test/compile` | 成功（sbt-hx4s は Scala 2.12 なので `scalacOptions :=` で対象外にしている） |
| `npm test`（ルート） | 成功。hx4s-vite のプラグイン設定 4 件、HonoX の SSR 包み 1 件、hydration 3 件（単純・入れ子・要素 props / 危険な文字列と複数インスタンス / Scala.js の client island と世代ずれ）、独立性 1 件 |
| `npm run typecheck`（hx4s-vite） | 成功 |
| `node scripts/publish-local.mjs` | 成功（sbt 5 成果物を `~/.ivy2/local`、`target/local/hx4s-vite.tgz`） |
| examples/minimal: `npm install` → `npm run build` → `npm run typecheck` → `npm test` | 成功。sbt test 3 件（型付き props のコンパイル検査を含む）、Node 6 件（SSR・動的ルートと 404・POST の検証・client のバンドル・jsdom の hydration と世代ずれ・Playwright + Chromium の E2E） |

### examples/minimal のバンドルサイズ（`vite build` の表示）

| 成果物 | サイズ | gzip |
| --- | --- | --- |
| `dist/server/index.js`（Worker） | 686.34 kB | 200.52 kB |
| `dist/client/static/client.js`（client の入口） | 209.01 kB | 67.61 kB |
| `dist/client/static/assets/client-*.js`（Scala.js の client のリンク成果物） | 178.24 kB | 50.39 kB |
| `dist/client/static/assets/counter-*.js` / `item-list-*.js`（island の chunk） | 0.07 kB ずつ | 0.08 kB |
| `dist/client/static/assets/runtime-*.js`（HonoX の template 処理） | 1.99 kB | 1.00 kB |

island の chunk は生成 TSX の再 export だけで、実体は共有の `client-*.js` に入る（Scala.js の client は 1 モジュールでリンクされるため）。

## 2. 途中で見つけて直したこと

| 内容 | 対応 |
| --- | --- |
| route の誤りがあると island の誤りが報告されない（`for` で短絡していた） | server と client の誤りをまとめて返すようにし、テストを足した |
| sbt から渡すソースのパスが Windows で `\` 区切りになり、誤りの表示が OS で変わる | `/` 区切りにそろえた |
| ビルドした Worker が workerd で `node:async_hooks` を解決できず起動しない（Node 上のテストでは見えず、E2E で発見） | examples の `wrangler.jsonc` に `nodejs_compat` を足した |
| ビルドした Worker を Node で import すると、React の scheduler の MessagePort でプロセスが終わらない | examples の `node --test` に `--test-force-exit` を付けた |
| `npm pack` の成果物に NOTICE が入らない | ステージの `package.json` の `files` に足した |

## 3. セルフレビュー（作業規約の観点）

| 観点 | 結果 |
| --- | --- |
| 移動元の規約との整合 | 波括弧を使い Optional Braces を使っていない（`-no-indent` で確認）。Scaladoc は 1〜2 行。業務上の拒否は `Either` / `CodegenError` で返し、sbt の境界でだけ例外にする |
| 言語の選択 | 生成・検証のロジックは Scala。TS は Vite の配線（hx4s-vite）、Node の loader、`app/*.ts` の雛形、テストに限る |
| FP / 可変状態 | codegen は純粋な計画と I/O を分けたまま。sbt-hx4s の `RecordingLogger` の可変バッファは fork の出力を集める境界の実装に限る |
| scalajs-react | コンポーネントは `ScalaFnComponent` と Hooks。コンポーネントを持つ値は `final val` |
| 依存 | 追加したのは scalajs-dom（Fetch の型、scalajs-react の推移的依存と同じ版）と、examples の `@hono/react-renderer`・wrangler・Playwright・jsdom だけ。Effect System・DI・状態管理は入れていない |
| Cloudflare への依存 | hx4s のモジュールには無い。examples の `vite.config.ts`・`wrangler.jsonc`・E2E の起動だけ |
| テストの網羅 | 上の 1 章。網羅していないものは 4 章 |

## 4. 既知の問題・未実装

- `@route` の URL パターンの衝突検出と、手書きルートとの衝突の警告（[routing-design.md](../routing-design.md) 2.2）は移動元でも未実装で、hx4s にも無い。
- 依存方向（client → server の禁止）は examples のプロジェクト定義とバンドルの文字列検査で見ているだけで、sbt の依存グラフ・Vite のモジュールグラフの自動検査は無い。
- `filePathToPath` の照合、非有限の `Double`・循環参照の挙動、`honox@latest` の定期実行（[compatibility.md](../compatibility.md) 3 章）は未実装。
- シンボリックリンクの負のテストは Windows の手元では権限が無く skip（ジャンクションは通過）。Linux の CI で走る想定だが、CI はリモートで未実行。
- examples/minimal は `package-lock.json` をコミットしない（ローカルの tgz の整合性ハッシュが公開のたびに変わるため）。推移的依存の版は固定されない。
- examples の `npm run build` は sbt を 4 回起動する（生成で 1 回、client ビルドで 1 回、server ビルドで server と client のリンクを直列に 2 回）。所要時間は 1 回あたり 15〜30 秒（U-15）。
- `npm install` が npm audit の moderate を報告する（ワークスペース 9 件）。内容は確認していない。

4 章の項目のうち、URL パターンの衝突検出・sbt の依存グラフの検査・`filePathToPath` の照合・examples の lockfile・npm audit の確認は 5 章の修正で対応した（4 章は当時の記録として残す）。

## 5. Phase 3 のレビューと対応（2026-11-16）

- 対象: `develop` の `a58e5d7`（Phase 3 の最後のコミット）
- 修正: `5b78cbf`〜`c806b18` と、この記録のコミット
- レビュー手段: セルフレビュー、Codex CLI 0.163.0（静的レビュー。根拠の行番号付き）、IntelliJ IDEA 2026.1 の CLI inspect（プロファイル scala）

### 5.1 Codex の指摘

すべて採用した。事実と違う指摘は無かった（各項目をコードで確かめた）。

| # | 重大度 | 指摘 | 判断・対応 |
| --- | --- | --- | --- |
| K1 | 高 | 出力ルートの祖先の symlink / junction を見逃す（`app` が junction で `app/routes` が通常のディレクトリなら通る） | 直した。`OutputWriter.write` に基準ディレクトリ（`projectRoot`）を渡し、そこより下の出力ルートの祖先（Scala の出力先の祖先を含む）もリンクなら拒否する。`projectRoot` 自体とその上、`projectRoot` の外の出力ルートの祖先は対象外として codegen.md に書いた（sbt の `target` をリンクにしている構成は誤りになる）。junction の負のテスト（Windows で通過）と symlink 版（権限が無く skip）を足した |
| K2 | 中 | 書き込み途中の失敗でマーカーを失い、再実行で収束しない | 直した。同じディレクトリの `.<名前>.hx4s-tmp` に書いてから `ATOMIC_MOVE`（不可なら `REPLACE_EXISTING`）で置き換える。一時ファイルの残骸は手書きとみなさず次の実行で消す。一時ファイルを作れない状態での失敗注入（既存ファイルが元の内容とマーカーのまま残り、障害を取り除いて再実行すると収束する）と、残骸の掃除のテストを足した。読み取り専用ファイルでの失敗注入は、置き換えが rename になり Linux では失敗しないので、この方式に替えた |
| K3 | 中 | export 名と生成 TS の識別子が衝突する（`createRoute`、`POST` など） | 直した。import に生成器専用の別名（`__hx4s_createRoute`、`__hx4s_handler_<名前>`、`__hx4s_island`）を付ける。export 名が `createRoute`・`POST`・`GET`・`default`・`class`・`__hx4s_createRoute` の生成 TS を、TypeScript の構文検査と Node での読み込み（スタブのモジュールに差し替えて各メソッドが正しいハンドラを呼ぶ）で確かめるテスト `tests/generated-ts.test.js` を足した。修正前は `c` という export 名でも `(c) => c(c)` になって壊れていた。`IslandComponents` のメソッド名は従来からバッククォートで囲んでおり、Scala の予約語（`match`）のテストがある |
| K4 | 中 | ソース一覧が空だと失敗し、古い出力を消せない | 直した。`--server-sources` / `--client-sources` は省けば空。`island-components` は server のソースを要求しない。sbt-hx4s は `hx4sClientProject` 未設定を「client なし」として扱う（エラーにしない）。最後の island を消すと古い TSX が消え空の Scala API になる scripted テストと、client なしの構成の scripted テストを足した |
| K5 | 中 | 増分判定が codegen の JAR の中身の変化を検出しない | 直した。`hx4sIslandComponents` のキャッシュの入力に、クラスパスの各 JAR（ディレクトリならその中のファイル）の内容のハッシュを含める。`javaHome` と `hx4sCodegenJavaOptions` もキーに含めると決めた（生成結果に影響しないことが多いが、含めても再生成が 1 回増えるだけ）。build-integration.md 2.2 に書いた |
| K6 | 中 | template 内の island は props が合わなくても外側の hydration を止められない | 直した。`hx4s-vite/client` の `islandPropsProblem(element)` が要素 props（template から復元する子）を辿って内側の `hx4sValidate` も呼ぶ。雛形の `app/client.ts` はこれを呼び、`createClient` のオプションは見せたまま。内側の island が不正なら外側も hydrate せず SSR の markup が残るテスト（jsdom、実際の Scala.js の island）を足し、保証範囲を rendering-model.md 2.4 に書いた。U-7 と ADR 0007 を更新した |
| K7 | 中 | server コードの client 混入を防ぐ依存グラフ検査が未実装 | sbt の依存グラフは直した。`hx4sCheckDependencies` が client（と推移的な `dependsOn`）から Hx4sPlugin を有効にしたプロジェクトへの経路、server から client への経路を探し、経路を出して失敗する（テストだけの依存は対象外）。scripted の `dependencies` を足した。Vite のモジュールグラフの検査は今回は実装せず、未実装として module-boundaries.md と roadmap.md に書いた |
| K8 | 中 | 同じ URL に変換される生成ルートを受理する | 直した。honox 0.1.62 の `filePathToPath` と、`createApp` がディレクトリごとの sub app を `app.route` で載せる規則（hono の `mergePath`）を `RoutePath` に写し、パラメータ名を除いた形とメソッドが同じなら `CodegenError` にする。変換表を `RoutePathSuite` と、`honox/server/server` の `createApp` に通す `tests/route-paths.test.js` で照合する。HonoX の内部依存 H11 に加えた。`(group)` は変換に入れたが出力パスの文字種のため生成されない。`[[optional]]` は HonoX に無いので扱わない。手書きルートとの衝突は警告も出さないまま（未実装と明記） |
| K9 | 中 | CI の生成物チェックが未追跡ファイルを見逃す | 直した。`git status --porcelain --untracked-files=all -- examples/minimal/app` が空であることを確かめる |
| K10 | 中 | example の CI が依存を固定していない | 直した。`examples/minimal/package-lock.json` をコミットし、package.json から hx4s-vite を外した。`scripts/setup-example.mjs` が `npm ci` の後に `target/local/hx4s-vite.tgz` を `npm install --no-save` で入れる（tgz の整合性ハッシュを lockfile から切り離す）。CI と README を合わせた |
| K11 | 中 | 設定文字列を生成ソースへ無加工で埋め込む | 直した。moduleID・仮想モジュール接頭辞・island ID 接頭辞を文字種で、パッケージ名・object 名・ブリッジ・export の型・アノテーションのパッケージを Scala の識別子と予約語で検証し、バナーとマーカーは U+2028 / U+2029 も行の区切りとして拒否する。文字列リテラルへ入れる値は TS / Scala 共通のエスケープを通す。引用符・改行・バックスラッシュ・`*/`・空白・U+2028 の負のテストを足した |
| K12 | 低 | ソースパスをカンマで再分割する | 直した。繰り返し指定だけにした。カンマと空白を含むパスのテストと、scripted の `generate` に `client app/.../Other, part.scala` を足した |
| K13 | 低 | ローカル公開の版が dirty の内容を識別しない | 直した。公開対象のソース（テストを除く）に追跡中の変更か未追跡のファイルがあれば、パスと内容のハッシュ 8 桁を `.dirty-<hash>` として版に付ける（semver の数字だけの識別子は先頭の 0 を許さないので 1 つの英数字の識別子にした）。ADR 0006 と README を合わせた |
| K14 | 低 | 古い ADR と実装が矛盾する（0005 の Node アダプタ、0002 の core Pure、0001 の旧マーカー自動受理） | 直した。各 ADR に「更新（2026-11-16）」として後継の判断を追記した（本文は残す）。K6 に伴い ADR 0006・0007 にも追記した |

Codex が問題なしとした点: sbt からの空白入りパスの引数は `Fork.java` に引数列で渡しておりクォートの不足は無い。props は HonoX / React の属性値エスケープを通り、直接の XSS 経路は無い。
examples の `wrangler.jsonc` は静的配信を `dist/client` に限り、Worker は `dist/server` に分かれている。core / codegen に Cloudflare のコンパイル依存は無い。
移動元の固有名は、設計・経緯の説明以外の実行コードに残っていない。

### 5.2 IntelliJ inspect

| 検査 | 件数 | 判断・対応 |
| --- | --- | --- |
| ComparingLength | 7 | 採用。`.sizeIs` にした（AnnotationScanner 5、CodegenMain 1、OutputWriter 1） |
| ConvertibleToMethodValue | 1 | 採用。`managed.exists(sibling.startsWith)` にした（同じ挙動） |
| MapGetOrElse | 1 | 採用。sbt-hx4s の `IO.relativize(...).fold(...)(...)` にした（同じ挙動） |
| LegacyStringFormatting | 6 | main の 3 件（AppFileRenderer 2、OutputWriter 1）と AnnotationScannerSuite の単純な 2 件（同じ形の 141・149 行）を補間にした。AnnotationScannerSuite 121 行はエスケープが増えて読みにくくなるので見送った |
| ScalaDocParserErrorInspection / ScalaDocUnknownTag | 1 / 1 | 採用。Hx4sPlugin の Scaladoc の `@route / @islands` をタグと解釈されない文面（「route / islands アノテーション」）にした |
| VulnerableLibrariesLocal | 2 | 採用。hono 4.13.11 → 4.13.18（ルートと examples/minimal）。hx4s-vite は hono を peerDependencies に持たない（honox の peer が `>=4.*`）ので範囲の変更は無い |
| SomeToOption | 29 | 不採用。`Some(...)` に渡す値はすべて null にならない（リテラル、生成した文字列、解析済みの値） |
| AbstractValueInTrait | 12 | 不採用。Scala.js の非ネイティブ JS trait（props の形）と native trait の設計どおり |
| ScalaWeakerAccess | 8 | 不採用。公開 API（`HttpMethod` の値、`Codegen.appOutputs`、`CodegenConfig.DefaultGeneratedMarker` / `DefaultBanner`、`hx4s.hono.Context` のメンバ、`Hx4sPlugin.autoImport` の configuration） |
| scala2InSource3 | 4 | 不採用。sbt-hx4s は Scala 2.12 で、`import sbt._` などは 2.12 の書き方 |
| ScalaUnusedSymbol | 1 | 不採用。`hx4sVersion` は利用者の build.sbt が使う値 |
| SbtDependencyVersionInspection | 1 | 不採用。sbt-scalajs は検証済みの 1.22.0 に固定する |
| VulnerableLibrariesGlobal | 4 | 不採用。log4j-api 2.25.4・jline-reader / jline-builtins 3.27.1・jansi 2.4.1 は sbt 1.13.0 が持ち込むもの。公開した sbt-hx4s の ivy.xml の依存は `sbt` 1.13.0（`provided`）と scripted 用だけで、hx4s の成果物の依存ではないことを確かめた |

npm audit（hono を上げた後）: ワークスペース、examples/minimal ともに moderate 8 件、high 以上は 0 件。すべて honox 0.1.62 の依存から来る
（`@hono/vite-dev-server` → `@hono/node-server` の serveStatic のパスの二重デコード GHSA-rmxm-3fg6-px4f、`precinct` → `detective-typescript` →
`@typescript-eslint/typescript-estree` → `minimatch` の ReDoS GHSA-g3hv-75wg-m249 など）。`npm audit fix --force` は honox を 0.1.54 へ下げる破壊的変更になるので行わず、上流待ちとした。
どちらも Vite の dev server とビルド時の依存解析で使われ、Worker と client のバンドルには入らない。

inspect の実行方法: hx4s のリポジトリには IDE が sbt から取り込んだ `.idea` が無いので、`.idea`（modules とライブラリ）を手で組み立ててから `idea64.exe inspect` を走らせた。
組み立てに使った scratchpad の `gen_idea.py` は、移動元の `.idea` の libraries をコピーして hx4s のモジュール構成を書くもので、移動元のチェックアウトに依存するため `scripts/` には入れなかった。
IntelliJ で hx4s を sbt プロジェクトとして一度開けば `.idea` ができるので、次回はそれを使う。

### 5.3 セルフレビュー

| # | 指摘 | 判断・対応 |
| --- | --- | --- |
| Z1 | sbt-hx4s が codegen の既定の生成マーカーを二重に持つ（`generateTask` の直書き） | 直した。codegen に `--output-list` を足し、書いたファイルと据え置いたファイルの一覧を書く。`hx4sGenerate` はそれを読んで返し、プラグインはマーカーを持たない |
| Z2 | hx4s-vite の client ビルドの出力名が固定 | 直した。`client.output`（`entryFileNames`・`chunkFileNames`・`assetFileNames`）で上書きできる。既定は従来どおり。entry のハッシュ名化は未決のまま（U-26）で、世代ずれは K6 の検証で検出する旨を build-integration.md と rendering-model.md に書いた |
| Z3 | extraction-plan.md の Phase 3 の完了条件「CI が通る」は push がユーザーの判断 | 直した。「ローカルで全検証が通る」と「リモート CI の確認は push 後」に分けた |

途中で気づいて直したこと: scripted の `$ copy-file` / `$ delete` は空白を含むパスを引用符で渡せない（`InvalidPathException`）ので、空白入りのパスの操作は build.sbt のタスクで行った。
`publish-local.mjs` の dirty 判定で `git status` の出力を trim すると先頭の空白（未ステージの変更の印）が消えて 1 文字目のパスが欠けるので、trim しない取り出しにした。

### 5.4 修正後の検証（`c806b18` で実行。この記録のコミットは文書だけ）

| コマンド | 結果 |
| --- | --- |
| `sbt test` | 成功。coreJVM 3 件、coreJS 9 件、codegen 55 件（52 件成功、シンボリックリンクの 3 件は作成権限が無く skip。ジャンクションの 2 件は通過）、react 7 件 |
| `sbt sbtHx4s/scripted` | 成功（`hx4s/dependencies`、`hx4s/error`、`hx4s/generate`） |
| `sbt 'set ThisBuild / scalacOptions += "-no-indent"' Test/compile` | 成功 |
| `npm test`（ルート） | 成功。16 件（生成 TS の識別子 2、独立性 1、hx4s-vite の設定 4、HonoX の SSR 包み 1、hydration 4〈template 内の island の検証を含む〉、URL 変換の照合 1 など） |
| `npm run typecheck`（hx4s-vite） | 成功 |
| `node scripts/publish-local.mjs` | 成功（版 `0.1.0-local.gc806b18238`。作業ツリーがクリーンなので dirty の印なし） |
| examples/minimal: node_modules・target・dist を消してから `node scripts/setup-example.mjs minimal` → `npx playwright install chromium` → `npm run build` → `npm run typecheck` → `npm test` | 成功。lockfile は変わらない。sbt test 3 件、Node 6 件（Playwright + Chromium の E2E を含む。skip なし） |
| `node scripts/check-independence.mjs` | 成功 |

examples/minimal のバンドルサイズ: `dist/server/index.js` 688.15 kB（gzip 201.20 kB）、`dist/client/static/client.js` 209.63 kB（gzip 67.77 kB。`hx4s-vite/client` の分で 0.62 kB 増えた）、
`client-*.js`（Scala.js の client）178.24 kB（gzip 50.39 kB）、island の chunk 0.07 kB ずつ、`runtime-*.js` 1.99 kB。

### 5.5 残る未実装

- Vite のモジュールグラフによる依存方向の検査（K7 の残り）。
- `app/routes` の手書きファイルとの URL の衝突の警告（U-23）。
- 非有限の `Double`・循環参照の挙動、`honox@latest` の定期実行（[compatibility.md](../compatibility.md) 3 章）。
- シンボリックリンクの負のテストは Windows の手元では skip。リモートの CI（Linux）は push の後に確かめる。
- npm audit の moderate 8 件は honox の依存の更新待ち。
