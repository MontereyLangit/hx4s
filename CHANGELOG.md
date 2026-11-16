# Changelog

このファイルは [Keep a Changelog](https://keepachangelog.com/ja/1.1.0/) の形式で、版ごとの変更を記録する。
版の付け方は [SemVer](https://semver.org/lang/ja/)（`0.x` の間は、マイナー版で互換性が変わりうる）。公開の状態は [docs/release.md](docs/release.md)。

## [Unreleased]

0.1.0 の開発中。Maven Central・npm には未公開で、ローカル公開（`scripts/publish-local.mjs`）だけで使える。

### Added

- **hx4s-core**: アノテーション `@route(filePath, method)`・`@islands[P](filePath)`（JVM と Scala.js）。JS 側に props の検証 `JsShape`（`JsKind`・`JsField`、`validate` / `read`）
- **hx4s-react**:
  - HonoX の island 境界を付ける `hx4s.react.Island`
  - client の export を props の型 `P` に結び付ける `IslandExport[P]`（`apply` は props を検証して描画、`unchecked` は検証しない）
  - Hono の Context の facade `hx4s.hono.Context[Env, R]`・`HonoRequest`（Request / Response は scalajs-dom の型）
  - `executionCtx`（`ExecutionContext`）と拡張メソッド `executionCtxOption`、`html`、`set` / `get` と型付きの `ContextVariable[A]`（`setVariable` / `getVariable`）、
    `HonoRequest.queries`・`text()`・`json()`・`formData()`
  - 本文をそのまま返す `body(data, status, headers)`（`null` 本文で 204 / 304 を返せる）と `notFound()`
- **hx4s-codegen**:
  - scalameta で `@route` / `@islands` を読み、`app/routes/**/*.ts`・`app/islands/**/*.tsx` と SSR 用の `IslandComponents.scala` を生成する
  - 設定を `--key value` で受ける `CodegenMain`（`app` / `island-components`）
  - 検査: `HEAD` と HonoX が拾わないファイル名の拒否、別のファイルが同じ URL・同じメソッドになる route の拒否、island のファイル名の規則、`@islands[P]` と export の型 `IslandExport[T]` の照合。server と client の誤りはまとめて報告する
  - 出力の安全策: 手書きファイルを上書き・削除しない、全検査のあとに書く、同じ内容は書かない、一時ファイル経由の置き換え、リンクを辿らない、大文字小文字だけ違うパスを拒否する
  - 生成コードの識別子の衝突回避（import の別名）と、生成ソースへ埋め込む設定値の検証
- **sbt-hx4s**: `Hx4sPlugin`。設定キー（`hx4sClientProject` ほか）、`hx4sGenerate`、`Compile / sourceGenerators` による `IslandComponents` の生成（入力が変わらなければ再生成しない）、
  `hx4sCheckDependencies`（client と server の依存の向きの検査）、client なし（SSR だけ）の構成。codegen は専用 configuration で解決して fork した JVM で動かす
- **hx4s-vite**: `hx4s()` Vite プラグイン（server / client の Scala.js プロジェクトの登録と `buildStart` の直列化、`scalajs:client.js` の別名、honox の React 設定、client ビルドの出力名）、
  hydrate 前の props の検証 `hx4s-vite/client` の `islandPropsProblem`（template 内の island を含む）、Node 用 loader `hx4s-vite/node-loader`・`node-register`
- **scripts**: `publish-local.mjs`（ローカル公開、公開記録、`--verify`）、`setup-example.mjs`、`check-independence.mjs`
- **examples/minimal**: SSR + Islands の最小アプリ（動的ルート、POST、JSON の API、island 2 種）。Cloudflare Workers で動く
- CI（Ubuntu と Windows。sbt・npm・examples の検証）
- 文書: 設計と経緯（`docs/hx4s/`）と、利用者向けのガイド（`docs/`）、貢献ガイド
- ライセンス: Apache-2.0
