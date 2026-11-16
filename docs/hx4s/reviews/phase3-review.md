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
