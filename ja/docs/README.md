# hx4s の文書

hx4s は、Scala.js から [HonoX](https://github.com/honojs/honox) と React を使うためのメタフレームワーク（0.1 開発中・未公開）。概要は [README](../README.md)。

## 利用者向けのガイド

| 文書 | 内容 |
| --- | --- |
| [はじめに](getting-started.md) | `examples/minimal` の動かし方。自分のプロジェクトの作り方（sbt・npm・Vite・Scala のコード） |
| [インストール](installation.md) | 座標、ローカル公開、版と commit の固定 |
| [Scala のソース構成](source-organization.md) | ハンドラ・ビュー・コンポーネント・island の任意の構成例 |
| [ルーティング](routing.md) | `@route`、ファイルパスと URL、codegen が拒否するもの、`Context` |
| [Islands](islands.md) | `@islands[P]`、props の trait と使える型、`IslandExport`、`IslandComponents` |
| [SSR と hydration](ssr-hydration.md) | 描画の流れ、hydrate 前の検証、世代ずれ、server のコードを client に入れない仕組み、テスト |
| [SPA](spa.md) | SPA の現状（Client Router は未実装）と、今できる構成 |
| [API](api.md) | API だけのルート、リクエストの読み方、React を使わない構成 |
| [ビルド設定](build-configuration.md) | sbt のキー、`hx4s()` のオプション、`CodegenMain` の引数、生成ファイルの扱い |
| [デプロイ](deployment.md) | Cloudflare Workers。ほかの実行基盤は未検証 |
| [移行](migration.md) | 手書きの Scala.js × HonoX 構成からの移行 |
| [examples](examples.md) | `examples/minimal`（SSR + Islands）と `examples/api-only`（API だけ）の説明 |
| [テスト](testing.md) | hx4s 自体のテストの構成と実行方法 |
| [リリース](release.md) | 版付けと検証の手順。公開の手順は未整備 |

関連する文書: [CONTRIBUTING](../CONTRIBUTING.md)、[CHANGELOG](../CHANGELOG.md)。

## 設計と経緯

[hx4s/](hx4s/README.md) は設計と経緯の文書。利用者がガイドを読むときに必要な部分は、ガイドからリンクしている。

| 文書 | 内容 |
| --- | --- |
| [architecture](hx4s/architecture.md) | 全体像、HonoX との責務分担、HonoX の内部 API への依存箇所 |
| [module-boundaries](hx4s/module-boundaries.md) | モジュールの境界と依存方向、公開 API と内部 API |
| [rendering-model](hx4s/rendering-model.md) | 描画方式の位置づけ、hydration の境界、props のシリアライズ、世代ずれ |
| [routing-design](hx4s/routing-design.md) | `@route` の設計と、型付きルート・Client Router の構想 |
| [build-integration](hx4s/build-integration.md) | sbt と Vite の統合の設計 |
| [codegen](hx4s/codegen.md) | codegen の宣言の規則・出力の規則・エラーの形式・設定 |
| [compatibility](hx4s/compatibility.md) | 依存バージョン、HonoX アルファ版への追従方針、ライセンス |
| [local-dependency](hx4s/local-dependency.md) | ローカル公開の記録と、利用側での固定の手順 |
| [roadmap](hx4s/roadmap.md) | 機能の状態（実装済み・構想）と未決事項 |
| [adr/](hx4s/README.md) | 設計判断の記録（1 判断 1 ファイル） |
| [reviews/](hx4s/README.md) | 検証とレビューの記録 |
