# リリース

**Maven Central と npm への公開の手順は整備していない。** この文書は、リリースの前に行う版付け・変更履歴・検証と、公開の前に決める必要があることを書く。
公開の操作（署名・アップロード・`npm publish`）は書かない。

## 現在の状態

| 項目 | 状態 |
| --- | --- |
| リリース済みの版 | なし。`0.1.0` は未公開 |
| ビルドの既定の版 | `0.1.0-SNAPSHOT`（`build.sbt` の `hx4s.version` のシステムプロパティを省いたとき）。`vite/package.json` の版も `0.1.0-SNAPSHOT` |
| ローカル公開の版 | `0.1.0-local.g<commit>`（`scripts/publish-local.mjs`。[installation.md](installation.md)） |
| 利用者の入手方法 | ローカル公開だけ |

## 版付けの方針

- [Semantic Versioning](https://semver.org/lang/ja/) に従う。`0.x` の間は、マイナー版で API・設定・生成物の形式が変わりうる
- sbt-hx4s・`hx4s-core`・`hx4s-react`・`hx4s-codegen` と、npm の `hx4s-vite` は**同じ版**にそろえる。sbt-hx4s の `hx4sVersion` が、プラグインとライブラリの版をそろえる
- リリースごとに、検証した HonoX・Hono・Vite・React などの版を明記する（[README](../README.md) の対応バージョン）。`hx4s-vite` の peerDependencies は検証した版だけにする
- 公開した版は書き換えない。誤りがあれば次の版で直す
- 版を決めるのは、`build.sbt` の `hx4s.version` と `vite/package.json` の `version`。`publish-local.mjs` はこれらを上書きして公開する

## リリースの手順

公開の方式が決まるまでの、版を確定するところまでの手順。

1. **変更履歴を整える**: [CHANGELOG.md](../CHANGELOG.md) の `[Unreleased]` を新しい版の節にし、日付を入れる。`[Unreleased]` を空で残し、比較用のリンクを更新する
2. **版を決める**: 変更の内容（API の破壊的変更・機能追加・修正）から、SemVer に沿って決める
3. **検証する**: 次をすべて通す。CI（Ubuntu と Windows）でも通ること。

   ```bash
   npm ci
   npm run typecheck
   sbt test
   sbt sbtHx4s/scripted
   sbt 'set ThisBuild / scalacOptions += "-no-indent"' Test/compile
   npm test
   node scripts/publish-local.mjs --version <版> && node scripts/publish-local.mjs --verify --version <版> --commit <40 桁の SHA>
   node scripts/setup-example.mjs minimal && (cd examples/minimal && npm run build && npm run typecheck && npm test)
   ```

4. **文書を確かめる**: README と `docs/` の版・コード例・リンクが実物と合っていること。`node scripts/check-independence.mjs` が通ること
5. **タグを付ける**: 検証した commit に `v<版>` の注釈付きタグを付ける
6. 公開（下の「未整備」）

## 未整備と未決事項

Maven Central と npm へ公開するには、次を決める必要がある。決めるまでは公開しない。

| 項目 | 状態 | 参照 |
| --- | --- | --- |
| Maven の organization | 仮に `io.github.montereylangit`。独自ドメインを持つなら変える。変えると利用者のビルド定義が変わる（Scala のパッケージ名 `hx4s.*` は変わらない） | [roadmap.md](hx4s/roadmap.md) U-2、[ADR 0001](hx4s/adr/0001-name-and-coordinates.md) |
| npm のスコープ | `hx4s-vite` か `@hx4s/vite` か。スコープが取れるかを確かめる | U-1 |
| 名前の衝突 | `hx4s` が Maven Central・npm・GitHub で既に使われていないかの確認 | U-20 |
| 成果物の署名 | Maven Central は署名が要る。鍵の管理の方法は未決 | — |
| CI での公開 | 公開を伴う CI での依存解決と、公開の自動化の方式 | U-3、[ADR 0006](hx4s/adr/0006-one-way-dependency-and-local-publish.md) |
| POM / package.json のメタデータ | 開発者・SCM・説明などの項目（Maven Central の要件）。ライセンスは Apache-2.0 で設定済み | — |
| 移したコードの権利の確認 | 移動元のコードの権利者の同意 | U-21 |
| 公開するかどうか | 公開の判断自体が未決 | [extraction-plan.md](hx4s/extraction-plan.md) Phase 5 |

これらが決まったら、この文書に公開の手順（署名・アップロード・タグからの自動化）を足す。
