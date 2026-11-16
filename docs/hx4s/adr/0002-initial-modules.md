# ADR 0002: 初期モジュール構成

- 状態: 承認（2026-11-16、Phase 0）
- 関連: [architecture.md](../architecture.md) 3.2、[module-boundaries.md](../module-boundaries.md)

## 背景

3ch の連携基盤は `common`（アノテーションとテンプレート）、`codegen`（生成器）、`bbs`（Island ブリッジと Hono facade）、`vite.config.ts`（ビルド設定）に散らばっている。
切り出す単位を決める必要がある。細かく分けると、P0 の段階で版の組み合わせと公開物が増え、利用者の設定も増える。

## 決定

| モジュール | 種別 | 内容 |
| --- | --- | --- |
| `hx4s-core` | crossProject（Pure, JVM + JS） | `@route` / `@islands[P]` など Renderer に依存しない宣言 |
| `hx4s-react` | Scala.js | HonoX Island ブリッジ（`React.createElement(HonoXIsland, …)`）、汎用 Hono Context facade（Env は型引数）、型付き Props の補助 |
| `hx4s-codegen` | JVM（Scala 3、scalameta） | アノテーション解析、route / island の TS(X) と Scala の `IslandComponents` 生成、安全な書き出し。設定は case class、CLI main も持つ |
| `sbt-hx4s` | sbt plugin（Scala 2.12） | 設定キーと `hx4sGenerate`。codegen は専用 configuration で解決し、別クラスローダか fork で実行する |
| `hx4s-vite` | npm | `@scala-js/vite-plugin-scalajs` を包み、server / client の登録・`scalajs:client.js` alias・honox の React 設定・client ビルド設定をまとめる |
| `examples/minimal` | 例 | 3ch に依存しない SSR + Islands の最小アプリ。独立性の必須テスト |

honox 連携と react 連携は当面 `hx4s-react` 1 つにまとめる。Renderer を追加する（例: hono/jsx を Scala から使う）ときに `hx4s-honox` と `hx4s-react` に分ける。
`hx4s-routing`（型付き URL・Client Router）、CLI、templates は P1 とする。

## 却下案

| 案 | 理由 |
| --- | --- |
| 1 つの成果物にまとめる | JVM の codegen と Scala.js の実行時コードは同じ成果物にできない。sbt plugin も別にせざるを得ない |
| `hx4s-honox`（ブリッジ）と `hx4s-react`（React 補助）を最初から分ける | 現時点で Renderer は React だけで、両者は常に一緒に使う。分けても利用者の依存行が増えるだけ |
| アノテーションを `hx4s-react` に入れ、`hx4s-core` を作らない | アノテーションは JVM（codegen のテスト、将来の JVM サーバー）からも参照しうる。Scala.js 専用モジュールに入れると JVM から使えない |
| codegen を sbt plugin の中に入れる | sbt 1.x は Scala 2.12。Scala 3 の scalameta 利用コードと、利用側の Scala 3 構文の解析を 2.12 で書き直すことになる。CLI として単独でも使えなくなる |
| `hx4s-cloudflare` を P0 に含める | Core を実行基盤から独立させる方針（[ADR 0005](0005-core-independent-of-cloudflare.md)）と、検証に Cloudflare 環境が要ることから P2 に回す |

## 影響

- 利用側の Scala.js プロジェクトは `hx4s-react`（推移的に `hx4s-core`）に依存し、ビルドに `sbt-hx4s` と `hx4s-vite` を入れる。
- `hx4s-react` は scalajs-react の `core` に依存する。scalajs-react を使わない利用者は想定しない（P0）。
- モジュールの追加・分割は、利用者の依存行を変えるのでマイナー版で行う。
