# ADR 0001: 名前と公開座標

- 状態: 承認（2026-11-16、Phase 0）
- 更新（2026-11-16、Phase 3）: 「影響」の旧バナーを移行期間だけ自動で認める案は採らなかった。生成マーカーは設定値 1 つで、旧バナーの生成物を置き換えるときは
  利用側が `hx4sBanner` / `hx4sGeneratedMarker` を旧値にする（[codegen.md](../codegen.md) の「設定」、[build-integration.md](../build-integration.md) 3.3）
- 関連: [roadmap.md](../roadmap.md) U-1・U-2・U-20

## 背景

3ch の Scala.js × HonoX 連携基盤は、Scala のパッケージ `io._3ch.*`、旧プロジェクト名 `ch3s`、ファイルパス `bbs/`・`bbs-client/` を前提に書かれている。
独立した OSS として公開するには、掲示板と無関係な名前と座標が要る。

## 決定

| 項目 | 値 |
| --- | --- |
| 正式名（仮） | `hx4s`（HonoX for Scala） |
| Scala パッケージ | `hx4s.*`（`hx4s.annotation`、`hx4s.react`、`hx4s.hono`、`hx4s.codegen`、`hx4s.sbt`） |
| Maven organization（仮） | `io.github.montereylangit` |
| 成果物名 | `hx4s-core`、`hx4s-react`、`hx4s-codegen`、`sbt-hx4s` |
| npm | `hx4s-vite`（スコープは未決 U-1） |

公開物（ソース・生成物のバナー・環境変数名・エラーメッセージ）に `io._3ch` / `ch3s` / `3ch` を残さない。
例: テスト用環境変数 `CH3S_CLIENT_JS_DIR` は `HX4S_CLIENT_JS_DIR` に改める。

## 却下案

| 案 | 理由 |
| --- | --- |
| `io._3ch.framework.*` のまま公開 | 掲示板の名前が残り、3ch と無関係な利用者に意味が通じない |
| パッケージを organization と同じ `io.github.montereylangit.hx4s.*` にする | 長い。organization は変わる可能性がある（U-2）が、パッケージを変えると利用者のコードがすべて変わる。パッケージは短く安定した `hx4s` にする |
| npm を最初からスコープ付き `@hx4s/vite` に固定 | スコープの取得が確認できていない。取得できたら移る（U-1） |

## 影響

- Phase 2 で 3ch 内のサブプロジェクトを作る時点から `hx4s.*` パッケージにする。3ch 側の import はその時点で変わる。
- 生成物のバナー文言が変わるので、生成済みファイルの判定は移行期間だけ旧バナーも認める（[build-integration.md](../build-integration.md) 3.3）。
- 名前の衝突確認（U-20）で問題が出たら、この ADR を改訂する。
