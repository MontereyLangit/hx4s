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
