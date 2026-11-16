# hx4s

hx4s is a meta-framework for using [HonoX](https://github.com/honojs/honox) and React from Scala.js. It is not a re-implementation of HonoX: routing, middleware, renderers and island hydration stay in HonoX. (0.1.0 is in development and not yet published.)

hx4s は、Scala.js から HonoX と React を使うためのメタフレームワークです。**HonoX の再実装ではありません。**
ルーティング・ミドルウェア・renderer・island の探索と hydration は HonoX に任せ、hx4s は次の 2 つだけを受け持ちます。

- Scala の宣言（`@route`・`@islands[P]`）から、HonoX の規約どおりのファイルと SSR 用の Scala API を生成する
- Scala.js のビルドを Vite と HonoX につなぐ

## 現在の状態

0.1 を開発中で、**Maven Central にも npm にもまだ公開していません**。使うには、hx4s のリポジトリを clone してローカルに公開する必要があります
（[docs/installation.md](docs/installation.md)）。API と設定は 0.x の間に変わることがあります。

## 特徴

- **Scala で書く route**: `@route("items/[id].ts", "POST")` を付けた関数が HonoX のファイルルートになる。URL への対応は HonoX の規約のまま
- **型付き island**: `@islands[P]("counter.tsx")` の `P`（props の trait）を、server の呼び出し `IslandComponents.CounterJs(props)` と client の export の両方がコンパイル時に照合する
- **hydrate 前の props の検証**: HTML と client の世代がずれて props が合わないとき、hydrate せず SSR の HTML を残す
- **server と client の分離**: 依存の向きを sbt の依存グラフで検査する（`hx4sCheckDependencies`）
- **安全な生成**: 手書きファイルを上書きしない、全検査を済ませてから書く、リンクを辿らない
- 実行基盤に依存しない: Cloudflare Workers の例を同梱するが、hx4s のモジュールは Cloudflare に依存しない

## 最小の例

props は server と client が共有する trait で、client が受け取る値を確かめる規則を添える。

```scala
// shared
trait CounterProps extends js.Object {
  val label: String
  val start: Int
}

object CounterProps {
  val fields: List[JsField] = List(required("label", JsKind.Str), required("start", JsKind.Int))
}
```

client は island を `IslandExport[P]` として export し、`@islands[P]` で `app/islands` へ公開する。

```scala
// client
@islands[app.shared.CounterProps]("counter.tsx")
@JSExportTopLevel("CounterJs", "client")
val counter: IslandExport[CounterProps] = IslandExport("CounterJs", CounterProps.fields)(props => Counter(props))
```

server のハンドラは `@route` で HonoX の route になり、生成された `IslandComponents` から island を置く。

```scala
// server
@route("index.ts")
@JSExportTopLevel("indexPage", "server")
def indexPage(c: AppContext): js.Promise[Response] = {
  val page = <.main(
    <.h1("Hello hx4s"),
    IslandComponents.CounterJs(new CounterProps { val label = "clicks"; val start = 0 })
  )
  c.renderPromise(page.rawElement, new RenderProps { val title = "Hello" })
}
```

`sbt server/hx4sGenerate` が `app/routes/index.ts` と `app/islands/counter.tsx` を生成する。
ビルドとプロジェクトの作り方は [docs/getting-started.md](docs/getting-started.md)、全体は `examples/minimal` を参照。

## 実装済みの機能と未実装

| 状態 | 内容 |
| --- | --- |
| 実装済み | `@route`（GET / POST / PUT / DELETE / PATCH / OPTIONS）、`@islands[P]`、`IslandComponents` の生成、`IslandExport[P]` と `JsShape` による props の検証、hydrate 前の検証（`hx4s-vite/client`）、Hono の `Context` の facade |
| 実装済み | sbt-hx4s（`hx4sGenerate`、依存の向きの検査、client なしの構成）、`hx4s()` Vite プラグイン、Node 用 loader、ローカル公開のスクリプト、`examples/minimal`・`examples/api-only` |
| 未実装 | 型付きルート、Client Router（SPA 用のルーター）、`ALL` メソッド、npm CLI、プロジェクトの雛形、dev server の HMR、型付き API クライアント、OpenAPI、Maven Central / npm への公開 |
| 未検証 | Cloudflare Workers 以外の実行基盤（Node・Deno・Bun など）、本番へのデプロイ |

未実装の機能の見込みは [docs/hx4s/roadmap.md](docs/hx4s/roadmap.md) にある。

## 対応バージョン

examples と CI で検証している組み合わせ。これ以外は検証していない。

| 対象 | 版 |
| --- | --- |
| Scala | 3.3.8（LTS） |
| Scala.js（sbt-scalajs） | 1.22.0 |
| sbt | 1.13.0（sbt-hx4s は sbt 1.x、Scala 2.12 のプラグイン） |
| scalajs-react | 4.0.0 |
| scalajs-dom | 2.8.1 |
| HonoX / Hono | 0.1.62 / 4.13.18 |
| Vite / `@scala-js/vite-plugin-scalajs` | 8.3.3 / 1.1.0 |
| React | 19.2.8 |
| JDK | 17 以上（CI は 21） |
| Node.js | 22.12 以上（`hx4s-vite` の `engines`。CI は 22） |

HonoX は 0.1.x のアルファ版で、hx4s は HonoX の内部の挙動にいくつか依存する。`honox` は検証した版に固定して使う（[docs/hx4s/compatibility.md](docs/hx4s/compatibility.md)）。

## 文書

利用者向けのガイドは [docs/README.md](docs/README.md) から読める。

| 文書 | 内容 |
| --- | --- |
| [はじめに](docs/getting-started.md) | `examples/minimal` の動かし方と、自分のプロジェクトの作り方 |
| [インストール](docs/installation.md) | 座標、ローカル公開、版と commit の固定 |
| [ルーティング](docs/routing.md) | `@route` と `Context` |
| [Islands](docs/islands.md) | `@islands[P]`、props の型、`IslandExport` |
| [SSR と hydration](docs/ssr-hydration.md) | 描画の流れ、世代ずれ、テスト |
| [SPA](docs/spa.md) / [API](docs/api.md) | SPA と API の構成の現状 |
| [ビルド設定](docs/build-configuration.md) | sbt のキー、`hx4s()` のオプション、codegen の引数 |
| [デプロイ](docs/deployment.md) | Cloudflare Workers |
| [移行](docs/migration.md) | 手書きの Scala.js × HonoX 構成からの移行 |
| [examples](docs/examples.md) / [テスト](docs/testing.md) / [リリース](docs/release.md) | |

設計と経緯の文書は [docs/hx4s/](docs/hx4s/README.md)。貢献の方法は [CONTRIBUTING.md](CONTRIBUTING.md)、変更履歴は [CHANGELOG.md](CHANGELOG.md)。

## ビルドとテスト

```bash
npm ci                        # ワークスペース（hx4s-vite と Node のテスト）の依存
sbt test                      # core / react / codegen の munit
sbt sbtHx4s/scripted          # sbt-hx4s の scripted テスト
npm test                      # hx4s-vite のビルド、island の SSR・hydration、独立性の検査
npm run typecheck             # hx4s-vite の型検査
```

examples の実行は [docs/getting-started.md](docs/getting-started.md)、テストの内訳は [docs/testing.md](docs/testing.md)。

## ライセンス

[Apache License 2.0](LICENSE)
