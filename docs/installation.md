# インストール

**hx4s は Maven Central にも npm にも公開していない。** 今使えるのは、hx4s のリポジトリを clone して手元に公開（`publishLocal` と `npm pack`）したものだけ。
公開の見込みは [release.md](release.md) と [hx4s/roadmap.md](hx4s/roadmap.md) の未決事項を参照。

## 座標

| 成果物 | 座標 | 使うところ |
| --- | --- | --- |
| sbt プラグイン | `"io.github.montereylangit" % "sbt-hx4s" % <版>`（sbt 1.x） | `project/plugins.sbt` |
| `hx4s-react` | `"io.github.montereylangit" %%% "hx4s-react" % hx4sVersion`（Scala.js） | server・client・shared の `libraryDependencies`。`hx4s-core` の JS 版、scalajs-react `core` 4.0.0、scalajs-dom 2.8.1 を連れてくる |
| `hx4s-core` | `"io.github.montereylangit" %%% "hx4s-core" % hx4sVersion`（JVM と Scala.js） | `@route` / `@islands` と `JsShape`。`hx4s-react` が依存するので、通常は直接書かない |
| `hx4s-codegen` | `io.github.montereylangit:hx4s-codegen_3` | `sbt-hx4s` が専用の configuration で解決して fork した JVM で動かす。直接依存しない |
| npm | `hx4s-vite` | `hx4s()` Vite プラグイン、`hx4s-vite/client`、Node 用 loader |

- organization `io.github.montereylangit` は仮の値で、公開時に変わる可能性がある（[hx4s/roadmap.md](hx4s/roadmap.md) U-2）。Scala のパッケージ名（`hx4s.*`）は変わらない。
- `hx4sVersion` は sbt-hx4s が提供する値で、プラグインと同じ版になる。`hx4s-react` の版に使うと、プラグインとライブラリの版がそろう。
- npm のパッケージ名（`hx4s-vite` かスコープ付きか）は未決（U-1）。
- `hx4s-vite` の peerDependencies は `honox` 0.1.62、`@scala-js/vite-plugin-scalajs` 1.1.0、`vite` ^8.3.3。Node.js は 22.12 以上。

## ローカル公開

```bash
git clone <hx4s のリポジトリ> hx4s && cd hx4s
node scripts/publish-local.mjs
```

`npm ci`（lockfile どおり）→ sbt の `publishLocal` → `hx4s-vite` のビルドと `npm pack` の順に動く。
成果物は `~/.ivy2/local` と `target/local/hx4s-vite.tgz` に置かれる。

```bash
node scripts/publish-local.mjs                 # 版は <version.txt の版>-local.g<commit>（未コミットの変更があれば .dirty-<内容のハッシュ> 付き）
node scripts/publish-local.mjs --version 0.1.0-local.3
node scripts/publish-local.mjs --verify --version 0.1.0-local.3 --commit <40 桁の SHA>   # 公開記録の照合。食い違えば終了コード 1
```

| フラグ | 意味 |
| --- | --- |
| `--version <v>` | 版を明示する |
| `--skip-sbt` / `--skip-npm` | 片方だけ公開する（部分公開） |
| `--reuse-node-modules` | 前回このスクリプトが `npm ci` したときと `package-lock.json` が同じなら `npm ci` を省く（既定は毎回 `npm ci`） |
| `--verify` | 公開せず、完了の記録を今の HEAD・作業ツリー・成果物と照らす。`--version` / `--commit` で期待値も照らす |

版は commit ごとに変える（既定の `<version.txt の版>-local.g<commit>`、今は `0.1.0-local.g<commit>` を使う）。同じ版で中身を変えると、sbt の依存解決が古い成果物を返すことがある。
ビルドの既定の版 `<version.txt の版>-SNAPSHOT` はローカル公開には使わない。

`target/local/` に書かれる記録は次のとおり（公開時刻は入れない）。

| ファイル | 書くとき | 内容 |
| --- | --- | --- |
| `hx4s-publish-sbt.json` / `hx4s-publish-npm.json` | それぞれの公開が成功したとき | 版、commit、作業ツリーの状態（`dirty` / `dirtyHash`）と、Maven の成果物の一覧または tgz のファイル名と SHA-256 |
| `hx4s-publish.json` | 上の 2 つが同じ版・commit・作業ツリーの状態で、成果物がそろっているときだけ | 2 つをまとめた完了の記録（`format: 1`） |
| `hx4s-version.txt` | `hx4s-publish.json` と同時 | 版だけ。JSON を読めない `project/plugins.sbt` 向け |

公開を始めると `hx4s-publish.json` と `hx4s-version.txt` を先に消すので、失敗や部分公開の後に古い完了の記録は残らない。
`~/.ivy2/local` 以外（`sbt.ivy.home` を変えた環境）は扱わない。

## 利用側での取り込み

sbt は `addSbtPlugin(... % "<版>")` で参照する。版は `target/local/hx4s-version.txt` の値（`examples/minimal` は環境変数 `HX4S_VERSION`、なければこのファイルから読む）。

npm は tgz を `--no-save` で入れる。

```bash
npm ci
npm install --no-save /path/to/hx4s/target/local/hx4s-vite.tgz
```

tgz の整合性ハッシュは公開のたびに変わる。`package.json` に `file:` で書くと、hx4s を公開し直すたびに lockfile が変わるので、lockfile で固定したいときは `--no-save` で入れる。
`--no-save` で入れたものは次の `npm ci` / `npm install` で消えるので、その後に入れ直す。

## 版と commit の固定

利用側に lock ファイル（hx4s の commit の完全な SHA と版）を置き、セットアップスクリプトで次を行うと、再現できる。

1. hx4s の checkout が lock の commit にあり、作業ツリーがクリーンであることを確かめる
2. `publish-local.mjs --verify --version <lock> --commit <lock>` が通れば公開を省き、通らなければ `--version <lock>` で公開して再度 `--verify` する
3. 利用側の `npm ci` のあとに tgz を `--no-save` で入れる

スクリプトの全文と `project/plugins.sbt` から lock の版を読む例は [hx4s/local-dependency.md](hx4s/local-dependency.md)。
examples は同じ考え方で `scripts/setup-example.mjs` を使う（`--verify` のあとに `npm ci` と tgz の導入を行う）。

## うまくいかないとき

| 症状 | 原因と対処 |
| --- | --- |
| `hx4s is not published locally` | `examples/minimal` の `project/plugins.sbt` が版を読めない。hx4s のリポジトリで `node scripts/publish-local.mjs` を実行するか、`HX4S_VERSION` を設定する |
| `the local publish does not match this checkout` | 公開後に hx4s のソースを変えた。もう一度 `publish-local.mjs` を実行する |
| `hx4s-codegen could not be resolved` | `hx4sCodegenVersion` の版の `hx4s-codegen_3` が解決できない。ローカル公開の版とプラグインの版がそろっているか確かめる |
| `npm ci` のあとに `hx4s-vite` が見つからない | `--no-save` で入れたものが消えた。tgz を入れ直す |
| Windows で npm / sbt の起動が壊れる | パスや引数に空白・`&` がある。スクリプトはシェルを介さず起動するが、自分の npm scripts では注意する |
