# hx4s

Scala.js から [HonoX](https://github.com/honojs/honox) と React を使うためのメタフレームワーク（開発中、0.1.0 未公開）。

hx4s は HonoX の再実装ではない。ルーティング・ミドルウェア・renderer・island の探索と hydration は HonoX に任せ、
hx4s は「Scala の宣言から HonoX の規約どおりのファイルを生成する」部分と、Scala.js のビルドを Vite / HonoX に繋ぐ部分だけを持つ。

## 現状の機能

| モジュール | 内容 |
| --- | --- |
| `hx4s-core`（Scala, JVM + JS） | `@route(filePath, method)`・`@islands[P](filePath)` アノテーション。JS 側に island の props を確かめる `hx4s.props.JsShape` |
| `hx4s-react`（Scala.js） | HonoX の island 境界を付ける `hx4s.react.Island`、client の export を props の型 `P` に結び付ける `hx4s.react.IslandExport[P]`、Hono の Context の facade `hx4s.hono.Context[Env, R]`（Request / Response は scalajs-dom の型） |
| `hx4s-codegen`（Scala 3, JVM） | scalameta で宣言を読み、`app/routes/**/*.ts`・`app/islands/**/*.tsx` と SSR 用の `IslandComponents.scala` を生成する。出力の安全策（手書きファイルの保護、リンクの拒否、全検査後の書き込み）を持つ |
| `sbt-hx4s`（sbt 1.x プラグイン） | `Hx4sPlugin`。`hx4sGenerate` で TS を、`Compile / sourceGenerators` で `IslandComponents` を生成する。codegen は fork した JVM で動く。client と server の間の禁止された依存を `hx4sCheckDependencies` で検出する |
| `hx4s-vite`（npm） | `hx4s()` Vite プラグイン（server / client の Scala.js プロジェクトの登録、`scalajs:client.js` の別名、honox の React 設定、client ビルドの出力名）、hydrate 前の props の検証 `hx4s-vite/client`、Node 用 loader `hx4s-vite/node-register` |

実行基盤（Cloudflare Workers など）は利用側が `@hono/vite-build` のアダプタで選ぶ。hx4s のモジュールは Cloudflare に依存しない。
未実装の機能（型付きルート、Client Router、npm CLI、dev server の HMR など）は [docs/hx4s/roadmap.md](docs/hx4s/roadmap.md) を参照。

## 使い方の概略

```scala
// project/plugins.sbt
addSbtPlugin("io.github.montereylangit" % "sbt-hx4s" % "<version>")

// build.sbt
lazy val server = project.enablePlugins(ScalaJSPlugin, Hx4sPlugin).dependsOn(shared)
  .settings(hx4sClientProject := client, libraryDependencies += "io.github.montereylangit" %%% "hx4s-react" % hx4sVersion)
```

```ts
// vite.config.ts
export default defineConfig(({ mode }) => {
  const plugins = hx4s({ mode, sbt: { cwd: import.meta.dirname } });
  // server ビルドには実行基盤のアダプタ（@hono/vite-build/cloudflare-workers など）を足す
  return mode === "client" ? { plugins } : { plugins: [...plugins, buildCloudflareWorkers()] };
});
```

設定キーと生成規則は [docs/hx4s/build-integration.md](docs/hx4s/build-integration.md) と [docs/hx4s/codegen.md](docs/hx4s/codegen.md)。

## ビルドとテスト

JDK 17 以上、sbt 1.13、Node.js 22.12 以上が要る。

```bash
npm ci                        # ワークスペース（hx4s-vite と Node のテスト）の依存
sbt test                      # core / react / codegen の munit（react のテストは node_modules の react と honox を使う）
sbt sbtHx4s/scripted          # sbt-hx4s の scripted テスト（codegen を publishLocal してから走る）
npm test                      # hx4s-vite のビルド、island の SSR・hydration、独立性の検査
npm run typecheck             # hx4s-vite の型検査
```

## ローカル公開

まだ Maven Central / npm に公開していない。利用側は次のスクリプトでローカルに公開したものを使う。

```bash
node scripts/publish-local.mjs              # 版は 0.1.0-local.g<commit>（未コミットの変更があれば .dirty-<内容のハッシュ> 付き）
node scripts/publish-local.mjs --version 0.1.0-local.3
```

sbt の成果物は `~/.ivy2/local` に、`hx4s-vite` は `target/local/hx4s-vite.tgz` に置かれ、公開した版は `target/local/hx4s-version.txt` に書かれる。
利用側は `addSbtPlugin(... % "<version>")` と `"hx4s-vite": "file:<hx4s>/target/local/hx4s-vite.tgz"` で参照する
（tgz の整合性ハッシュは公開のたびに変わるので、lockfile で依存を固定したいときは examples と同じく `npm ci` の後に `npm install --no-save <tgz>` で入れる）。

## examples/minimal

SSR + Islands の最小アプリ（静的ページ、動的ルート `items/[id]`、POST、island 2 種）。Cloudflare Workers（wrangler）で動かす。
hx4s はローカル公開した成果物だけで解決する。

```bash
node scripts/publish-local.mjs
node scripts/setup-example.mjs minimal   # lockfile どおりに npm ci し、hx4s-vite の tgz を --no-save で入れる
cd examples/minimal
npx playwright install chromium   # E2E テストを動かすとき
npm run build                     # hx4sGenerate → client ビルド → Worker ビルド
npm run dev                       # http://127.0.0.1:8787
npm test                          # sbt test（型付き props のコンパイル検査）と、SSR・POST・バンドル・jsdom の hydration・Playwright の E2E
```

`HX4S_SKIP_E2E=1` で E2E を飛ばせる。

## ライセンス

[Apache License 2.0](LICENSE)
