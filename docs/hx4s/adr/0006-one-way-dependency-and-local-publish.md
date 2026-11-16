# ADR 0006: 3ch → hx4s の一方向依存と、分離直後のローカル依存

- 状態: 承認（2026-11-16、Phase 0）。CI での解決方式は未決（U-3）
- 関連: [extraction-plan.md](../extraction-plan.md) Phase 3〜5、[module-boundaries.md](../module-boundaries.md) 2 章

## 背景

hx4s を 3ch から分離したあと、3ch は hx4s を使う側になる。hx4s がまだ公開されていない期間、3ch がどうやって hx4s を解決するかを決める必要がある。
また、hx4s 側に 3ch の都合が逆流しないようにする必要がある。

## 決定

- 依存は 3ch → hx4s の一方向。hx4s は 3ch のコード・名前・ファイルパス・文言を参照しない。3ch だけが必要とする機能は 3ch に置く。
- 分離後しばらくは、ローカルの依存を許す。
  - sbt: hx4s で `sbt publishLocal` し、3ch は通常の `libraryDependencies` / `addSbtPlugin` で `~/.ivy2/local` から解決する。版は `-SNAPSHOT` を使わず、`0.1.0-local.N` のように明示する案（同じ版の上書きでキャッシュが古いまま残る事故を避けるため）。
  - npm: 3ch の `package.json` で `"hx4s-vite": "file:../hx4s/vite"` のように参照する。
- CI での **公開を伴う** 解決方式は Phase 5 で決める。
- ただし、公開が無くても再現できる検証経路は Phase 4 の前提にする。
  - commit を固定した hx4s の checkout から、`publishLocal` と npm パッケージの準備（`npm pack` など）を行う手順をスクリプトにする。
  - **空の依存キャッシュ**（空の coursier / ivy キャッシュと `node_modules`）から、3ch がビルドとテストを通せることを完了条件に含める。
  - 手元の `~/.ivy2/local` と `node_modules` にたまたま残っているものに頼って成功する、という状態を移行完了と見なさない。

### CI での解決方式の比較

| 方式 | 長所 | 短所 |
| --- | --- | --- |
| sbt の source dependency（`RootProject(uri("https://…#<commit>"))`） | 公開不要。commit で固定できる | sbt plugin（`sbt-hx4s`）には使えない（`project/` の中で別途 `dependsOn` が要る）。npm 側は別の手段が要る。sbt のキャッシュ（`~/.sbt/1.0/staging`）の扱いが分かりにくい |
| git submodule + CI で `publishLocal` と npm `file:` | sbt plugin・ライブラリ・npm を同じ手順で扱える。commit で固定できる | CI が毎回 hx4s をビルドする。submodule の更新を忘れやすい |
| Maven Central / npm に公開 | 利用側の設定が最も普通になる | 公開の手続き（署名、名前空間の確認）が要る。アルファの段階で公開版が増える |
| GitHub Packages（Maven / npm） | 公開範囲を限定できる | 読み取りにも認証が要り、CI と開発者の設定が増える |

提案: 公開までは submodule + `publishLocal`、公開後は Maven Central / npm。

## 却下案

| 案 | 理由 |
| --- | --- |
| hx4s から 3ch のテストを呼んで回帰を見る | 依存が逆向きになる。3ch 固有の回帰は 3ch の CI で見る。hx4s の回帰は `examples/minimal` と互換性テストで見る |
| 最初から公開版だけに依存する | 分離直後は hx4s と 3ch を同時に直すことが多い。公開の往復が開発を遅くする |
| `-SNAPSHOT` でローカル公開 | 同じ版の上書きで、依存解決のキャッシュが古い成果物を返すことがある。明示的な版の方が追いやすい |

## 影響

- Phase 4 の 3ch は、hx4s のチェックアウトが隣にあることを前提にする（`../hx4s`）。手順を 3ch の docs に書く。
- 3ch の本番デプロイ（`npm run deploy`）は、ローカル依存の期間はローカル環境からだけ行える。CI からのデプロイは Phase 5 以降。
- Phase 4 の移行は、上の前提（commit 固定の checkout からの手順、空の依存キャッシュからのビルド）が通ってから完了とする（[extraction-plan.md](../extraction-plan.md) Phase 4）。
- hx4s に変更を入れるときに 3ch が壊れていないかは、Phase 4 以降、3ch 側で確認する。hx4s の CI では確認できない。
