# Phase 4（最初の利用側の移行）で分かった不足と、Codex の指摘への対応

- 日付: 2026-11-16
- 出どころ: 移動元の掲示板アプリが hx4s を使う構成に乗り換えたとき（以下「利用側の移行」）と、`scripts/publish-local.mjs` への Codex CLI 0.163.0 のレビュー
- 対象: hx4s `develop`（`d133d7c` の次から）

## 1. 指摘と対応

| ID | 指摘 | 出どころ | 判断・対応 |
| --- | --- | --- | --- |
| F1-1 | `hx4s.hono.Context` に `executionCtx` が無く、利用側が `js.Dynamic` に落として読んでいる | 利用側の移行 | `hx4s.hono.ExecutionContext`（`waitUntil(promise)`・`passThroughOnException()` だけの `@js.native` trait）と `Context.executionCtx` を足した。型引数にはしない（既存の `Context[Env, R]` の書き換えが要り、基盤固有のメンバを使う場面が少ない。Cloudflare の型にも依存しない）。理由は [routing-design.md](../routing-design.md) 3 章 |
| F1-2 | Hono の `c.executionCtx` は実行コンテキストが無いと例外を投げる getter | 利用側の移行（hono 4.13.18 の `dist/context.js` で確認） | 拡張メソッド `executionCtxOption: Option[ExecutionContext]` を足した。`Either` にしなかったのは、失敗の理由が「渡されていない」の 1 つしかないため。Node の SSR 検証のように実行コンテキストを渡さない呼び出しでも同じハンドラが動く |
| F1-3 | Context / HonoRequest の基本 API の不足 | 利用側の移行 | 実物の型定義（`dist/types/context.d.ts`・`request.d.ts`）から、API だけのルートと SSR のルートで要る最小限として `Context.html`・`set` / `get`、`HonoRequest.queries`・`text()`・`json()`・`formData()` を足した。`set` / `get` は型付きで使う `ContextVariable[A]` と `setVariable` / `getVariable` も足した。`c.json` / `c.text` / `c.redirect` / `c.status` / `req.path` / `req.method` / `req.url` は既にあった。`parseBody`・`c.body`・`c.notFound`・ヘッダの一覧などは足していない |
| F1-4 | 足した API の検証 | — | `ContextSuite`（実物の `Hono` に Scala のハンドラを登録して `fetch` する）で全メンバを使う。examples/minimal は POST を `c.req.formData()` に、`api/items` の JSON ルートで `c.json` と `c.req.queries` を使う |
| F2 | `hx4s-vite/node-register` は hx4s の loader しか登録せず、実行基盤のスタブと組み合わせる方法が分からない | 利用側の移行 | [build-integration.md](../build-integration.md) 2.3 に、`register("hx4s-vite/node-loader", import.meta.url)` と利用側の loader を並べる register ファイルの例、登録順の意味、sbt の `Test / jsEnv` の例を書いた。`tests/node-loader.test.js` がその構成で `scalajs:client.js` と `cloudflare:workers` の両方を解決できることを確かめる。sbt-hx4s の設定での補助は P1 のまま（U-19） |
| F3-1 | clone 直後（`node_modules` 無し）に `publish-local` が失敗する | 利用側の移行 | npm の公開の前に `npm ci` を実行する。`--reuse-node-modules` は、前回このスクリプトが `npm ci` したときの `package-lock.json` の SHA-256（`node_modules/.hx4s-npm-ci.json`）と一致するときだけ省く |
| F3-2 | `--skip-sbt` / `--skip-npm` でも最後に `hx4s-version.txt` を書くので、部分公開が全体の公開に見える | Codex | sbt と npm の記録（`hx4s-publish-sbt.json` / `hx4s-publish-npm.json`）を分け、両方が同じ版・commit・作業ツリーの状態で成果物がそろうときだけ完了の記録 `hx4s-publish.json` と `hx4s-version.txt` を書く。公開の開始時に先に消すので、失敗や部分公開の後に古い完了の印が残らない |
| F3-3 | 機械可読の公開記録が無い | Codex | `hx4s-publish.json`（`format`、版、commit、`dirty` / `dirtyHash`、Maven の成果物（organization・name・版・`~/.ivy2/local` からのパス）、tgz のファイル名と SHA-256。時刻なし）。`hx4s-version.txt` は `project/plugins.sbt` が JSON を読めないので残した |
| F3-4 | Windows でパスに空白や `&` があると、`shell: true` の起動が壊れる | Codex | `scripts/lib/command.mjs` に起動を集めた。npm は `process.execPath` で npm-cli.js を直接、sbt は POSIX では直接、Windows では sbt.bat を絶対パスにして `cmd.exe /d /s /c` に引用済みの 1 行で渡す（`" % ! ^` と改行は拒否）。sbt.bat を引用した名前で PATH から探させると、バッチ内の `%~dp0` が作業ディレクトリになって起動できなかったので絶対パスにした。`tests/scripts-command.test.js` で、空白と `&` を含むディレクトリから npm を起動し、同じ名前のディレクトリに置いたバッチへ空白・`&`・`|`・末尾の `\` を含む引数を渡す |
| F3-5 | `setup-example.mjs` と `check-independence.mjs` も同じ観点で見る | Codex | `setup-example` は同じ起動方法にし、入れる前に `--verify` で公開記録を照合する。`check-independence` は元から `execFileSync`（シェルなし）なので起動は変えず、`git ls-files -z` で読むようにした |
| F3-6 | 利用側が再公開を省いてよいかを判断する手段が無い | Codex | `--verify [--version V] [--commit SHA]`。完了の記録・部分の記録の一致、期待値、HEAD と作業ツリー、`~/.ivy2/local` の `ivy.xml`、tgz の SHA-256、`hx4s-version.txt` を照らし、不一致なら理由を出して終了コード 1 |
| F4 | `--no-save` の tgz が利用側の `npm ci` で消えること、版と commit の固定方法が書かれていない | 利用側の移行 | [local-dependency.md](../local-dependency.md) に、注意点と、lock ファイル（commit の完全な SHA と版）・セットアップスクリプト・`plugins.sbt` の例を書いた。README からリンクし、ADR 0006 に追記した |

## 2. 公開 API の変更

- hx4s-react: `hx4s.hono.ExecutionContext`、`hx4s.hono.ContextVariable[A]`、`Context.executionCtx`・`html`・`set`・`get`、
  拡張メソッド `executionCtxOption`・`setVariable`・`getVariable`、`HonoRequest.queries`・`text()`・`json()`・`formData()`。既存のメンバは変えていない。
- `scripts/publish-local.mjs`: フラグ `--reuse-node-modules`・`--verify`・`--commit`（`--verify` と一緒にだけ使う）。
  出力 `target/local/hx4s-publish.json`・`hx4s-publish-sbt.json`・`hx4s-publish-npm.json`。`hx4s-version.txt` は完了したときだけ書く。

## 3. 残すこと

- `--verify` は `~/.ivy2/local` 以外の ivy の場所を扱わない。
- 成果物の照合は `ivy.xml` の有無までで、JAR の内容のハッシュは記録しない。
- sbt-hx4s の設定で `Test / jsEnv` を組む補助（U-19）は P1 のまま。

## 4. 利用者向けの文書を書く過程で見つかった不整合（2026-11-16）

利用者向けの文書（`docs/`）のコード例を実物で確かめる過程で、実装側の不整合が見つかった。各項目は再現してから直した。

| ID | 不整合 | 再現 | 対応 |
| --- | --- | --- | --- |
| G1 | `hx4s()` は server モードで常に client の sbt プロジェクトをリンクするので、client の無い構成（API だけ・island の無い SSR）で `Not a valid key: client` になる。sbt-hx4s は client なしを扱えるのに、Vite 側だけが対応していない | client の無い sbt ビルドで `vite build` | `hx4s({ client: false })` を足した。client のリンク・`scalajs:client.js` の別名・client ビルドを省き、`honox()` に React の設定も足さない（island が無いので要らない。`reactApiImportSource: "react"` を渡しても、React を入れない構成でビルドが通ることも確かめた。honox の island 用のコンポーネントが読み込まれないため）。`mode: "client"` との組み合わせはエラー。既定は従来どおり client あり。最小の例 `examples/api-only`（React・react-dom・`@hono/react-renderer` に依存しない。Worker を Node で呼ぶテスト、CI と独立性検査の対象）を足した |
| G2 | codegen の失敗で、同じ誤りの行が sbt の出力に 3 回出る | scripted の `error`。fork した JVM の stderr、`MessageOnlyException` のメッセージ（sbt がタスクの失敗で 1 回、最後の `(server / hx4sGenerate) …` で 1 回出す） | 誤りの行は記録だけしてタスクのログに 1 回出し、fork した JVM の `Exception in thread "main" …CodegenFailure` の行は捨てる。例外は `FeedbackProvidedException` にして、sbt が最後の行に要約（`hx4s-codegen app failed: 2 error(s)`）だけを出すようにした。`getMessage` は要約と誤りの行を持つ。scripted の `error` がタスクのログ（`target/streams/…/out`）で行が重複しないことを確かめる |
| G3 | `Context` に `c.body` と `c.notFound` が無く、本文なしの 204 を素直に書けない。`c.text("", 204)` は 500 になる | hono 4.13.18 の `dist/context.js`: `c.text` は空文字列でも本文として `new Response(text, { status })` を作り、Fetch API の `Response` は 204 / 304 に本文（`null` 以外）を許さないので `TypeError` になり、Hono の既定のエラー処理が 500 を返す | `body(data: BodyInit \| Null, status, headers)` と `notFound(): Response \| js.Promise[Response]` を足した。`ContextSuite` で 204 / 304 / 本文とヘッダ / `c.text("", 204)` の 500 / `notFound` を確かめる。`examples/api-only` の DELETE と GET で使う |
| G4 | `vite/package.json` の `version` が `0.1.0-SNAPSHOT` 固定で、sbt の版と手で合わせていた | — | 版の定義を `version.txt`（`X.Y.Z`）の 1 か所にした。sbt の既定は `<版>-SNAPSHOT`、ローカル公開は `<版>-local.g<commit>` をそこから作る（`scripts/lib/version.mjs`）。npm には `-SNAPSHOT` を使わない（semver の書式としては通るが、npm に書き換わる開発版の意味は無く、`0.1.0` より前の版として並ぶだけ）ので、`vite/package.json` は `version.txt` と同じ版にし、`tests/version.test.js` が `package.json`・`package-lock.json`・`build.sbt` の食い違いを検出する |
| G5 | codegen が誤りを段階的に報告する。個別の宣言に誤りがあると、export 名の重複や URL の衝突の検査まで進まない | `HEAD` の route と、同じ URL になる 2 つの route を同じ実行で | 宣言ごとの検査を通ったエントリ（と読めたソース）だけで重複・衝突を確かめ、全部の誤りを 1 回で返す。誤った宣言は衝突の検査から外すので、それが原因の二次的な誤りは出さない（直したあとで初めて出る衝突はありうる）。`AnnotationScannerSuite` に 2 件足した |

### 4.1 直さずに記録したもの

| 事項 | 内容 | 扱い |
| --- | --- | --- |
| `IslandExport` の `fields` と props の trait の食い違い | `IslandExport("X", fields)` の `fields`（`JsField` の一覧）と props の trait を別々に書くので、キーの名前・型・省略の可否が食い違ってもコンパイルで見つからない。codegen の照合は `@islands[P]` と `IslandExport[T]` の型名が構文上一致するかだけ | P1。trait から `fields` を導出する案。Scala 3 の `Mirror` は JS の trait に使えないので、マクロか codegen になる（[roadmap.md](../roadmap.md) U-27） |
| ビルドの sbt 起動回数と JDK 25 の警告 | `npm run build` は sbt を 4 回起動する。JDK 25 では起動のたびに `sun.misc.Unsafe` の警告が出る（sbt の起動の部品が呼ぶ。動作への影響は見ていない） | 未対応（U-15、U-28） |
| 要素 props・入れ子 island の例 | `IslandComponents` / `IslandExport` で要素 props や入れ子の island を書く例が無い。`JsKind` には React の要素や `null` を表す種類が無い | 未対応（U-29） |
| getting-started の新規解決 | 文書の `package.json` から lockfile なしで `npm install` する手順を実行していなかった | **確かめた**: 下の 4.2 |

### 4.2 getting-started の手順の確認

`docs/getting-started.md` の「自分のプロジェクトを作る」だけを見て、作業用のディレクトリ（examples の複製ではない）に同じファイルを作り、次を通した（2026-11-16、Windows 11、Node 22.18.0、JDK 25.0.1、hx4s `0.1.0-local.g933e0f7e1b`）。

1. lockfile なしで `npm install`（138 パッケージを新規に解決）
2. `npm install --no-save <hx4s>/target/local/hx4s-vite.tgz`
3. `npm run build`（`app/routes/index.ts` と `app/islands/counter.tsx` を生成し、client と Worker をビルド）と `npm run typecheck`
4. `wrangler dev` で起動し、`/` が `<h1>Hello hx4s</h1>` と island（`clicks: 0` のボタン）を持つ HTML を返し、`/static/client.js` が 200 になること

文書と違うところは無かったので、getting-started は変えていない。ブラウザでのクリックの確認はこのときは行っていない（examples/minimal の E2E が同じ構成を確かめる）。

### 4.3 公開 API の変更

- hx4s-react: `Context.body(data, status, headers)`・`Context.notFound()` を足した。既存のメンバは変えていない。
- hx4s-vite: `Hx4sOptions.client` が `Hx4sClientOptions | false` になった（`Hx4sClientOptions` を export する）。既存の設定（`client` を省くか、オブジェクトを渡す）の挙動は変わらない。
- sbt-hx4s: codegen の失敗の例外が `MessageOnlyException` から、要約を `toString` に持つ `FeedbackProvidedException` になった。`getMessage` は従来どおり誤りの行を含むが、先頭の行が `hx4s-codegen <command> failed: N error(s)` に変わった。
- 版: `version.txt` が版の定義。`vite/package.json` の `version` は `0.1.0`。
