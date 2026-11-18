# 貢献ガイド

hx4s への変更（バグ修正・機能追加・文書）を歓迎します。hx4s は 0.1 を開発中で、API と設定は変わることがあります。大きな変更は、先に issue で方針を相談してください。

## 開発環境

| 道具 | 版 |
| --- | --- |
| JDK | 17 以上（CI は 21） |
| sbt | 1.x（`project/build.properties` は 1.13.0） |
| Node.js | 22.12 以上 |

```bash
git clone <hx4s のリポジトリ> && cd hx4s
npm ci
```

ブランチは `develop` を基準にします。

## ビルドとテスト

```bash
sbt test                      # core / react / codegen の munit
sbt sbtHx4s/scripted          # sbt-hx4s の scripted テスト（codegen を publishLocal してから走る）
sbt 'set ThisBuild / scalacOptions += "-no-indent"' Test/compile   # インデント構文が残っていないことの確認
npm test                      # hx4s-vite のビルド、island の SSR・hydration、独立性の検査
npm run typecheck             # hx4s-vite と、文書の TypeScript の例（tests/docs-typecheck）の型検査
node scripts/check-independence.mjs
```

examples を動かすには、ローカル公開が要ります。テストはビルドの出力（`dist`）を読むので、`npm test` の前に `npm run build` を実行します。
`examples/minimal` の E2E は Playwright の Chromium を使います（`HX4S_SKIP_E2E=1` で E2E だけ飛ばせます）。

```bash
node scripts/publish-local.mjs
node scripts/setup-example.mjs minimal
cd examples/minimal
npx playwright install chromium   # 初回だけ
npm run build
npm run typecheck
npm test
cd ../..

node scripts/setup-example.mjs api-only
cd examples/api-only
npm run build
npm run typecheck
npm test
```

CI（`.github/workflows/ci.yml`）も同じ順で動かします。

テストの内訳は [docs/testing.md](docs/testing.md)、examples は [docs/examples.md](docs/examples.md)。
コードを変えたら、関係するテストを足し、上のコマンドが通ることを確かめてから出してください。CI は Ubuntu と Windows で同じものを走らせます。

## 生成物

`examples/minimal/app/routes`・`app/islands` と `examples/api-only/app/routes` の生成ファイルはコミットしてあります。`@route` / `@islands` や codegen の出力を変えたときは、examples で `npm run generate` を実行して差分を含めてください
（CI が、ビルド後に各 example の `app` に差分や未追跡のファイルが無いことを確かめます）。

## 構成と依存

| モジュール | 場所 | 言語 |
| --- | --- | --- |
| hx4s-core | `modules/core` | Scala 3（JVM と Scala.js） |
| hx4s-react | `modules/react` | Scala.js |
| hx4s-codegen | `modules/codegen` | Scala 3（JVM） |
| sbt-hx4s | `sbt-plugin` | Scala 2.12（sbt 1.x のプラグイン） |
| hx4s-vite | `vite` | TypeScript |

- **依存を増やさない。** 新しい Maven / npm の依存は、既存のもので足りない理由と、ライセンス（Apache-2.0 と両立するか）を issue か PR に書いてください。版は固定します
- `hx4s-codegen` は `hx4s-core` に依存しません（アノテーションを名前で見分けるため）
- hx4s のモジュールは実行基盤（Cloudflare など）に依存しません。基盤固有のコードは examples か利用側に置きます
- Scala で書けるロジックを TypeScript で書かないでください。TypeScript は Vite・HonoX の配線など、Scala で書けない部分に限ります

## コーディング規約

- 既存のコードのスタイル・命名・置き場所に合わせます
- **Scala の Optional Braces（インデント構文）は原則使いません。** `{}` で書きます。`if ... then` などの制御構文は使えますが、複数行は `{}` で囲みます（CI が `-no-indent` でのコンパイルを確かめます）
- 設計は FP を優先します。純粋な計算と I/O を分け、拒否や失敗は例外ではなく `Either` や enum で返します。可変状態や文字列で表した種別を必要以上に使いません
- scalajs-react のコンポーネントは関数コンポーネント（`ScalaFnComponent`）にし、コンポーネントを持つ `val` は `final val` にします
- Scala.js のライブラリがあれば、npm のパッケージの facade より優先します
- TypeScript の `any` と Scala の未型付きの構造型を避けます。コンパイル・Lint の警告は抑制せず直します
- **Scaladoc / JSDoc は簡潔に**: 「何をするか」と利用者が知るべき制約を 1〜2 行で書きます。英語で書き、末尾にピリオドを付けません。経緯・却下した案・実測値は `docs/hx4s/` に書きます
- 不要なコメントや過剰な抽象化を入れません
- 秘匿値（キー・トークン・パスワード）をコード・ログ・コミットメッセージに含めません

## 文書

- 利用者向けの文書は `docs/` 直下、設計と経緯は `docs/hx4s/` に置きます。英語で書き、日本語版を `ja/` の同じ位置に置きます（文書を変えたら日本語版も合わせます）。簡潔な技術文書の文体にします（説明の羅列や飾った言い回しを避け、表と短い段落とコード例を使います）
- **コード例は実物で確かめたものだけを載せます。** `examples/minimal` と同じ書き方にするか、実際にコンパイル・実行して確かめます。確かめられていない機能を、できるかのように書きません
- 未実装・未検証のものは、そう書きます。性能の主張はしません。数値は `docs/hx4s/reviews/` に記録があるものだけ、条件つきで引用します
- 相対リンクが切れていないことを確かめます
- 利用者向けの文書とコードに、特定のアプリケーションの名前を入れません（`node scripts/check-independence.mjs` が検査します。`docs/hx4s/` の経緯の説明は除きます）

## コミット

[Conventional Commits](https://www.conventionalcommits.org/) の形式で、件名は日本語です。

```
<type>(<scope>): <変更の要約>
```

| type | 用途 |
| --- | --- |
| `feat` | 機能の追加 |
| `fix` | 不具合の修正 |
| `docs` | 文書だけの変更 |
| `refactor` | 挙動を変えない整理（互換性を壊すときは `refactor!:` のように `!` を付ける） |
| `build` / `ci` / `chore` | ビルド設定、CI、そのほか |

scope は `core`・`react`・`codegen`・`sbt`・`vite`・`scripts`・`examples`・`deps` など。例: `fix(codegen): 出力の安全策と生成コードの識別子・設定値の扱いを直す`。
1 つのコミットは 1 つの目的にまとめ、本文には変更の理由を書きます。

## レビューの流れ

1. `develop` から作業ブランチを切り、変更を加える
2. 上の「ビルドとテスト」を通し、自分でレビューする（規約との整合、Scala で書けるものを TypeScript で書いていないか、テストが足りているか、文書の文体）
3. Pull Request を `develop` に出す。目的、変更点、確かめたこと、確かめていないことを書く
4. CI（Ubuntu と Windows）が通ることを確かめ、レビューの指摘に対応する
5. 設計に関わる判断（公開 API、生成物の形式、依存）は `docs/hx4s/adr/` に ADR（1 判断 1 ファイル）で残す

## ライセンス

貢献は [Apache License 2.0](../LICENSE) で提供されるものとします（ライセンスの第 5 条）。
