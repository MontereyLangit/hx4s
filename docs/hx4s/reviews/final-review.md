# 最終レビュー（Codex・IntelliJ inspect）の指摘と対応

- 日付: 2026-11-16
- 対象: hx4s `develop` の `d133d7c..95d77c2`（Phase 4 の利用側の移行、利用者向け文書、`body` / `notFound`、`client: false`、版の定義の 1 か所化）
- 環境: Windows 11、JDK 25.0.1、sbt 1.13.0、Node.js 22.18.0、npm 10.9.3

## 1. 手段

| 手段 | 内容 |
| --- | --- |
| Codex CLI 0.163.0 | 上の範囲の差分を読み取りだけでレビュー（ファイル変更・ビルド・テストはしていない）。重大度「高」の指摘は無し。「中」4 件、「低」3 件 |
| IntelliJ IDEA 2026.1 の CLI inspect | プロファイル `scala`。前回の結果を引き継がないよう、新しい system ディレクトリで実行した |

各指摘は実物（hono 4.13.18 の `dist/request.js`、`~/.ivy2/local` の成果物、TypeScript の型検査）で確かめてから直した。事実と違う指摘は無かった。

## 2. Codex の指摘

| # | 重大度 | 指摘 | 判断・対応 |
| --- | --- | --- | --- |
| Q1 | 中 | ルートの `package.json` / `package-lock.json` が公開対象のソースに無く、npm のビルドの入力を変えても `--verify` が通る | 事実。公開対象のソースに 2 つを足した（`dirtyHash` と既定の版の `.dirty-…` に効く）。lockfile だけ・`package.json` だけを変えると照合が失敗するテストを足した |
| Q2 | 中 | ivy は `ivy.xml` の存在しか見ず、JAR の削除・置き換えを検出できない | 事実。`ivy.xml` と、その `publications` が指す実体（JAR・sources・javadoc・pom）の SHA-256 を公開時に `files` として記録し、`--verify` で存在と内容を照らす。JAR・sources・pom・`ivy.xml` の削除と置き換え、tgz の置き換えの負のテストを足した |
| Q3 | 中 | 完了の記録から `maven` / `npm` が欠けても照合が通りうる | 事実（`record.maven ?? []`、`npm` が無ければ照合を省いていた）。`format: 1` の項目をちょうど持つこと、`maven` の名前の集合が 1 か所の一覧 `mavenNames` と同じこと（`build.sbt` の aggregate との対応はテストが見る）、各要素の項目・版・path・`files` の形、`npm` の名前・ファイル・ハッシュの形、部分の記録の成果物の情報との一致を確かめる。欠落・空配列・余分な項目・重複・集合の食い違いの負のテストを足した |
| Q4 | 中 | `docs/routing.md` の変数ミドルウェアの例が型検査を通らない | 事実（`c.set` のキーが `never` になる TS2769 を確かめた）。Hono の `ContextVariableMap` への宣言の例を足し、文書と同じ 2 ファイルを `tests/docs-typecheck` に置いて `npm run typecheck` の対象にした |
| Q5 | 低 | routing / migration に `body`・`notFound` が無い、client なしを扱えないという古い説明 | 事実。`body(null, 204)`・`notFound()`・`client: false` の説明に替え、routing.md の `Context` のメンバ表と routing-design.md の表に足した。`grep` で他の文書（README・CHANGELOG・build-configuration・examples・testing）も見たが、同種の古い記述は無かった |
| Q6 | 低 | 「本文は 1 種類だけ読める」が実物より厳しい | 事実。hono 4.13.18 の `HonoRequest` は最初に読んだ形を `bodyCache` に持ち、別の形はそこから作り直す。`ContextSuite` で、`text()` → `json()`、`json()` → `text()` → `json()`、`text()` → `formData()` が読めること、`formData()` の後の `text()` は multipart に組み直した文字列になること、`raw` を直接読むと `c.req` から読めず、`c.req` で読んだ後の `raw` も読めないことを確かめ、`Context.scala` の Scaladoc・api.md・routing.md を直した |
| Q7 | 低 | 貢献ガイドの example の手順が fresh clone で失敗する | 事実（テストが読む `dist` を作っていない、Chromium の導入が無い）。`npx playwright install chromium`・`npm run build`・`npm run typecheck` を足し、api-only も並べて CI と同じ順にした |

Q1〜Q3 は同じ記録の形と照合の処理（`scripts/lib/publish-record.mjs` に分けた）に関わるので、1 つのコミットにした。

完了の記録は `format: 1` のまま `maven[].files` を足した。0.1.0 は未公開で、記録を読むのは `--verify` だけなので、前の形の記録は「`files` が無い」で照合に失敗し、利用側のセットアップが公開し直す。

## 3. inspect の指摘

採用したもの（Q8）:

| 指摘 | 場所 | 対応 |
| --- | --- | --- |
| 文字列の連結 2 件 | `sbt-plugin/.../DependencyCheck.scala` | 1 つの補間文字列にした |
| 無名関数をメソッド値にできる | `Hx4sPlugin.scala` の `map(file(_))` | `map(file)`（Scala 2.12 の eta 展開で通る） |
| 空のパラメーター節 | `ContextSuite.scala` の `private class Hono()` | 外した（呼び出し側の `new Hono()` はそのまま書ける） |

採用しなかったもの:

| 指摘 | 件数 | 理由 |
| --- | --- | --- |
| SomeToOption | 39 | `Some(x)` を `Option(x)` にする提案（codegen の `OutputPath`・`JsShape.read`・テストなど）。値はリテラルや検査済みの値で null にならない。`Option(x)` は null を `None` にする、Java の値を受けるときの書き方で、意図が変わる |
| AbstractValueInTrait | 25 | 文書のコード例と island の props の JS trait（`extends js.Object`）の抽象 `val`。Scala.js の JS trait では props のフィールドを `val` で宣言するのが決まった書き方で、`def` にすると JS のメソッドの意味になる |
| ScalaWeakerAccess | 6 | `CodegenConfig` の既定値・`Codegen.appOutputs`・sbt の `autoImport` の configuration は利用側に見せる API、`getting-started.md` の 1 件は文書の例。`DependencyCheck.compileScoped` は `private[sbt]` の object の中にあり、狭めても公開範囲は変わらない |
| scala2InSource3 | 4 | `Hx4sPlugin.scala` の `import sbt._` などの `_`。sbt-hx4s は `-Xsource:3` を付けない Scala 2.12 でコンパイルするので、`*` には替えない |
| ScalaUnusedSymbol | 1 | `autoImport.hx4sVersion`。利用側の `build.sbt` が hx4s-core / hx4s-react の版に使う値で、このリポジトリの中には使う箇所が無い |
| SbtDependencyVersion | 1 | `project/plugins.sbt` の sbt-scalajs に新しい版がある、という通知。依存の版は固定し、上げるときは互換性の検証と一緒に行う（[../compatibility.md](../compatibility.md)） |
| 脆弱な推移的依存 | 4 | log4j-api 2.25.4、jline-reader / jline-builtins 3.27.1、jansi 2.4.1。sbt-hx4s のコンパイルの classpath に sbt 1.13.0 本体が持ち込むもので、hx4s の公開物には入らない。sbt の版を上げるときに見直す |

## 4. 修正後の検証

コードの最後の commit `5a7cf07`（以降の commit は文書だけ）で、`node_modules`・各 `target`・`vite/dist`・examples の `node_modules`・`dist`・`target`（`project/target`・`project/project` を含む）を消してから、CONTRIBUTING の順に実行した。

| コマンド | 結果 |
| --- | --- |
| `npm ci` | 成功 |
| `sbt test` | 成功。coreJVM 3 件、coreJS 9 件、codegen 57 件（シンボリックリンクの 3 件は作成権限が無く skip）、react 13 件（`ContextSuite` の本文の読み直し 1 件を足した） |
| `sbt sbtHx4s/scripted` | 成功（3 件） |
| `sbt 'set ThisBuild / scalacOptions += "-no-indent"' Test/compile` | 成功 |
| `npm test`（ルート） | 34 件すべて成功（`publish-record.test.js` の 9 件を含む） |
| `npm run typecheck` | 成功（hx4s-vite と `tests/docs-typecheck`） |
| `node scripts/check-independence.mjs` | 成功 |
| `node scripts/publish-local.mjs` → `--verify --commit <HEAD>` | 成功。`0.1.0-local.g5a7cf07d4b`、5 成果物とも `files` は 5 件（`ivy.xml`・jar・sources・javadoc・pom） |
| `--verify` の負のケース（実物の `~/.ivy2/local` と記録で。確認後に戻した） | lockfile に 1 文字足す・`hx4s-core_3.jar` の削除・置き換え・sources の削除・完了の記録の `npm` の削除・`maven: []`・余分な項目・`maven` から 1 件除く、のすべてで理由を出して終了コード 1。戻すと通る |
| examples/minimal: setup → `npx playwright install chromium` → build → typecheck → test | 成功。sbt test 4 件、Node 7 件（Playwright の E2E を含む）。生成は `wrote 0, unchanged 5` |
| examples/api-only: setup → build → typecheck → test | 成功。sbt test 1 件、Node 4 件。生成は `wrote 0, unchanged 2` |
| 文書の相対リンク | 切れ無し |
| 作業後の `git status --porcelain --untracked-files=all` | 空 |

examples/minimal の E2E は、最初の実行で「Worker のコードは静的配信しない」（`/index.js` が 404 でなく 200）で失敗した。既定のポート 8791 を別の作業ツリーの `wrangler dev`（workerd）が使っていて、
テストがその既に動いているサーバーに接続したためで、`HX4S_E2E_PORT=8795` で再実行すると通った。テストは起動したサーバーが自分の子プロセスかを確かめないので、ポートが使用中なら失敗の理由が分かりにくい（残課題）。

## 5. 残課題

- examples/minimal の E2E は、ポートが使用中のとき別のサーバーに接続してしまう。起動前にポートの空きを確かめるか、空いているポートを選ぶようにする
- 完了の記録の `format` は 1 のまま形を変えた。0.1.0 の公開（Phase 5）までに、記録を読む側が増えるなら版を上げる
