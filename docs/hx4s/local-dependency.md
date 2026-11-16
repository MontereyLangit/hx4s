# ローカル公開した hx4s を利用側で固定して使う

hx4s を Maven Central / npm に公開するまでの間（[ADR 0006](adr/0006-one-way-dependency-and-local-publish.md)）、利用側は隣に置いた hx4s の checkout から
`scripts/publish-local.mjs` で公開したものを使う。この文書は、利用側が版と commit を固定し、再公開が要るかを機械的に判断する手順をまとめる。

## 1. 公開と記録

| コマンド | 結果 |
| --- | --- |
| `node scripts/publish-local.mjs [--version V]` | `npm ci`（lockfile どおり）→ sbt の `publishLocal` → hx4s-vite のビルドと `npm pack`。両方そろえば完了の記録を書く |
| `--skip-sbt` / `--skip-npm` | 片方だけ公開し、その記録だけを書き直す。もう片方の記録が同じ版・commit・作業ツリーの状態でなければ、完了の記録は書かない |
| `--reuse-node-modules` | 前回このスクリプトが `npm ci` したときと `package-lock.json` の SHA-256 が同じなら `npm ci` を省く。clone 直後や lockfile が変わったときは `npm ci` する |
| `--verify [--version V] [--commit SHA]` | 公開せずに照合する。一致すれば 0、しなければ理由を標準エラーに出して 1 |

完了の記録 `target/local/hx4s-publish.json` の例（時刻は入れないので、同じ commit から同じ版で公開すれば同じ内容になる）:

```json
{
  "format": 1,
  "version": "0.1.0-local.gd140b21886",
  "commit": "d140b21886ae6f454bf625cdd1dcf549b3d79010",
  "dirty": false,
  "dirtyHash": null,
  "maven": [
    { "organization": "io.github.montereylangit", "name": "hx4s-codegen_3", "version": "0.1.0-local.gd140b21886", "path": "io.github.montereylangit/hx4s-codegen_3/0.1.0-local.gd140b21886" },
    { "organization": "io.github.montereylangit", "name": "sbt-hx4s", "version": "0.1.0-local.gd140b21886", "path": "io.github.montereylangit/sbt-hx4s/scala_2.12/sbt_1.0/0.1.0-local.gd140b21886" }
  ],
  "npm": { "name": "hx4s-vite", "file": "hx4s-vite.tgz", "sha256": "e1015d78…" }
}
```

（`maven` は実際には `hx4s-core_3`・`hx4s-core_sjs1_3`・`hx4s-react_sjs1_3` を含む 5 件。`path` は `~/.ivy2/local` からの相対）

`--verify` が見るのは次のとおり。

1. `hx4s-publish.json` があり、sbt・npm の記録と同じ版・commit・作業ツリーの状態であること
2. `--version` / `--commit` を渡したときは、記録の版・commit がそれと同じであること（`--commit` は 40 桁の SHA だけを受け付ける）
3. 記録の commit が今の HEAD と同じで、公開対象のソース（`build.sbt`・`project`・`modules`・`sbt-plugin/src/main`・`vite`・`LICENSE`・`NOTICE`。テストを除く）の未コミットの変更が記録と同じであること
4. `~/.ivy2/local` に記録の成果物（`ivys/ivy.xml`）があり、`hx4s-vite.tgz` の SHA-256 が記録と同じであること
5. `hx4s-version.txt` が記録の版を持つこと

`~/.ivy2/local` 以外（`sbt.ivy.home` を変えた環境）は扱わない。

## 2. 利用側の注意

- **`--no-save` で入れた tgz は `npm ci` で消える。** `npm ci` は `node_modules` を作り直し、lockfile に無いパッケージを残さない。
  `npm ci` の後には毎回 `npm install --no-save <tgz>` をやり直す。下のセットアップスクリプトはこの順で両方を行う。
- tgz を `package.json` に `file:` で書くと、lockfile に tgz の整合性ハッシュが入り、hx4s を公開し直すたびに lockfile が変わる。固定は下の lock ファイルで行い、npm の lockfile には載せない。
- 版は commit ごとに変える（既定の `0.1.0-local.g<commit 10 桁>` を使う）。同じ版で中身を変えると、sbt の依存解決が古い成果物を返すことがある。
- Windows では、npm・sbt をシェル経由で起動すると、パスや引数の空白・`&` で壊れる。Node から起動するときは `process.execPath` で npm-cli.js を直接起動する（下の例）。

## 3. 推奨手順

利用側のリポジトリに、hx4s の commit（完全な SHA）と版を書いた lock ファイルを置く。

```properties
# hx4s.lock（利用側のリポジトリ直下）
commit=d140b21886ae6f454bf625cdd1dcf549b3d79010
version=0.1.0-local.gd140b21886
```

セットアップスクリプトは次の順で動く。

1. hx4s の checkout の HEAD が lock の commit と同じで、作業ツリーがクリーンであることを確かめる（違えば止まり、checkout の手順を出す）
2. `publish-local.mjs --verify --version <lock> --commit <lock>` が通れば公開を省く。通らなければ `publish-local.mjs --version <lock>` で公開し、もう一度 `--verify` する
3. 利用側の `npm ci` の後に hx4s-vite の tgz を `--no-save` で入れる

```js
// scripts/setup-hx4s.mjs（利用側。npm run から呼ぶか、node で直接呼ぶ）
// hx4s の場所は環境変数 HX4S_DIR（既定は ../hx4s）
import { execFileSync, spawnSync } from "node:child_process";
import { existsSync, readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const hx4s = path.resolve(root, process.env.HX4S_DIR ?? "../hx4s");
const lock = Object.fromEntries(readFileSync(path.join(root, "hx4s.lock"), "utf8").split(/\r?\n/)
  .map((line) => /^(\w+)=(.+)$/.exec(line.trim())).filter(Boolean).map((match) => [match[1], match[2]]));
if (!/^[0-9a-f]{40}$/.test(lock.commit ?? "") || !lock.version) throw new Error("hx4s.lock needs commit (40-character SHA) and version");

const git = (...args) => execFileSync("git", ["-C", hx4s, ...args], { encoding: "utf8" }).trim();
const publishLocal = (...args) =>
  spawnSync(process.execPath, [path.join(hx4s, "scripts", "publish-local.mjs"), ...args], { cwd: hx4s, stdio: "inherit" }).status === 0;
// npm-cli.js を Node で直接起動する（シェルを介さない）
const npmCli = [process.env.npm_execpath, path.join(path.dirname(process.execPath), "node_modules", "npm", "bin", "npm-cli.js"),
  path.join(path.dirname(process.execPath), "..", "lib", "node_modules", "npm", "bin", "npm-cli.js")]
  .find((file) => file && path.basename(file) === "npm-cli.js" && existsSync(file));
if (!npmCli) throw new Error("npm-cli.js was not found next to the running Node");
const npm = (...args) => {
  if (spawnSync(process.execPath, [npmCli, ...args], { cwd: root, stdio: "inherit" }).status !== 0) throw new Error(`npm ${args.join(" ")} failed`);
};

if (git("rev-parse", "HEAD") !== lock.commit) throw new Error(`${hx4s} is not at ${lock.commit}. Run: git -C "${hx4s}" checkout ${lock.commit}`);
if (git("status", "--porcelain", "--untracked-files=all") !== "") throw new Error(`${hx4s} has local changes`);

const expected = ["--version", lock.version, "--commit", lock.commit];
if (!publishLocal("--verify", ...expected)) {
  if (!publishLocal("--version", lock.version, "--reuse-node-modules")) throw new Error("hx4s publish-local failed");
  if (!publishLocal("--verify", ...expected)) throw new Error("hx4s was published but does not match hx4s.lock");
}

npm("ci", "--no-audit", "--no-fund");
npm("install", "--no-save", "--no-audit", "--no-fund", path.relative(root, path.join(hx4s, "target", "local", "hx4s-vite.tgz")).replaceAll("\\", "/"));
```

sbt 側は同じ lock ファイルから版を読む。

```scala
// project/plugins.sbt（利用側）
val hx4sLockedVersion: String = {
  val properties = new java.util.Properties
  IO.load(properties, file("hx4s.lock"))
  Option(properties.getProperty("version")).getOrElse(sys.error("hx4s.lock has no version"))
}

addSbtPlugin("io.github.montereylangit" % "sbt-hx4s" % hx4sLockedVersion)
```

`project/plugins.sbt` の作業ディレクトリはビルドのルートなので、`file("hx4s.lock")` はリポジトリ直下を指す。ライブラリの版は sbt-hx4s の `hx4sVersion` で同じ版になる。

hx4s を更新するときは、hx4s を新しい commit に進めて lock ファイルの `commit` と `version` を書き換え、セットアップスクリプトを流す。
`--verify` が失敗する（commit が違う）ので公開し直される。
